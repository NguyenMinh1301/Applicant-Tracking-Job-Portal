package com.vietrecruit.feature.candidate.service.impl;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.storage.StorageService;
import com.vietrecruit.feature.ai.shared.event.CvUploadedEvent;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.candidate.dto.request.CandidateUpdateRequest;
import com.vietrecruit.feature.candidate.dto.response.CandidateProfileResponse;
import com.vietrecruit.feature.candidate.dto.response.CandidateSearchResult;
import com.vietrecruit.feature.candidate.dto.response.CvUploadResponse;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.mapper.CandidateMapper;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.candidate.service.CandidateService;
import com.vietrecruit.feature.user.repository.UserRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateServiceImpl implements CandidateService {

    private static final long MAX_CV_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "image/jpeg",
                    "image/png");

    private final CandidateRepository candidateRepository;
    private final CandidateMapper candidateMapper;
    private final StorageService storageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Override
    public CandidateProfileResponse getProfile(UUID userId) {
        Candidate candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));
        return candidateMapper.toResponse(candidate);
    }

    @Override
    @Transactional
    public CandidateProfileResponse updateProfile(UUID userId, CandidateUpdateRequest request) {
        Candidate candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));

        candidateMapper.updateEntity(request, candidate);
        candidateRepository.save(candidate);
        return candidateMapper.toResponse(candidate);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "uploadCvFallback")
    public CvUploadResponse uploadCv(UUID userId, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ApiErrorCode.CANDIDATE_CV_INVALID_TYPE);
        }

        if (file.getSize() > MAX_CV_SIZE_BYTES) {
            throw new ApiException(ApiErrorCode.CANDIDATE_CV_SIZE_EXCEEDED);
        }

        Candidate candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));

        String oldCvUrl = candidate.getDefaultCvUrl();

        String sanitizedFilename = sanitizeFilename(file.getOriginalFilename());
        String objectKey =
                String.format("candidates/%s/%s-%s", userId, UUID.randomUUID(), sanitizedFilename);

        // Upload to R2 first (outside DB save try-catch for compensation)
        String publicUrl;
        try {
            publicUrl =
                    storageService.upload(
                            objectKey, file.getInputStream(), contentType, file.getSize());
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to read upload file", e);
        }

        Instant now = Instant.now();
        candidate.setDefaultCvUrl(publicUrl);
        candidate.setCvOriginalFilename(sanitizedFilename);
        candidate.setCvContentType(contentType);
        candidate.setCvFileSizeBytes(file.getSize());
        candidate.setCvUploadedAt(now);

        // DB save with compensation: delete R2 object if DB fails
        try {
            candidateRepository.save(candidate);
        } catch (Exception e) {
            log.error("DB save failed after R2 upload, compensating: key={}", objectKey);
            try {
                storageService.delete(objectKey);
            } catch (Exception deleteEx) {
                log.error(
                        "Compensation delete failed for key={}: {}",
                        objectKey,
                        deleteEx.getMessage());
            }
            throw e;
        }

        // Resolve email before afterCommit (session may be closed after commit)
        String candidateEmail =
                userRepository.findById(candidate.getUserId()).map(u -> u.getEmail()).orElse(null);
        if (candidateEmail == null) {
            log.error("AI ingestion: cannot resolve email for candidateId={}", candidate.getId());
        }

        // Publish Kafka event only after transaction commits successfully
        final UUID candidateId = candidate.getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            CvUploadedEvent event =
                                    new CvUploadedEvent(candidateId, objectKey, candidateEmail);
                            kafkaTemplate
                                    .send("ai.cv-uploaded", candidateId.toString(), event)
                                    .whenComplete(
                                            (result, ex) -> {
                                                if (ex != null) {
                                                    log.warn(
                                                            "Failed to publish CV uploaded event: candidateId={}",
                                                            candidateId,
                                                            ex);
                                                }
                                            });
                        } catch (Exception e) {
                            log.warn(
                                    "Failed to publish CV uploaded event: candidateId={}",
                                    candidateId,
                                    e);
                        }
                    }
                });

        // Delete old CV only after DB commit succeeds — prevents permanent loss on rollback
        if (oldCvUrl != null) {
            String oldKey = extractObjectKey(oldCvUrl);
            if (oldKey != null) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                try {
                                    storageService.delete(oldKey);
                                } catch (Exception e) {
                                    log.warn("Failed to delete old CV: key={}", oldKey, e);
                                }
                            }
                        });
            }
        }

        return CvUploadResponse.builder()
                .cvUrl(publicUrl)
                .cvOriginalFilename(sanitizedFilename)
                .cvContentType(contentType)
                .cvFileSizeBytes(file.getSize())
                .cvUploadedAt(now)
                .build();
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "deleteCvFallback")
    public void deleteCv(UUID userId) {
        Candidate candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));

        String cvUrl = candidate.getDefaultCvUrl();
        if (cvUrl != null) {
            String objectKey = extractObjectKey(cvUrl);
            if (objectKey != null) {
                storageService.delete(objectKey);
            }
        }

        candidate.setDefaultCvUrl(null);
        candidate.setCvOriginalFilename(null);
        candidate.setCvContentType(null);
        candidate.setCvFileSizeBytes(null);
        candidate.setCvUploadedAt(null);
        candidateRepository.save(candidate);
    }

    @Override
    public CandidateProfileResponse getById(UUID candidateId) {
        Candidate candidate =
                candidateRepository
                        .findByIdAndDeletedAtIsNull(candidateId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));
        return candidateMapper.toResponse(candidate);
    }

    /**
     * Strips path components and limits filename length to prevent path traversal and storage
     * issues.
     */
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unnamed";
        }
        String name = originalFilename.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name;
    }

    /**
     * Extracts the R2 object key from a public URL. Assumes URL format: {publicUrl}/{objectKey}.
     */
    private String extractObjectKey(String url) {
        if (url == null) return null;
        int idx = url.indexOf("candidates/");
        if (idx < 0) return null;
        return url.substring(idx);
    }

    @SuppressWarnings("unused")
    private CvUploadResponse uploadCvFallback(UUID userId, MultipartFile file, Throwable t) {
        log.error("R2 upload circuit breaker triggered for userId={}: {}", userId, t.getMessage());
        throw new ApiException(
                ApiErrorCode.STORAGE_UNAVAILABLE,
                "File storage is temporarily unavailable. Please try again later.");
    }

    @SuppressWarnings("unused")
    private void deleteCvFallback(UUID userId, Throwable t) {
        log.warn(
                "R2 delete circuit breaker triggered for userId={}: {}. "
                        + "CV metadata cleared, orphaned object may remain.",
                userId,
                t.getMessage());
    }

    @Override
    public Optional<Candidate> findActiveCandidateById(UUID candidateId) {
        return candidateRepository.findByIdAndDeletedAtIsNull(candidateId);
    }

    @Override
    public List<CandidateSearchResult> searchCandidates(
            String skills, String desiredPosition, Short minYearsExperience, int limit) {
        Specification<Candidate> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.isNull(root.get("deletedAt")));
                    predicates.add(cb.equal(root.get("isOpenToWork"), true));

                    if (desiredPosition != null && !desiredPosition.isBlank()) {
                        predicates.add(
                                cb.like(
                                        cb.lower(root.get("desiredPosition")),
                                        "%" + desiredPosition.toLowerCase() + "%"));
                    }
                    if (minYearsExperience != null) {
                        predicates.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("yearsOfExperience"), minYearsExperience));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<Candidate> page = candidateRepository.findAll(spec, PageRequest.of(0, limit));
        var stream = page.getContent().stream();

        if (skills != null && !skills.isBlank()) {
            String[] requiredSkills =
                    Arrays.stream(skills.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .toArray(String[]::new);
            stream =
                    stream.filter(
                            c -> {
                                if (c.getSkills() == null) return false;
                                List<String> candidateSkills =
                                        Arrays.stream(c.getSkills())
                                                .map(String::toLowerCase)
                                                .toList();
                                return Arrays.stream(requiredSkills)
                                        .anyMatch(candidateSkills::contains);
                            });
        }

        return stream.map(
                        c ->
                                new CandidateSearchResult(
                                        c.getId(),
                                        c.getDesiredPosition(),
                                        c.getYearsOfExperience(),
                                        c.getSkills()))
                .toList();
    }

    @Override
    public List<CandidateSearchResult> searchCandidatesForCompany(
            String skills,
            String desiredPosition,
            Short minYearsExperience,
            int limit,
            UUID companyId) {
        List<UUID> applicantIds = applicationRepository.findCandidateIdsByCompanyId(companyId);
        if (applicantIds.isEmpty()) {
            return List.of();
        }

        Specification<Candidate> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.isNull(root.get("deletedAt")));
                    predicates.add(root.get("id").in(applicantIds));

                    if (desiredPosition != null && !desiredPosition.isBlank()) {
                        predicates.add(
                                cb.like(
                                        cb.lower(root.get("desiredPosition")),
                                        "%" + desiredPosition.toLowerCase() + "%"));
                    }
                    if (minYearsExperience != null) {
                        predicates.add(
                                cb.greaterThanOrEqualTo(
                                        root.get("yearsOfExperience"), minYearsExperience));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<Candidate> page = candidateRepository.findAll(spec, PageRequest.of(0, limit));
        var stream = page.getContent().stream();

        if (skills != null && !skills.isBlank()) {
            String[] requiredSkills =
                    Arrays.stream(skills.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .toArray(String[]::new);
            stream =
                    stream.filter(
                            c -> {
                                if (c.getSkills() == null) return false;
                                List<String> candidateSkills =
                                        Arrays.stream(c.getSkills())
                                                .map(String::toLowerCase)
                                                .toList();
                                return Arrays.stream(requiredSkills)
                                        .anyMatch(candidateSkills::contains);
                            });
        }

        return stream.map(
                        c ->
                                new CandidateSearchResult(
                                        c.getId(),
                                        c.getDesiredPosition(),
                                        c.getYearsOfExperience(),
                                        c.getSkills()))
                .toList();
    }
}

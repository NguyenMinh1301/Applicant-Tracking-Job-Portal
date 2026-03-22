package com.vietrecruit.feature.application.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.feature.application.dto.request.ApplicationCreateRequest;
import com.vietrecruit.feature.application.dto.request.ApplicationStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.ApplicationResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationStatusHistoryResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationSummaryResponse;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.entity.ApplicationStatusHistory;
import com.vietrecruit.feature.application.enums.ApplicationStatus;
import com.vietrecruit.feature.application.mapper.ApplicationMapper;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.repository.ApplicationStatusHistoryRepository;
import com.vietrecruit.feature.application.service.ApplicationService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final ApplicationMapper applicationMapper;
    private final NotificationService notificationService;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    ApplicationStatus.NEW,
                            Set.of(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED),
                    ApplicationStatus.SCREENING,
                            Set.of(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED),
                    ApplicationStatus.INTERVIEW,
                            Set.of(ApplicationStatus.OFFER, ApplicationStatus.REJECTED));

    @Override
    @Transactional
    public ApplicationResponse apply(UUID userId, ApplicationCreateRequest request) {
        var candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.CANDIDATE_NOT_FOUND,
                                                "Candidate profile not found. Complete your profile first."));

        if (candidate.getDefaultCvUrl() == null || candidate.getDefaultCvUrl().isBlank()) {
            throw new ApiException(
                    ApiErrorCode.APPLICATION_CV_REQUIRED, "Please upload your CV before applying");
        }

        var job =
                jobRepository
                        .findByIdAndStatusAndDeletedAtIsNull(
                                request.getJobId(), JobStatus.PUBLISHED)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.JOB_NOT_PUBLISHED));

        if (applicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId())) {
            throw new ApiException(ApiErrorCode.APPLICATION_DUPLICATE);
        }

        var application =
                Application.builder()
                        .jobId(job.getId())
                        .candidateId(candidate.getId())
                        .appliedCvUrl(candidate.getDefaultCvUrl())
                        .coverLetter(request.getCoverLetter())
                        .status(ApplicationStatus.NEW)
                        .build();

        application = applicationRepository.save(application);

        insertHistory(application.getId(), null, ApplicationStatus.NEW, userId, null);

        sendApplicationSubmittedNotification(job, candidate, userId);

        return enrichResponse(application, job, candidate, userId);
    }

    @Override
    public ApplicationResponse getApplication(UUID applicationId, UUID userId) {
        var application =
                applicationRepository
                        .findByIdAndDeletedAtIsNull(applicationId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found"));

        var job =
                jobRepository
                        .findById(application.getJobId())
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "Job not found"));

        // Dual access control
        boolean isHr =
                user.getCompanyId() != null && user.getCompanyId().equals(job.getCompanyId());
        boolean isCandidate = isCandidateOwner(application.getCandidateId(), userId);

        if (!isHr && !isCandidate) {
            throw new ApiException(
                    ApiErrorCode.FORBIDDEN, "You do not have access to this application");
        }

        return enrichResponse(application, job, userId);
    }

    @Override
    public PageResponse<ApplicationSummaryResponse> listApplications(
            UUID companyId, UUID jobId, ApplicationStatus status, Pageable pageable) {

        var page = applicationRepository.findByCompanyFiltered(companyId, jobId, status, pageable);
        var content = page.getContent();
        var summaries = content.stream().map(applicationMapper::toSummaryResponse).toList();
        batchEnrichSummaries(summaries, content);
        return PageResponse.from(page.map(a -> summaries.get(content.indexOf(a))));
    }

    @Override
    public PageResponse<ApplicationSummaryResponse> listMyApplications(
            UUID userId, Pageable pageable) {

        var page = applicationRepository.findByUserId(userId, pageable);
        var content = page.getContent();
        var summaries = content.stream().map(applicationMapper::toSummaryResponse).toList();
        batchEnrichSummaries(summaries, content);
        return PageResponse.from(page.map(a -> summaries.get(content.indexOf(a))));
    }

    @Override
    @Transactional
    public ApplicationResponse updateStatus(
            UUID applicationId,
            UUID companyId,
            UUID userId,
            ApplicationStatusUpdateRequest request) {

        var application =
                applicationRepository
                        .findByIdAndCompanyId(applicationId, companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        var currentStatus = application.getStatus();
        var targetStatus = request.getStatus();

        var allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new ApiException(
                    ApiErrorCode.APPLICATION_INVALID_TRANSITION,
                    String.format("Cannot transition from %s to %s", currentStatus, targetStatus));
        }

        application.setStatus(targetStatus);
        try {
            application = applicationRepository.save(application);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ApiException(ApiErrorCode.CONCURRENT_MODIFICATION);
        }

        insertHistory(applicationId, currentStatus, targetStatus, userId, request.getNotes());

        // Load related entities once for notification instead of re-querying inside
        var job = jobRepository.findById(application.getJobId()).orElse(null);
        var candidateEntity =
                candidateRepository.findByIdAndDeletedAtIsNull(application.getCandidateId());
        var candidateUser =
                candidateEntity.flatMap(c -> userRepository.findById(c.getUserId())).orElse(null);

        sendStatusChangedNotification(application, job, candidateUser);

        return enrichResponse(application, userId);
    }

    @Override
    public List<ApplicationStatusHistoryResponse> getStatusHistory(
            UUID applicationId, UUID companyId) {

        applicationRepository
                .findByIdAndCompanyId(applicationId, companyId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));

        return historyRepository.findByApplicationIdOrderByChangedAtDesc(applicationId).stream()
                .map(
                        h -> {
                            var resp =
                                    ApplicationStatusHistoryResponse.builder()
                                            .id(h.getId())
                                            .oldStatus(h.getOldStatus())
                                            .newStatus(h.getNewStatus())
                                            .notes(h.getNotes())
                                            .changedAt(h.getChangedAt())
                                            .build();
                            if (h.getChangedBy() != null) {
                                userRepository
                                        .findById(h.getChangedBy())
                                        .ifPresent(u -> resp.setChangedByName(u.getFullName()));
                            }
                            return resp;
                        })
                .toList();
    }

    // ── Internal helpers ───────────────────────────────────────────────

    @Override
    public void insertHistory(
            UUID applicationId,
            ApplicationStatus oldStatus,
            ApplicationStatus newStatus,
            UUID changedBy,
            String notes) {

        var history =
                ApplicationStatusHistory.builder()
                        .applicationId(applicationId)
                        .oldStatus(oldStatus)
                        .newStatus(newStatus)
                        .changedBy(changedBy)
                        .notes(notes)
                        .changedAt(Instant.now())
                        .build();

        historyRepository.save(history);
    }

    private boolean isCandidateOwner(UUID candidateId, UUID userId) {
        return candidateRepository
                .findByIdAndDeletedAtIsNull(candidateId)
                .map(c -> c.getUserId().equals(userId))
                .orElse(false);
    }

    private ApplicationResponse enrichResponse(
            Application application, Job job, Candidate candidate, UUID userId) {

        var resp = applicationMapper.toResponse(application);
        resp.setJobTitle(job.getTitle());
        var user = userRepository.findById(candidate.getUserId()).orElse(null);
        resp.setCandidateName(user != null ? user.getFullName() : null);
        return resp;
    }

    private ApplicationResponse enrichResponse(Application application, Job job, UUID userId) {
        var resp = applicationMapper.toResponse(application);
        resp.setJobTitle(job.getTitle());
        enrichCandidateName(resp, application.getCandidateId());
        return resp;
    }

    private ApplicationResponse enrichResponse(Application application, UUID userId) {
        var resp = applicationMapper.toResponse(application);
        enrichJobTitle(resp, application.getJobId());
        enrichCandidateName(resp, application.getCandidateId());
        return resp;
    }

    private void enrichJobTitle(ApplicationResponse resp, UUID jobId) {
        jobRepository.findById(jobId).ifPresent(j -> resp.setJobTitle(j.getTitle()));
    }

    private void enrichCandidateName(ApplicationResponse resp, UUID candidateId) {
        candidateRepository
                .findByIdAndDeletedAtIsNull(candidateId)
                .ifPresent(
                        c ->
                                userRepository
                                        .findById(c.getUserId())
                                        .ifPresent(u -> resp.setCandidateName(u.getFullName())));
    }

    /**
     * Batch-enriches summary responses to avoid N+1 queries. Collects all jobIds and candidateIds,
     * fetches them in bulk, then populates the responses from in-memory maps.
     */
    private void batchEnrichSummaries(
            List<ApplicationSummaryResponse> summaries, List<Application> applications) {

        if (summaries.isEmpty()) return;

        // Collect all unique IDs
        var jobIds =
                applications.stream()
                        .map(Application::getJobId)
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
        var candidateIds =
                applications.stream()
                        .map(Application::getCandidateId)
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());

        // Batch fetch
        var jobMap =
                jobRepository.findAllById(jobIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Job::getId, j -> j));
        var candidateMap =
                candidateRepository.findAllById(candidateIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Candidate::getId, c -> c));

        // Batch fetch users for candidates
        var userIds =
                candidateMap.values().stream()
                        .map(Candidate::getUserId)
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
        var userMap =
                userRepository.findAllById(userIds).stream()
                        .collect(java.util.stream.Collectors.toMap(u -> u.getId(), u -> u));

        // Enrich from maps
        for (int i = 0; i < summaries.size(); i++) {
            var resp = summaries.get(i);
            var app = applications.get(i);

            var job = jobMap.get(app.getJobId());
            if (job != null) {
                resp.setJobTitle(job.getTitle());
            }

            var candidate = candidateMap.get(app.getCandidateId());
            if (candidate != null) {
                var user = userMap.get(candidate.getUserId());
                if (user != null) {
                    resp.setCandidateName(user.getFullName());
                }
            }
        }
    }

    // ── Notifications ──────────────────────────────────────────────────

    private void sendApplicationSubmittedNotification(Job job, Candidate candidate, UUID userId) {
        try {
            // Find HR users for this company to notify
            var user = userRepository.findById(userId).orElse(null);
            var candidateName = user != null ? user.getFullName() : "A candidate";

            // Notify job creator (HR)
            if (job.getCreatedBy() != null) {
                var hrUser = userRepository.findById(job.getCreatedBy()).orElse(null);
                if (hrUser != null && hrUser.getEmail() != null) {
                    notificationService.send(
                            new EmailRequest(
                                    List.of(hrUser.getEmail()),
                                    EmailSenderAlias.NOTIFICATION,
                                    "New Application for " + job.getTitle(),
                                    null,
                                    "application-submitted",
                                    Map.of(
                                            "jobTitle", job.getTitle(),
                                            "candidateName", candidateName,
                                            "hrName", hrUser.getFullName())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send application submitted notification", e);
        }
    }

    private void sendStatusChangedNotification(
            Application application,
            com.vietrecruit.feature.job.entity.Job job,
            com.vietrecruit.feature.user.entity.User candidateUser) {
        try {
            if (candidateUser == null || candidateUser.getEmail() == null) return;

            var jobTitle = job != null ? job.getTitle() : "a position";

            notificationService.send(
                    new EmailRequest(
                            List.of(candidateUser.getEmail()),
                            EmailSenderAlias.NOTIFICATION,
                            "Application Status Update — " + jobTitle,
                            null,
                            "application-status-changed",
                            Map.of(
                                    "candidateName", candidateUser.getFullName(),
                                    "jobTitle", jobTitle,
                                    "newStatus", application.getStatus().name())));
        } catch (Exception e) {
            log.warn("Failed to send status changed notification", e);
        }
    }
}

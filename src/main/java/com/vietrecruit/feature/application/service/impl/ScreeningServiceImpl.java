package com.vietrecruit.feature.application.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.application.dto.response.ApplicationScreeningResponse;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.mapper.ScreeningMapper;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.service.ScreeningScoringExecutor;
import com.vietrecruit.feature.application.service.ScreeningService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningServiceImpl implements ScreeningService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final EmbeddingService embeddingService;
    private final ScreeningScoringExecutor scoringExecutor;
    private final ScreeningMapper screeningMapper;

    private static final int TOP_K_CANDIDATES = 20;
    private static final int SCREENING_BATCH_SIZE = 100;

    @Override
    public List<ApplicationScreeningResponse> screenApplications(UUID jobId, UUID companyId) {
        verifyJobOwnership(jobId, companyId);

        List<Application> applications = applicationRepository.findByJobIdAndDeletedAtIsNull(jobId);
        if (applications.isEmpty()) {
            return List.of();
        }

        Map<UUID, Candidate> candidateMap = buildCandidateMap(applications);
        Map<UUID, User> userMap = buildUserMap(candidateMap);

        List<ApplicationScreeningResponse> scored = new ArrayList<>();
        List<ApplicationScreeningResponse> unscored = new ArrayList<>();

        for (Application app : applications) {
            Candidate candidate = candidateMap.get(app.getCandidateId());
            User user = candidate != null ? userMap.get(candidate.getUserId()) : null;

            ApplicationScreeningResponse response = buildResponse(app, candidate, user);

            if (app.getAiScore() != null) {
                scored.add(response);
            } else {
                unscored.add(response);
            }
        }

        scored.sort(Comparator.comparing(ApplicationScreeningResponse::getAiScore).reversed());
        unscored.sort(
                Comparator.comparing(
                        (ApplicationScreeningResponse r) -> r.getApplicationId().toString()));

        List<ApplicationScreeningResponse> result =
                new ArrayList<>(scored.size() + unscored.size());
        result.addAll(scored);
        result.addAll(unscored);
        return result;
    }

    @Async("aiTaskExecutor")
    @Override
    public void triggerAsyncScoring(UUID jobId, UUID companyId) {
        try {
            Job job = verifyJobOwnership(jobId, companyId);

            String jobContent = buildJobContent(job);

            Filter.Expression cvFilter =
                    new Filter.Expression(
                            Filter.ExpressionType.EQ,
                            new Filter.Key("type"),
                            new Filter.Value("cv"));
            List<Document> similarDocs =
                    embeddingService.search(jobContent, TOP_K_CANDIDATES, cvFilter);

            Set<UUID> topCandidateIds =
                    similarDocs.stream()
                            .map(
                                    doc -> {
                                        String cid = (String) doc.getMetadata().get("candidateId");
                                        return cid != null ? UUID.fromString(cid) : null;
                                    })
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());

            Map<UUID, Double> similarityScores = new HashMap<>();
            for (Document doc : similarDocs) {
                String cid = (String) doc.getMetadata().get("candidateId");
                if (cid != null && doc.getScore() != null) {
                    similarityScores.put(UUID.fromString(cid), doc.getScore());
                }
            }

            // Process unscored applications in fixed-size batches to avoid full-table load
            int pageNum = 0;
            int scored = 0;
            while (true) {
                var page =
                        applicationRepository.findByJobIdAndAiScoreIsNullAndDeletedAtIsNull(
                                jobId, PageRequest.of(pageNum, SCREENING_BATCH_SIZE));
                if (page.isEmpty()) {
                    break;
                }

                Map<UUID, Candidate> candidateMap = buildCandidateMap(page.getContent());

                for (Application app : page.getContent()) {
                    if (!topCandidateIds.contains(app.getCandidateId())) {
                        continue;
                    }
                    Candidate candidate = candidateMap.get(app.getCandidateId());
                    if (candidate == null) {
                        continue;
                    }
                    scoringExecutor.scoreApplication(
                            app, job, candidate, similarityScores.get(app.getCandidateId()));
                    scored++;
                }

                if (!page.hasNext()) {
                    break;
                }
                pageNum++;
            }

            log.info("Screening: completed scoring {} applications for jobId={}", scored, jobId);
        } catch (Exception e) {
            log.error(
                    "Screening: async scoring failed for jobId={}, error={}",
                    jobId,
                    e.getMessage());
        }
    }

    private Job verifyJobOwnership(UUID jobId, UUID companyId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Job does not belong to your company");
        }
        if (job.getDeletedAt() != null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "Job not found");
        }
        return job;
    }

    private Map<UUID, Candidate> buildCandidateMap(List<Application> applications) {
        List<UUID> candidateIds =
                applications.stream().map(Application::getCandidateId).distinct().toList();
        return candidateRepository.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(Candidate::getId, Function.identity()));
    }

    private Map<UUID, User> buildUserMap(Map<UUID, Candidate> candidateMap) {
        List<UUID> userIds =
                candidateMap.values().stream().map(Candidate::getUserId).distinct().toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ApplicationScreeningResponse buildResponse(
            Application app, Candidate candidate, User user) {
        ApplicationScreeningResponse response = screeningMapper.toScreeningResponse(app, user);

        if (app.getAiScoreBreakdown() != null) {
            applyBreakdownFields(response, app.getAiScoreBreakdown());
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private void applyBreakdownFields(
            ApplicationScreeningResponse response, Map<String, Object> breakdown) {
        Object breakdownObj = breakdown.get("breakdown");
        if (breakdownObj instanceof Map<?, ?> bdMap) {
            Map<String, Integer> scoreBreakdown = new HashMap<>();
            bdMap.forEach(
                    (k, v) -> {
                        if (v instanceof Number num) {
                            scoreBreakdown.put(k.toString(), num.intValue());
                        }
                    });
            response.setScoreBreakdown(scoreBreakdown);
        }
        if (breakdown.get("strengths") instanceof List<?> strengthsList) {
            response.setStrengths(strengthsList.stream().map(Object::toString).toList());
        }
        if (breakdown.get("gaps") instanceof List<?> gapsList) {
            response.setGaps(gapsList.stream().map(Object::toString).toList());
        }
        if (breakdown.get("summary") instanceof String summaryStr) {
            response.setSummary(summaryStr);
        }
        if (breakdown.get("similarityScore") instanceof Number simNum) {
            response.setSimilarityScore(simNum.doubleValue());
        }
    }

    private String buildJobContent(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append(job.getTitle());
        if (job.getDescription() != null) {
            sb.append(" ").append(job.getDescription());
        }
        if (job.getRequirements() != null) {
            sb.append(" ").append(job.getRequirements());
        }
        return sb.toString();
    }
}

package com.vietrecruit.feature.candidate.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.embedding.EmbeddingService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.mapper.RecommendationMapper;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.candidate.service.RecommendationService;
import com.vietrecruit.feature.job.dto.response.JobRecommendationResponse;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.repository.JobRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final String FALLBACK_REASON =
            "Ph\u00f9 h\u1ee3p v\u1edbi k\u1ef9 n\u0103ng v\u00e0 kinh nghi\u1ec7m c\u1ee7a b\u1ea1n.";

    private final EmbeddingService embeddingService;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final ChatClient ragChatClient;
    private final ObjectMapper objectMapper;
    private final RecommendationMapper recommendationMapper;

    public RecommendationServiceImpl(
            EmbeddingService embeddingService,
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            ObjectMapper objectMapper,
            RecommendationMapper recommendationMapper) {
        this.embeddingService = embeddingService;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.ragChatClient = ragChatClient;
        this.objectMapper = objectMapper;
        this.recommendationMapper = recommendationMapper;
    }

    @Override
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "getJobRecommendationsFallback")
    public List<JobRecommendationResponse> getJobRecommendations(UUID userId, int limit) {
        Candidate candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));
        UUID candidateId = candidate.getId();

        Filter.Expression cvFilter =
                new Filter.Expression(
                        Filter.ExpressionType.AND,
                        new Filter.Expression(
                                Filter.ExpressionType.EQ,
                                new Filter.Key("type"),
                                new Filter.Value("cv")),
                        new Filter.Expression(
                                Filter.ExpressionType.EQ,
                                new Filter.Key("candidateId"),
                                new Filter.Value(candidateId.toString())));
        List<Document> cvDocs = embeddingService.search("", 1, cvFilter);
        if (cvDocs.isEmpty()) {
            return List.of();
        }

        String cvContent = cvDocs.get(0).getText();
        Filter.Expression jobFilter =
                new Filter.Expression(
                        Filter.ExpressionType.EQ, new Filter.Key("type"), new Filter.Value("job"));
        List<Document> jobDocs = embeddingService.search(cvContent, limit, jobFilter);
        if (jobDocs.isEmpty()) {
            return List.of();
        }

        List<UUID> jobIds =
                jobDocs.stream()
                        .map(
                                doc -> {
                                    String jid = (String) doc.getMetadata().get("jobId");
                                    return jid != null ? UUID.fromString(jid) : null;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        Map<UUID, Job> jobMap =
                jobRepository.findAllByIdInAndDeletedAtIsNull(jobIds).stream()
                        .filter(job -> job.getStatus() == JobStatus.PUBLISHED)
                        .collect(Collectors.toMap(Job::getId, Function.identity()));

        if (jobMap.isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> scoreMap = new HashMap<>();
        for (Document doc : jobDocs) {
            String jid = (String) doc.getMetadata().get("jobId");
            if (jid != null && doc.getScore() != null) {
                scoreMap.put(UUID.fromString(jid), doc.getScore());
            }
        }

        Map<String, String> reasonMap = generateMatchReasons(cvContent, jobMap);

        List<JobRecommendationResponse> results = new ArrayList<>();
        for (Map.Entry<UUID, Job> entry : jobMap.entrySet()) {
            UUID jobId = entry.getKey();
            Job job = entry.getValue();
            Double score = scoreMap.get(jobId);
            double roundedScore =
                    BigDecimal.valueOf(score != null ? score : 0.0)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();
            String reason = reasonMap.getOrDefault(jobId.toString(), "");

            results.add(recommendationMapper.toResponse(job, roundedScore, reason));
        }

        results.sort(Comparator.comparing(JobRecommendationResponse::matchScore).reversed());
        return results;
    }

    private Map<String, String> generateMatchReasons(String cvContent, Map<UUID, Job> jobMap) {
        try {
            String truncatedCv = cvContent.length() > 500 ? cvContent.substring(0, 500) : cvContent;

            StringBuilder jobsBlock = new StringBuilder();
            for (Map.Entry<UUID, Job> entry : jobMap.entrySet()) {
                Job job = entry.getValue();
                String desc = job.getDescription() != null ? job.getDescription() : "";
                if (desc.length() > 200) {
                    desc = desc.substring(0, 200);
                }
                jobsBlock
                        .append("jobId: ")
                        .append(entry.getKey())
                        .append(", title: ")
                        .append(job.getTitle())
                        .append(", description: ")
                        .append(desc)
                        .append("\n");
            }

            String prompt =
                    "Given this candidate profile:\n"
                            + truncatedCv
                            + "\n\n"
                            + "For each of these jobs, write exactly ONE sentence (max 20 words)"
                            + " explaining the best match reason.\n"
                            + "Respond as JSON array:"
                            + " [{\"jobId\":\"...\",\"reason\":\"...\"}]\n\n"
                            + "Jobs:\n"
                            + jobsBlock;

            MDC.put("ai_model", "recommendation");
            try {
                long startMs = System.currentTimeMillis();
                ChatResponse chatResponse =
                        ragChatClient.prompt().user(prompt).call().chatResponse();
                long durationMs = System.currentTimeMillis() - startMs;

                Usage usage = chatResponse.getMetadata().getUsage();
                log.info(
                        "ai_call model={} prompt_tokens={} completion_tokens={} total_tokens={}"
                                + " duration_ms={}",
                        chatResponse.getMetadata().getModel(),
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens(),
                        durationMs);

                String response = chatResponse.getResult().getOutput().getText();

                String cleaned = response.trim();
                if (cleaned.startsWith("```")) {
                    cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
                }

                List<Map<String, String>> parsed =
                        objectMapper.readValue(cleaned, new TypeReference<>() {});

                return parsed.stream()
                        .filter(m -> m.get("jobId") != null && m.get("reason") != null)
                        .collect(Collectors.toMap(m -> m.get("jobId"), m -> m.get("reason")));
            } finally {
                MDC.remove("ai_model");
            }
        } catch (Exception e) {
            log.warn("Failed to generate match reasons: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unused")
    private List<JobRecommendationResponse> getJobRecommendationsFallback(
            UUID userId, int limit, Throwable t) {
        log.warn(
                "Recommendation circuit breaker triggered for userId={}: {}",
                userId,
                t.getMessage());
        return List.of();
    }
}

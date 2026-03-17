package com.vietrecruit.feature.ai.cv.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.cv.dto.CvImprovementResponse;
import com.vietrecruit.feature.ai.cv.service.CvImprovementService;
import com.vietrecruit.feature.ai.shared.service.RagService;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.repository.JobRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CvImprovementServiceImpl implements CvImprovementService {

    private static final String CACHE_KEY_PREFIX = "ai:cv-improvement:";
    private static final long CACHE_TTL_HOURS = 6;
    // ~2000 tokens at ~4 chars/token
    private static final int CV_TEXT_MAX_CHARS = 8000;

    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final RagService ragService;
    private final ChatClient ragChatClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CvImprovementServiceImpl(
            CandidateRepository candidateRepository,
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            RagService ragService,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.candidateRepository = candidateRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.ragService = ragService;
        this.ragChatClient = ragChatClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "analyzeFallback")
    public CvImprovementResponse analyze(UUID userId) {
        Candidate candidate =
                candidateRepository
                        .findByUserIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));

        if (candidate.getParsedCvText() == null || candidate.getParsedCvText().isBlank()) {
            throw new ApiException(ApiErrorCode.CV_NOT_PARSED);
        }

        // Check Redis cache
        String cacheKey = CACHE_KEY_PREFIX + candidate.getId();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                CvImprovementResponse cachedResponse =
                        objectMapper.readValue(cached, CvImprovementResponse.class);
                Instant cvUploadedAt = candidate.getCvUploadedAt();
                if (cvUploadedAt != null && cvUploadedAt.isBefore(cachedResponse.analysedAt())) {
                    return cachedResponse;
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to deserialize cached cv-improvement response, regenerating."
                                + " cause={}",
                        e.getMessage());
            }
        }

        // Step 2: Get target job context from latest applications
        Page<Application> applications =
                applicationRepository.findByUserId(
                        userId, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "createdAt")));

        String targetJobSkills = "";
        if (!applications.isEmpty()) {
            List<UUID> jobIds =
                    applications.getContent().stream().map(Application::getJobId).toList();
            List<Job> jobs = jobRepository.findAllByIdInAndDeletedAtIsNull(jobIds);
            targetJobSkills =
                    jobs.stream()
                            .map(
                                    j ->
                                            j.getTitle()
                                                    + ": "
                                                    + (j.getRequirements() != null
                                                            ? j.getRequirements()
                                                            : ""))
                            .collect(Collectors.joining("\n"));
        }

        // Step 3: RAG knowledge retrieval
        String ragQuery = "how to write a strong CV for " + resolveTopSkills(candidate);
        List<Document> ragDocs = ragService.retrieveKnowledge(ragQuery, "cv-guide", 4);
        String ragChunks =
                ragDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        // Step 4-5: Build prompt and invoke LLM
        String cvText = truncate(candidate.getParsedCvText(), CV_TEXT_MAX_CHARS);
        CvImprovementResponse response = invokeAi(cvText, targetJobSkills, ragChunks);

        // Step 6: Cache result
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache cv-improvement response. cause={}", e.getMessage());
        }

        return response;
    }

    @SuppressWarnings("unused")
    public CvImprovementResponse analyzeFallback(UUID userId, Throwable t) {
        if (t instanceof ApiException apiEx) {
            throw apiEx;
        }
        log.warn("CV improvement circuit open. cause={}", t.getMessage());
        return new CvImprovementResponse(-1, List.of(), List.of(), Instant.now());
    }

    private CvImprovementResponse invokeAi(
            String cvText, String targetJobSkills, String ragChunks) {
        String prompt = buildPrompt(cvText, targetJobSkills, ragChunks);

        MDC.put("ai_model", "cv-improvement");
        try {
            long startMs = System.currentTimeMillis();
            ChatResponse response =
                    ragChatClient
                            .prompt()
                            .options(OpenAiChatOptions.builder().maxTokens(1024).build())
                            .user(prompt)
                            .call()
                            .chatResponse();
            long durationMs = System.currentTimeMillis() - startMs;

            Usage usage = response.getMetadata().getUsage();
            log.info(
                    "ai_call model={} prompt_tokens={} completion_tokens={} total_tokens={}"
                            + " duration_ms={}",
                    response.getMetadata().getModel(),
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens(),
                    durationMs);

            Generation result = response.getResult();
            if (result == null
                    || result.getOutput() == null
                    || result.getOutput().getText() == null
                    || result.getOutput().getText().isBlank()) {
                throw new ApiException(ApiErrorCode.AI_INVALID_RESPONSE);
            }

            return parseAiResponse(result.getOutput().getText());
        } finally {
            MDC.remove("ai_model");
        }
    }

    private CvImprovementResponse parseAiResponse(String raw) {
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").strip();
        }
        try {
            // Parse into response without analysedAt, then add it
            var node = objectMapper.readTree(json);
            int overallScore = node.path("overallScore").asInt(-1);
            List<CvSuggestionRaw> rawSuggestions =
                    objectMapper.convertValue(
                            node.path("suggestions"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, CvSuggestionRaw.class));
            List<String> strengths =
                    objectMapper.convertValue(
                            node.path("strengths"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));

            var suggestions =
                    rawSuggestions.stream()
                            .map(
                                    s ->
                                            new com.vietrecruit.feature.ai.cv.dto.CvSuggestion(
                                                    s.priority(),
                                                    s.section(),
                                                    s.issue(),
                                                    s.suggestion(),
                                                    s.source()))
                            .toList();

            return new CvImprovementResponse(overallScore, suggestions, strengths, Instant.now());
        } catch (Exception e) {
            log.error("Failed to parse AI cv-improvement response: {}", raw, e);
            throw new ApiException(ApiErrorCode.AI_INVALID_RESPONSE);
        }
    }

    private record CvSuggestionRaw(
            String priority, String section, String issue, String suggestion, String source) {}

    private String buildPrompt(String cvText, String targetJobSkills, String ragChunks) {
        return """
You are a professional CV coach with expertise in the Vietnamese tech job market.

CANDIDATE CV TEXT:
%s

TARGET ROLE CONTEXT (from jobs they applied to):
%s

KNOWLEDGE BASE (CV writing best practices):
%s

Analyze the CV and return a JSON object with this exact structure:
{
"overallScore": <integer 0-100>,
"suggestions": [
	{
	"priority": "HIGH" | "MEDIUM" | "LOW",
	"section": "summary" | "experience" | "skills" | "education" | "format",
	"issue": "<what is wrong, max 15 words>",
	"suggestion": "<specific actionable fix, max 40 words>",
	"source": "<knowledge chunk source section, or null>"
	}
],
"strengths": ["<strength 1>", "<strength 2>"]
}

Rules:
- Maximum 5 suggestions, ordered by priority (HIGH first)
- Be specific — never give generic advice
- Suggestions must reference actual CV content
- overallScore: 0-40 needs_work, 41-70 average, 71-100 strong
- Return ONLY the JSON object, no markdown, no preamble
"""
                .formatted(cvText, targetJobSkills, ragChunks);
    }

    private String resolveTopSkills(Candidate candidate) {
        if (candidate.getSkills() != null && candidate.getSkills().length > 0) {
            int limit = Math.min(5, candidate.getSkills().length);
            return String.join(", ", Arrays.copyOf(candidate.getSkills(), limit));
        }
        return candidate.getDesiredPosition() != null ? candidate.getDesiredPosition() : "";
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }
}

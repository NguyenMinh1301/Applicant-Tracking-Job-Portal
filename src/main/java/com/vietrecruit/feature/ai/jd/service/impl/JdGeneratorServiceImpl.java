package com.vietrecruit.feature.ai.jd.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.jd.dto.ApplyDescriptionRequest;
import com.vietrecruit.feature.ai.jd.dto.GeneratedJobDescription;
import com.vietrecruit.feature.ai.jd.dto.JdGenerateRequest;
import com.vietrecruit.feature.ai.jd.dto.JdGenerateResponse;
import com.vietrecruit.feature.ai.jd.service.JdGeneratorService;
import com.vietrecruit.feature.ai.shared.service.RagService;
import com.vietrecruit.feature.department.repository.DepartmentRepository;
import com.vietrecruit.feature.job.service.JobService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JdGeneratorServiceImpl implements JdGeneratorService {

    private static final int RAG_JD_TOP_K = 3;
    private static final int RAG_BIAS_TOP_K = 2;

    private final RagService ragService;
    private final DepartmentRepository departmentRepository;
    private final JobService jobService;
    private final ChatClient ragChatClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.jd.model:gpt-4o}")
    private String jdModel;

    @Value("${ai.jd.temperature:0.5}")
    private Double jdTemperature;

    public JdGeneratorServiceImpl(
            RagService ragService,
            DepartmentRepository departmentRepository,
            JobService jobService,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.departmentRepository = departmentRepository;
        this.jobService = jobService;
        this.ragChatClient = ragChatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "generateFallback")
    public JdGenerateResponse generate(JdGenerateRequest request, UUID companyId) {
        // Step 1 — validate optional departmentId belongs to the company
        if (request.departmentId() != null) {
            departmentRepository
                    .findByIdAndCompanyIdAndDeletedAtIsNull(request.departmentId(), companyId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.DEPARTMENT_NOT_FOUND));
        }

        // Step 2 — RAG: retrieve JD writing knowledge
        String jdQuery =
                "job description template " + request.title() + " " + request.employmentType();
        List<Document> jdDocs = ragService.retrieveKnowledge(jdQuery, "jd-template", RAG_JD_TOP_K);
        String ragChunks =
                jdDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        List<Document> biasDocs =
                ragService.retrieveKnowledge(
                        "inclusive job description bias-free language",
                        "jd-template",
                        RAG_BIAS_TOP_K);
        String biasChunks =
                biasDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        // Step 3-4 — Build prompt and call gpt-4o
        String prompt = buildPrompt(request, ragChunks, biasChunks);
        return invokeAi(request.title(), prompt);
    }

    @Override
    public void applyDescription(UUID jobId, UUID companyId, ApplyDescriptionRequest request) {
        String formatted = formatToText(request.generatedDescription());
        jobService.updateDescription(companyId, jobId, formatted);
    }

    @SuppressWarnings("unused")
    public JdGenerateResponse generateFallback(
            JdGenerateRequest request, UUID companyId, Throwable t) {
        if (t instanceof ApiException apiEx) {
            throw apiEx;
        }
        log.warn("JD generation circuit open. cause={}", t.getMessage());
        var placeholder =
                new GeneratedJobDescription(
                        "[FILL IN] We are looking for a " + request.title() + " to join our team.",
                        List.of(
                                "[FILL IN] Responsibility 1",
                                "[FILL IN] Responsibility 2",
                                "[FILL IN] Responsibility 3"),
                        List.of(
                                "[FILL IN] "
                                        + request.yearsOfExperience()
                                        + "+ years of relevant experience",
                                "[FILL IN] Proficiency in required skills"),
                        request.niceToHaveSkills() != null && !request.niceToHaveSkills().isEmpty()
                                ? request.niceToHaveSkills().stream()
                                        .map(s -> "[FILL IN] Experience with " + s)
                                        .toList()
                                : List.of("[FILL IN] Nice to have 1"),
                        null);
        return new JdGenerateResponse(request.title(), placeholder, List.of(), Instant.now());
    }

    private JdGenerateResponse invokeAi(String title, String prompt) {
        MDC.put("ai_model", "jd-generator");
        try {
            long startMs = System.currentTimeMillis();
            ChatResponse response =
                    ragChatClient
                            .prompt()
                            .options(
                                    OpenAiChatOptions.builder()
                                            .model(jdModel)
                                            .temperature(jdTemperature)
                                            .maxTokens(1500)
                                            .build())
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

            return parseAiResponse(title, result.getOutput().getText());
        } finally {
            MDC.remove("ai_model");
        }
    }

    private JdGenerateResponse parseAiResponse(String title, String raw) {
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").strip();
        }
        try {
            var node = objectMapper.readTree(json);
            String overview = node.path("overview").asText("");
            List<String> responsibilities =
                    objectMapper.convertValue(
                            node.path("responsibilities"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
            List<String> requirements =
                    objectMapper.convertValue(
                            node.path("requirements"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
            List<String> niceToHave =
                    objectMapper.convertValue(
                            node.path("niceToHave"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
            List<String> biasFlags =
                    objectMapper.convertValue(
                            node.path("biasFlags"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));

            var description =
                    new GeneratedJobDescription(
                            overview,
                            responsibilities != null ? responsibilities : List.of(),
                            requirements != null ? requirements : List.of(),
                            niceToHave != null ? niceToHave : List.of(),
                            null);

            return new JdGenerateResponse(
                    title, description, biasFlags != null ? biasFlags : List.of(), Instant.now());
        } catch (Exception e) {
            log.error("Failed to parse AI jd-generator response: {}", raw, e);
            throw new ApiException(ApiErrorCode.AI_INVALID_RESPONSE);
        }
    }

    private String buildPrompt(JdGenerateRequest req, String ragChunks, String biasChunks) {
        String responsibilities =
                req.keyResponsibilities().stream()
                        .map(r -> "  " + (req.keyResponsibilities().indexOf(r) + 1) + ". " + r)
                        .collect(Collectors.joining("\n"));
        String requiredSkills = String.join(", ", req.requiredSkills());
        String niceToHave =
                req.niceToHaveSkills() != null && !req.niceToHaveSkills().isEmpty()
                        ? String.join(", ", req.niceToHaveSkills())
                        : "None";

        return """
You are a professional HR copywriter specializing in inclusive, compelling job descriptions.

JOB INPUTS:
Title: %s
Employment type: %s
Key responsibilities:
%s
Required skills: %s
Nice-to-have: %s
Years of experience: %d
Tone: %s

JD WRITING KNOWLEDGE BASE:
%s

BIAS GUIDELINES:
%s

Write a complete job description and return this exact JSON:
{
"overview": "<2-3 sentence role summary, tone-appropriate, max 80 words>",
"responsibilities": ["<responsibility 1>", "<responsibility 2>"],
"requirements": ["<requirement 1>", "<requirement 2>"],
"niceToHave": ["<nice to have 1>"],
"biasFlags": ["<description of any bias found and how it was corrected>"]
}

Rules:
- Do NOT use: rockstar, ninja, guru, aggressive, dominant, seamless, passionate
- Do NOT specify gender, age, or marital status
- Requirements must start with action nouns: "Experience in...", "Proficiency in..."
- Responsibilities must start with action verbs: "Design...", "Lead...", "Build..."
- STARTUP tone: conversational, emphasize impact and growth
- PROFESSIONAL tone: formal, emphasize quality and stability
- CORPORATE tone: structured, emphasize process and scale
- responsibilities: up to 8 items
- requirements: up to 8 items
- niceToHave: up to 5 items
- biasFlags: list only if bias was detected, otherwise empty array
- Return ONLY the JSON object
"""
                .formatted(
                        req.title(),
                        req.employmentType(),
                        responsibilities,
                        requiredSkills,
                        niceToHave,
                        req.yearsOfExperience(),
                        req.tone().name(),
                        ragChunks,
                        biasChunks);
    }

    /**
     * Formats a GeneratedJobDescription into plain text suitable for storing in Job.description.
     */
    private String formatToText(GeneratedJobDescription d) {
        var sb = new StringBuilder();
        if (d.overview() != null && !d.overview().isBlank()) {
            sb.append(d.overview()).append("\n\n");
        }
        if (d.responsibilities() != null && !d.responsibilities().isEmpty()) {
            sb.append("Responsibilities:\n");
            d.responsibilities().forEach(r -> sb.append("- ").append(r).append("\n"));
            sb.append("\n");
        }
        if (d.requirements() != null && !d.requirements().isEmpty()) {
            sb.append("Requirements:\n");
            d.requirements().forEach(r -> sb.append("- ").append(r).append("\n"));
            sb.append("\n");
        }
        if (d.niceToHave() != null && !d.niceToHave().isEmpty()) {
            sb.append("Nice to Have:\n");
            d.niceToHave().forEach(n -> sb.append("- ").append(n).append("\n"));
        }
        return sb.toString().strip();
    }
}

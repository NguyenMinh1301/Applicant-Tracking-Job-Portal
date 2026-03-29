package com.vietrecruit.feature.ai.interview.service.impl;

import java.time.Instant;
import java.util.Arrays;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.interview.dto.InterviewQuestion;
import com.vietrecruit.feature.ai.interview.dto.InterviewQuestionResponse;
import com.vietrecruit.feature.ai.interview.entity.InterviewAiQuestion;
import com.vietrecruit.feature.ai.interview.repository.InterviewAiQuestionRepository;
import com.vietrecruit.feature.ai.interview.service.InterviewQuestionService;
import com.vietrecruit.feature.ai.shared.service.RagService;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.application.entity.Interview;
import com.vietrecruit.feature.application.repository.ApplicationRepository;
import com.vietrecruit.feature.application.repository.InterviewRepository;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.UserRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InterviewQuestionServiceImpl implements InterviewQuestionService {

    // ~800 tokens at ~4 chars/token
    private static final int CV_TEXT_MAX_CHARS = 3200;
    // ~500 tokens at ~4 chars/token
    private static final int JOB_DESC_MAX_CHARS = 2000;
    private static final int GAP_SKILLS_LIMIT = 10;

    private static final List<InterviewQuestion> GENERIC_FALLBACK_QUESTIONS =
            List.of(
                    new InterviewQuestion(
                            "TECHNICAL",
                            "Walk me through your most technically challenging project. What was the"
                                    + " stack and what problems did you solve?",
                            "Assess technical depth and problem-solving ability",
                            "HIGH"),
                    new InterviewQuestion(
                            "TECHNICAL",
                            "Describe a performance issue you encountered and how you diagnosed and"
                                    + " resolved it.",
                            "Probe debugging and optimization skills",
                            "MEDIUM"),
                    new InterviewQuestion(
                            "BEHAVIORAL",
                            "Tell me about a time you disagreed with a technical decision. How did"
                                    + " you handle it?",
                            "Assess communication and professional maturity",
                            "MEDIUM"),
                    new InterviewQuestion(
                            "BEHAVIORAL",
                            "Describe a situation where you had to learn a new technology quickly"
                                    + " under pressure. What was your approach?",
                            "Assess adaptability and learning agility",
                            "MEDIUM"),
                    new InterviewQuestion(
                            "CULTURE_FIT",
                            "What type of engineering culture and working environment brings out"
                                    + " your best work?",
                            "Understand team fit and working style preferences",
                            "LOW"));

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final InterviewAiQuestionRepository interviewAiQuestionRepository;
    private final RagService ragService;
    private final ChatClient ragChatClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.model:gpt-4o-mini}")
    private String chatModel;

    public InterviewQuestionServiceImpl(
            InterviewRepository interviewRepository,
            ApplicationRepository applicationRepository,
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            InterviewAiQuestionRepository interviewAiQuestionRepository,
            RagService ragService,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            ObjectMapper objectMapper) {
        this.interviewRepository = interviewRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.interviewAiQuestionRepository = interviewAiQuestionRepository;
        this.ragService = ragService;
        this.ragChatClient = ragChatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "generateFallback")
    public InterviewQuestionResponse generate(UUID interviewId, UUID companyId) {
        Interview interview = loadInterview(interviewId);
        Application application = loadApplication(interview.getApplicationId());
        Job job = loadJobAndVerifyOwnership(application.getJobId(), companyId);

        // Idempotent: return stored questions without re-calling OpenAI
        return interviewAiQuestionRepository
                .findByInterviewId(interviewId)
                .map(stored -> buildResponseFromStored(stored, interviewId, job, application))
                .orElseGet(() -> generateAndPersist(interviewId, job, application));
    }

    @Override
    public InterviewQuestionResponse getQuestions(UUID interviewId, UUID companyId) {
        Interview interview = loadInterview(interviewId);
        Application application = loadApplication(interview.getApplicationId());
        loadJobAndVerifyOwnership(application.getJobId(), companyId);

        InterviewAiQuestion stored =
                interviewAiQuestionRepository
                        .findByInterviewId(interviewId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.NOT_FOUND,
                                                "Questions not yet generated for this interview"));

        return buildResponseFromStored(stored, interviewId, null, application);
    }

    @SuppressWarnings("unused")
    public InterviewQuestionResponse generateFallback(
            UUID interviewId, UUID companyId, Throwable t) {
        if (t instanceof ApiException apiEx) {
            throw apiEx;
        }
        log.warn(
                "Interview question circuit open for interviewId={}. cause={}",
                interviewId,
                t.getMessage());

        return interviewAiQuestionRepository
                .findByInterviewId(interviewId)
                .map(
                        stored -> {
                            try {
                                List<InterviewQuestion> questions = parseQuestionsJson(stored);
                                return new InterviewQuestionResponse(
                                        interviewId,
                                        null,
                                        null,
                                        stored.getGeneratedAt(),
                                        questions,
                                        "STORED");
                            } catch (Exception e) {
                                log.warn(
                                        "Failed to parse stored questions in fallback."
                                                + " interviewId={}",
                                        interviewId);
                                return fallbackResponse(interviewId);
                            }
                        })
                .orElseGet(() -> fallbackResponse(interviewId));
    }

    private InterviewQuestionResponse generateAndPersist(
            UUID interviewId, Job job, Application application) {
        Candidate candidate =
                candidateRepository
                        .findByIdAndDeletedAtIsNull(application.getCandidateId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.CANDIDATE_NOT_FOUND));

        if (candidate.getParsedCvText() == null || candidate.getParsedCvText().isBlank()) {
            throw new ApiException(ApiErrorCode.CV_NOT_AVAILABLE_FOR_INTERVIEW);
        }

        User candidateUser =
                userRepository
                        .findById(candidate.getUserId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.NOT_FOUND,
                                                "Candidate user not found"));

        String gapSkills = identifyGapSkills(job.getRequirements(), candidate.getSkills());

        String ragQuery =
                "interview questions for "
                        + job.getTitle()
                        + " "
                        + (job.getRequirements() != null ? job.getRequirements() : "");
        List<Document> ragDocs = ragService.retrieveKnowledge(ragQuery, "interview-guide", 5);
        String ragChunks =
                ragDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        String cvText = truncate(candidate.getParsedCvText(), CV_TEXT_MAX_CHARS);
        String jobDesc = truncate(job.getDescription(), JOB_DESC_MAX_CHARS);

        List<InterviewQuestion> questions =
                invokeAi(buildPrompt(job, cvText, jobDesc, gapSkills, ragChunks));

        InterviewAiQuestion entity = persistQuestions(interviewId, questions);

        return new InterviewQuestionResponse(
                interviewId,
                job.getTitle(),
                candidateUser.getFullName(),
                entity.getGeneratedAt(),
                questions,
                "GENERATED");
    }

    private List<InterviewQuestion> invokeAi(String prompt) {
        MDC.put("ai_model", "interview-questions");
        try {
            long startMs = System.currentTimeMillis();
            ChatResponse response =
                    ragChatClient
                            .prompt()
                            .options(OpenAiChatOptions.builder().maxTokens(2048).build())
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

    private List<InterviewQuestion> parseAiResponse(String raw) {
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").strip();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode questionsNode = root.path("questions");
            return objectMapper.convertValue(
                    questionsNode,
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionType(List.class, InterviewQuestion.class));
        } catch (Exception e) {
            log.error("Failed to parse AI interview questions response: {}", raw, e);
            throw new ApiException(ApiErrorCode.AI_INVALID_RESPONSE);
        }
    }

    private InterviewAiQuestion persistQuestions(
            UUID interviewId, List<InterviewQuestion> questions) {
        try {
            String json = objectMapper.writeValueAsString(questions);
            InterviewAiQuestion entity =
                    InterviewAiQuestion.builder()
                            .interviewId(interviewId)
                            .questionsJson(json)
                            .generatedAt(Instant.now())
                            .modelUsed(chatModel)
                            .build();
            return interviewAiQuestionRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist interview questions for interviewId={}", interviewId, e);
            throw new ApiException(ApiErrorCode.INTERVIEW_QUESTIONS_UNAVAILABLE);
        }
    }

    private InterviewQuestionResponse buildResponseFromStored(
            InterviewAiQuestion stored, UUID interviewId, Job job, Application application) {
        String jobTitle = null;
        String candidateName = null;

        if (job != null) {
            jobTitle = job.getTitle();
            try {
                Candidate candidate =
                        candidateRepository
                                .findByIdAndDeletedAtIsNull(application.getCandidateId())
                                .orElse(null);
                if (candidate != null) {
                    User user = userRepository.findById(candidate.getUserId()).orElse(null);
                    if (user != null) {
                        candidateName = user.getFullName();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not resolve candidate name for interviewId={}", interviewId);
            }
        }

        List<InterviewQuestion> questions;
        try {
            questions = parseQuestionsJson(stored);
        } catch (Exception e) {
            log.warn("Failed to parse stored questions for interviewId={}", interviewId);
            questions = List.of();
        }

        return new InterviewQuestionResponse(
                interviewId, jobTitle, candidateName, stored.getGeneratedAt(), questions, "STORED");
    }

    private List<InterviewQuestion> parseQuestionsJson(InterviewAiQuestion stored) {
        try {
            return objectMapper.readValue(
                    stored.getQuestionsJson(), new TypeReference<List<InterviewQuestion>>() {});
        } catch (Exception e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to parse AI-generated questions");
        }
    }

    private InterviewQuestionResponse fallbackResponse(UUID interviewId) {
        return new InterviewQuestionResponse(
                interviewId, null, null, Instant.now(), GENERIC_FALLBACK_QUESTIONS, "FALLBACK");
    }

    private String identifyGapSkills(String jobRequirements, String[] candidateSkills) {
        if (jobRequirements == null || jobRequirements.isBlank()) {
            return "";
        }
        if (candidateSkills == null || candidateSkills.length == 0) {
            return "";
        }
        String requirementsLower = jobRequirements.toLowerCase();
        List<String> gaps =
                Arrays.stream(candidateSkills)
                        .filter(skill -> !requirementsLower.contains(skill.toLowerCase()))
                        .limit(GAP_SKILLS_LIMIT)
                        .toList();
        return String.join(", ", gaps);
    }

    private String buildPrompt(
            Job job, String cvText, String jobDesc, String gapSkills, String ragChunks) {
        String requiredSkills =
                job.getRequirements() != null
                        ? truncate(job.getRequirements(), 300)
                        : "Not specified";

        return """
You are an expert technical interviewer helping prepare structured interview questions.

JOB DETAILS:
Title: %s
Required skills: %s
Key responsibilities: %s

CANDIDATE CV SUMMARY:
%s

SKILL GAPS (skills required by job but not found in CV):
%s

INTERVIEW KNOWLEDGE BASE:
%s

Generate exactly 10 interview questions in this JSON format:
{
"questions": [
	{
	"category": "TECHNICAL" | "BEHAVIORAL" | "GAP" | "CULTURE_FIT",
	"question": "<the actual question — specific to this candidate>",
	"intent": "<why you are asking this, max 20 words>",
	"difficulty": "LOW" | "MEDIUM" | "HIGH"
	}
]
}

Distribution rules (strictly enforce):
- TECHNICAL: 4 questions — probe specific skills listed in CV
- BEHAVIORAL: 3 questions — use STAR method framing
- GAP: 2 questions — one per top-2 skill gaps (must mention the gap explicitly)
- CULTURE_FIT: 1 question

Rules:
- Every TECHNICAL question must reference a specific skill from the candidate's CV
- Every GAP question must name the missing skill explicitly
- Do NOT use generic questions like "Tell me about yourself"
- Return ONLY the JSON object
"""
                .formatted(
                        job.getTitle(),
                        requiredSkills,
                        jobDesc,
                        cvText,
                        gapSkills.isBlank() ? "None identified" : gapSkills,
                        ragChunks.isBlank() ? "No knowledge base content available" : ragChunks);
    }

    private Interview loadInterview(UUID interviewId) {
        return interviewRepository
                .findByIdAndDeletedAtIsNull(interviewId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INTERVIEW_NOT_FOUND));
    }

    private Application loadApplication(UUID applicationId) {
        return applicationRepository
                .findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.APPLICATION_NOT_FOUND));
    }

    private Job loadJobAndVerifyOwnership(UUID jobId, UUID companyId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.NOT_FOUND, "Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new ApiException(
                    ApiErrorCode.FORBIDDEN, "Interview does not belong to your company");
        }
        return job;
    }

    private String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text != null ? text : "";
        return text.substring(0, maxChars);
    }
}

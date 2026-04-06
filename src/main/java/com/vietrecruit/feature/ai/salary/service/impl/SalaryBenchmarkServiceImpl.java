package com.vietrecruit.feature.ai.salary.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.salary.dto.SalaryBenchmarkResponse;
import com.vietrecruit.feature.ai.salary.dto.SalaryRange;
import com.vietrecruit.feature.ai.salary.service.SalaryBenchmarkService;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.ai.shared.service.RagService;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.service.JobService;
import com.vietrecruit.feature.location.entity.Location;
import com.vietrecruit.feature.location.repository.LocationRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SalaryBenchmarkServiceImpl implements SalaryBenchmarkService {

    private static final String CACHE_KEY_PREFIX = "ai:salary:";
    private static final long CACHE_TTL_HOURS = 24;
    private static final int MIN_DATA_POINTS = 3;
    private static final int VECTOR_TOP_K = 20;
    private static final int RAG_TOP_K = 3;

    private final EmbeddingService embeddingService;
    private final RagService ragService;
    private final JobService jobService;
    private final LocationRepository locationRepository;
    private final ChatClient ragChatClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SalaryBenchmarkServiceImpl(
            EmbeddingService embeddingService,
            RagService ragService,
            JobService jobService,
            LocationRepository locationRepository,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.ragService = ragService;
        this.jobService = jobService;
        this.locationRepository = locationRepository;
        this.ragChatClient = ragChatClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "benchmarkFallback")
    public SalaryBenchmarkResponse benchmarkForCandidate(String jobTitle, UUID locationId) {
        String locationName = resolveLocationName(locationId);
        return doBenchmark(jobTitle, locationName, "", locationId);
    }

    @Override
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "benchmarkFallback")
    public SalaryBenchmarkResponse benchmarkForJob(UUID jobId) {
        Job job =
                jobService
                        .findJobById(jobId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        String locationName = resolveLocationName(job.getLocationId());
        String skills = job.getRequirements() != null ? job.getRequirements() : "";
        return doBenchmark(job.getTitle(), locationName, skills, job.getLocationId());
    }

    private SalaryBenchmarkResponse doBenchmark(
            String jobTitle, String locationName, String skills, UUID locationId) {
        // Step 6 (cache check): Redis
        String cacheKey = buildCacheKey(jobTitle, locationId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, SalaryBenchmarkResponse.class);
            } catch (Exception e) {
                log.warn(
                        "Failed to deserialize cached salary benchmark, regenerating. cause={}",
                        e.getMessage());
            }
        }

        // Step 2: pgvector salary search
        SalaryStats stats = queryInternalSalaryData(jobTitle, locationName);

        String experienceLevel;
        String marketPosition;
        List<String> insights;
        String disclaimer;

        if (stats.dataPoints() < MIN_DATA_POINTS) {
            // Expected path: skip LLM entirely, return raw stats
            experienceLevel = inferExperienceLevel(jobTitle);
            marketPosition = "INSUFFICIENT_DATA";
            insights = List.of();
            disclaimer =
                    "Based on "
                            + stats.dataPoints()
                            + " job postings on VietRecruit. Insufficient data for accurate"
                            + " benchmarking.";
        } else {
            // Step 3: RAG knowledge retrieval
            int currentYear = java.time.Year.now().getValue();
            String ragQuery = jobTitle + " salary Vietnam " + currentYear;
            List<Document> ragDocs =
                    ragService.retrieveKnowledge(ragQuery, "salary-data", RAG_TOP_K);
            String ragChunks =
                    ragDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

            // Step 4-5: Build prompt and call LLM
            String prompt = buildPrompt(jobTitle, locationName, skills, stats, ragChunks);
            LlmSalaryResult llmResult = callLlm(prompt);

            experienceLevel = llmResult.experienceLevel();
            marketPosition = llmResult.marketPosition();
            insights = llmResult.insights();
            disclaimer =
                    "Based on "
                            + stats.dataPoints()
                            + " job postings on VietRecruit and salary survey data.";
        }

        SalaryBenchmarkResponse response =
                new SalaryBenchmarkResponse(
                        jobTitle,
                        locationName,
                        experienceLevel,
                        "VND",
                        new SalaryRange(stats.min(), stats.median(), stats.max()),
                        marketPosition,
                        stats.dataPoints(),
                        insights,
                        disclaimer,
                        Instant.now());

        // Step 6: Cache result
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache salary benchmark. cause={}", e.getMessage());
        }

        return response;
    }

    // Step 2 implementation
    private SalaryStats queryInternalSalaryData(String jobTitle, String locationName) {
        String query = jobTitle + " " + locationName + " salary";

        Filter.Expression typeFilter =
                new Filter.Expression(
                        Filter.ExpressionType.EQ, new Filter.Key("type"), new Filter.Value("job"));
        Filter.Expression salaryFilter =
                new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key("hasSalary"),
                        new Filter.Value("true"));
        Filter.Expression filter =
                new Filter.Expression(Filter.ExpressionType.AND, typeFilter, salaryFilter);

        List<Document> docs = embeddingService.search(query, VECTOR_TOP_K, filter);
        return computeStatsFromDocuments(docs);
    }

    private SalaryStats computeStatsFromDocuments(List<Document> docs) {
        List<BigDecimal> mins = new ArrayList<>();
        List<BigDecimal> maxes = new ArrayList<>();
        List<BigDecimal> midpoints = new ArrayList<>();

        for (Document doc : docs) {
            Object minObj = doc.getMetadata().get("salaryMin");
            Object maxObj = doc.getMetadata().get("salaryMax");
            if (minObj == null || maxObj == null) continue;
            try {
                BigDecimal minVal = new BigDecimal(minObj.toString());
                BigDecimal maxVal = new BigDecimal(maxObj.toString());
                mins.add(minVal);
                maxes.add(maxVal);
                midpoints.add(
                        minVal.add(maxVal).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
            } catch (NumberFormatException e) {
                log.debug("Failed to parse salary value from metadata: {}", e.getMessage());
            }
        }

        if (mins.isEmpty()) {
            return new SalaryStats(null, null, null, 0);
        }

        BigDecimal overallMin = mins.stream().min(Comparator.naturalOrder()).orElse(null);
        BigDecimal overallMax = maxes.stream().max(Comparator.naturalOrder()).orElse(null);
        BigDecimal median = computeMedian(midpoints);

        return new SalaryStats(overallMin, median, overallMax, mins.size());
    }

    private SalaryStats computeStatsFromJobs(List<Job> jobs) {
        List<BigDecimal> mins = new ArrayList<>();
        List<BigDecimal> maxes = new ArrayList<>();
        List<BigDecimal> midpoints = new ArrayList<>();

        for (Job job : jobs) {
            if (job.getMinSalary() == null || job.getMaxSalary() == null) continue;
            mins.add(job.getMinSalary());
            maxes.add(job.getMaxSalary());
            midpoints.add(
                    job.getMinSalary()
                            .add(job.getMaxSalary())
                            .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
        }

        if (mins.isEmpty()) {
            return new SalaryStats(null, null, null, 0);
        }

        return new SalaryStats(
                mins.stream().min(Comparator.naturalOrder()).orElse(null),
                computeMedian(midpoints),
                maxes.stream().max(Comparator.naturalOrder()).orElse(null),
                mins.size());
    }

    private BigDecimal computeMedian(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) return sorted.get(n / 2);
        return sorted.get(n / 2 - 1)
                .add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private LlmSalaryResult callLlm(String prompt) {
        MDC.put("ai_model", "salary-benchmark");
        try {
            long startMs = System.currentTimeMillis();
            ChatResponse response =
                    ragChatClient
                            .prompt()
                            .options(OpenAiChatOptions.builder().maxTokens(512).build())
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

            return parseLlmResponse(result.getOutput().getText());
        } finally {
            MDC.remove("ai_model");
        }
    }

    private LlmSalaryResult parseLlmResponse(String raw) {
        String json = raw.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").strip();
        }
        try {
            var node = objectMapper.readTree(json);
            String experienceLevel = node.path("experienceLevel").asText("Mid (3-5 years)");
            String marketPosition = node.path("marketPosition").asText("INSUFFICIENT_DATA");
            List<String> insights =
                    objectMapper.convertValue(
                            node.path("insights"),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
            return new LlmSalaryResult(
                    experienceLevel, marketPosition, insights != null ? insights : List.of());
        } catch (Exception e) {
            log.error("Failed to parse AI salary benchmark response: {}", raw, e);
            throw new ApiException(ApiErrorCode.AI_INVALID_RESPONSE);
        }
    }

    private String buildPrompt(
            String jobTitle, String location, String skills, SalaryStats stats, String ragChunks) {
        return """
You are a compensation analyst specializing in the Vietnamese tech market.

ROLE BEING ANALYZED: %s
LOCATION: %s
SKILLS: %s

INTERNAL MARKET DATA (from %d job postings on VietRecruit):
- Minimum salary seen: %s VND/month
- Maximum salary seen: %s VND/month
- Median salary: %s VND/month

SALARY KNOWLEDGE BASE:
%s

Based on this data, return a JSON object:
{
"experienceLevel": "<inferred from job title: Junior/Mid/Senior/Lead>",
"marketPosition": "BELOW_MARKET" | "COMPETITIVE" | "ABOVE_MARKET",
"insights": ["<insight 1, max 25 words>", "<insight 2, max 25 words>"]
}

marketPosition determination:
- Compare internal median against knowledge base benchmarks
- COMPETITIVE = within 10%% of knowledge base median
- BELOW_MARKET = more than 10%% below
- ABOVE_MARKET = more than 10%% above

Rules:
- Maximum 3 insights
- Insights must cite specific data (percentages, timeframes, skill premiums)
- Return ONLY the JSON object
"""
                .formatted(
                        jobTitle,
                        location,
                        skills,
                        stats.dataPoints(),
                        formatSalary(stats.min()),
                        formatSalary(stats.max()),
                        formatSalary(stats.median()),
                        ragChunks);
    }

    // Fallback for benchmarkForCandidate(String, UUID)
    @SuppressWarnings("unused")
    public SalaryBenchmarkResponse benchmarkFallback(
            String jobTitle, UUID locationId, Throwable t) {
        if (t instanceof ApiException apiEx) throw apiEx;
        log.warn("Salary benchmark circuit open (candidate). cause={}", t.getMessage());
        String locationName = resolveLocationName(locationId);
        return buildFallbackResponse(jobTitle, locationName);
    }

    // Fallback for benchmarkForJob(UUID)
    @SuppressWarnings("unused")
    public SalaryBenchmarkResponse benchmarkFallback(UUID jobId, Throwable t) {
        if (t instanceof ApiException apiEx) throw apiEx;
        log.warn("Salary benchmark circuit open (job). cause={}", t.getMessage());
        Job job = jobService.findJobById(jobId).orElse(null);
        String jobTitle = job != null ? job.getTitle() : "";
        String locationName = job != null ? resolveLocationName(job.getLocationId()) : "";
        return buildFallbackResponse(jobTitle, locationName);
    }

    private SalaryBenchmarkResponse buildFallbackResponse(String jobTitle, String locationName) {
        List<Job> jobs = jobService.findPublishedJobsWithSalary(jobTitle);
        SalaryStats stats = computeStatsFromJobs(jobs);

        return new SalaryBenchmarkResponse(
                jobTitle,
                locationName,
                inferExperienceLevel(jobTitle),
                "VND",
                new SalaryRange(stats.min(), stats.median(), stats.max()),
                "INSUFFICIENT_DATA",
                stats.dataPoints(),
                List.of(),
                "Based on "
                        + stats.dataPoints()
                        + " job postings on VietRecruit. AI service temporarily unavailable.",
                Instant.now());
    }

    private String resolveLocationName(UUID locationId) {
        if (locationId == null) return "";
        return locationRepository.findById(locationId).map(Location::getName).orElse("");
    }

    private String inferExperienceLevel(String jobTitle) {
        String lower = jobTitle.toLowerCase();
        if (lower.contains("senior") || lower.contains("lead") || lower.contains("principal")) {
            return "Senior (5+ years)";
        }
        if (lower.contains("junior") || lower.contains("fresher")) {
            return "Junior (0-2 years)";
        }
        if (lower.contains("manager") || lower.contains("director") || lower.contains("head")) {
            return "Lead/Manager";
        }
        return "Mid (3-5 years)";
    }

    private String buildCacheKey(String jobTitle, UUID locationId) {
        String yearMonth = YearMonth.now().toString();
        String input =
                jobTitle.toLowerCase()
                        + "_"
                        + (locationId != null ? locationId.toString() : "")
                        + "_"
                        + yearMonth;
        return CACHE_KEY_PREFIX + sha256(input);
    }

    private String formatSalary(BigDecimal value) {
        return value != null ? value.toPlainString() : "N/A";
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record SalaryStats(BigDecimal min, BigDecimal median, BigDecimal max, int dataPoints) {}

    private record LlmSalaryResult(
            String experienceLevel, String marketPosition, List<String> insights) {}
}

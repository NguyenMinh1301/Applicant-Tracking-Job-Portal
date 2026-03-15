package com.vietrecruit.feature.ai.rag;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.embedding.EmbeddingService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RagService {

    private final ChatClient ragChatClient;
    private final EmbeddingService embeddingService;

    public RagService(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            EmbeddingService embeddingService) {
        this.ragChatClient = ragChatClient;
        this.embeddingService = embeddingService;
    }

    @Retry(name = "openaiApi")
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "generateFallback")
    public String generate(String userQuery, String systemContext, int topK) {
        List<Document> relevantDocs = embeddingService.search(userQuery, topK);

        String context =
                relevantDocs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        String augmentedPrompt =
                "Use the following context to answer the question.\n\n"
                        + "Context:\n"
                        + context
                        + "\n\n"
                        + "Additional instructions: "
                        + systemContext
                        + "\n\n"
                        + "Question: "
                        + userQuery;

        MDC.put("ai_model", "rag");
        try {
            long startMs = System.currentTimeMillis();
            ChatResponse response =
                    ragChatClient.prompt().user(augmentedPrompt).call().chatResponse();
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

            return result.getOutput().getText();
        } finally {
            MDC.remove("ai_model");
        }
    }

    @SuppressWarnings("unused")
    public String generateFallback(String userQuery, String systemContext, int topK, Throwable t) {
        log.warn("RAG circuit open, returning degraded response. cause={}", t.getMessage());
        return "AI service is temporarily unavailable. Please try again shortly.";
    }
}

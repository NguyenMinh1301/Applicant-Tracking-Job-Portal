package com.vietrecruit.feature.ai.shared.service;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.ai.shared.memory.AgentMemoryStore;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AgentService {

    private final ChatClient agentChatClient;
    private final AgentMemoryStore memoryStore;

    public AgentService(
            @Qualifier("agentChatClient") ChatClient agentChatClient,
            AgentMemoryStore memoryStore) {
        this.agentChatClient = agentChatClient;
        this.memoryStore = memoryStore;
    }

    @Retry(name = "openaiApi")
    @CircuitBreaker(name = "openaiApi", fallbackMethod = "executeFallback")
    @RateLimiter(name = "openaiApi", fallbackMethod = "rateLimitFallback")
    public String execute(String sessionId, String userMessage, Object... toolBeans) {
        String userId =
                SecurityUtils.getCurrentUserIdOptional()
                        .map(java.util.UUID::toString)
                        .orElse("anon");
        List<AgentMemoryStore.ChatMessage> history = memoryStore.getHistory(userId, sessionId);

        StringBuilder contextBuilder = new StringBuilder();
        for (AgentMemoryStore.ChatMessage msg : history) {
            contextBuilder
                    .append(msg.role().equals("user") ? "Human" : "Assistant")
                    .append(": ")
                    .append(msg.content())
                    .append("\n\n");
        }

        String fullPrompt =
                contextBuilder.isEmpty() ? userMessage : contextBuilder + "Human: " + userMessage;

        ChatClient.ChatClientRequestSpec spec = agentChatClient.prompt().user(fullPrompt);
        if (toolBeans != null && toolBeans.length > 0) {
            spec = spec.tools(toolBeans);
        }

        MDC.put("ai_model", "agent");
        try {
            long startMs = System.currentTimeMillis();
            ChatResponse chatResponse = spec.call().chatResponse();
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

            String response = null;
            if (chatResponse != null
                    && chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null) {
                response = chatResponse.getResult().getOutput().getText();
            }

            if (response == null || response.isBlank()) {
                log.warn("Agent returned null or empty response: sessionId={}", sessionId);
                return "The AI assistant could not generate a response. Please try again.";
            }

            memoryStore.append(userId, sessionId, "user", userMessage);
            memoryStore.append(userId, sessionId, "assistant", response);

            return response;
        } finally {
            MDC.remove("ai_model");
        }
    }

    @SuppressWarnings("unused")
    private String executeFallback(
            String sessionId, String userMessage, Object[] toolBeans, Throwable t) {
        log.error(
                "OpenAI circuit breaker triggered: sessionId={}, error={}",
                sessionId,
                t.getMessage());
        return "The AI service is temporarily unavailable. Please try again later.";
    }

    @SuppressWarnings("unused")
    private String rateLimitFallback(
            String sessionId, String userMessage, Object[] toolBeans, Throwable t) {
        log.warn("OpenAI rate limit reached: sessionId={}, error={}", sessionId, t.getMessage());
        return "AI request rate limit exceeded. Please wait a moment and try again.";
    }
}

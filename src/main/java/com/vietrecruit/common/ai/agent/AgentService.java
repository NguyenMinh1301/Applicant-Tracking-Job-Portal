package com.vietrecruit.common.ai.agent;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    @CircuitBreaker(name = "openaiApi", fallbackMethod = "executeFallback")
    public String execute(String sessionId, String userMessage, Object... toolBeans) {
        List<AgentMemoryStore.ChatMessage> history = memoryStore.getHistory(sessionId);

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

        String response = spec.call().content();

        memoryStore.append(sessionId, "user", userMessage);
        memoryStore.append(sessionId, "assistant", response);

        return response;
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
}

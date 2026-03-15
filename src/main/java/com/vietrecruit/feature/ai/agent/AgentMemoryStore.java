package com.vietrecruit.feature.ai.agent;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "ai:mem:";
    private static final long TTL_HOURS = 1;
    private static final int MAX_HISTORY = 10;

    public void append(String sessionId, String role, String content) {
        String key = KEY_PREFIX + sessionId;
        List<ChatMessage> history = getHistory(sessionId);
        history.add(new ChatMessage(role, content));
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }
        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize agent memory: sessionId={}", sessionId, e);
        }
    }

    public List<ChatMessage> getHistory(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return new java.util.ArrayList<>();
        }
        try {
            return new java.util.ArrayList<>(
                    objectMapper.readValue(json, new TypeReference<List<ChatMessage>>() {}));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize agent memory: sessionId={}", sessionId, e);
            return new java.util.ArrayList<>();
        }
    }

    public void clear(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
    }

    public record ChatMessage(String role, String content) {}
}

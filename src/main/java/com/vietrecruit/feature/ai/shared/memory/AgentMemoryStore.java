package com.vietrecruit.feature.ai.shared.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    private static final long TTL_SECONDS = 3600;
    private static final int MAX_HISTORY = 10;

    /**
     * Lua script for atomic append + trim + expire. Eliminates read-modify-write race condition
     * when multiple threads concurrently update the same session.
     *
     * <p>KEYS[1] = redis key ARGV[1] = serialized ChatMessage JSON to append ARGV[2] = max history
     * size ARGV[3] = TTL in seconds
     */
    private static final DefaultRedisScript<Void> APPEND_SCRIPT =
            new DefaultRedisScript<>(
                    """
					local key = KEYS[1]
					local msg = ARGV[1]
					local max_size = tonumber(ARGV[2])
					local ttl = tonumber(ARGV[3])
					redis.call('RPUSH', key, msg)
					redis.call('LTRIM', key, -max_size, -1)
					redis.call('EXPIRE', key, ttl)
					return nil
					""",
                    Void.class);

    public void append(String userId, String sessionId, String role, String content) {
        String key = KEY_PREFIX + userId + ":" + sessionId;
        try {
            String msgJson = objectMapper.writeValueAsString(new ChatMessage(role, content));
            redisTemplate.execute(
                    APPEND_SCRIPT,
                    List.of(key),
                    msgJson,
                    String.valueOf(MAX_HISTORY),
                    String.valueOf(TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize agent memory message: sessionId={}", sessionId, e);
        }
    }

    public List<ChatMessage> getHistory(String userId, String sessionId) {
        String key = KEY_PREFIX + userId + ":" + sessionId;
        List<String> entries = redisTemplate.opsForList().range(key, 0, -1);
        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatMessage> history = new ArrayList<>(entries.size());
        for (String entry : entries) {
            try {
                history.add(objectMapper.readValue(entry, ChatMessage.class));
            } catch (JsonProcessingException e) {
                log.warn(
                        "Skipping malformed memory entry for sessionId={}: {}",
                        sessionId,
                        e.getMessage());
            }
        }
        return history;
    }

    public void clear(String userId, String sessionId) {
        String key = KEY_PREFIX + userId + ":" + sessionId;
        redisTemplate.delete(key);
    }

    public record ChatMessage(String role, String content) {}
}

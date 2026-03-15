package com.vietrecruit.feature.ai.embedding;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_KEY_PREFIX = "ai:emb:";
    private static final long CACHE_TTL_HOURS = 24;

    public void embedAndStore(String id, String content, Map<String, Object> metadata) {
        Document doc = new Document(id, content, metadata);
        vectorStore.add(List.of(doc));
        log.debug("Stored embedding: id={}, metadataKeys={}", id, metadata.keySet());
    }

    public List<Document> search(String query, int topK, Filter.Expression filter) {
        SearchRequest request =
                SearchRequest.builder().query(query).topK(topK).filterExpression(filter).build();
        return vectorStore.similaritySearch(request);
    }

    public List<Document> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder().query(query).topK(topK).build();
        return vectorStore.similaritySearch(request);
    }

    public void deleteByMetadata(String key, String value) {
        Filter.Expression filter =
                new Filter.Expression(
                        Filter.ExpressionType.EQ, new Filter.Key(key), new Filter.Value(value));
        List<Document> docs = search("", 100, filter);
        if (!docs.isEmpty()) {
            List<String> ids = docs.stream().map(Document::getId).toList();
            vectorStore.delete(ids);
            log.debug("Deleted {} documents matching {}={}", ids.size(), key, value);
        }
    }

    @CircuitBreaker(name = "openaiApi", fallbackMethod = "embedFallback")
    public float[] embed(String text) {
        String hash = sha256(text);
        String cacheKey = CACHE_KEY_PREFIX + hash;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return decodeFloatArray(cached);
        }

        float[] embedding = embeddingModel.embed(text);
        redisTemplate
                .opsForValue()
                .set(cacheKey, encodeFloatArray(embedding), CACHE_TTL_HOURS, TimeUnit.HOURS);
        return embedding;
    }

    @SuppressWarnings("unused")
    private float[] embedFallback(String text, Throwable t) {
        log.warn("Embedding circuit open. cause={}", t.getMessage());
        throw new ApiException(ApiErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String encodeFloatArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static float[] decodeFloatArray(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }
}

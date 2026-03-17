package com.vietrecruit.feature.ai.knowledge.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.storage.StorageService;
import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeDocumentResponse;
import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeUploadResponse;
import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeUploadedEvent;
import com.vietrecruit.feature.ai.knowledge.entity.KnowledgeDocument;
import com.vietrecruit.feature.ai.knowledge.repository.KnowledgeDocumentRepository;
import com.vietrecruit.feature.ai.knowledge.service.KnowledgeIngestionService;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    static final String TOPIC_KNOWLEDGE_UPLOADED = "ai.knowledge-uploaded";

    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/markdown",
                    "text/plain");

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int MIN_CHUNK_TOKENS = 100;
    private static final int MAX_CHUNK_TOKENS = 800;
    private static final int MAX_CHUNKS_PER_DOCUMENT = 50;
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^##\\s+");

    private final KnowledgeDocumentRepository repository;
    private final StorageService storageService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EmbeddingService embeddingService;
    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Override
    @Transactional
    public KnowledgeUploadResponse upload(
            MultipartFile file, String title, String category, UUID uploadedBy) {

        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String fileKey = "knowledge/" + UUID.randomUUID() + "/" + originalFilename;

        try {
            storageService.upload(
                    fileKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new ApiException(
                    ApiErrorCode.STORAGE_UNAVAILABLE, "Failed to upload knowledge document");
        }

        KnowledgeDocument doc =
                KnowledgeDocument.builder()
                        .title(title)
                        .category(category)
                        .fileName(originalFilename)
                        .fileKey(fileKey)
                        .status("PENDING")
                        .uploadedBy(uploadedBy)
                        .build();
        doc = repository.save(doc);

        KnowledgeUploadedEvent event =
                new KnowledgeUploadedEvent(doc.getId(), fileKey, category, title);
        kafkaTemplate.send(TOPIC_KNOWLEDGE_UPLOADED, doc.getId().toString(), event);

        log.info(
                "Knowledge document uploaded: id={}, title={}, category={}",
                doc.getId(),
                title,
                category);

        return new KnowledgeUploadResponse(doc.getId(), title, category, doc.getStatus());
    }

    @Override
    public Page<KnowledgeDocumentResponse> list(String category, Pageable pageable) {
        Page<KnowledgeDocument> page;
        if (category != null && !category.isBlank()) {
            page = repository.findByCategory(category, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional
    public void delete(UUID documentId) {
        KnowledgeDocument doc =
                repository
                        .findById(documentId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.NOT_FOUND,
                                                "Knowledge document not found"));

        if (doc.getFileKey() != null) {
            try {
                storageService.delete(doc.getFileKey());
            } catch (Exception e) {
                log.warn(
                        "Failed to delete R2 file for knowledge doc: id={}, key={}, error={}",
                        documentId,
                        doc.getFileKey(),
                        e.getMessage());
            }
        }

        embeddingService.deleteByMetadata("knowledgeDocumentId", documentId.toString());
        repository.delete(doc);

        log.info("Knowledge document deleted: id={}, title={}", documentId, doc.getTitle());
    }

    @Override
    @Transactional
    public void processIngestion(UUID documentId, String fileKey, String category, String title) {
        KnowledgeDocument doc =
                repository
                        .findById(documentId)
                        .orElseThrow(
                                () -> {
                                    log.error(
                                            "Knowledge ingestion: document not found: id={}",
                                            documentId);
                                    return new ApiException(
                                            ApiErrorCode.NOT_FOUND, "Knowledge document not found");
                                });

        // Idempotency guard: skip if already processed
        if ("INDEXED".equals(doc.getStatus())) {
            log.info("Knowledge ingestion: document already indexed, skipping: id={}", documentId);
            return;
        }

        try {
            String fullText = fetchAndParseFromR2(fileKey);
            if (fullText == null || fullText.isBlank()) {
                doc.setStatus("FAILED");
                repository.save(doc);
                log.error(
                        "Knowledge ingestion: empty text extracted: id={}, fileKey={}",
                        documentId,
                        fileKey);
                return;
            }

            List<String> chunks = chunkText(fullText, fileKey);
            if (chunks.isEmpty()) {
                doc.setStatus("FAILED");
                repository.save(doc);
                log.error("Knowledge ingestion: no valid chunks produced: id={}", documentId);
                return;
            }

            // Cap chunks to prevent unbounded OpenAI embedding calls
            if (chunks.size() > MAX_CHUNKS_PER_DOCUMENT) {
                log.warn(
                        "Knowledge ingestion: truncating chunks from {} to {} for id={}",
                        chunks.size(),
                        MAX_CHUNKS_PER_DOCUMENT,
                        documentId);
                chunks = chunks.subList(0, MAX_CHUNKS_PER_DOCUMENT);
            }

            int successCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                try {
                    String chunkId = "knowledge-" + documentId + "-" + i;
                    Map<String, Object> metadata =
                            Map.of(
                                    "type",
                                    "knowledge",
                                    "knowledgeDocumentId",
                                    documentId.toString(),
                                    "category",
                                    category,
                                    "chunkIndex",
                                    i,
                                    "totalChunks",
                                    chunks.size(),
                                    "title",
                                    title);

                    embeddingService.embedAndStore(chunkId, chunks.get(i), metadata);
                    successCount++;
                } catch (Exception e) {
                    log.error(
                            "Knowledge ingestion: chunk embedding failed: id={}, chunkIndex={},"
                                    + " error={}",
                            documentId,
                            i,
                            e.getMessage());
                }
            }

            if (successCount == 0) {
                doc.setStatus("FAILED");
            } else {
                doc.setStatus("INDEXED");
            }
            doc.setChunkCount(successCount);
            repository.save(doc);

            log.info(
                    "Knowledge ingestion complete: id={}, chunks={}/{}, status={}",
                    documentId,
                    successCount,
                    chunks.size(),
                    doc.getStatus());

        } catch (Exception e) {
            doc.setStatus("FAILED");
            repository.save(doc);
            log.error("Knowledge ingestion failed: id={}, error={}", documentId, e.getMessage(), e);
            throw e;
        }
    }

    private String fetchAndParseFromR2(String fileKey) {
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucket).key(fileKey).build();
            var response = s3Client.getObject(request);

            org.springframework.ai.reader.tika.TikaDocumentReader reader =
                    new org.springframework.ai.reader.tika.TikaDocumentReader(
                            new InputStreamResource(response));
            var docs = reader.read();

            return docs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .reduce("", (a, b) -> a + "\n" + b)
                    .trim();
        } catch (Exception e) {
            log.error(
                    "Knowledge ingestion: failed to fetch/parse from R2: key={}, error={}",
                    fileKey,
                    e.getMessage());
            return null;
        }
    }

    List<String> chunkText(String fullText, String fileKey) {
        boolean isMarkdown =
                fileKey != null && (fileKey.endsWith(".md") || fileKey.endsWith(".markdown"));

        List<String> rawSections;
        if (isMarkdown) {
            rawSections = splitByMarkdownHeadings(fullText);
        } else {
            rawSections = splitByParagraphs(fullText);
        }

        List<String> chunks = new ArrayList<>();
        for (String section : rawSections) {
            String trimmed = section.trim();
            int tokenEstimate = estimateTokens(trimmed);

            if (tokenEstimate < MIN_CHUNK_TOKENS) {
                continue;
            }

            if (tokenEstimate <= MAX_CHUNK_TOKENS) {
                chunks.add(trimmed);
            } else {
                chunks.addAll(hardSplitChunk(trimmed));
            }
        }
        return chunks;
    }

    private List<String> splitByMarkdownHeadings(String text) {
        String[] parts = MARKDOWN_HEADING.split(text);
        List<String> sections = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                sections.add(part.trim());
            }
        }
        return sections;
    }

    private List<String> splitByParagraphs(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int currentTokens = estimateTokens(current.toString());
            int paraTokens = estimateTokens(trimmed);

            if (currentTokens + paraTokens > MAX_CHUNK_TOKENS
                    && currentTokens >= MIN_CHUNK_TOKENS) {
                merged.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }

        if (current.length() > 0 && estimateTokens(current.toString()) >= MIN_CHUNK_TOKENS) {
            merged.add(current.toString().trim());
        }

        return merged;
    }

    private List<String> hardSplitChunk(String text) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        int tokenCount = 0;

        for (String word : words) {
            if (tokenCount >= MAX_CHUNK_TOKENS) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                tokenCount = 0;
            }
            if (current.length() > 0) current.append(" ");
            current.append(word);
            tokenCount++;
        }

        String remaining = current.toString().trim();
        if (estimateTokens(remaining) >= MIN_CHUNK_TOKENS) {
            result.add(remaining);
        } else if (!result.isEmpty()) {
            result.set(result.size() - 1, result.get(result.size() - 1) + " " + remaining);
        }

        return result;
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\s+").length;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(ApiErrorCode.FILE_TOO_LARGE, "File size exceeds the 20MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(
                    ApiErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "Unsupported file type. Accepted: PDF, DOCX, MD, TXT");
        }
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument doc) {
        return new KnowledgeDocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getCategory(),
                doc.getFileName(),
                doc.getChunkCount(),
                doc.getStatus(),
                doc.getUploadedBy(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }
}

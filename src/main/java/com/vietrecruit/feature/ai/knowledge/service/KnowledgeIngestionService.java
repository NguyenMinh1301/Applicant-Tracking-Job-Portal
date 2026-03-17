package com.vietrecruit.feature.ai.knowledge.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeDocumentResponse;
import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeUploadResponse;

public interface KnowledgeIngestionService {

    KnowledgeUploadResponse upload(
            MultipartFile file, String title, String category, UUID uploadedBy);

    Page<KnowledgeDocumentResponse> list(String category, Pageable pageable);

    void delete(UUID documentId);

    void processIngestion(UUID documentId, String fileKey, String category, String title);
}

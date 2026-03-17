package com.vietrecruit.feature.ai.knowledge.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentResponse(
        UUID id,
        String title,
        String category,
        String fileName,
        Integer chunkCount,
        String status,
        UUID uploadedBy,
        Instant createdAt,
        Instant updatedAt) {}

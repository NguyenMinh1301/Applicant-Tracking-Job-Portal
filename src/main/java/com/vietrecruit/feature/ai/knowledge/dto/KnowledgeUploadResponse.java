package com.vietrecruit.feature.ai.knowledge.dto;

import java.util.UUID;

public record KnowledgeUploadResponse(
        UUID documentId, String title, String category, String status) {}

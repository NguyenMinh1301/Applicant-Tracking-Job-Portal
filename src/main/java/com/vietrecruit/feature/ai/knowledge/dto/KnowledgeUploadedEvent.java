package com.vietrecruit.feature.ai.knowledge.dto;

import java.util.UUID;

public record KnowledgeUploadedEvent(
        UUID documentId, String fileKey, String category, String title) {}

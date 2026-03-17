package com.vietrecruit.feature.ai.shared.event;

import java.util.UUID;

public record CvUploadedEvent(UUID candidateId, String cvFileKey, String candidateEmail) {}

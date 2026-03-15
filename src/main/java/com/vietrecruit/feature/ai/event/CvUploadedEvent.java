package com.vietrecruit.feature.ai.event;

import java.util.UUID;

public record CvUploadedEvent(UUID candidateId, String cvFileKey, String candidateEmail) {}

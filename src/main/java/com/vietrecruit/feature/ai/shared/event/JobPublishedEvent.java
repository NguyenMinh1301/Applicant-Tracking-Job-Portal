package com.vietrecruit.feature.ai.shared.event;

import java.util.UUID;

public record JobPublishedEvent(UUID jobId, UUID employerId, String jobTitle) {}

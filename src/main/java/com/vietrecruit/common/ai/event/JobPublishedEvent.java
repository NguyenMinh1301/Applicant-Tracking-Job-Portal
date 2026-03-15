package com.vietrecruit.common.ai.event;

import java.util.UUID;

public record JobPublishedEvent(UUID jobId, UUID employerId, String jobTitle) {}

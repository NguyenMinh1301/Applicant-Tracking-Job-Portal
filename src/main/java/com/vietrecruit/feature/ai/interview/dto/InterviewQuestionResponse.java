package com.vietrecruit.feature.ai.interview.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InterviewQuestionResponse(
        UUID interviewId,
        String jobTitle,
        String candidateName,
        Instant generatedAt,
        List<InterviewQuestion> questions,
        String source) {}

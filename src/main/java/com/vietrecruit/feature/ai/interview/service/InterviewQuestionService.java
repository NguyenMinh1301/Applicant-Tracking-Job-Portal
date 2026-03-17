package com.vietrecruit.feature.ai.interview.service;

import java.util.UUID;

import com.vietrecruit.feature.ai.interview.dto.InterviewQuestionResponse;

public interface InterviewQuestionService {

    /**
     * Generates interview questions for a specific interview. Idempotent — returns stored questions
     * if already generated without calling OpenAI again.
     */
    InterviewQuestionResponse generate(UUID interviewId, UUID companyId);

    /** Returns previously generated questions for an interview, or 404 if not yet generated. */
    InterviewQuestionResponse getQuestions(UUID interviewId, UUID companyId);
}

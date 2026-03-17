package com.vietrecruit.feature.ai.interview.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InterviewQuestion(
        String category, String question, String intent, String difficulty) {}

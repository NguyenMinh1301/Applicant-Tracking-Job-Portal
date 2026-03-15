package com.vietrecruit.feature.candidate.dto.response;

import java.util.UUID;

public record CandidateSearchResult(
        UUID id, String desiredPosition, Short yearsOfExperience, String[] skills) {}

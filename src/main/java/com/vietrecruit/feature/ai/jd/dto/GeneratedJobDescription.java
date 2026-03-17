package com.vietrecruit.feature.ai.jd.dto;

import java.util.List;

public record GeneratedJobDescription(
        String overview,
        List<String> responsibilities,
        List<String> requirements,
        List<String> niceToHave,
        String benefits) {}

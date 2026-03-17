package com.vietrecruit.feature.ai.shared.tools;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.candidate.dto.response.CandidateSearchResult;
import com.vietrecruit.feature.candidate.service.CandidateService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CandidateSearchTool {

    private final CandidateService candidateService;

    @Tool(
            description =
                    "Search candidates by skills, experience years, location, and availability."
                            + " Use when employers need to find matching talent.")
    public String searchCandidates(
            String skills, String minYearsExperience, String desiredPosition) {

        Short minYears = null;
        if (minYearsExperience != null && !minYearsExperience.isBlank()) {
            try {
                minYears = Short.parseShort(minYearsExperience);
            } catch (NumberFormatException ignored) {
            }
        }

        List<CandidateSearchResult> results =
                candidateService.searchCandidates(skills, desiredPosition, minYears, 10);

        if (results.isEmpty()) {
            return "No matching candidates found.";
        }

        StringBuilder sb =
                new StringBuilder("Found ").append(results.size()).append(" candidates:\n");
        for (CandidateSearchResult c : results) {
            sb.append("- [")
                    .append(c.id())
                    .append("] ")
                    .append(c.desiredPosition() != null ? c.desiredPosition() : "N/A");
            if (c.yearsOfExperience() != null) {
                sb.append(" | ").append(c.yearsOfExperience()).append(" yrs exp");
            }
            if (c.skills() != null) {
                sb.append(" | Skills: ").append(String.join(", ", c.skills()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

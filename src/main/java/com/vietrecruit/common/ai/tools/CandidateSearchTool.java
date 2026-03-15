package com.vietrecruit.common.ai.tools;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CandidateSearchTool {

    private final CandidateRepository candidateRepository;

    @Tool(
            description =
                    "Search candidates by skills, experience years, location, and availability."
                            + " Use when employers need to find matching talent.")
    public String searchCandidates(
            String skills, String minYearsExperience, String desiredPosition) {

        List<Candidate> all = candidateRepository.findAll();

        Stream<Candidate> stream =
                all.stream()
                        .filter(
                                c ->
                                        c.getDeletedAt() == null
                                                && Boolean.TRUE.equals(c.getIsOpenToWork()));

        if (desiredPosition != null && !desiredPosition.isBlank()) {
            String lower = desiredPosition.toLowerCase();
            stream =
                    stream.filter(
                            c ->
                                    c.getDesiredPosition() != null
                                            && c.getDesiredPosition()
                                                    .toLowerCase()
                                                    .contains(lower));
        }

        if (minYearsExperience != null && !minYearsExperience.isBlank()) {
            try {
                short minYears = Short.parseShort(minYearsExperience);
                stream =
                        stream.filter(
                                c ->
                                        c.getYearsOfExperience() != null
                                                && c.getYearsOfExperience() >= minYears);
            } catch (NumberFormatException ignored) {
            }
        }

        if (skills != null && !skills.isBlank()) {
            String[] requiredSkills =
                    Arrays.stream(skills.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .toArray(String[]::new);
            stream =
                    stream.filter(
                            c -> {
                                if (c.getSkills() == null) return false;
                                List<String> candidateSkills =
                                        Arrays.stream(c.getSkills())
                                                .map(String::toLowerCase)
                                                .toList();
                                return Arrays.stream(requiredSkills)
                                        .anyMatch(candidateSkills::contains);
                            });
        }

        List<Candidate> results = stream.limit(10).toList();

        if (results.isEmpty()) {
            return "No matching candidates found.";
        }

        StringBuilder sb =
                new StringBuilder("Found ").append(results.size()).append(" candidates:\n");
        for (Candidate c : results) {
            sb.append("- [")
                    .append(c.getId())
                    .append("] ")
                    .append(c.getDesiredPosition() != null ? c.getDesiredPosition() : "N/A");
            if (c.getYearsOfExperience() != null) {
                sb.append(" | ").append(c.getYearsOfExperience()).append(" yrs exp");
            }
            if (c.getSkills() != null) {
                sb.append(" | Skills: ").append(String.join(", ", c.getSkills()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

package com.vietrecruit.feature.ai.shared.tools;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.service.JobService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobSearchTool {

    private final JobService jobService;

    @Tool(
            description =
                    "Search published jobs by skills, location, job category, or salary range."
                            + " Use when matching candidates to opportunities.")
    public String searchJobs(
            String keyword, String locationId, String categoryId, String minSalary) {

        UUID parsedLocationId = parseUuid(locationId);
        UUID parsedCategoryId = parseUuid(categoryId);
        BigDecimal parsedMinSalary = parseBigDecimal(minSalary);

        Page<Job> results =
                jobService.searchPublishedJobs(
                        keyword,
                        parsedLocationId,
                        parsedCategoryId,
                        parsedMinSalary,
                        PageRequest.of(0, 10));

        return formatJobResults(results.getContent());
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatJobResults(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return "No matching jobs found.";
        }
        StringBuilder sb = new StringBuilder("Found ").append(jobs.size()).append(" jobs:\n");
        for (Job job : jobs) {
            sb.append("- [").append(job.getId()).append("] ").append(job.getTitle());
            if (job.getMinSalary() != null && job.getMaxSalary() != null) {
                sb.append(" | Salary: ")
                        .append(job.getMinSalary())
                        .append("-")
                        .append(job.getMaxSalary())
                        .append(" ")
                        .append(job.getCurrency());
            }
            if (job.getDeadline() != null) {
                sb.append(" | Deadline: ").append(job.getDeadline());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

package com.vietrecruit.common.ai.tools;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobSearchTool {

    private final JobRepository jobRepository;

    @Tool(
            description =
                    "Search published jobs by skills, location, job category, or salary range."
                            + " Use when matching candidates to opportunities.")
    public String searchJobs(
            String keyword, String locationId, String categoryId, String minSalary) {

        Specification<Job> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
                    predicates.add(cb.isNull(root.get("deletedAt")));

                    if (keyword != null && !keyword.isBlank()) {
                        String pattern = "%" + keyword.toLowerCase() + "%";
                        predicates.add(
                                cb.or(
                                        cb.like(cb.lower(root.get("title")), pattern),
                                        cb.like(cb.lower(root.get("description")), pattern)));
                    }

                    if (locationId != null && !locationId.isBlank()) {
                        try {
                            predicates.add(
                                    cb.equal(root.get("locationId"), UUID.fromString(locationId)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    if (categoryId != null && !categoryId.isBlank()) {
                        try {
                            predicates.add(
                                    cb.equal(root.get("categoryId"), UUID.fromString(categoryId)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }

                    if (minSalary != null && !minSalary.isBlank()) {
                        try {
                            predicates.add(
                                    cb.greaterThanOrEqualTo(
                                            root.get("maxSalary"), new BigDecimal(minSalary)));
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        Page<Job> results = jobRepository.findAll(spec, PageRequest.of(0, 10));
        return formatJobResults(results.getContent());
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

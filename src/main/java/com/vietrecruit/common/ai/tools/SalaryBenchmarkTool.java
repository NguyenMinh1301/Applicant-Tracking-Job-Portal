package com.vietrecruit.common.ai.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.criteria.Predicate;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.repository.JobRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SalaryBenchmarkTool {

    private final JobRepository jobRepository;

    @Tool(
            description =
                    "Get salary statistics for a job title in a specific location and industry."
                            + " Returns min, median, max from existing job postings.")
    public String getSalaryBenchmark(String jobTitle, String location) {

        Specification<Job> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
                    predicates.add(cb.isNull(root.get("deletedAt")));
                    predicates.add(cb.isNotNull(root.get("minSalary")));
                    predicates.add(cb.isNotNull(root.get("maxSalary")));

                    if (jobTitle != null && !jobTitle.isBlank()) {
                        String pattern = "%" + jobTitle.toLowerCase() + "%";
                        predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        List<Job> jobs = jobRepository.findAll(spec);

        if (jobs.isEmpty()) {
            return "No salary data found for '"
                    + (jobTitle != null ? jobTitle : "all positions")
                    + "'.";
        }

        List<BigDecimal> minSalaries =
                jobs.stream().map(Job::getMinSalary).filter(Objects::nonNull).sorted().toList();

        List<BigDecimal> maxSalaries =
                jobs.stream().map(Job::getMaxSalary).filter(Objects::nonNull).sorted().toList();

        BigDecimal overallMin =
                minSalaries.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal overallMax =
                maxSalaries.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        BigDecimal medianMin = median(minSalaries);
        BigDecimal medianMax = median(maxSalaries);

        String currency = jobs.getFirst().getCurrency();

        return String.format(
                "Salary benchmark for '%s' (%d job postings):\n"
                        + "  Min salary range: %s %s\n"
                        + "  Median salary range: %s - %s %s\n"
                        + "  Max salary range: %s %s",
                jobTitle != null ? jobTitle : "all positions",
                jobs.size(),
                overallMin.toPlainString(),
                currency,
                medianMin.toPlainString(),
                medianMax.toPlainString(),
                currency,
                overallMax.toPlainString(),
                currency);
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        if (sorted.isEmpty()) return BigDecimal.ZERO;
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return sorted.get(mid - 1)
                    .add(sorted.get(mid))
                    .divide(BigDecimal.TWO, RoundingMode.HALF_UP);
        }
        return sorted.get(mid);
    }
}

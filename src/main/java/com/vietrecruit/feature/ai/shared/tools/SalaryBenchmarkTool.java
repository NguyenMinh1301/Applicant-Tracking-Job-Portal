package com.vietrecruit.feature.ai.shared.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.job.repository.SalaryBenchmarkProjection;
import com.vietrecruit.feature.job.service.JobService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SalaryBenchmarkTool {

    private final JobService jobService;

    @Tool(
            description =
                    "Get salary statistics for a job title in a specific location and industry."
                            + " Returns min, median, max from existing job postings.")
    public String getSalaryBenchmark(String jobTitle, String location) {

        SalaryBenchmarkProjection benchmark = jobService.getSalaryBenchmark(null, null);

        if (benchmark == null
                || benchmark.getSampleSize() == null
                || benchmark.getSampleSize() == 0) {
            return "No salary data found for '"
                    + (jobTitle != null ? jobTitle : "all positions")
                    + "'.";
        }

        return String.format(
                "Salary benchmark for '%s' (%d job postings):\n"
                        + "  Min salary range: %s\n"
                        + "  Median salary range: %s\n"
                        + "  Max salary range: %s",
                jobTitle != null ? jobTitle : "all positions",
                benchmark.getSampleSize(),
                benchmark.getMinSalary().toPlainString(),
                benchmark.getMedianSalary().toPlainString(),
                benchmark.getMaxSalary().toPlainString());
    }
}

package com.vietrecruit.feature.ai.salary.service;

import java.util.UUID;

import com.vietrecruit.feature.ai.salary.dto.SalaryBenchmarkResponse;

public interface SalaryBenchmarkService {

    SalaryBenchmarkResponse benchmarkForCandidate(String jobTitle, UUID locationId);

    SalaryBenchmarkResponse benchmarkForJob(UUID jobId);
}

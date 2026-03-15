package com.vietrecruit.feature.job.repository;

import java.math.BigDecimal;

public interface SalaryBenchmarkProjection {

    BigDecimal getMinSalary();

    BigDecimal getMaxSalary();

    BigDecimal getMedianSalary();

    Long getSampleSize();
}

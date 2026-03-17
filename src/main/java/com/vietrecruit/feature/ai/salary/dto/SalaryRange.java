package com.vietrecruit.feature.ai.salary.dto;

import java.math.BigDecimal;

public record SalaryRange(BigDecimal min, BigDecimal median, BigDecimal max) {}

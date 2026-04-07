package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerformanceReport {
    private Long id;
    private Long cycleId;
    private Long userId;
    private BigDecimal score;
    private String detailJson;
}

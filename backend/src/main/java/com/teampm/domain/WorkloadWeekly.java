package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkloadWeekly {
    private Long id;
    private Long userId;
    private Long teamId;
    private String yearWeek;
    private BigDecimal actualHours;
    private BigDecimal forecastHours;
}

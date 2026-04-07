package com.teampm.domain;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class PerformanceCycle {
    private Long id;
    private Long teamId;
    private String cycleType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer closedFlag;
    private Instant createdAt;
}

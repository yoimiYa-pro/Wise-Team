package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
public class Task {
    private Long id;
    private Long teamId;
    private Long creatorId;
    private Long assigneeId;
    private String title;
    private String description;
    private BigDecimal difficulty;
    private Integer priority;
    private BigDecimal estHours;
    private LocalDate deadline;
    private Integer progress;
    private String status;
    private String requiredSkillsJson;
    private String riskLevel;
    private BigDecimal delayProbability;
    private Instant lastRiskEvalAt;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}

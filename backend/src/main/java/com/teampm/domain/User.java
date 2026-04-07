package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String role;
    private String skillsJson;
    private BigDecimal baseCapacity;
    private BigDecimal avgPerformance;
    private BigDecimal delayHistoryScore;
    private Integer status;
    private Instant createdAt;
    private Instant updatedAt;
}

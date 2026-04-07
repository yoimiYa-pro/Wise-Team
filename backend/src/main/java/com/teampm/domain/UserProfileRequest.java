package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class UserProfileRequest {
    private Long id;
    private Long userId;
    private String proposedDisplayName;
    private String proposedSkillsJson;
    private BigDecimal proposedBaseCapacity;
    private String applyReason;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewComment;
    private Instant createdAt;
    private Instant updatedAt;
}

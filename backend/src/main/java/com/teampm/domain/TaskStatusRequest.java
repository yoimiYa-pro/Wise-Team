package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class TaskStatusRequest {
    private Long id;
    private Long taskId;
    private Long applicantId;
    private String fromStatus;
    private String toStatus;
    private String applyReason;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewComment;
    private Instant createdAt;
    private Instant updatedAt;
}

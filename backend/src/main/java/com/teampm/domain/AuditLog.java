package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class AuditLog {
    private Long id;
    private Long actorId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String detail;
    private Instant createdAt;
}

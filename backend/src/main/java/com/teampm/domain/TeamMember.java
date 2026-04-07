package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class TeamMember {
    private Long id;
    private Long teamId;
    private Long userId;
    private String approvalStatus;
    private Instant joinedAt;
}

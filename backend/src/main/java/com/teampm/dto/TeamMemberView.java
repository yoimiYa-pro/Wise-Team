package com.teampm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberView {
    private Long id;
    private Long teamId;
    private Long userId;
    private String approvalStatus;
    private String username;
    private String displayName;
    private Instant joinedAt;
}

package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class PeerReview {
    private Long id;
    private Long teamId;
    private Long cycleId;
    private Long targetUserId;
    private Long reviewerUserId;
    private String dimensionScoresJson;
    private Instant createdAt;
}

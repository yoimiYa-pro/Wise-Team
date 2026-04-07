package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class Team {
    private Long id;
    private String name;
    private String goal;
    private String announcement;
    private String status;
    private Long managerId;
    private Instant createdAt;
    private Instant updatedAt;
}

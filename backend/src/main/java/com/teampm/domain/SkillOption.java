package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class SkillOption {
    private Long id;
    /** 0 = 全局；正数为团队 id */
    private Long teamId;
    private String skillCode;
    private String label;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
}

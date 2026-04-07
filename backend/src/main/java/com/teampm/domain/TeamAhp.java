package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TeamAhp {
    private Long id;
    private Long teamId;
    private String matrixJson;
    private String weightsJson;
    private BigDecimal crValue;
    private Integer consistentFlag;
    private Instant updatedAt;
}

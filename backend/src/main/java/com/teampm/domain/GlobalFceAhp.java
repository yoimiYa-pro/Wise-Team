package com.teampm.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class GlobalFceAhp {
    public static final long SINGLETON_ID = 1L;

    private Long id;
    private String matrixJson;
    private String weightsJson;
    private BigDecimal crValue;
    private Integer consistentFlag;
    private Instant updatedAt;
}

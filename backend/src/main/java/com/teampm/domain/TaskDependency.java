package com.teampm.domain;

import lombok.Data;

@Data
public class TaskDependency {
    private Long id;
    private Long predecessorId;
    private Long successorId;
}

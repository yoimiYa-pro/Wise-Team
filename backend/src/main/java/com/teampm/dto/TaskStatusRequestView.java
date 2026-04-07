package com.teampm.dto;

import com.teampm.domain.TaskStatusRequest;
import lombok.Data;

@Data
public class TaskStatusRequestView {
    private TaskStatusRequest request;
    private String taskTitle;
    private String applicantUsername;
    private Long teamId;
    private String teamName;
}

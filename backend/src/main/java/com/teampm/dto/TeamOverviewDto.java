package com.teampm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamOverviewDto {
    private Long id;
    private String name;
    private String goal;
    private String announcement;
    private String status;
    private Long managerId;
    private String managerUsername;
    private int approvedMemberCount;
}

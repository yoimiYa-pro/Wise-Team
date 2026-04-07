package com.teampm.dto;

import com.teampm.domain.UserProfileRequest;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProfileRequestView {
    private UserProfileRequest request;
    private String applicantUsername;
    /** 审核时对比用：用户当前已生效资料 */
    private String currentDisplayName;
    private BigDecimal currentBaseCapacity;
    private String currentSkillsJson;
}

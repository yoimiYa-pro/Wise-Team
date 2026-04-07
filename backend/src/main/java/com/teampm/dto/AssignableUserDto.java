package com.teampm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignableUserDto {
    private Long userId;
    private String username;
    private String displayName;
}

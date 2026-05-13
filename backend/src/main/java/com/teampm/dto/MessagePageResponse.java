package com.teampm.dto;

import com.teampm.domain.InAppMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagePageResponse {
    private List<InAppMessage> items;
    private long total;
}

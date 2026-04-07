package com.teampm.domain;

import lombok.Data;

import java.time.Instant;

@Data
public class InAppMessage {
    private Long id;
    private Long userId;
    private String title;
    private String body;
    private String msgType;
    private Integer readFlag;
    private String refType;
    private Long refId;
    private Instant createdAt;
}

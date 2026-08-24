package com.rain.zhixueadmin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resource;
    private String status;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
    private String details;
}

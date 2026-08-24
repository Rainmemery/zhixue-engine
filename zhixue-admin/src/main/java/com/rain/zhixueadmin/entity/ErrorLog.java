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
public class ErrorLog {
    private Long id;
    private Long userId;
    private String username;
    private String requestUrl;
    private String requestMethod;
    private String requestParams;
    private String errorMessage;
    private String errorStack;
    private Integer statusCode;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}

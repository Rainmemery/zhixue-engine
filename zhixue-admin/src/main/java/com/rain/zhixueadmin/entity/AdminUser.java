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
public class AdminUser {
    private Long id;
    private String username;
    private String email;
    private Long roleId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}

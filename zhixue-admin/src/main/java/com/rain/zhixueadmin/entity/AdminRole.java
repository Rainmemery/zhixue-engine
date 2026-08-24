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
public class AdminRole {
    private Long id;
    private String name;
    private String description;
    private String permissions;
    private LocalDateTime createdAt;
}

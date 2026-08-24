package com.rain.zhixueproblem.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhixueProblemCategory {
    private Long id;
    private String name;
    private String nameEn;
    private String description;
    private String icon;
    private Long parentId;
    private Integer sortOrder;
    private Integer problemCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
}

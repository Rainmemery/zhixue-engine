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
public class ProblemCategory {
    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

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
public class Problem {
    private Long id;
    private String title;
    private String description;
    private String difficulty;
    private Long categoryId;
    private String tags;
    private String initialCode;
    private String solutionCode;
    private String testCases;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private Double acceptanceRate;
    private Integer viewCount;
    private Integer submitCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
public class CodeSubmission {
    private Long id;
    private Long problemId;
    private Long userId;
    private String code;
    private String language;
    private String status;
    private Double score;
    private Long executionTimeMs;
    private Long memoryUsedKb;
    private String testCaseResults;
    private String feedback;
    private LocalDateTime createdAt;
}

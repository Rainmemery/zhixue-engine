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
public class ZhixueTestcaseResult {
    private Long id;
    private Long submissionId;
    private Long testcaseId;
    private String testcaseName;
    private String status;
    private Integer executionTimeMs;
    private Long memoryUsedKb;
    private String actualOutput;
    private String errorMessage;
    private LocalDateTime createdAt;
}

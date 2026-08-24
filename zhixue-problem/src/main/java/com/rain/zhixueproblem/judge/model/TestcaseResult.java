package com.rain.zhixueproblem.judge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestcaseResult {
    private Long testcaseId;
    private String testcaseName;
    private String status;
    private Integer executionTimeMs;
    private Long memoryUsedKb;
    private String actualOutput;
    private String errorOutput;
}

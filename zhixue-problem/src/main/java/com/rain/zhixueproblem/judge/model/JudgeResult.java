package com.rain.zhixueproblem.judge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResult {
    private String status;
    private Double score;
    private Integer totalTimeMs;
    private Long maxMemoryKb;
    private Integer passedCount;
    private Integer totalCount;
    private String errorMessage;
    private List<TestcaseResult> testcaseResults;
}

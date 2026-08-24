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
public class ZhixueSubmission {
    private Long id;
    private Long userId;
    private Long problemId;
    private String language;
    private String code;
    private Integer codeLength;
    private String status;
    private Double score;
    private Integer totalTimeMs;
    private Long maxMemoryKb;
    private Integer passedCount;
    private Integer totalCount;
    private String errorMessage;
    private Boolean isContest;
    private LocalDateTime createdAt;
    private LocalDateTime judgedAt;
}

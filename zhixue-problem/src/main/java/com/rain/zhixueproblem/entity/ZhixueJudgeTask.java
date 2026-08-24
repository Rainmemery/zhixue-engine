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
public class ZhixueJudgeTask {
    private Long id;
    private Long submissionId;
    private String status;
    private Integer priority;
    private Integer retryCount;
    private Integer maxRetry;
    private String workerId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}

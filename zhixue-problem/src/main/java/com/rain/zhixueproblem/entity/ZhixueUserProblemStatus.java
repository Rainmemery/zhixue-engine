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
public class ZhixueUserProblemStatus {
    private Long id;
    private Long userId;
    private Long problemId;
    private String status;
    private Long bestSubmissionId;
    private Integer bestTimeMs;
    private Long bestMemoryKb;
    private Integer attemptCount;
    private LocalDateTime acceptedAt;
    private LocalDateTime updatedAt;
}

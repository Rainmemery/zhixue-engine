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
public class ZhixueProblemTestcase {
    private Long id;
    private Long problemId;
    private String testcaseName;
    private String inputFilePath;
    private String outputFilePath;
    private Long inputFileSize;
    private Long outputFileSize;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private Boolean isSample;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

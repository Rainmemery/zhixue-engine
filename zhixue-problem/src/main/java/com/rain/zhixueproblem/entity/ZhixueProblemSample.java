package com.rain.zhixueproblem.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZhixueProblemSample {
    private Long id;
    private Long problemId;
    @JsonProperty("input")
    private String sampleInput;
    @JsonProperty("output")
    private String sampleOutput;
    private String explanation;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

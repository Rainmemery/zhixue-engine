package com.rain.zhixueai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGenerationRequest {

    private String knowledgePoint;
    private String difficulty;
    @Builder.Default
    private String problemType = "programming";
    @Builder.Default
    private String language = "cpp";
    private String context;
    private Long userId;
}

package com.rain.zhixueai.agent.generation;

import com.rain.zhixueai.agent.dto.ProblemGenerationRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProblemGenerationTask {
    private String taskId;
    private ProblemGenerationRequest request;
    private GenerationStatusTracker.GenerationStatus status;
    private java.time.LocalDateTime createdAt;
}

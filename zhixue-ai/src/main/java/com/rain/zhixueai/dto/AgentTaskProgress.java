package com.rain.zhixueai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AgentTaskProgress {
    private String taskId;
    private String status;
    private List<AgentChatResponse.MessageSegment> newSegments;
    private List<AgentChatResponse.ProblemInfo> allProblems;
    private int totalSegments;
    private boolean completed;
    private String error;
}

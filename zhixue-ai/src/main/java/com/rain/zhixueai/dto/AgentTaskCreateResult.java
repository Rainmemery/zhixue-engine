package com.rain.zhixueai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AgentTaskCreateResult {
    private String taskId;
    private String status;

    public static AgentTaskCreateResult created(String taskId) {
        AgentTaskCreateResult result = new AgentTaskCreateResult();
        result.setTaskId(taskId);
        result.setStatus("created");
        return result;
    }
}

package com.rain.zhixueai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentNotificationMessage {

    private String eventType;

    private String taskId;

    private Long userId;

    private String status;

    private Long timestamp;

    private ProblemNotificationData problem;

    private List<ProblemNotificationData> problems;

    private String toolName;

    private String message;

    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProblemNotificationData {

        private Long problemId;

        private String title;

        private String difficulty;

        @JsonProperty("isRecommended")
        private Boolean isRecommended;

        private String status;

        private String problemType;

        private Double acceptanceRate;
    }

    public static AgentNotificationMessage taskCompleted(String taskId, Long userId, List<ProblemNotificationData> problems) {
        return AgentNotificationMessage.builder()
                .eventType("TASK_COMPLETED")
                .taskId(taskId)
                .userId(userId)
                .status("completed")
                .timestamp(System.currentTimeMillis())
                .problems(problems)
                .message("任务执行完成")
                .build();
    }

    public static AgentNotificationMessage taskFailed(String taskId, Long userId, String error) {
        return AgentNotificationMessage.builder()
                .eventType("TASK_FAILED")
                .taskId(taskId)
                .userId(userId)
                .status("failed")
                .timestamp(System.currentTimeMillis())
                .error(error)
                .message("任务执行失败")
                .build();
    }

    public static AgentNotificationMessage problemGenerated(String taskId, Long userId, ProblemNotificationData problem) {
        return AgentNotificationMessage.builder()
                .eventType("PROBLEM_GENERATED")
                .taskId(taskId)
                .userId(userId)
                .status("completed")
                .timestamp(System.currentTimeMillis())
                .problem(problem)
                .toolName("problem_generate")
                .message("题目生成完成")
                .build();
    }

    public static AgentNotificationMessage problemRecommended(String taskId, Long userId, List<ProblemNotificationData> problems) {
        return AgentNotificationMessage.builder()
                .eventType("PROBLEM_RECOMMENDED")
                .taskId(taskId)
                .userId(userId)
                .status("completed")
                .timestamp(System.currentTimeMillis())
                .problems(problems)
                .toolName("problem_recommend")
                .message("题目推荐完成")
                .build();
    }

    public static AgentNotificationMessage toolExecuted(String taskId, Long userId, String toolName, boolean success) {
        return AgentNotificationMessage.builder()
                .eventType("TOOL_EXECUTED")
                .taskId(taskId)
                .userId(userId)
                .status(success ? "success" : "failed")
                .timestamp(System.currentTimeMillis())
                .toolName(toolName)
                .message(success ? "工具执行成功" : "工具执行失败")
                .build();
    }
}

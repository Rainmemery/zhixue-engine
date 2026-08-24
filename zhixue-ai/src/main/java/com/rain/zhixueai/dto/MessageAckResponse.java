package com.rain.zhixueai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAckResponse {

    private boolean success;

    private String taskId;

    private int acknowledgedCount;

    private List<SegmentAckResult> results;

    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentAckResult {
        private Integer sequenceNumber;
        private boolean acknowledged;
        private String error;
    }

    public static MessageAckResponse success(String taskId, int count, List<SegmentAckResult> results) {
        return MessageAckResponse.builder()
                .success(true)
                .taskId(taskId)
                .acknowledgedCount(count)
                .results(results)
                .message("确认成功")
                .build();
    }

    public static MessageAckResponse failure(String taskId, String message) {
        return MessageAckResponse.builder()
                .success(false)
                .taskId(taskId)
                .message(message)
                .build();
    }
}

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
public class MessageAckRequest {

    private String taskId;

    private List<SegmentAck> segments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentAck {
        private Integer sequenceNumber;
        private String checksum;
        private String status;
        private Long receivedAt;
    }
}

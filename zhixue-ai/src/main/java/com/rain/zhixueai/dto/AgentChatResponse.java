package com.rain.zhixueai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class AgentChatResponse {

    private boolean success;

    private String error;

    private List<MessageSegment> segments;

    private List<ProblemInfo> problems;

    public static AgentChatResponse ok(List<MessageSegment> segments, List<ProblemInfo> problems) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSuccess(true);
        response.setSegments(segments);
        response.setProblems(problems);
        return response;
    }

    public static AgentChatResponse fail(String error) {
        AgentChatResponse response = new AgentChatResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }

    @Data
    @NoArgsConstructor
    public static class MessageSegment {

        private Integer sequenceNumber;

        private String type;

        private String content;

        @JsonProperty("toolName")
        private String toolName;

        private String displayName;

        private String status;

        private Boolean success;

        private Map<String, Object> data;

        private String checksum;

        private String sendStatus;

        private int retryCount;

        private Long timestamp;

        public static MessageSegment text(String content) {
            MessageSegment segment = new MessageSegment();
            segment.setType("text");
            segment.setContent(content);
            segment.setSendStatus("pending");
            segment.setRetryCount(0);
            segment.setTimestamp(System.currentTimeMillis());
            return segment;
        }

        public static MessageSegment toolCall(String toolName, String displayName, String status) {
            MessageSegment segment = new MessageSegment();
            segment.setType("tool_call");
            segment.setToolName(toolName);
            segment.setDisplayName(displayName);
            segment.setStatus(status);
            segment.setSendStatus("pending");
            segment.setRetryCount(0);
            segment.setTimestamp(System.currentTimeMillis());
            return segment;
        }

        public static MessageSegment toolResult(String toolName, boolean success, Map<String, Object> data) {
            MessageSegment segment = new MessageSegment();
            segment.setType("tool_result");
            segment.setToolName(toolName);
            segment.setSuccess(success);
            segment.setData(data);
            segment.setSendStatus("pending");
            segment.setRetryCount(0);
            segment.setTimestamp(System.currentTimeMillis());
            return segment;
        }

        public void calculateChecksum() {
            StringBuilder sb = new StringBuilder();
            sb.append(type != null ? type : "");
            sb.append(content != null ? content : "");
            sb.append(toolName != null ? toolName : "");
            sb.append(displayName != null ? displayName : "");
            sb.append(timestamp != null ? timestamp : "");
            this.checksum = md5(sb.toString());
        }

        public boolean verifyChecksum() {
            if (checksum == null) return false;
            String originalChecksum = this.checksum;
            calculateChecksum();
            return originalChecksum.equals(this.checksum);
        }

        private static String md5(String input) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                return String.valueOf(input.hashCode());
            }
        }
    }

    @Data
    @NoArgsConstructor
    public static class ProblemInfo {

        private Long problemId;

        private String title;

        private String difficulty;

        @JsonProperty("isRecommended")
        private boolean isRecommended;

        @JsonProperty("saveFailed")
        private boolean saveFailed;

        private String saveError;

        private Double acceptanceRate;

        private String problemType;
    }
}

package com.rain.zhixueai.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.rain.zhixueai.enums.StreamEventType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.zip.CRC32;

@Slf4j
@Data
@NoArgsConstructor
public class AiResponse {

    private StreamEventType type;

    private String content;

    private String delta;

    private Boolean complete;

    @JSONField(name = "error")
    private String errorMessage;

    @JSONField(name = "toolName")
    private String toolName;

    @JSONField(name = "toolArgs")
    private Map<String, Object> toolArgs;

    @JSONField(name = "success")
    private Boolean success;

    @JSONField(name = "data")
    private Map<String, Object> data;

    private String callId;

    @JSONField(name = "sequence")
    private Long sequence;

    @JSONField(name = "checksum")
    private String checksum;

    @JSONField(name = "streamId")
    private String streamId;

    @JSONField(name = "totalChunks")
    private Long totalChunks;

    @JSONField(name = "metrics")
    private StreamMetrics metrics;

    public AiResponse(StreamEventType type, String content, String delta) {
        this.type = type;
        this.content = content;
        this.delta = delta;
        this.complete = StreamEventType.COMPLETE.equals(type);
    }

    public AiResponse(String content) {
        this.type = StreamEventType.COMPLETE;
        this.content = content;
        this.delta = content;
        this.complete = true;
    }

    public AiResponse createErrorResponse(String errorMessage) {
        AiResponse response = new AiResponse();
        response.setType(StreamEventType.COMPLETE);
        response.setErrorMessage(errorMessage);
        response.setComplete(true);
        return response;
    }

    public boolean isError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    public boolean isComplete() {
        return complete != null && complete;
    }

    public String toJson() {
        try {
            com.alibaba.fastjson2.JSONObject json = new com.alibaba.fastjson2.JSONObject();
            json.put("type", type != null ? type.getCode() : "text");
            json.put("content", content != null ? content : "");
            if (delta != null) {
                json.put("delta", delta);
            }
            if (errorMessage != null) {
                json.put("error", errorMessage);
            }
            json.put("complete", complete != null ? complete : false);
            if (toolName != null) {
                json.put("toolName", toolName);
            }
            if (toolArgs != null) {
                json.put("toolArgs", toolArgs);
            }
            if (success != null) {
                json.put("success", success);
            }
            if (data != null) {
                json.put("data", data);
            }
            if (callId != null) {
                json.put("callId", callId);
            }
            if (sequence != null) {
                json.put("sequence", sequence);
            }
            if (checksum != null) {
                json.put("checksum", checksum);
            }
            if (streamId != null) {
                json.put("streamId", streamId);
            }
            if (totalChunks != null) {
                json.put("totalChunks", totalChunks);
            }
            if (metrics != null) {
                json.put("metrics", metrics.toMap());
            }
            String result = json.toJSONString();
            validateJsonIntegrity(result);
            return result;
        } catch (Exception e) {
            log.error("AiResponse序列化失败", e);
            return "{\"type\":\"complete\",\"content\":\"\",\"complete\":true,\"error\":\"序列化失败\"}";
        }
    }

    public void calculateChecksum() {
        if (delta != null && !delta.isEmpty()) {
            CRC32 crc32 = new CRC32();
            crc32.update(delta.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            this.checksum = String.format("%08x", crc32.getValue());
        }
    }

    public static String calculateChecksum(String data) {
        if (data == null || data.isEmpty()) {
            return "00000000";
        }
        CRC32 crc32 = new CRC32();
        crc32.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("%08x", crc32.getValue());
    }

    private void validateJsonIntegrity(String json) {
        if (json == null || json.isEmpty()) {
            log.warn("AiResponse JSON序列化结果为空");
            return;
        }
        int openBraces = 0;
        int closeBraces = 0;
        boolean inString = false;
        boolean escapeNext = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escapeNext) { escapeNext = false; continue; }
            if (c == '\\' && inString) { escapeNext = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') openBraces++;
            else if (c == '}') closeBraces++;
        }
        if (openBraces != closeBraces) {
            log.warn("AiResponse JSON完整性校验: 大括号不平衡 open={} close={}", openBraces, closeBraces);
        }
    }

    public static AiResponse toolCall(String callId, String toolName, Map<String, Object> toolArgs) {
        AiResponse response = new AiResponse();
        response.setType(StreamEventType.TOOL_CALL);
        response.callId = callId;
        response.setToolName(toolName);
        response.setToolArgs(toolArgs);
        response.setComplete(false);
        return response;
    }

    public static AiResponse toolResult(String callId, String toolName, boolean success, Map<String, Object> data) {
        AiResponse response = new AiResponse();
        response.setType(StreamEventType.TOOL_RESULT);
        response.callId = callId;
        response.setToolName(toolName);
        response.setSuccess(success);
        response.setData(data);
        response.setComplete(false);
        return response;
    }
}

package com.rain.zhixueai.controller;


import com.rain.zhixueai.dto.AgentChatResponse;
import com.rain.zhixueai.dto.AgentTaskCreateResult;
import com.rain.zhixueai.dto.AgentTaskProgress;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.enums.AiRole;
import com.rain.zhixueai.enums.AiModelType;
import com.rain.zhixueai.service.AiService;
import com.rain.zhixueai.util.JsonUtils;
import com.rain.zhixuecommon.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI控制器
 * 提供所有AI相关的RESTful API接口
 * 
 * @author rain
 * @since 2026-02-05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/")
public class AiController {
    
    @Autowired
    private AiService aiService;

    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 流式通用聊天接口
     * 
     * @param authHeader 授权头
     * @param request AI请求体
     * @return SSE发射器
     */
    @PostMapping("stream-chat")
    public SseEmitter streamChat(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiRequest request) {
        
        try {
            request.setMaxTokens(10000);
            
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return createErrorEmitter("消息列表不能为空");
            }
            
            return aiService.streamChat(request);
            
        } catch (Exception e) {
            log.error("流式聊天接口处理失败", e);
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + e.getMessage() + "\"}"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("发送错误响应失败", ex);
            }
            return emitter;
        }
    }
    
    /**
     * 流式代码解释接口
     * 
     * @param authHeader 授权头
     * @param request AI请求体
     * @return SSE发射器
     */
    @PostMapping("explain")
    public SseEmitter streamExplain(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiRequest request) {
        
        try {
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                return createErrorEmitter("代码内容不能为空");
            }
            
            if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
                return createErrorEmitter("编程语言不能为空");
            }
            
            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }
            
            return aiService.streamExplain(request);
            
        } catch (Exception e) {
            log.error("流式解释接口处理失败", e);
            return createErrorEmitter(e.getMessage());
        }
    }
    
    /**
     * 流式代码评审接口
     * 
     * @param authHeader 授权头
     * @param request AI请求体
     * @return SSE发射器
     */
    @PostMapping("review")
    public SseEmitter streamReview(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiRequest request) {
        
        try {
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                return createErrorEmitter("代码内容不能为空");
            }
            
            if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
                return createErrorEmitter("编程语言不能为空");
            }
            
            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }
            
            return aiService.streamReview(request);
            
        } catch (Exception e) {
            log.error("流式评审接口处理失败", e);
            return createErrorEmitter(e.getMessage());
        }
    }
    
    /**
     * 智能重构代码接口
     *
     * @param authHeader 授权头
     * @param request AI请求体
     * @return 重构结果
     */
    @PostMapping("refactor")
    public ResponseEntity<Map<String, Object>> refactorCode(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiRequest request) {

        try {
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "error", "代码内容不能为空")
                );
            }

            if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "error", "编程语言不能为空")
                );
            }

            Map<String, Object> result = aiService.refactorCode(request);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("重构接口处理失败", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "error", "重构失败: " + e.getMessage())
            );
        }
    }

    @PostMapping("complete")
    public ResponseEntity<Map<String, Object>> completeCode(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiRequest request) {

        try {
            if (request.getCode() == null || request.getCode().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "error", "代码内容不能为空")
                );
            }

            if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "error", "编程语言不能为空")
                );
            }

            if (request.getTemperature() == null) {
                request.setTemperature(0.2);
            }

            if (request.getMaxTokens() == null) {
                request.setMaxTokens(256);
            }

            Map<String, Object> result = aiService.completeCode(request);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("补全接口处理失败", e);
            return ResponseEntity.internalServerError().body(
                    Map.of("success", false, "error", "补全失败: " + e.getMessage())
            );
        }
    }

    /**
     * 流式智能提问者接口
     * 
     * @param authHeader 授权头
     * @param request AI请求体
     * @return SSE发射器
     */
    @PostMapping("questioner")
    public SseEmitter streamQuestioner(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AiRequest request) {
        
        try {
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return createErrorEmitter("消息列表不能为空");
            }
            
            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }
            
            return aiService.streamQuestioner(request);
            
        } catch (Exception e) {
            log.error("流式提问者接口处理失败", e);
            return createErrorEmitter(e.getMessage());
        }
    }

    @PostMapping("agent/chat")
    public SseEmitter streamAgentChat(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody AiRequest request) {

        try {
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return createErrorEmitter("消息列表不能为空");
            }

            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }

            if (request.getUserId() == null) {
                Long userIdFromToken = extractUserIdFromAuth(authHeader);
                if (userIdFromToken != null) {
                    request.setUserId(userIdFromToken);
                    log.info("userId from JWT token: {}", userIdFromToken);
                }
            }

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                request.setAuthToken(authHeader.substring(7).trim());
            }

            return aiService.streamAgentChat(request);

        } catch (Exception e) {
            log.error("Agent对话接口处理失败", e);
            return createErrorEmitter(e.getMessage());
        }
    }

    @PostMapping("agent/chat-sync")
    public ResponseEntity<AgentChatResponse> agentChatSync(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody AiRequest request) {

        try {
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                AgentChatResponse errorResponse = AgentChatResponse.fail("消息列表不能为空");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }

            if (request.getUserId() == null) {
                Long userIdFromToken = extractUserIdFromAuth(authHeader);
                if (userIdFromToken != null) {
                    request.setUserId(userIdFromToken);
                    log.info("Sync chat - userId from JWT token: {}", userIdFromToken);
                }
            }

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                request.setAuthToken(authHeader.substring(7).trim());
            }

            AgentChatResponse response = aiService.agentChatSync(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Agent同步对话接口处理失败", e);
            AgentChatResponse errorResponse = AgentChatResponse.fail("处理失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("agent/chat-async")
    public ResponseEntity<AgentTaskCreateResult> createAgentTask(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody AiRequest request) {

        try {
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return ResponseEntity.badRequest().body(AgentTaskCreateResult.created("invalid"));
            }

            if (request.getMaxTokens() == null) {
                request.setMaxTokens(10000);
            }

            if (request.getUserId() == null) {
                Long userIdFromToken = extractUserIdFromAuth(authHeader);
                if (userIdFromToken != null) {
                    request.setUserId(userIdFromToken);
                    log.info("Async chat - userId from JWT token: {}", userIdFromToken);
                }
            }

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                request.setAuthToken(authHeader.substring(7).trim());
            }

            AgentTaskCreateResult result = aiService.createAgentTask(request);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("创建异步Agent任务失败", e);
            return ResponseEntity.internalServerError().body(AgentTaskCreateResult.created("error"));
        }
    }

    @GetMapping("agent/chat-async/{taskId}/progress")
    public ResponseEntity<AgentTaskProgress> getAgentTaskProgress(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int after) {

        try {
            AgentTaskProgress progress = aiService.getAgentTaskProgress(taskId, after);
            if ("not_found".equals(progress.getStatus())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            log.error("获取Agent任务进度失败: taskId={}", taskId, e);
            AgentTaskProgress errorProgress = new AgentTaskProgress();
            errorProgress.setTaskId(taskId);
            errorProgress.setCompleted(true);
            errorProgress.setError("获取进度失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorProgress);
        }
    }

    @GetMapping("agent/generation-status/{taskId}")
    public ResponseEntity<Map<String, Object>> getGenerationStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId) {

        try {
            Map<String, Object> status = aiService.getGenerationStatus(taskId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("获取出题状态失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", "获取状态失败: " + e.getMessage())
            );
        }
    }

    @PostMapping("agent/acknowledge/{taskId}/{sequenceNumber}")
    public ResponseEntity<Map<String, Object>> acknowledgeSegment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId,
            @PathVariable int sequenceNumber,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            String checksum = body != null ? body.get("checksum") : null;
            Map<String, Object> result = aiService.acknowledgeSegment(taskId, sequenceNumber, checksum);
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                return ResponseEntity.badRequest().body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("确认消息段失败: taskId={}, sequenceNumber={}", taskId, sequenceNumber, e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", "确认失败: " + e.getMessage())
            );
        }
    }

    @PostMapping("agent/acknowledge/{taskId}/batch")
    public ResponseEntity<Map<String, Object>> acknowledgeSegments(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {

        try {
            @SuppressWarnings("unchecked")
            List<Integer> sequenceNumbers = (List<Integer>) body.get("sequenceNumbers");
            
            if (sequenceNumbers == null || sequenceNumbers.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", "序列号列表不能为空")
                );
            }
            
            Map<String, Object> result = aiService.acknowledgeSegments(taskId, sequenceNumbers);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("批量确认消息段失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", "批量确认失败: " + e.getMessage())
            );
        }
    }

    @GetMapping("agent/unacknowledged/{taskId}")
    public ResponseEntity<Map<String, Object>> getUnacknowledgedSegments(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId) {

        try {
            Map<String, Object> result = aiService.getUnacknowledgedSegments(taskId);
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                return ResponseEntity.badRequest().body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取未确认消息段失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", "获取失败: " + e.getMessage())
            );
        }
    }

    @GetMapping("agent/recover/{taskId}")
    public ResponseEntity<Map<String, Object>> recoverSegments(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int fromSeq) {

        try {
            Map<String, Object> result = aiService.recoverSegments(taskId, fromSeq);
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                log.warn("[恢复接口] 恢复失败: taskId={}, fromSeq={}, error={}", 
                    taskId, fromSeq, result.get("error"));
                return ResponseEntity.badRequest().body(result);
            }
            
            log.info("[恢复接口] 恢复成功: taskId={}, fromSeq={}, recoveredCount={}", 
                taskId, fromSeq, result.get("recoveredCount"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[恢复接口] 恢复异常: taskId={}, fromSeq={}", taskId, fromSeq, e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", "恢复失败: " + e.getMessage())
            );
        }
    }

    @GetMapping("agent/recovery-status/{taskId}")
    public ResponseEntity<Map<String, Object>> getRecoveryStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId) {

        try {
            Map<String, Object> result = aiService.getRecoveryStatus(taskId);
            
            if (Boolean.FALSE.equals(result.get("success"))) {
                return ResponseEntity.badRequest().body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取恢复状态失败: taskId={}", taskId, e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "error", "获取状态失败: " + e.getMessage())
            );
        }
    }
    
    /**
     * 切换AI模型
     * 
     * @param authHeader 授权头
     * @param modelType 模型类型
     * @return 响应结果
     */
    @PostMapping("switch-model")
    public ResponseEntity<Map<String, Object>> switchModel(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        
        try {
            String modelType = body.get("modelType");
            
            if (!AiModelType.isValid(modelType)) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "不支持的模型类型: " + modelType)
                );
            }
            
            AiModelType modelTypeEnum = AiModelType.fromCode(modelType);
            aiService.switchModel(modelTypeEnum);
            
            return ResponseEntity.ok(
                Map.of(
                    "success", true, 
                    "message", "模型切换成功",
                    "currentModel", modelTypeEnum.getCode()
                )
            );
            
        } catch (Exception e) {
            log.error("切换模型失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "切换模型失败: " + e.getMessage())
            );
        }
    }
    
    /**
     * 获取模型状态
     * 
     * @param authHeader 授权头
     * @return 模型状态信息
     */
    @PostMapping("model-status")
    public ResponseEntity<Map<String, Object>> getModelStatus(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            Map<String, Object> status = aiService.getModelStatus();
            status.put("success", true);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("获取模型状态失败", e);
            return ResponseEntity.internalServerError().body(
                Map.of("success", false, "message", "获取模型状态失败: " + e.getMessage())
            );
        }
    }


    private SseEmitter createErrorEmitter(String errorMessage) {
        SseEmitter emitter = new SseEmitter();
        try {
            emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + errorMessage + "\"}"));
            emitter.complete();
        } catch (Exception e) {
            log.error("创建错误发射器失败", e);
        }
        return emitter;
    }

    private Long extractUserIdFromAuth(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
            String userIdStr = jwtUtil.getUserIdFromToken(token);
            if (userIdStr != null) {
                return Long.parseLong(userIdStr);
            }
        } catch (Exception e) {
            log.debug("Failed to extract userId from JWT token: {}", e.getMessage());
        }
        return null;
    }
}

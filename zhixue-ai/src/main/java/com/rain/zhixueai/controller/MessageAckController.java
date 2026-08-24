package com.rain.zhixueai.controller;

import com.rain.zhixueai.agent.core.AgentTaskTracker;
import com.rain.zhixueai.dto.MessageAckRequest;
import com.rain.zhixueai.dto.MessageAckResponse;
import com.rain.zhixueai.service.MessageAckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai/agent")
@RequiredArgsConstructor
public class MessageAckController {

    private final MessageAckService messageAckService;

    @PostMapping("/ack")
    public ResponseEntity<MessageAckResponse> acknowledgeMessages(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody MessageAckRequest request) {
        
        log.info("[AckController] Received acknowledgment request: taskId={}, segmentCount={}", 
                request.getTaskId(), 
                request.getSegments() != null ? request.getSegments().size() : 0);
        
        MessageAckResponse response = messageAckService.acknowledgeSegments(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/ack/stats/{taskId}")
    public ResponseEntity<Map<String, Object>> getAckStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String taskId) {
        
        AgentTaskTracker.SegmentSendStats stats = messageAckService.getSendStats(taskId);
        
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "totalSegments", stats.getTotalSegments(),
                "acknowledged", stats.getAcknowledgedCount(),
                "sent", stats.getSentCount(),
                "pending", stats.getPendingCount(),
                "failed", stats.getFailedCount(),
                "retrying", stats.getRetryingCount()
        ));
    }
}

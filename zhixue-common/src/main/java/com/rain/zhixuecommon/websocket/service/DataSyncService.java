package com.rain.zhixuecommon.websocket.service;

import com.rain.zhixuecommon.websocket.dto.DataChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class DataSyncService {

    private final SimpMessagingTemplate messagingTemplate;
    
    private static final String TOPIC_PREFIX = "/topic/data/";
    
    private static final int MAX_RETRY = 3;
    
    private static final long RETRY_DELAY_MS = 1000;

    public DataSyncService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastDataChange(DataChangeEvent event) {
        broadcastDataChangeWithRetry(event, 0);
    }
    
    private void broadcastDataChangeWithRetry(DataChangeEvent event, int retryCount) {
        CompletableFuture.runAsync(() -> {
            try {
                String destination = TOPIC_PREFIX + event.getEntityType();
                messagingTemplate.convertAndSend(destination, event);
                
                messagingTemplate.convertAndSend(TOPIC_PREFIX + "all", event);
                
                log.debug("数据变更事件已推送: type={}, id={}, operation={}", 
                        event.getEntityType(), event.getEntityId(), event.getOperation());
            } catch (Exception e) {
                log.error("推送数据变更事件失败: {}", e.getMessage());
                if (retryCount < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                        broadcastDataChangeWithRetry(event, retryCount + 1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试被中断", ie);
                    }
                }
            }
        });
    }

    public void notifyUser(String userId, DataChangeEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                messagingTemplate.convertAndSendToUser(userId, "/queue/data", event);
                log.debug("用户数据变更通知已推送: userId={}, type={}", userId, event.getEntityType());
            } catch (Exception e) {
                log.error("推送用户数据变更通知失败: userId={}, error={}", userId, e.getMessage());
            }
        });
    }

    public void notifySpecificUsers(java.util.List<String> userIds, DataChangeEvent event) {
        userIds.forEach(userId -> notifyUser(userId, event));
    }
}

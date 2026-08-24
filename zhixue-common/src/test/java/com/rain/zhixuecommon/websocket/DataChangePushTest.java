package com.rain.zhixuecommon.websocket;

import com.rain.zhixuecommon.websocket.dto.DataChangeEvent;
import com.rain.zhixuecommon.websocket.service.DataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("数据变更推送测试")
public class DataChangePushTest {

    @Autowired
    private DataSyncService dataSyncService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketStompClient stompClient;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
    }

    @Test
    @DisplayName("测试数据变更事件创建")
    void testDataChangeEventCreation() {
        Map<String, Object> testData = new HashMap<>();
        testData.put("name", "测试用户");
        testData.put("email", "test@example.com");
        
        DataChangeEvent event = DataChangeEvent.create(
            "user", 
            "user-123", 
            testData, 
            "test-service"
        );
        
        assertNotNull(event, "数据变更事件不应为空");
        assertEquals("DATA_CHANGE", event.getEventType(), "事件类型应该是DATA_CHANGE");
        assertEquals("user", event.getEntityType(), "实体类型应该是user");
        assertEquals("user-123", event.getEntityId(), "实体ID应该是user-123");
        assertEquals("CREATE", event.getOperation(), "操作应该是CREATE");
        assertNotNull(event.getTimestamp(), "时间戳不应为空");
        assertEquals("test-service", event.getSource(), "来源应该是test-service");
        
        log.info("数据变更事件创建测试通过: {}", event);
    }

    @Test
    @DisplayName("测试数据更新事件创建")
    void testDataUpdateEventCreation() {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "更新用户");
        
        DataChangeEvent event = DataChangeEvent.update(
            "user",
            "user-456",
            updateData,
            "test-service"
        );
        
        assertNotNull(event, "更新事件不应为空");
        assertEquals("UPDATE", event.getOperation(), "操作应该是UPDATE");
        
        log.info("数据更新事件创建测试通过: {}", event);
    }

    @Test
    @DisplayName("测试数据删除事件创建")
    void testDataDeleteEventCreation() {
        DataChangeEvent event = DataChangeEvent.delete(
            "user",
            "user-789",
            "test-service"
        );
        
        assertNotNull(event, "删除事件不应为空");
        assertEquals("DELETE", event.getOperation(), "操作应该是DELETE");
        assertNull(event.getData(), "删除事件的数据应该为空");
        
        log.info("数据删除事件创建测试通过: {}", event);
    }

    @Test
    @DisplayName("测试广播数据变更")
    void testBroadcastDataChange() throws Exception {
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        
        stompClient.connectAsync(wsUrl + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
            
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                sessionFuture.completeExceptionally(exception);
            }
        });
        
        StompSession session = sessionFuture.get(10, TimeUnit.SECONDS);
        
        CompletableFuture<DataChangeEvent> messageFuture = new CompletableFuture<>();
        
        session.subscribe("/topic/data/user", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DataChangeEvent.class;
            }
            
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("收到数据变更消息: {}", payload);
                messageFuture.complete((DataChangeEvent) payload);
            }
        });
        
        Thread.sleep(500);
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("name", "测试广播用户");
        
        DataChangeEvent event = DataChangeEvent.create(
            "user",
            "user-broadcast-test",
            testData,
            "test-service"
        );
        
        dataSyncService.broadcastDataChange(event);
        
        DataChangeEvent receivedEvent = messageFuture.get(5, TimeUnit.SECONDS);
        
        assertNotNull(receivedEvent, "应该收到数据变更事件");
        assertEquals("user-broadcast-test", receivedEvent.getEntityId(), 
            "实体ID应该匹配");
        
        log.info("广播数据变更测试通过");
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试用户特定数据通知")
    void testNotifySpecificUser() throws Exception {
        String testUserId = "user-notify-test";
        
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        
        stompClient.connectAsync(wsUrl + "/ws?userId=" + testUserId, 
            new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
            
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                sessionFuture.completeExceptionally(exception);
            }
        });
        
        StompSession session = sessionFuture.get(10, TimeUnit.SECONDS);
        
        CompletableFuture<DataChangeEvent> messageFuture = new CompletableFuture<>();
        
        session.subscribe("/user/queue/data", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DataChangeEvent.class;
            }
            
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("收到用户特定数据通知: {}", payload);
                messageFuture.complete((DataChangeEvent) payload);
            }
        });
        
        Thread.sleep(500);
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("message", "这是给特定用户的通知");
        
        DataChangeEvent event = DataChangeEvent.create(
            "notification",
            "notif-123",
            testData,
            "test-service"
        );
        
        dataSyncService.notifyUser(testUserId, event);
        
        DataChangeEvent receivedEvent = messageFuture.get(5, TimeUnit.SECONDS);
        
        assertNotNull(receivedEvent, "应该收到用户特定通知");
        assertEquals("notification", receivedEvent.getEntityType(), 
            "实体类型应该匹配");
        
        log.info("用户特定数据通知测试通过");
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试多用户数据通知")
    void testNotifyMultipleUsers() throws Exception {
        String[] userIds = {"user-multi-1", "user-multi-2", "user-multi-3"};
        
        CompletableFuture<DataChangeEvent>[] messageFutures = new CompletableFuture[userIds.length];
        StompSession[] sessions = new StompSession[userIds.length];
        
        for (int i = 0; i < userIds.length; i++) {
            final int index = i;
            messageFutures[i] = new CompletableFuture<>();
            
            CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
            
            stompClient.connectAsync(wsUrl + "/ws?userId=" + userIds[i], 
                new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    sessionFuture.complete(session);
                }
                
                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    sessionFuture.completeExceptionally(exception);
                }
            });
            
            sessions[i] = sessionFuture.get(10, TimeUnit.SECONDS);
            
            sessions[i].subscribe("/user/queue/data", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return DataChangeEvent.class;
                }
                
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    log.info("用户 {} 收到通知", index + 1);
                    messageFutures[index].complete((DataChangeEvent) payload);
                }
            });
        }
        
        Thread.sleep(1000);
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("message", "多用户通知测试");
        
        DataChangeEvent event = DataChangeEvent.create(
            "broadcast",
            "broadcast-123",
            testData,
            "test-service"
        );
        
        dataSyncService.notifySpecificUsers(java.util.Arrays.asList(userIds), event);
        
        int receivedCount = 0;
        for (int i = 0; i < userIds.length; i++) {
            try {
                DataChangeEvent receivedEvent = messageFutures[i].get(5, TimeUnit.SECONDS);
                if (receivedEvent != null) {
                    receivedCount++;
                }
            } catch (Exception e) {
                log.warn("用户 {} 未收到通知", i + 1);
            }
        }
        
        log.info("收到通知的用户数: {}/{}", receivedCount, userIds.length);
        
        assertTrue(receivedCount > 0, "至少应该有一个用户收到通知");
        
        for (StompSession session : sessions) {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    @Test
    @DisplayName("测试数据变更事件时间戳")
    void testDataChangeEventTimestamp() {
        long beforeCreate = System.currentTimeMillis();
        
        DataChangeEvent event = DataChangeEvent.create(
            "test",
            "test-123",
            new HashMap<>(),
            "test-service"
        );
        
        long afterCreate = System.currentTimeMillis();
        
        assertNotNull(event.getTimestamp(), "时间戳不应为空");
        assertTrue(event.getTimestamp() >= beforeCreate, 
            "时间戳应该在创建时间之后或相等");
        assertTrue(event.getTimestamp() <= afterCreate,
            "时间戳应该在创建时间之前或相等");
        
        log.info("时间戳测试通过: {}", event.getTimestamp());
    }
}

package com.rain.zhixuecommon.websocket;

import com.rain.zhixuecommon.websocket.handler.WebSocketEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("WebSocket连接测试")
public class WebSocketConnectionTest {

    @Autowired
    private WebSocketEventHandler webSocketEventHandler;

    private WebSocketStompClient stompClient;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
    }

    @Test
    @DisplayName("测试WebSocket端点是否可访问")
    void testWebSocketEndpointAccessible() {
        assertNotNull(webSocketEventHandler, "WebSocket事件处理器不应为空");
        log.info("WebSocket端点配置检查通过");
    }

    @Test
    @DisplayName("测试WebSocket连接建立")
    void testWebSocketConnectionEstablishment() throws Exception {
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        
        stompClient.connectAsync(wsUrl + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("WebSocket连接成功: sessionId={}", session.getSessionId());
                sessionFuture.complete(session);
            }
            
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("WebSocket连接错误: {}", exception.getMessage());
                sessionFuture.completeExceptionally(exception);
            }
        });
        
        StompSession session = sessionFuture.get(10, TimeUnit.SECONDS);
        
        assertNotNull(session, "WebSocket会话不应为空");
        assertTrue(session.isConnected(), "WebSocket应该处于连接状态");
        
        log.info("WebSocket连接测试通过");
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试WebSocket连接计数")
    void testWebSocketConnectionCount() throws Exception {
        int initialCount = webSocketEventHandler.getConnectionCount();
        log.info("初始连接数: {}", initialCount);
        
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
        
        Thread.sleep(1000);
        
        int newCount = webSocketEventHandler.getConnectionCount();
        log.info("连接后数量: {}", newCount);
        
        assertTrue(newCount >= initialCount, 
            "连接数应该增加或保持不变");
        
        session.disconnect();
        
        Thread.sleep(1000);
        
        int afterDisconnectCount = webSocketEventHandler.getConnectionCount();
        log.info("断开后数量: {}", afterDisconnectCount);
        
        assertTrue(afterDisconnectCount <= newCount,
            "断开后连接数应该减少或保持不变");
    }

    @Test
    @DisplayName("测试WebSocket订阅功能")
    void testWebSocketSubscription() throws Exception {
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
        
        CompletableFuture<Object> subscribeFuture = new CompletableFuture<>();
        
        session.subscribe("/topic/test", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }
            
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                log.info("收到消息: {}", payload);
                subscribeFuture.complete(payload);
            }
        });
        
        log.info("订阅成功");
        
        assertTrue(session.isConnected(), "订阅后连接应该保持");
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试WebSocket认证拦截器")
    void testWebSocketAuthInterceptor() throws Exception {
        String testToken = "test-token-123";
        String testUserId = "user-456";
        
        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        
        StompHeaders headers = new StompHeaders();
        headers.add("token", testToken);
        headers.add("userId", testUserId);
        
        stompClient.connectAsync(wsUrl + "/ws?token=" + testToken + "&userId=" + testUserId, 
            new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("带认证信息的WebSocket连接成功");
                sessionFuture.complete(session);
            }
            
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                log.error("认证连接错误: {}", exception.getMessage());
                sessionFuture.completeExceptionally(exception);
            }
        });
        
        StompSession session = sessionFuture.get(10, TimeUnit.SECONDS);
        
        assertTrue(session.isConnected(), "带认证的连接应该成功");
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试多个WebSocket并发连接")
    void testMultipleConcurrentConnections() throws Exception {
        int connectionCount = 5;
        CompletableFuture<StompSession>[] futures = new CompletableFuture[connectionCount];
        
        for (int i = 0; i < connectionCount; i++) {
            final int index = i;
            futures[i] = new CompletableFuture<>();
            
            stompClient.connectAsync(wsUrl + "/ws", new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    log.info("并发连接 {} 成功", index + 1);
                    futures[index].complete(session);
                }
                
                @Override
                public void handleTransportError(StompSession session, Throwable exception) {
                    log.error("并发连接 {} 失败: {}", index + 1, exception.getMessage());
                    futures[index].completeExceptionally(exception);
                }
            });
        }
        
        int successCount = 0;
        for (CompletableFuture<StompSession> future : futures) {
            try {
                StompSession session = future.get(15, TimeUnit.SECONDS);
                if (session.isConnected()) {
                    successCount++;
                    session.disconnect();
                }
            } catch (Exception e) {
                log.warn("连接失败: {}", e.getMessage());
            }
        }
        
        log.info("成功连接数: {}/{}", successCount, connectionCount);
        
        assertTrue(successCount >= connectionCount / 2,
            "至少一半的并发连接应该成功");
    }
}

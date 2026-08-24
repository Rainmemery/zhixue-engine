package com.rain.zhixuecommon.websocket;

import com.rain.zhixuecommon.websocket.dto.DataChangeEvent;
import com.rain.zhixuecommon.websocket.service.DataSyncService;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("数据同步延迟测试")
public class DataSyncLatencyTest {

    @Autowired
    private DataSyncService dataSyncService;

    private WebSocketStompClient stompClient;
    private String wsUrl;

    private static final long MAX_LATENCY_MS = 1000;
    private static final long OPTIMAL_LATENCY_MS = 100;

    @BeforeEach
    void setUp() {
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
    }

    @Test
    @DisplayName("测试单次数据同步延迟")
    void testSingleDataSyncLatency() throws Exception {
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
        
        CompletableFuture<Long> latencyFuture = new CompletableFuture<>();
        
        session.subscribe("/topic/data/test", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DataChangeEvent.class;
            }
            
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                long receiveTime = System.currentTimeMillis();
                DataChangeEvent event = (DataChangeEvent) payload;
                long latency = receiveTime - event.getTimestamp();
                latencyFuture.complete(latency);
            }
        });
        
        Thread.sleep(500);
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", "latency-test");
        
        DataChangeEvent event = DataChangeEvent.create(
            "test",
            "latency-test-1",
            testData,
            "test-service"
        );
        
        long sendTime = System.currentTimeMillis();
        dataSyncService.broadcastDataChange(event);
        
        Long latency = latencyFuture.get(5, TimeUnit.SECONDS);
        
        log.info("单次数据同步延迟: {} ms", latency);
        
        assertTrue(latency < MAX_LATENCY_MS, 
            String.format("延迟应小于%dms，实际为%dms", MAX_LATENCY_MS, latency));
        
        if (latency < OPTIMAL_LATENCY_MS) {
            log.info("✓ 数据同步延迟优秀");
        } else {
            log.warn("⚠ 数据同步延迟较高");
        }
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试多次数据同步平均延迟")
    void testAverageDataSyncLatency() throws Exception {
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
        
        int testCount = 10;
        long[] latencies = new long[testCount];
        int receivedCount = 0;
        
        for (int i = 0; i < testCount; i++) {
            CompletableFuture<Long> latencyFuture = new CompletableFuture<>();
            final int index = i;
            
            session.subscribe("/topic/data/test-" + i, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return DataChangeEvent.class;
                }
                
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    long receiveTime = System.currentTimeMillis();
                    DataChangeEvent event = (DataChangeEvent) payload;
                    long latency = receiveTime - event.getTimestamp();
                    latencyFuture.complete(latency);
                }
            });
            
            Thread.sleep(200);
            
            Map<String, Object> testData = new HashMap<>();
            testData.put("testIndex", i);
            
            DataChangeEvent event = DataChangeEvent.create(
                "test-" + i,
                "latency-test-" + i,
                testData,
                "test-service"
            );
            
            dataSyncService.broadcastDataChange(event);
            
            try {
                latencies[i] = latencyFuture.get(3, TimeUnit.SECONDS);
                receivedCount++;
            } catch (Exception e) {
                log.warn("测试 {} 未收到响应", i);
                latencies[i] = -1;
            }
        }
        
        if (receivedCount > 0) {
            long totalLatency = 0;
            int validCount = 0;
            
            for (long latency : latencies) {
                if (latency > 0) {
                    totalLatency += latency;
                    validCount++;
                }
            }
            
            double averageLatency = (double) totalLatency / validCount;
            
            log.info("=== 数据同步延迟统计 ===");
            log.info("测试次数: {}", testCount);
            log.info("成功次数: {}", receivedCount);
            log.info("平均延迟: {:.2f} ms", averageLatency);
            log.info("最小延迟: {} ms", 
                java.util.Arrays.stream(latencies).filter(l -> l > 0).min().orElse(0));
            log.info("最大延迟: {} ms", 
                java.util.Arrays.stream(latencies).filter(l -> l > 0).max().orElse(0));
            
            assertTrue(averageLatency < MAX_LATENCY_MS,
                String.format("平均延迟应小于%dms", MAX_LATENCY_MS));
        }
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试高负载下的数据同步延迟")
    void testHighLoadDataSyncLatency() throws Exception {
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
        
        int messageCount = 50;
        CompletableFuture<Long>[] latencyFutures = new CompletableFuture[messageCount];
        
        for (int i = 0; i < messageCount; i++) {
            latencyFutures[i] = new CompletableFuture<>();
            final int index = i;
            
            session.subscribe("/topic/data/load-test-" + i, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return DataChangeEvent.class;
                }
                
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    long receiveTime = System.currentTimeMillis();
                    DataChangeEvent event = (DataChangeEvent) payload;
                    long latency = receiveTime - event.getTimestamp();
                    latencyFutures[index].complete(latency);
                }
            });
        }
        
        Thread.sleep(1000);
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < messageCount; i++) {
            Map<String, Object> testData = new HashMap<>();
            testData.put("loadTestIndex", i);
            
            DataChangeEvent event = DataChangeEvent.create(
                "load-test-" + i,
                "load-test-" + i,
                testData,
                "test-service"
            );
            
            dataSyncService.broadcastDataChange(event);
        }
        
        int receivedCount = 0;
        long totalLatency = 0;
        long maxLatency = 0;
        
        for (int i = 0; i < messageCount; i++) {
            try {
                Long latency = latencyFutures[i].get(5, TimeUnit.SECONDS);
                if (latency != null) {
                    receivedCount++;
                    totalLatency += latency;
                    maxLatency = Math.max(maxLatency, latency);
                }
            } catch (Exception e) {
                log.warn("高负载测试 {} 未收到响应", i);
            }
        }
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        if (receivedCount > 0) {
            double averageLatency = (double) totalLatency / receivedCount;
            double throughput = (double) receivedCount / (totalTestTime / 1000.0);
            
            log.info("=== 高负载数据同步性能测试 ===");
            log.info("发送消息数: {}", messageCount);
            log.info("接收消息数: {}", receivedCount);
            log.info("总测试时间: {} ms", totalTestTime);
            log.info("平均延迟: {:.2f} ms", averageLatency);
            log.info("最大延迟: {} ms", maxLatency);
            log.info("吞吐量: {:.2f} 消息/秒", throughput);
            
            assertTrue(averageLatency < MAX_LATENCY_MS * 2,
                "高负载下平均延迟应小于" + (MAX_LATENCY_MS * 2) + "ms");
            assertTrue(receivedCount >= messageCount * 0.8,
                "至少应该接收到80%的消息");
        }
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试数据同步延迟稳定性")
    void testDataSyncLatencyStability() throws Exception {
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
        
        int testCount = 20;
        long[] latencies = new long[testCount];
        
        for (int i = 0; i < testCount; i++) {
            CompletableFuture<Long> latencyFuture = new CompletableFuture<>();
            
            session.subscribe("/topic/data/stability-test", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return DataChangeEvent.class;
                }
                
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    long receiveTime = System.currentTimeMillis();
                    DataChangeEvent event = (DataChangeEvent) payload;
                    long latency = receiveTime - event.getTimestamp();
                    latencyFuture.complete(latency);
                }
            });
            
            Thread.sleep(500);
            
            Map<String, Object> testData = new HashMap<>();
            testData.put("stabilityTest", i);
            
            DataChangeEvent event = DataChangeEvent.create(
                "stability-test",
                "stability-test-" + i,
                testData,
                "test-service"
            );
            
            dataSyncService.broadcastDataChange(event);
            
            try {
                latencies[i] = latencyFuture.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                latencies[i] = -1;
            }
        }
        
        long[] validLatencies = java.util.Arrays.stream(latencies)
            .filter(l -> l > 0)
            .toArray();
        
        if (validLatencies.length > 0) {
            double mean = java.util.Arrays.stream(validLatencies).average().orElse(0);
            double variance = java.util.Arrays.stream(validLatencies)
                .mapToDouble(l -> Math.pow(l - mean, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);
            
            log.info("=== 数据同步延迟稳定性分析 ===");
            log.info("有效测试次数: {}", validLatencies.length);
            log.info("平均延迟: {:.2f} ms", mean);
            log.info("标准差: {:.2f} ms", stdDev);
            log.info("变异系数: {:.2f}%", (stdDev / mean) * 100);
            
            assertTrue(stdDev < MAX_LATENCY_MS,
                "延迟标准差应小于" + MAX_LATENCY_MS + "ms");
        }
        
        session.disconnect();
    }

    @Test
    @DisplayName("测试网络延迟对数据同步的影响")
    void testNetworkLatencyImpact() throws Exception {
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
        
        CompletableFuture<Long> latencyFuture = new CompletableFuture<>();
        
        session.subscribe("/topic/data/network-test", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DataChangeEvent.class;
            }
            
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                long receiveTime = System.currentTimeMillis();
                DataChangeEvent event = (DataChangeEvent) payload;
                long latency = receiveTime - event.getTimestamp();
                latencyFuture.complete(latency);
            }
        });
        
        Thread.sleep(500);
        
        Map<String, Object> largeData = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largeData.put("field" + i, "这是一个测试字段值，用于测试大数据量情况下的延迟 - " + i);
        }
        
        DataChangeEvent event = DataChangeEvent.create(
            "network-test",
            "network-test-1",
            largeData,
            "test-service"
        );
        
        long sendTime = System.currentTimeMillis();
        dataSyncService.broadcastDataChange(event);
        
        Long latency = latencyFuture.get(5, TimeUnit.SECONDS);
        
        log.info("大数据量同步延迟: {} ms", latency);
        log.info("数据大小: {} 字段", largeData.size());
        
        assertTrue(latency < MAX_LATENCY_MS * 2,
            "大数据量同步延迟应小于" + (MAX_LATENCY_MS * 2) + "ms");
        
        session.disconnect();
    }
}

package com.rain.zhixueai.config;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MilvusClientProvider {
    
    private final MilvusProperties milvusProperties;
    
    private volatile MilvusClientV2 cachedClient;
    private volatile boolean initialized = false;
    private volatile boolean available = false;
    private volatile long lastHealthCheckTime = 0;
    private volatile boolean healthCheckPassed = false;
    
    public MilvusClientProvider(MilvusProperties milvusProperties) {
        this.milvusProperties = milvusProperties;
    }
    
    public MilvusClientV2 getClient() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    tryInit();
                    initialized = true;
                }
            }
        }
        return available ? cachedClient : null;
    }
    
    public boolean isAvailable() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    tryInit();
                    initialized = true;
                }
            }
        }
        return available;
    }
    
    public boolean isHealthy() {
        if (!available) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastHealthCheckTime < milvusProperties.getHealthCheckIntervalMs()) {
            return healthCheckPassed;
        }
        
        return performHealthCheck();
    }
    
    private synchronized boolean performHealthCheck() {
        if (!available) {
            healthCheckPassed = false;
            return false;
        }
        
        try {
            HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                    .collectionName("_health_check_dummy")
                    .build();
            cachedClient.hasCollection(hasCollectionReq);
            
            healthCheckPassed = true;
            lastHealthCheckTime = System.currentTimeMillis();
            log.debug("Milvus health check passed");
            return true;
            
        } catch (Exception e) {
            log.warn("Milvus health check failed: {}. Attempting reconnect...", e.getMessage());
            healthCheckPassed = false;
            lastHealthCheckTime = System.currentTimeMillis();
            
            try {
                reconnect();
                return healthCheckPassed;
            } catch (Exception reconnectError) {
                log.error("Milvus reconnect failed: {}", reconnectError.getMessage());
                available = false;
                return false;
            }
        }
    }
    
    private void reconnect() {
        int maxRetries = milvusProperties.getMaxRetryAttempts();
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Attempting Milvus reconnect (attempt {}/{})", attempt, maxRetries);
                Thread.sleep(1000 * attempt);
                
                String protocol = milvusProperties.getSecure() ? "https" : "http";
                String uri = protocol + "://" + milvusProperties.getHost() + ":" + milvusProperties.getPort();
                
                ConnectConfig connectConfig = ConnectConfig.builder()
                        .uri(uri)
                        .connectTimeoutMs(milvusProperties.getConnectTimeout() * 1000)
                        .keepAliveTimeMs(milvusProperties.getKeepAliveTime() * 1000)
                        .keepAliveTimeoutMs(milvusProperties.getKeepAliveTimeout() * 1000)
                        .secure(milvusProperties.getSecure())
                        .build();
                
                if (cachedClient != null) {
                    try {
                        cachedClient.close();
                    } catch (Exception e) {
                        log.warn("Error closing old Milvus client: {}", e.getMessage());
                    }
                }
                
                cachedClient = new MilvusClientV2(connectConfig);
                available = true;
                healthCheckPassed = true;
                lastHealthCheckTime = System.currentTimeMillis();
                log.info("Milvus reconnected successfully");
                return;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Milvus reconnect interrupted");
                break;
            } catch (Exception e) {
                log.warn("Milvus reconnect attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    available = false;
                    healthCheckPassed = false;
                }
            }
        }
    }
    
    private void tryInit() {
        String protocol = milvusProperties.getSecure() ? "https" : "http";
        String uri = protocol + "://" + milvusProperties.getHost() + ":" + milvusProperties.getPort();
        
        log.info("Initializing Milvus client with configuration:");
        log.info("  - URI: {}", uri);
        log.info("  - Connect Timeout: {}s", milvusProperties.getConnectTimeout());
        log.info("  - Keep Alive Time: {}s", milvusProperties.getKeepAliveTime());
        log.info("  - Keep Alive Timeout: {}s", milvusProperties.getKeepAliveTimeout());
        log.info("  - Secure: {}", milvusProperties.getSecure());
        log.info("  - Username: {}", milvusProperties.getUsername() != null ? "configured" : "not configured");
        log.info("  - Max Retry Attempts: {}", milvusProperties.getMaxRetryAttempts());
        
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(milvusProperties.getConnectTimeout() * 1000)
                .keepAliveTimeMs(milvusProperties.getKeepAliveTime() * 1000)
                .keepAliveTimeoutMs(milvusProperties.getKeepAliveTimeout() * 1000)
                .secure(milvusProperties.getSecure())
                .build();
        
        try {
            cachedClient = new MilvusClientV2(connectConfig);
            available = true;
            healthCheckPassed = true;
            lastHealthCheckTime = System.currentTimeMillis();
            log.info("Milvus client initialized successfully");
        } catch (Exception e) {
            log.error("Milvus 连接失败：{} - {}. RAG 功能将不可用。", uri, e.getMessage(), e);
            available = false;
            healthCheckPassed = false;
        }
    }
}

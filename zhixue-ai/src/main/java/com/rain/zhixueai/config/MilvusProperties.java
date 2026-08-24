package com.rain.zhixueai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    
    private String host = "localhost";
    
    private Integer port = 19530;
    
    private String collectionPrefix = "rag_vectors";
    
    private Integer connectTimeout = 10;
    
    private Integer keepAliveTime = 55;
    
    private Integer keepAliveTimeout = 20;
    
    private Boolean secure = false;
    
    private String username;
    
    private String password;
    
    private Integer maxRetryAttempts = 3;
    
    private Long healthCheckIntervalMs = 60000L;
}

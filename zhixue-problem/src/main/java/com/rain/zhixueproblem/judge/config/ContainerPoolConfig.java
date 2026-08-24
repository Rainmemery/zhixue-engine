package com.rain.zhixueproblem.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "judge.container-pool")
@Data
public class ContainerPoolConfig {
    private PoolConfig java;
    private PoolConfig cpp;
    private PoolConfig c;

    @Data
    public static class PoolConfig {
        private int coreSize;
        private int maxSize;
        private int maxIdleTimeSeconds;
        private int borrowTimeoutSeconds;
        private int maxReuseCount;
        private String image;
    }
}

package com.rain.zhixueproblem.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "judge.health-check")
@Data
public class HealthCheckConfig {
    private int intervalSeconds;
    private int maxActiveTimeoutSeconds;
}

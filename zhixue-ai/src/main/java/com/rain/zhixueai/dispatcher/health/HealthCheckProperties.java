package com.rain.zhixueai.dispatcher.health;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dispatcher.health-check")
public class HealthCheckProperties {

    private int intervalSeconds = 30;

    private int timeoutSeconds = 30;

    private int failureThreshold = 3;

    private int recoveryThreshold = 2;

    private String healthEndpoint = "/v1/models";

    private int maxRetries = 3;

    private long retryDelayMillis = 1000;
}

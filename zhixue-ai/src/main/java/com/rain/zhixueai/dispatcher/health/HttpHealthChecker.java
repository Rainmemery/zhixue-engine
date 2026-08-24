package com.rain.zhixueai.dispatcher.health;

import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dispatcher.entity.ModelGroup;
import com.rain.zhixueai.dispatcher.service.ModelInstanceService;
import com.rain.zhixueai.dispatcher.service.ModelGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class HttpHealthChecker implements InstanceHealthChecker {

    private final ModelInstanceService instanceService;
    private final ModelGroupService modelGroupService;
    private final HealthCheckProperties healthCheckProperties;
    private final WebClient.Builder webClientBuilder;

    @Override
    public HealthCheckResult checkHealth(ModelInstance instance) {
        if (instance == null) {
            return HealthCheckResult.unhealthy("实例不存在");
        }

        if (instance.getApiEndpoint() == null || instance.getApiEndpoint().isBlank()) {
            return HealthCheckResult.unhealthy("实例API端点未配置");
        }

        String healthEndpoint = determineHealthEndpoint(instance);
        String healthUrl = buildHealthUrl(instance, healthEndpoint);

        log.debug("开始健康检查: instanceId={}, instanceName={}, url={}, endpoint={}",
                instance.getId(), instance.getName(), healthUrl, healthEndpoint);

        long startTime = System.currentTimeMillis();

        try {
            WebClient webClient = webClientBuilder
                    .baseUrl(healthUrl)
                    .build();

            String responseBody = webClient.get()
                    .uri(uri -> uri.path(healthEndpoint).build())
                    .headers(headers -> {
                        if (instance.getApiKey() != null && !instance.getApiKey().isBlank()) {
                            headers.setBearerAuth(instance.getApiKey());
                        }
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(healthCheckProperties.getTimeoutSeconds()))
                    .block();

            long responseTime = System.currentTimeMillis() - startTime;

            log.debug("健康检查成功: instanceId={}, instanceName={}, responseTime={}ms",
                    instance.getId(), instance.getName(), responseTime);
            return HealthCheckResult.healthy(responseTime);

        } catch (WebClientResponseException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            String errorMessage = String.format("HTTP错误: %d %s", e.getStatusCode().value(), e.getStatusText());
            log.warn("健康检查失败: instanceId={}, instanceName={}, error={}",
                    instance.getId(), instance.getName(), errorMessage);
            return HealthCheckResult.unhealthy(errorMessage, responseTime);

        } catch (IllegalStateException e) {
            if (e.getCause() instanceof TimeoutException) {
                long responseTime = System.currentTimeMillis() - startTime;
                String errorMessage = "请求超时";
                log.warn("健康检查超时: instanceId={}, instanceName={}, timeout={}s",
                        instance.getId(), instance.getName(), healthCheckProperties.getTimeoutSeconds());
                return HealthCheckResult.unhealthy(errorMessage, responseTime);
            }
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("健康检查异常: instanceId={}, instanceName={}", instance.getId(), instance.getName(), e);
            return HealthCheckResult.unhealthy(e.getMessage(), responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("健康检查异常: instanceId={}, instanceName={}", instance.getId(), instance.getName(), e);
            return HealthCheckResult.unhealthy(e.getMessage(), responseTime);
        }
    }

    @Override
    public HealthCheckResult checkHealth(Long instanceId) {
        if (instanceId == null) {
            return HealthCheckResult.unhealthy("实例ID不能为空");
        }

        ModelInstance instance = instanceService.getInstanceById(instanceId);
        if (instance == null) {
            return HealthCheckResult.unhealthy("实例不存在: " + instanceId);
        }

        return checkHealth(instance);
    }

    private String determineHealthEndpoint(ModelInstance instance) {
        if (instance.getHealthCheckEndpoint() != null && !instance.getHealthCheckEndpoint().isBlank()) {
            return instance.getHealthCheckEndpoint();
        }

        ModelGroup group = null;
        if (instance.getGroupId() != null) {
            group = modelGroupService.getGroupById(instance.getGroupId());
        }

        String modelType = group != null ? group.getModelTypeOrDefault() : "CUSTOM";

        return switch (modelType.toUpperCase()) {
            case "OLLAMA" -> "/api/tags";
            case "OPENAI", "CUSTOM" -> "/v1/models";
            default -> "/health";
        };
    }

    private String buildHealthUrl(ModelInstance instance, String healthEndpoint) {
        String endpoint = instance.getApiEndpoint();

        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        return endpoint;
    }
}

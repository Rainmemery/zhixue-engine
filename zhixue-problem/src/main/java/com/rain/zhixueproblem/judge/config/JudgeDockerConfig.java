package com.rain.zhixueproblem.judge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "judge.docker.enabled", havingValue = "true", matchIfMissing = true)
public class JudgeDockerConfig {

    @Value("${judge.docker.host:unix:///var/run/docker.sock}")
    private String configuredDockerHost;

    @Bean
    public DockerClient dockerClient() {
        try {
            String dockerHost = resolveDockerHost();
            log.info("Initializing Docker client with host: {}", dockerHost);

            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .build();

            DockerClient dockerClient = DockerClientImpl.getInstance(config)
                    .withDockerCmdExecFactory(new NettyDockerCmdExecFactory()
                            .withConnectTimeout(30000));
            dockerClient.pingCmd().exec();
            log.info("Docker client connected successfully to {}", config.getDockerHost());
            return dockerClient;
        } catch (NoClassDefFoundError e) {
            log.warn("Docker client initialization failed (missing dependency: {}): {}. Docker-based judging will be unavailable, LocalCodeRunner will be used as fallback.", e.getMessage(), e.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("Docker client initialization failed: {}. Docker-based judging will be unavailable, LocalCodeRunner will be used as fallback.", e.getMessage());
            return null;
        }
    }

    private String resolveDockerHost() {
        String envHost = System.getenv("DOCKER_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            log.info("Using DOCKER_HOST from environment: {}", envHost);
            return envHost;
        }
        log.info("Using configured docker host: {}", configuredDockerHost);
        return configuredDockerHost;
    }
}

package com.rain.zhixueproblem.judge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({JudgeMachineConfig.class, ContainerPoolConfig.class, HealthCheckConfig.class})
public class JudgeConfig {
}

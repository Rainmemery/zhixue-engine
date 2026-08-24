package com.rain.zhixueproblem.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "judge.machine")
@Data
public class JudgeMachineConfig {
    private int cpuCores;
    private int memoryMb;
    private int systemReservedMemoryMb;
    private int containerMemoryMb;
    private int containerCpus;
    private int maxConcurrency;
}

package com.rain.zhixueai.config;

import com.rain.zhixueai.resource.PriorityToolExecutor;
import com.rain.zhixueai.resource.ResourceIsolationManager;
import com.rain.zhixueai.resource.ToolConcurrencyController;
import com.rain.zhixueai.timeout.RetryExecutor;
import com.rain.zhixueai.timeout.TimeoutEventLogger;
import com.rain.zhixueai.timeout.TimeoutManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Autowired
    private ResourceConfigProperties resourceConfig;
    
    @Autowired
    private TimeoutConfigProperties timeoutConfig;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutConfig.getLlm().getConnectionTimeoutSeconds() * 1000);
        factory.setReadTimeout(timeoutConfig.getLlm().getFirstRoundSeconds() * 1000);
        return new RestTemplate(factory);
    }
    
    @Bean(name = "documentProcessingExecutor")
    public Executor documentProcessingExecutor() {
        int corePoolSize = 2;
        int maxPoolSize = 4;
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("doc-process-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        
        executor.initialize();
        
        log.info("Document processing executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                corePoolSize, maxPoolSize, 10);
        
        return executor;
    }

    @Bean(name = "problemGenerationExecutor")
    public Executor problemGenerationExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;
        int queueCapacity = 50;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("problem-gen-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("Problem generation executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    @Bean(name = "llmCallExecutor")
    public Executor llmCallExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        int maxPoolSize = corePoolSize * 2;
        int queueCapacity = 100;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("llm-call-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        executor.initialize();

        log.info("LLM call executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    @Bean(name = "toolExecutionExecutor")
    public Executor toolExecutionExecutor() {
        ResourceConfigProperties.ExecutorConfig execConfig = resourceConfig.getExecutor();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(execConfig.getCorePoolSize());
        executor.setMaxPoolSize(execConfig.getMaxPoolSize());
        executor.setQueueCapacity(execConfig.getQueueCapacity());
        executor.setKeepAliveSeconds(execConfig.getKeepAliveSeconds());
        executor.setThreadNamePrefix(execConfig.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setAllowCoreThreadTimeOut(execConfig.getAllowCoreThreadTimeOut());

        executor.initialize();

        log.info("Tool execution executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                execConfig.getCorePoolSize(), execConfig.getMaxPoolSize(), execConfig.getQueueCapacity());

        return executor;
    }
    
    @Bean
    public PriorityToolExecutor priorityToolExecutor() {
        return new PriorityToolExecutor(resourceConfig);
    }
    
    @Bean
    public ToolConcurrencyController toolConcurrencyController() {
        return new ToolConcurrencyController(resourceConfig);
    }
    
    @Bean
    public ResourceIsolationManager resourceIsolationManager() {
        return new ResourceIsolationManager(resourceConfig);
    }
    
    @Bean
    public TimeoutEventLogger timeoutEventLogger() {
        return new TimeoutEventLogger();
    }
    
    @Bean
    public TimeoutManager timeoutManager() {
        return new TimeoutManager(timeoutConfig, timeoutEventLogger());
    }
    
    @Bean
    public RetryExecutor retryExecutor() {
        return new RetryExecutor(timeoutConfig);
    }
}

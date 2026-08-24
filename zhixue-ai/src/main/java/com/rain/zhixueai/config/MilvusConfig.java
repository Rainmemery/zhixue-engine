package com.rain.zhixueai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Slf4j
@Configuration
public class MilvusConfig {
    
    @Autowired
    private MilvusProperties milvusProperties;
    
    @Lazy
    @Bean
    public MilvusClientProvider milvusClientProvider() {
        return new MilvusClientProvider(milvusProperties);
    }
}

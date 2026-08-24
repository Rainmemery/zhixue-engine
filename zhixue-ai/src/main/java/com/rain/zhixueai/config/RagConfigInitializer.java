package com.rain.zhixueai.config;

import com.rain.zhixueai.entity.rag.RagConfig;
import com.rain.zhixueai.mapper.rag.RagConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RagConfigInitializer {
    
    @Autowired
    private RagConfigMapper configMapper;
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeRagConfigs() {
        log.info("Initializing RAG module configurations...");
        
        initializeModuleConfig("intelligent_question", "智能提问模块", true);
        initializeModuleConfig("ai_chat", "AI聊天模块", false);
        initializeModuleConfig("code_explain", "代码解释模块", false);
        initializeModuleConfig("code_review", "代码评审模块", false);
        
        initializeGlobalConfig();
        
        log.info("RAG module configurations initialized successfully");
    }
    
    private void initializeModuleConfig(String moduleCode, String description, boolean enabled) {
        RagConfig existing = configMapper.findByKey("MODULE", moduleCode);
        if (existing == null) {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", enabled);
            config.put("knowledgeBaseIds", java.util.Collections.emptyList());
            
            RagConfig newConfig = new RagConfig();
            newConfig.setConfigType("MODULE");
            newConfig.setConfigKey(moduleCode);
            newConfig.setDescription(description);
            newConfig.setConfigValue(com.alibaba.fastjson2.JSON.toJSONString(config));
            
            configMapper.insert(newConfig);
            log.info("Created default config for module: {}", moduleCode);
        } else {
            log.debug("Module config already exists: {}", moduleCode);
        }
    }
    
    private void initializeGlobalConfig() {
        RagConfig existing = configMapper.findByKey("GLOBAL", "rag_enabled");
        if (existing == null) {
            RagConfig newConfig = new RagConfig();
            newConfig.setConfigType("GLOBAL");
            newConfig.setConfigKey("rag_enabled");
            newConfig.setConfigValue("true");
            newConfig.setDescription("RAG功能全局开关");
            
            configMapper.insert(newConfig);
            log.info("Created global RAG enabled config");
        }
    }
}

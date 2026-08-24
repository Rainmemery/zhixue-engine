package com.rain.zhixueai.service.rag.impl;

import com.alibaba.fastjson2.JSON;
import com.rain.zhixueai.entity.rag.RagConfig;
import com.rain.zhixueai.mapper.rag.RagConfigMapper;
import com.rain.zhixueai.service.rag.RagConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagConfigServiceImpl implements RagConfigService {
    
    @Autowired
    private RagConfigMapper configMapper;
    
    @Override
    public Map<String, Object> getGlobalConfig() {
        List<RagConfig> configs = configMapper.findByType("GLOBAL");
        
        Map<String, Object> result = new HashMap<>();
        for (RagConfig config : configs) {
            try {
                Object value = parseConfigValue(config.getConfigValue());
                result.put(config.getConfigKey(), value);
            } catch (Exception e) {
                log.warn("Failed to parse config value: {} = {}", config.getConfigKey(), config.getConfigValue());
                result.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public void updateGlobalConfig(Map<String, Object> config) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String valueStr = value instanceof String ? (String) value : JSON.toJSONString(value);
            
            RagConfig existing = configMapper.findByKey("GLOBAL", key);
            if (existing != null) {
                existing.setConfigValue(valueStr);
                configMapper.update(existing);
            } else {
                RagConfig newConfig = new RagConfig();
                newConfig.setConfigType("GLOBAL");
                newConfig.setConfigKey(key);
                newConfig.setConfigValue(valueStr);
                configMapper.insert(newConfig);
            }
        }
        
        log.info("Updated global RAG config");
    }
    
    @Override
    @Transactional
    public void setGlobalEnabled(boolean enabled) {
        RagConfig config = configMapper.findByKey("GLOBAL", "rag_enabled");
        if (config != null) {
            config.setConfigValue(String.valueOf(enabled));
            configMapper.update(config);
        } else {
            RagConfig newConfig = new RagConfig();
            newConfig.setConfigType("GLOBAL");
            newConfig.setConfigKey("rag_enabled");
            newConfig.setConfigValue(String.valueOf(enabled));
            newConfig.setDescription("RAG功能全局开关");
            configMapper.insert(newConfig);
        }
        
        log.info("Set RAG enabled: {}", enabled);
    }
    
    @Override
    public boolean isRagEnabled() {
        String value = getConfigValue("GLOBAL", "rag_enabled");
        return "true".equalsIgnoreCase(value);
    }
    
    @Override
    public Map<String, Object> getModuleConfig(String moduleCode) {
        RagConfig config = configMapper.findByKey("MODULE", moduleCode);
        
        if (config == null) {
            log.info("Module config not found for: {}, returning default", moduleCode);
            Map<String, Object> defaultConfig = new HashMap<>();
            defaultConfig.put("enabled", false);
            defaultConfig.put("knowledgeBaseIds", List.of());
            return defaultConfig;
        }
        
        try {
            Map<String, Object> result = JSON.parseObject(config.getConfigValue(), Map.class);
            log.info("Module config for {}: {}", moduleCode, result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse module config: {}", moduleCode);
            return new HashMap<>();
        }
    }
    
    @Override
    @Transactional
    public void updateModuleConfig(String moduleCode, Map<String, Object> config) {
        String valueStr = JSON.toJSONString(config);
        
        RagConfig existing = configMapper.findByKey("MODULE", moduleCode);
        if (existing != null) {
            existing.setConfigValue(valueStr);
            configMapper.update(existing);
        } else {
            RagConfig newConfig = new RagConfig();
            newConfig.setConfigType("MODULE");
            newConfig.setConfigKey(moduleCode);
            newConfig.setConfigValue(valueStr);
            configMapper.insert(newConfig);
        }
        
        log.info("Updated module RAG config: {}", moduleCode);
    }
    
    @Override
    public List<Map<String, Object>> getModuleConfigs() {
        List<RagConfig> configs = configMapper.findByType("MODULE");
        
        return configs.stream().map(config -> {
            Map<String, Object> item = new HashMap<>();
            item.put("moduleCode", config.getConfigKey());
            item.put("description", config.getDescription());
            try {
                item.put("config", JSON.parseObject(config.getConfigValue(), Map.class));
            } catch (Exception e) {
                item.put("config", new HashMap<>());
            }
            return item;
        }).collect(Collectors.toList());
    }
    
    @Override
    public boolean isModuleRagEnabled(String moduleCode) {
        if (!isRagEnabled()) {
            return false;
        }
        
        Map<String, Object> moduleConfig = getModuleConfig(moduleCode);
        Object enabled = moduleConfig.get("enabled");
        return Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }
    
    @Override
    public List<Long> getModuleKnowledgeBaseIds(String moduleCode) {
        Map<String, Object> moduleConfig = getModuleConfig(moduleCode);
        Object ids = moduleConfig.get("knowledgeBaseIds");
        
        if (ids instanceof List) {
            return ((List<?>) ids).stream()
                    .map(id -> {
                        if (id instanceof Number) {
                            return ((Number) id).longValue();
                        }
                        return Long.parseLong(String.valueOf(id));
                    })
                    .collect(Collectors.toList());
        }
        
        return List.of();
    }
    
    @Override
    public String getConfigValue(String configType, String configKey) {
        RagConfig config = configMapper.findByKey(configType, configKey);
        return config != null ? config.getConfigValue() : null;
    }
    
    @Override
    @Transactional
    public void setConfigValue(String configType, String configKey, String value, String description) {
        RagConfig config = new RagConfig();
        config.setConfigType(configType);
        config.setConfigKey(configKey);
        config.setConfigValue(value);
        config.setDescription(description);
        configMapper.upsert(config);
    }
    
    private Object parseConfigValue(String value) {
        if (value == null) {
            return null;
        }
        
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                return JSON.parse(value);
            } catch (Exception e) {
                return value;
            }
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }
        
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            // Not a float
        }
        
        return value;
    }
}

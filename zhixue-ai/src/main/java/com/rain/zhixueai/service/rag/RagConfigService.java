package com.rain.zhixueai.service.rag;

import java.util.List;
import java.util.Map;

public interface RagConfigService {
    
    Map<String, Object> getGlobalConfig();
    
    void updateGlobalConfig(Map<String, Object> config);
    
    void setGlobalEnabled(boolean enabled);
    
    boolean isRagEnabled();
    
    Map<String, Object> getModuleConfig(String moduleCode);
    
    void updateModuleConfig(String moduleCode, Map<String, Object> config);
    
    List<Map<String, Object>> getModuleConfigs();
    
    boolean isModuleRagEnabled(String moduleCode);
    
    List<Long> getModuleKnowledgeBaseIds(String moduleCode);
    
    String getConfigValue(String configType, String configKey);
    
    void setConfigValue(String configType, String configKey, String value, String description);
}

package com.rain.zhixueai.agent.tool;

import com.rain.zhixueai.agent.dto.ToolCallResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> toolMap = new ConcurrentHashMap<>();

    @Autowired
    private List<ToolDefinition> toolDefinitions;

    @PostConstruct
    public void init() {
        if (toolDefinitions != null) {
            for (ToolDefinition tool : toolDefinitions) {
                register(tool);
            }
        }
        log.info("ToolRegistry initialized with {} tools: {}", toolMap.size(), toolMap.keySet());
    }

    public void register(ToolDefinition tool) {
        if (tool != null && tool.getName() != null) {
            toolMap.put(tool.getName(), tool);
            log.info("Registered tool: {}", tool.getName());
        }
    }

    public ToolDefinition getTool(String name) {
        return toolMap.get(name);
    }

    public List<ToolDefinition> getAllTools() {
        return new ArrayList<>(toolMap.values());
    }

    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition tool : toolMap.values()) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append(tool.getDescription()).append("\n");
            sb.append(tool.getParameterSchema()).append("\n\n");
        }
        return sb.toString();
    }

    public boolean hasTool(String name) {
        return toolMap.containsKey(name);
    }

    public Set<String> getAvailableToolNames() {
        return new HashSet<>(toolMap.keySet());
    }
}

package com.rain.zhixueai.agent.tool;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        ReflectionTestUtils.setField(toolRegistry, "toolDefinitions", Collections.emptyList());
    }

    @Test
    void init_withNoTools_initializesEmpty() {
        toolRegistry.init();
        assertTrue(toolRegistry.getAllTools().isEmpty());
    }

    @Test
    void register_validTool_registersSuccessfully() {
        ToolDefinition tool = createMockTool("test_tool", "A test tool");
        toolRegistry.register(tool);
        assertTrue(toolRegistry.hasTool("test_tool"));
        assertEquals(tool, toolRegistry.getTool("test_tool"));
    }

    @Test
    void register_nullTool_doesNotRegister() {
        toolRegistry.register(null);
        assertTrue(toolRegistry.getAllTools().isEmpty());
    }

    @Test
    void register_toolWithNullName_doesNotRegister() {
        ToolDefinition tool = createMockTool(null, "desc");
        toolRegistry.register(tool);
        assertTrue(toolRegistry.getAllTools().isEmpty());
    }

    @Test
    void getTool_nonExistentTool_returnsNull() {
        assertNull(toolRegistry.getTool("non_existent"));
    }

    @Test
    void getAllTools_returnsAllRegisteredTools() {
        toolRegistry.register(createMockTool("tool1", "desc1"));
        toolRegistry.register(createMockTool("tool2", "desc2"));
        assertEquals(2, toolRegistry.getAllTools().size());
    }

    @Test
    void getToolDescriptions_returnsFormattedDescriptions() {
        toolRegistry.register(createMockTool("user_profile", "查询用户画像"));
        String descriptions = toolRegistry.getToolDescriptions();
        assertTrue(descriptions.contains("user_profile"));
        assertTrue(descriptions.contains("查询用户画像"));
    }

    @Test
    void init_autoRegistersProvidedTools() {
        ToolDefinition tool = createMockTool("auto_tool", "Auto registered");
        ReflectionTestUtils.setField(toolRegistry, "toolDefinitions", List.of(tool));
        toolRegistry.init();
        assertTrue(toolRegistry.hasTool("auto_tool"));
    }

    private ToolDefinition createMockTool(String name, String description) {
        return new ToolDefinition() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public String getParameterSchema() { return "{}"; }
            @Override public ToolCallResult execute(Map<String, Object> parameters, ToolExecutionContext context) {
                return ToolCallResult.success(name, "mock result");
            }
        };
    }
}

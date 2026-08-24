package com.rain.zhixueai.agent.core;

import com.rain.zhixueai.agent.prompt.AgentPromptBuilder;
import com.rain.zhixueai.agent.tool.ToolRegistry;
import com.rain.zhixueai.client.DynamicAiClientFactory;
import com.rain.zhixueai.dispatcher.ModelDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock private ToolCallParser toolCallParser;
    @Mock private ToolRegistry toolRegistry;
    @Mock private AgentPromptBuilder agentPromptBuilder;
    @Mock private ModelDispatcher modelDispatcher;
    @Mock private DynamicAiClientFactory dynamicAiClientFactory;

    @InjectMocks
    private AgentOrchestrator agentOrchestrator;

    @Test
    void orchestrateStream_withNullMessages_createsErrorEmitter() {
        var request = com.rain.zhixueai.dto.AiRequest.builder()
            .messages(null).build();
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        assertDoesNotThrow(() -> agentOrchestrator.orchestrateStream(request, emitter));
    }

    @Test
    void orchestrateStream_withEmptyMessages_createsErrorEmitter() {
        var request = com.rain.zhixueai.dto.AiRequest.builder()
            .messages(java.util.Collections.emptyList()).build();
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        assertDoesNotThrow(() -> agentOrchestrator.orchestrateStream(request, emitter));
    }
}

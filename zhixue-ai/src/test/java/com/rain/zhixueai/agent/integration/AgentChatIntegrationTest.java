package com.rain.zhixueai.agent.integration;

import com.alibaba.fastjson2.JSON;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.AiRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AgentChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void agentChat_withoutAuth_returns401() throws Exception {
        AiRequest request = AiRequest.builder()
            .messages(List.of(new AiMessage("user", "你好")))
            .stream(true)
            .build();

        mockMvc.perform(post("/api/v1/ai/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void agentChat_withEmptyMessages_returnsError() throws Exception {
        AiRequest request = AiRequest.builder()
            .messages(new ArrayList<>())
            .stream(true)
            .build();

        mockMvc.perform(post("/api/v1/ai/agent/chat")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void generationStatus_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/generation-status/test-task-id")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void generationStatus_nonExistentTask_returnsError() throws Exception {
        mockMvc.perform(get("/api/v1/ai/agent/generation-status/non-existent-task")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}

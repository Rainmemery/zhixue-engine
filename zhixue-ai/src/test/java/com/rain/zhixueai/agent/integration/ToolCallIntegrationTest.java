package com.rain.zhixueai.agent.integration;

import com.rain.zhixueai.agent.core.ToolCallParser;
import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.dto.ToolCallRequest;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import com.rain.zhixueai.agent.tool.ToolDefinition;
import com.rain.zhixueai.agent.tool.ToolRegistry;
import com.rain.zhixueai.agent.tool.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("工具调用集成测试")
class ToolCallIntegrationTest {

    private ToolCallParser parser;
    private ToolRegistry registry;

    @Mock private RestTemplate restTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        parser = new ToolCallParser();
        registry = new ToolRegistry();
    }

    private void injectRestAndRedis(Object tool) {
        try {
            ReflectionTestUtils.setField(tool, "restTemplate", restTemplate);
        } catch (IllegalArgumentException ignored) {}
        try {
            ReflectionTestUtils.setField(tool, "redisTemplate", redisTemplate);
        } catch (IllegalArgumentException ignored) {}
    }

    @Nested
    @DisplayName("端到端工具调用流程：LLM输出 → 解析 → 执行 → 结果格式化")
    class EndToEndToolCallFlow {

        @Test
        @DisplayName("user_profile 工具完整调用流程（userId有效）")
        void userProfile_fullFlow_withValidUserId() {
            UserProfileTool tool = new UserProfileTool();
            injectRestAndRedis(tool);
            registry.register(tool);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(contains("/api/v1/users/15"), eq(Map.class)))
                .thenReturn(Map.of("learningLevel", 3, "experiencePoints", 1200, "dailyStreak", 5));
            when(restTemplate.getForObject(contains("/profile"), eq(Map.class)))
                .thenReturn(Map.of("difficultyPreference", "medium", "programmingLanguage", "cpp"));

            String llmOutput = "让我查询一下你的学习情况<tool_call: name: \"user_profile\" arguments: {\"fields\": [\"basic\", \"preference\"]}>";
            assertTrue(parser.hasToolCall(llmOutput));

            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);
            assertEquals(1, calls.size());
            assertEquals("user_profile", calls.get(0).getName());

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());
            assertNotNull(resolvedTool);

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertTrue(result.isSuccess());
            assertNotNull(result.getData());
            assertTrue(result.getExecutionTimeMs() >= 0);

            String formatted = parser.formatToolResult(result);
            assertTrue(formatted.contains("<tool_result:"));
            assertTrue(formatted.contains("user_profile"));
        }

        @Test
        @DisplayName("user_profile 工具完整调用流程（userId缺失）")
        void userProfile_fullFlow_withNullUserId() {
            UserProfileTool tool = new UserProfileTool();
            injectRestAndRedis(tool);
            registry.register(tool);

            String llmOutput = "<tool_call: name: \"user_profile\" arguments: {}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(null).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));

            String formatted = parser.formatToolResult(result);
            assertTrue(formatted.contains("user_profile"));
            assertTrue(formatted.contains("false"));
        }

        @Test
        @DisplayName("difficulty_adapt 工具完整调用流程（userId有效）")
        void difficultyAdapt_fullFlow_withValidUserId() {
            DifficultyAdaptTool tool = new DifficultyAdaptTool();
            injectRestAndRedis(tool);
            registry.register(tool);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("passRate", 0.8, "averageAttempts", 1.2, "trend", "improving",
                    "consecutiveFails", 0, "consecutivePasses", 4));

            String llmOutput = "<tool_call: name: \"difficulty_adapt\" arguments: {\"context\": \"recommend\"}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertTrue(result.isSuccess());

            Map<?, ?> data = (Map<?, ?>) result.getData();
            assertEquals("hard", data.get("recommendedDifficulty"));
            assertEquals("promote", data.get("adjustmentStrategy"));
        }

        @Test
        @DisplayName("difficulty_adapt 工具完整调用流程（userId缺失）")
        void difficultyAdapt_fullFlow_withNullUserId() {
            DifficultyAdaptTool tool = new DifficultyAdaptTool();
            injectRestAndRedis(tool);
            registry.register(tool);

            String llmOutput = "<tool_call: name: \"difficulty_adapt\" arguments: {}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(null).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
        }

        @Test
        @DisplayName("problem_generate 工具参数验证失败流程（缺少knowledgePoint）")
        void problemGenerate_missingKnowledgePoint_validationFailed() {
            ProblemGenerateTool tool = new ProblemGenerateTool();
            registry.register(tool);

            String llmOutput = "<tool_call: name: \"problem_generate\" arguments: {\"difficulty\": \"easy\"}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("knowledgePoint参数必填"));
        }

        @Test
        @DisplayName("problem_generate 工具 knowledgePoint 为空字符串验证失败")
        void problemGenerate_emptyKnowledgePoint_validationFailed() {
            ProblemGenerateTool tool = new ProblemGenerateTool();
            registry.register(tool);

            String llmOutput = "<tool_call: name: \"problem_generate\" arguments: {\"knowledgePoint\": \"\", \"difficulty\": \"easy\"}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("knowledgePoint参数不能为空"));
        }

        @Test
        @DisplayName("problem_generate 工具 difficulty 无效值验证失败")
        void problemGenerate_invalidDifficulty_validationFailed() {
            ProblemGenerateTool tool = new ProblemGenerateTool();
            registry.register(tool);

            String llmOutput = "<tool_call: name: \"problem_generate\" arguments: {\"knowledgePoint\": \"排序\", \"difficulty\": \"extreme\"}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("无效的difficulty参数"));
        }

        @Test
        @DisplayName("wrong_question 工具 action 无效值验证失败")
        void wrongQuestion_invalidAction_validationFailed() {
            WrongQuestionTool tool = new WrongQuestionTool();
            injectRestAndRedis(tool);
            registry.register(tool);

            String llmOutput = "<tool_call: name: \"wrong_question\" arguments: {\"action\": \"delete\"}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            ToolDefinition resolvedTool = registry.getTool(calls.get(0).getName());

            ToolCallResult result = resolvedTool.execute(calls.get(0).getArguments(), context);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("无效的action参数"));
        }
    }

    @Nested
    @DisplayName("多工具连续调用场景")
    class MultipleToolCallScenario {

        @Test
        @DisplayName("连续调用 user_profile + difficulty_adapt（userId有效）")
        void consecutiveCalls_userProfileAndDifficulty_withValidUserId() {
            UserProfileTool profileTool = new UserProfileTool();
            DifficultyAdaptTool adaptTool = new DifficultyAdaptTool();
            injectRestAndRedis(profileTool);
            injectRestAndRedis(adaptTool);
            registry.register(profileTool);
            registry.register(adaptTool);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(contains("/api/v1/users/15"), eq(Map.class)))
                .thenReturn(Map.of("learningLevel", 3));
            when(restTemplate.getForObject(contains("/profile"), eq(Map.class)))
                .thenReturn(Map.of("difficultyPreference", "medium"));
            when(restTemplate.getForObject(contains("/agent/user-status/"), eq(Map.class)))
                .thenReturn(Map.of("passRate", 0.6, "averageAttempts", 2.0, "trend", "stable",
                    "consecutiveFails", 0, "consecutivePasses", 1));

            String llmOutput = "<tool_call: name: \"user_profile\" arguments: {\"fields\": [\"basic\"]}>" +
                "<tool_call: name: \"difficulty_adapt\" arguments: {}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);
            assertEquals(2, calls.size());

            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            List<ToolCallResult> results = new ArrayList<>();

            for (ToolCallRequest call : calls) {
                ToolDefinition tool = registry.getTool(call.getName());
                assertNotNull(tool);
                ToolCallResult result = tool.execute(call.getArguments(), context);
                results.add(result);
                context.addToolResult(result);
                context.incrementToolCallCount();
            }

            assertEquals(2, results.size());
            assertTrue(results.get(0).isSuccess());
            assertTrue(results.get(1).isSuccess());
            assertEquals(2, context.getToolCallCount());
            assertEquals(2, context.getToolResults().size());

            String formatted = parser.formatToolResults(results);
            assertTrue(formatted.contains("user_profile"));
            assertTrue(formatted.contains("difficulty_adapt"));
        }

        @Test
        @DisplayName("连续调用 user_profile + difficulty_adapt（userId缺失）")
        void consecutiveCalls_userProfileAndDifficulty_withNullUserId() {
            UserProfileTool profileTool = new UserProfileTool();
            DifficultyAdaptTool adaptTool = new DifficultyAdaptTool();
            injectRestAndRedis(profileTool);
            injectRestAndRedis(adaptTool);
            registry.register(profileTool);
            registry.register(adaptTool);

            String llmOutput = "<tool_call: name: \"user_profile\" arguments: {}>" +
                "<tool_call: name: \"difficulty_adapt\" arguments: {}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolExecutionContext context = ToolExecutionContext.builder().userId(null).build();
            List<ToolCallResult> results = new ArrayList<>();

            for (ToolCallRequest call : calls) {
                ToolDefinition tool = registry.getTool(call.getName());
                ToolCallResult result = tool.execute(call.getArguments(), context);
                results.add(result);
                context.addToolResult(result);
                context.incrementToolCallCount();
            }

            assertEquals(2, results.size());
            assertFalse(results.get(0).isSuccess());
            assertTrue(results.get(0).getError().contains("用户ID缺失"));
            assertFalse(results.get(1).isSuccess());
            assertTrue(results.get(1).getError().contains("用户ID缺失"));
        }
    }

    @Nested
    @DisplayName("ToolExecutionContext 状态管理")
    class ContextStateManagement {

        @Test
        @DisplayName("工具调用计数和限制检查")
        void toolCallCount_andLimitCheck() {
            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            assertEquals(0, context.getToolCallCount());
            assertTrue(context.canCallMoreTools());

            context.incrementToolCallCount();
            context.incrementToolCallCount();
            context.incrementToolCallCount();
            assertEquals(3, context.getToolCallCount());
            assertFalse(context.canCallMoreTools());
        }

        @Test
        @DisplayName("工具结果累积")
        void toolResults_accumulation() {
            ToolExecutionContext context = ToolExecutionContext.builder().userId(15L).build();
            assertTrue(context.getToolResults().isEmpty());

            context.addToolResult(ToolCallResult.success("tool1", "data1"));
            context.addToolResult(ToolCallResult.error("tool2", "error1"));
            assertEquals(2, context.getToolResults().size());
            assertTrue(context.getToolResults().get(0).isSuccess());
            assertFalse(context.getToolResults().get(1).isSuccess());
        }
    }

    @Nested
    @DisplayName("ToolCallResult 格式化与序列化")
    class ToolCallResultFormatting {

        @Test
        @DisplayName("成功结果格式化包含完整信息")
        void successResult_formatting_containsAllInfo() {
            ToolCallResult result = ToolCallResult.success("user_profile", Map.of("level", 5));
            result.setExecutionTimeMs(123);

            String formatted = parser.formatToolResult(result);
            assertTrue(formatted.contains("<tool_result: name: \"user_profile\""));
            assertTrue(formatted.contains("result:"));
            assertTrue(formatted.contains("true"));
        }

        @Test
        @DisplayName("失败结果格式化包含错误信息")
        void errorResult_formatting_containsError() {
            ToolCallResult result = ToolCallResult.error("user_profile", "用户ID缺失");
            result.setExecutionTimeMs(5);

            String formatted = parser.formatToolResult(result);
            assertTrue(formatted.contains("user_profile"));
            assertTrue(formatted.contains("用户ID缺失"));
            assertTrue(formatted.contains("false"));
        }

        @Test
        @DisplayName("多个结果格式化以换行分隔")
        void multipleResults_formatting_newlineSeparated() {
            List<ToolCallResult> results = List.of(
                ToolCallResult.success("tool1", Map.of("key", "val")),
                ToolCallResult.error("tool2", "error msg")
            );

            String formatted = parser.formatToolResults(results);
            assertTrue(formatted.contains("tool1"));
            assertTrue(formatted.contains("tool2"));
            assertTrue(formatted.contains("\n"));
        }
    }

    @Nested
    @DisplayName("错误恢复场景")
    class ErrorRecoveryScenario {

        @Test
        @DisplayName("工具不存在时返回 null 而不崩溃")
        void toolNotFound_returnsNull() {
            String llmOutput = "<tool_call: name: \"non_existent_tool\" arguments: {}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);

            ToolDefinition tool = registry.getTool(calls.get(0).getName());
            assertNull(tool);
        }

        @Test
        @DisplayName("LLM 输出格式错误时解析器不崩溃")
        void malformedLlmOutput_parserDoesNotCrash() {
            String llmOutput = "这是普通文本，没有工具调用";
            assertFalse(parser.hasToolCall(llmOutput));
            assertTrue(parser.parseToolCalls(llmOutput).isEmpty());
        }

        @Test
        @DisplayName("LLM 输出中 JSON 参数格式错误时仍能解析工具名")
        void malformedJsonArguments_stillParsesToolName() {
            String llmOutput = "<tool_call: name: \"user_profile\" arguments: {invalid json}>";
            List<ToolCallRequest> calls = parser.parseToolCalls(llmOutput);
            assertEquals(1, calls.size());
            assertEquals("user_profile", calls.get(0).getName());
        }
    }
}

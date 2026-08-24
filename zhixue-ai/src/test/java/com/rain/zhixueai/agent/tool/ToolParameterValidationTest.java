package com.rain.zhixueai.agent.tool;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.dto.ToolCallResult;
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
@DisplayName("工具参数验证单元测试")
class ToolParameterValidationTest {

    @Mock private RestTemplate restTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ToolExecutionContext contextWithUser;
    private ToolExecutionContext contextWithoutUser;

    @BeforeEach
    void setUp() {
        contextWithUser = ToolExecutionContext.builder().userId(15L).build();
        contextWithoutUser = ToolExecutionContext.builder().userId(null).build();
    }

    @Nested
    @DisplayName("AbstractTool - userId 验证机制")
    class AbstractToolUserIdTest {

        @Test
        @DisplayName("requiresUserId=true 且 context.userId=null 时应返回错误")
        void requiresUserId_nullUserId_returnsError() {
            UserProfileTool tool = new UserProfileTool();
            ToolCallResult result = tool.execute(Map.of("fields", List.of("basic")), contextWithoutUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
            assertTrue(result.getError().contains("user_profile"));
        }

        @Test
        @DisplayName("requiresUserId=true 且 context=null 时应返回错误")
        void requiresUserId_nullContext_returnsError() {
            UserProfileTool tool = new UserProfileTool();
            ToolCallResult result = tool.execute(Map.of("fields", List.of("basic")), null);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
        }

        @Test
        @DisplayName("requiresUserId=true 且 context.userId 有值时不应拦截")
        void requiresUserId_validUserId_notBlocked() {
            DifficultyAdaptTool tool = new DifficultyAdaptTool();
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("passRate", 0.5, "averageAttempts", 2.0, "trend", "stable",
                    "consecutiveFails", 0, "consecutivePasses", 0));

            ToolCallResult result = tool.execute(Map.of(), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("requiresUserId=false 的工具不检查 userId")
        void noRequiresUserId_nullUserId_notBlocked() {
            ProblemGenerateTool tool = new ProblemGenerateTool();
            ToolCallResult result = tool.execute(
                Map.of("knowledgePoint", "排序", "difficulty", "easy"), contextWithoutUser);
            assertTrue(result.isSuccess() || !result.getError().contains("用户ID缺失"));
        }
    }

    @Nested
    @DisplayName("UserProfileTool - 参数验证")
    class UserProfileToolTest {

        private UserProfileTool tool;

        @BeforeEach
        void setUp() {
            tool = new UserProfileTool();
            ReflectionTestUtils.setField(tool, "restTemplate", restTemplate);
            ReflectionTestUtils.setField(tool, "redisTemplate", redisTemplate);
        }

        @Test
        @DisplayName("userId=null 时应返回用户ID缺失错误")
        void execute_nullUserId_returnsUserIdError() {
            ToolCallResult result = tool.execute(Map.of("fields", List.of("basic")), contextWithoutUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
        }

        @Test
        @DisplayName("fields 参数包含无效值时应抛出验证异常")
        void validateParameters_invalidFieldValue_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("fields", List.of("invalid_field")), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("无效的fields参数值"));
        }

        @Test
        @DisplayName("fields 参数为 null 时不报错，使用默认值")
        void execute_nullFields_usesDefault() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("learningLevel", 3));

            ToolCallResult result = tool.execute(new HashMap<>(), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("fields 参数包含 all 时应返回所有字段")
        void execute_fieldsAll_returnsAllFields() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(contains("/api/v1/users/15"), eq(Map.class)))
                .thenReturn(Map.of("learningLevel", 3, "experiencePoints", 1200, "dailyStreak", 5));
            when(restTemplate.getForObject(contains("/profile"), eq(Map.class)))
                .thenReturn(Map.of("difficultyPreference", "medium", "programmingLanguage", "cpp"));
            when(restTemplate.getForObject(contains("/agent/user-status/"), eq(Map.class)))
                .thenReturn(Map.of("passRate", 0.6, "averageScore", 80, "totalSubmissions", 10, "trend", "improving"));
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            ToolCallResult result = tool.execute(Map.of("fields", List.of("all")), contextWithUser);
            assertTrue(result.isSuccess(), "Tool should succeed, error was: " + result.getError());
            Map<?, ?> data = (Map<?, ?>) result.getData();
            assertTrue(data.containsKey("basic"));
            assertTrue(data.containsKey("preference"));
            assertTrue(data.containsKey("recentPerformance"));
        }
    }

    @Nested
    @DisplayName("DifficultyAdaptTool - 参数验证")
    class DifficultyAdaptToolTest {

        private DifficultyAdaptTool tool;

        @BeforeEach
        void setUp() {
            tool = new DifficultyAdaptTool();
            ReflectionTestUtils.setField(tool, "restTemplate", restTemplate);
            ReflectionTestUtils.setField(tool, "redisTemplate", redisTemplate);
        }

        @Test
        @DisplayName("userId=null 时应返回用户ID缺失错误")
        void execute_nullUserId_returnsUserIdError() {
            ToolCallResult result = tool.execute(Map.of(), contextWithoutUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
        }

        @Test
        @DisplayName("context 参数无效时应抛出验证异常")
        void validateParameters_invalidContext_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("context", "invalid_value"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("无效的context参数"));
        }

        @Test
        @DisplayName("context 参数有效时不应报错")
        void validateParameters_validContext_noException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("passRate", 0.6, "averageAttempts", 1.5, "trend", "improving",
                    "consecutiveFails", 0, "consecutivePasses", 2));

            ToolCallResult result = tool.execute(Map.of("context", "recommend"), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("无参数时使用默认值正常执行")
        void execute_noParameters_usesDefaults() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("passRate", 0.5, "averageAttempts", 2.0, "trend", "stable",
                    "consecutiveFails", 0, "consecutivePasses", 0));

            ToolCallResult result = tool.execute(Map.of(), contextWithUser);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("ProblemGenerateTool - 参数验证")
    class ProblemGenerateToolTest {

        private ProblemGenerateTool tool;

        @BeforeEach
        void setUp() {
            tool = new ProblemGenerateTool();
        }

        @Test
        @DisplayName("缺少 knowledgePoint 参数时应抛出验证异常")
        void validateParameters_missingKnowledgePoint_throwsException() {
            ToolCallResult result = tool.execute(Map.of("difficulty", "easy"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("knowledgePoint参数必填"));
        }

        @Test
        @DisplayName("knowledgePoint 为空字符串时应抛出验证异常")
        void validateParameters_emptyKnowledgePoint_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("knowledgePoint", "", "difficulty", "easy"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("knowledgePoint参数不能为空"));
        }

        @Test
        @DisplayName("knowledgePoint 为空白字符串时应抛出验证异常")
        void validateParameters_blankKnowledgePoint_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("knowledgePoint", "   ", "difficulty", "easy"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("knowledgePoint参数不能为空"));
        }

        @Test
        @DisplayName("缺少 difficulty 参数时应抛出验证异常")
        void validateParameters_missingDifficulty_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("knowledgePoint", "排序"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("difficulty参数必填"));
        }

        @Test
        @DisplayName("difficulty 参数无效时应抛出验证异常")
        void validateParameters_invalidDifficulty_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("knowledgePoint", "排序", "difficulty", "extreme"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("无效的difficulty参数"));
        }

        @Test
        @DisplayName("parameters 为 null 时应抛出验证异常")
        void validateParameters_nullParameters_throwsException() {
            ToolCallResult result = tool.execute(null, contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("knowledgePoint参数必填"));
        }

        @Test
        @DisplayName("knowledgePoint 和 difficulty 都有效时验证通过")
        void validateParameters_validParams_noException() {
            ToolCallResult result = tool.execute(
                Map.of("knowledgePoint", "排序", "difficulty", "medium"), contextWithUser);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("ProblemRecommendTool - 参数验证")
    class ProblemRecommendToolTest {

        private ProblemRecommendTool tool;

        @BeforeEach
        void setUp() {
            tool = new ProblemRecommendTool();
            ReflectionTestUtils.setField(tool, "restTemplate", restTemplate);
        }

        @Test
        @DisplayName("userId=null 时应返回用户ID缺失错误")
        void execute_nullUserId_returnsUserIdError() {
            ToolCallResult result = tool.execute(Map.of(), contextWithoutUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
        }

        @Test
        @DisplayName("limit 参数超出范围时应抛出验证异常")
        void validateParameters_limitOutOfRange_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("limit", 10), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("推荐数量必须在1-5之间"));
        }

        @Test
        @DisplayName("limit 参数为 0 时应抛出验证异常")
        void validateParameters_limitZero_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("limit", 0), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("推荐数量必须在1-5之间"));
        }

        @Test
        @DisplayName("limit 参数为负数时应抛出验证异常")
        void validateParameters_limitNegative_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("limit", -1), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("推荐数量必须在1-5之间"));
        }

        @Test
        @DisplayName("limit 参数在有效范围内时验证通过")
        void validateParameters_validLimit_noException() {
            ToolCallResult result = tool.execute(Map.of("limit", 3), contextWithUser);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("WrongQuestionTool - 参数验证")
    class WrongQuestionToolTest {

        private WrongQuestionTool tool;

        @BeforeEach
        void setUp() {
            tool = new WrongQuestionTool();
            ReflectionTestUtils.setField(tool, "restTemplate", restTemplate);
        }

        @Test
        @DisplayName("userId=null 时应返回用户ID缺失错误")
        void execute_nullUserId_returnsUserIdError() {
            ToolCallResult result = tool.execute(Map.of("action", "analyze"), contextWithoutUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("用户ID缺失"));
        }

        @Test
        @DisplayName("缺少 action 参数时应抛出验证异常")
        void validateParameters_missingAction_throwsException() {
            ToolCallResult result = tool.execute(Map.of(), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("action参数必填"));
        }

        @Test
        @DisplayName("action 参数无效时应抛出验证异常")
        void validateParameters_invalidAction_throwsException() {
            ToolCallResult result = tool.execute(
                Map.of("action", "delete"), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("无效的action参数"));
        }

        @Test
        @DisplayName("action 参数为 collect 时验证通过")
        void validateParameters_collectAction_noException() {
            ToolCallResult result = tool.execute(
                Map.of("action", "collect"), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("action 参数为 analyze 时验证通过")
        void validateParameters_analyzeAction_noException() {
            ToolCallResult result = tool.execute(
                Map.of("action", "analyze"), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("action 参数为 review 时验证通过")
        void validateParameters_reviewAction_noException() {
            ToolCallResult result = tool.execute(
                Map.of("action", "review"), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("action 参数为 knowledge_graph 时验证通过")
        void validateParameters_knowledgeGraphAction_noException() {
            ToolCallResult result = tool.execute(
                Map.of("action", "knowledge_graph"), contextWithUser);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("parameters 为 null 时应抛出验证异常")
        void validateParameters_nullParameters_throwsException() {
            ToolCallResult result = tool.execute(null, contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("action参数必填"));
        }
    }

    @Nested
    @DisplayName("TestDataGenerateTool - 参数验证")
    class TestDataGenerateToolTest {

        private TestDataGenerateTool tool;

        @BeforeEach
        void setUp() {
            tool = new TestDataGenerateTool();
        }

        @Test
        @DisplayName("缺少 problemData 参数时应抛出验证异常")
        void validateParameters_missingProblemData_throwsException() {
            ToolCallResult result = tool.execute(Map.of(), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("problemData参数必填"));
        }

        @Test
        @DisplayName("parameters 为 null 时应抛出验证异常")
        void validateParameters_nullParameters_throwsException() {
            ToolCallResult result = tool.execute(null, contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("problemData参数必填"));
        }

        @Test
        @DisplayName("problemData 参数存在时验证通过")
        void validateParameters_withProblemData_noException() {
            ToolCallResult result = tool.execute(
                Map.of("problemData", Map.of("title", "test")), contextWithUser);
            assertTrue(result.isSuccess());
        }
    }

    @Nested
    @DisplayName("ProblemValidateTool - 参数验证")
    class ProblemValidateToolTest {

        private ProblemValidateTool tool;

        @BeforeEach
        void setUp() {
            tool = new ProblemValidateTool();
        }

        @Test
        @DisplayName("缺少 problemData 参数时应抛出验证异常")
        void validateParameters_missingProblemData_throwsException() {
            ToolCallResult result = tool.execute(Map.of(), contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("problemData参数必填"));
        }

        @Test
        @DisplayName("problemData 为 null 时应抛出验证异常")
        void validateParameters_nullProblemData_throwsException() {
            Map<String, Object> params = new HashMap<>();
            params.put("problemData", null);
            ToolCallResult result = tool.execute(params, contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("problemData参数不能为null"));
        }

        @Test
        @DisplayName("parameters 为 null 时应抛出验证异常")
        void validateParameters_nullParameters_throwsException() {
            ToolCallResult result = tool.execute(null, contextWithUser);
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("problemData参数必填"));
        }

        @Test
        @DisplayName("problemData 参数存在时验证通过")
        void validateParameters_withProblemData_noException() {
            ToolCallResult result = tool.execute(
                Map.of("problemData", Map.of("title", "test", "description", "desc",
                    "inputDescription", "input", "outputDescription", "output")), contextWithUser);
            assertTrue(result.isSuccess());
        }
    }
}

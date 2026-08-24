package com.rain.zhixueai.agent.tool.impl;

import com.rain.zhixueai.agent.core.ToolExecutionContext;
import com.rain.zhixueai.agent.tool.AbstractTool;
import com.rain.zhixueai.client.DynamicAiClientFactory;
import com.rain.zhixueai.dispatcher.ModelDispatcher;
import com.rain.zhixueai.dispatcher.entity.ModelInstance;
import com.rain.zhixueai.dto.AiMessage;
import com.rain.zhixueai.dto.AiRequest;
import com.rain.zhixueai.enums.AiRole;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ProblemValidateTool extends AbstractTool {

    private static final String NAME = "problem_validate";
    private static final String VALIDATION_PROMPT_TEMPLATE = """
你是题目质量审核专家。请对以下题目进行逻辑一致性验证：

## 题目数据
%s

## 验证维度
1. 题目描述是否清晰无歧义
2. 输入输出格式说明是否与描述一致
3. 约束条件是否合理且完整
4. 样例是否可由题目描述推导得出
5. 参考解答是否正确解决题目
6. 难度标注是否与实际难度匹配

## 输出格式（严格JSON，不要用```包裹）
{"passed":true,"issues":[],"suggestions":[]}
或
{"passed":false,"issues":[{"dimension":"逻辑一致性","severity":"error","description":"问题描述"}],"suggestions":["改进建议"]}
""";

    @Autowired
    private ModelDispatcher modelDispatcher;

    @Autowired
    private DynamicAiClientFactory dynamicAiClientFactory;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "- 功能：对生成的题目和测试数据进行多维度验证\n" +
               "- 适用场景：题目生成后需要验证质量时；用户要求检查题目正确性时\n" +
               "- 触发条件：用户提到\u201C验证题目\u201D、\u201C检查题目\u201D、\u201C题目对不对\u201D等关键词";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - problemData (必填, object): 题目数据对象，包含title、description等字段\n" +
               "  - testCases (必填, array): 测试用例数组\n" +
               "- 调用示例：<tool_call: name: \"problem_validate\" arguments: {\"problemData\": {\"title\": \"...\"}, \"testCases\": [...]}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("problemData")) {
            throw new IllegalArgumentException("problemData参数必填");
        }
        Object pdObj = parameters.get("problemData");
        if (pdObj == null) {
            throw new IllegalArgumentException("problemData参数不能为null");
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        Object problemDataObj = parameters.get("problemData");
        String problemDataJson = problemDataObj instanceof String ? (String) problemDataObj : JSON.toJSONString(problemDataObj);

        log.info("[ProblemValidate] Validating problem, data length={}", problemDataJson.length());

        Map<String, Object> validationResults = new LinkedHashMap<>();

        boolean syntaxPassed = validateSyntax(problemDataJson);
        validationResults.put("syntaxCheck", Map.of("passed", syntaxPassed,
            "details", syntaxPassed ? "所有必填字段完整" : "缺少必填字段"));
        log.info("[ProblemValidate] Syntax check: passed={}", syntaxPassed);

        boolean logicPassed = false;
        List<Map<String, Object>> logicIssues = new ArrayList<>();
        try {
            JSONObject logicResult = validateLogic(problemDataJson);
            logicPassed = logicResult.getBooleanValue("passed");
            if (logicResult.containsKey("issues")) {
                logicIssues = logicResult.getJSONArray("issues").toJavaList(JSONObject.class).stream()
                    .map(obj -> (Map<String, Object>) new LinkedHashMap<>(obj))
                    .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("[ProblemValidate] Logic validation LLM call failed: {}", e.getMessage());
            logicIssues.add(Map.of("dimension", "逻辑一致性", "severity", "warning", "description", "LLM验证失败: " + e.getMessage()));
        }
        validationResults.put("logicCheck", Map.of("passed", logicPassed, "issues", logicIssues));

        boolean overallPassed = syntaxPassed && logicPassed;
        log.info("[ProblemValidate] Validation completed: syntaxPassed={}, logicPassed={}, overallPassed={}",
            syntaxPassed, logicPassed, overallPassed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", overallPassed ? "validated" : "validation_failed");
        result.put("overallPassed", overallPassed);
        result.put("validationResults", validationResults);
        return result;
    }

    private boolean validateSyntax(String problemDataJson) {
        try {
            JSONObject data = JSON.parseObject(problemDataJson);
            return data.containsKey("title") && data.containsKey("description")
                && data.containsKey("inputDescription") && data.containsKey("outputDescription");
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject validateLogic(String problemDataJson) {
        String prompt = String.format(VALIDATION_PROMPT_TEMPLATE, problemDataJson);
        String llmOutput = callLlmSync(prompt);

        String cleaned = llmOutput.trim();
        if (cleaned.contains("```")) {
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) cleaned = cleaned.substring(start, end);
        }

        try { return JSON.parseObject(cleaned); }
        catch (Exception e) {
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                try { return JSON.parseObject(cleaned.substring(start, end)); }
                catch (Exception ex) { return new JSONObject().fluentPut("passed", false).fluentPut("issues", Collections.emptyList()); }
            }
            return new JSONObject().fluentPut("passed", false).fluentPut("issues", Collections.emptyList());
        }
    }

    private String callLlmSync(String prompt) {
        ModelInstance instance = modelDispatcher.getAvailableInstance();
        if (instance == null) throw new RuntimeException("没有可用的模型实例");

        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", prompt));

        AiRequest request = AiRequest.builder()
            .messages(messages).role(AiRole.AGENT_QUESTIONER).stream(false)
            .temperature(0.3).maxTokens(2000).build();

        try {
            String result = dynamicAiClientFactory.syncChat(instance, request);
            return result != null ? result : "";
        } catch (Exception e) {
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }
}

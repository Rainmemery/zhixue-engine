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
public class TestDataGenerateTool extends AbstractTool {

    private static final String NAME = "test_data_generate";
    private static final String TEST_DATA_PROMPT_TEMPLATE = """
你是测试数据生成专家。请为以下题目生成 %d 组测试数据。

## 题目信息
%s

## 生成要求
1. 正常用例（5组）：覆盖典型输入场景
2. 边界用例（3组）：最小值、最大值、临界值
3. 特殊用例（2组）：空输入、单元素、全相同元素等

## 输出格式（严格JSON，不要用```包裹）
{"testCases":[{"name":"normal_1","type":"normal","input":"输入数据","expectedOutput":"期望输出"},{"name":"boundary_min","type":"boundary","input":"边界输入","expectedOutput":"期望输出"}]}

## 关键约束
- 每组测试数据必须能通过参考解答代码
- 输入数据必须符合题目约束条件
- 边界用例必须覆盖数据范围的极值
""";

    @Autowired
    private ModelDispatcher modelDispatcher;

    @Autowired
    private DynamicAiClientFactory dynamicAiClientFactory;

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() {
        return "- 功能：为已生成的题目自动创建测试样例和测试用例数据\n" +
               "- 适用场景：题目生成后需要补充测试数据时；用户要求生成测试用例时\n" +
               "- 触发条件：用户提到\u201C测试数据\u201D、\u201C测试用例\u201D、\u201C生成测试\u201D等关键词";
    }

    @Override
    public String getParameterSchema() {
        return "- 参数说明：\n" +
               "  - problemData (必填, object): 题目数据对象\n" +
               "  - caseCount (选填, integer): 生成测试用例数量，默认: 10\n" +
               "- 调用示例：<tool_call: name: \"test_data_generate\" arguments: {\"problemData\": {\"title\": \"...\"}, \"caseCount\": 10}>";
    }

    @Override
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null || !parameters.containsKey("problemData")) {
            throw new IllegalArgumentException("problemData参数必填");
        }
    }

    @Override
    protected Object doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        Object problemDataObj = parameters.get("problemData");
        int caseCount = parameters.containsKey("caseCount") ? ((Number) parameters.get("caseCount")).intValue() : 10;

        log.info("[TestDataGenerate] Generating test data, caseCount={}", caseCount);

        String problemDataJson = problemDataObj instanceof String
            ? (String) problemDataObj
            : JSON.toJSONString(problemDataObj);

        String prompt = String.format(TEST_DATA_PROMPT_TEMPLATE, caseCount, problemDataJson);

        try {
            String llmOutput = callLlmSync(prompt);
            log.info("[TestDataGenerate] LLM output received, length={}", llmOutput != null ? llmOutput.length() : 0);
            JSONObject testData = parseTestDataJson(llmOutput);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "data_generated");
            result.put("totalCases", testData.getJSONArray("testCases") != null ? testData.getJSONArray("testCases").size() : 0);
            result.put("testCases", testData.getJSONArray("testCases"));
            log.info("[TestDataGenerate] Test data generated successfully, totalCases={}", result.get("totalCases"));
            return result;

        } catch (Exception e) {
            log.error("[TestDataGenerate] Failed to generate test data, caseCount={}", caseCount, e);
            return Map.of("status", "failed", "error", "测试数据生成失败: " + e.getMessage());
        }
    }

    private String callLlmSync(String prompt) {
        ModelInstance instance = modelDispatcher.getAvailableInstance();
        if (instance == null) throw new RuntimeException("没有可用的模型实例");

        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", prompt));

        AiRequest request = AiRequest.builder()
            .messages(messages).role(AiRole.AGENT_QUESTIONER).stream(false)
            .temperature(0.7).maxTokens(4000).build();

        try {
            String result = dynamicAiClientFactory.syncChat(instance, request);
            return result != null ? result : "";
        } catch (Exception e) {
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    private JSONObject parseTestDataJson(String llmOutput) {
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
                catch (Exception ex) { throw new RuntimeException("JSON解析失败"); }
            }
            throw new RuntimeException("JSON解析失败");
        }
    }
}

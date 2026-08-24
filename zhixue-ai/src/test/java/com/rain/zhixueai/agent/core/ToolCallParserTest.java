package com.rain.zhixueai.agent.core;

import com.rain.zhixueai.agent.dto.ToolCallRequest;
import com.rain.zhixueai.agent.dto.ToolCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallParserTest {

    private ToolCallParser parser;

    @BeforeEach
    void setUp() {
        parser = new ToolCallParser();
    }

    @Test
    void hasToolCall_withValidToolCall_returnsTrue() {
        String input = "让我查询一下用户画像<tool_call: name: \"user_profile\" arguments: {\"fields\": [\"basic\"]}>";
        assertTrue(parser.hasToolCall(input));
    }

    @Test
    void hasToolCall_withoutToolCall_returnsFalse() {
        String input = "这是一个普通的回复，没有工具调用";
        assertFalse(parser.hasToolCall(input));
    }

    @Test
    void hasToolCall_withNullInput_returnsFalse() {
        assertFalse(parser.hasToolCall(null));
    }

    @Test
    void hasToolCall_withEmptyInput_returnsFalse() {
        assertFalse(parser.hasToolCall(""));
    }

    @Test
    void parseToolCalls_singleToolCall_parsesCorrectly() {
        String input = "<tool_call: name: \"user_profile\" arguments: {\"fields\": [\"basic\"]}>";
        List<ToolCallRequest> calls = parser.parseToolCalls(input);

        assertEquals(1, calls.size());
        assertEquals("user_profile", calls.get(0).getName());
        assertNotNull(calls.get(0).getArguments());
    }

    @Test
    void parseToolCalls_multipleToolCalls_parsesAll() {
        String input = "<tool_call: name: \"user_profile\" arguments: {\"fields\": [\"all\"]}>" +
                "<tool_call: name: \"difficulty_adapt\" arguments: {}>";
        List<ToolCallRequest> calls = parser.parseToolCalls(input);

        assertEquals(2, calls.size());
        assertEquals("user_profile", calls.get(0).getName());
        assertEquals("difficulty_adapt", calls.get(1).getName());
    }

    @Test
    void parseToolCalls_exceedsMaxLimit_truncatesToThree() {
        String input = "<tool_call: name: \"tool1\" arguments: {}>" +
                "<tool_call: name: \"tool2\" arguments: {}>" +
                "<tool_call: name: \"tool3\" arguments: {}>" +
                "<tool_call: name: \"tool4\" arguments: {}>";
        List<ToolCallRequest> calls = parser.parseToolCalls(input);
        assertEquals(3, calls.size());
    }

    @Test
    void parseToolCalls_malformedArguments_returnsEmptyArgs() {
        String input = "<tool_call: name: \"user_profile\" arguments: {invalid json}>";
        List<ToolCallRequest> calls = parser.parseToolCalls(input);
        assertEquals(1, calls.size());
        assertEquals("user_profile", calls.get(0).getName());
    }

    @Test
    void extractTextBeforeToolCalls_returnsTextBeforeFirstCall() {
        String input = "这是工具调用前的文本<tool_call: name: \"user_profile\" arguments: {}>";
        String text = parser.extractTextBeforeToolCalls(input);
        assertEquals("这是工具调用前的文本", text);
    }

    @Test
    void extractTextBeforeToolCalls_noToolCall_returnsFullText() {
        String input = "没有工具调用的文本";
        String text = parser.extractTextBeforeToolCalls(input);
        assertEquals("没有工具调用的文本", text);
    }

    @Test
    void formatToolResult_returnsCorrectFormat() {
        ToolCallResult result = ToolCallResult.success("user_profile", Map.of("level", 5));
        String formatted = parser.formatToolResult(result);
        assertTrue(formatted.contains("<tool_result: name: \"user_profile\""));
        assertTrue(formatted.contains("result:"));
    }

    @Test
    void formatToolResults_multipleResults_formatsAll() {
        List<ToolCallResult> results = List.of(
            ToolCallResult.success("tool1", "data1"),
            ToolCallResult.error("tool2", "error1")
        );
        String formatted = parser.formatToolResults(results);
        assertTrue(formatted.contains("tool1"));
        assertTrue(formatted.contains("tool2"));
    }
}

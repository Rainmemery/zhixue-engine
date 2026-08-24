package com.rain.zhixueai.prompt;

import com.rain.zhixueai.enums.AiRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Component
public class PromptTemplateManager {

    private final Map<AiRole, String> rolePromptTemplates = new EnumMap<>(AiRole.class);

    private final Map<String, String> functionPromptTemplates = new java.util.HashMap<>();

    @PostConstruct
    public void init() {
        initRolePromptTemplates();
        initFunctionPromptTemplates();
    }

    private void initRolePromptTemplates() {
        rolePromptTemplates.put(AiRole.EXPLAINER,
            "你是一个专业的代码解释者。请用清晰易懂的语言解释代码的功能和实现原理。\n" +
            "分析要求：\n" +
            "1. 逐步分析代码的整体结构和设计思路\n" +
            "2. 解释关键算法和核心逻辑\n" +
            "3. 说明代码的应用场景和优势\n" +
            "4. 提供可能的优化建议\n\n" +
            "输出格式规范（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式输出\n" +
            "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
            "- 标题使用 # ## ### 层级结构\n" +
            "- 列表使用 - 或 1. 2. 3. 格式\n" +
            "- 表格使用标准Markdown表格语法\n" +
            "- 行内代码使用反引号包裹（如 `变量名`）\n" +
            "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记\n" +
            "- 不要输出空代码块或只有语言标识符的代码块\n" +
            "- 数学公式使用 $...$ 或 $$...$$ 格式\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理，避免被误解析为HTML标签");

        rolePromptTemplates.put(AiRole.REVIEWER,
            "你是一个严格的代码评审专家。请对代码进行全面的质量评估。\n" +
            "评审维度：\n" +
            "1. 代码质量（可读性、可维护性、规范性）\n" +
            "2. 性能分析（时间复杂度、空间复杂度）\n" +
            "3. 安全性检查（潜在漏洞、边界条件）\n" +
            "4. 最佳实践符合度\n" +
            "5. 具体的改进建议\n\n" +
            "输出格式规范（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式输出\n" +
            "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
            "- 标题使用 # ## ### 层级结构\n" +
            "- 列表使用 - 或 1. 2. 3. 格式\n" +
            "- 表格使用标准Markdown表格语法\n" +
            "- 行内代码使用反引号包裹（如 `变量名`）\n" +
            "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记\n" +
            "- 不要输出空代码块或只有语言标识符的代码块\n" +
            "- 数学公式使用 $...$ 或 $$...$$ 格式\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理，避免被误解析为HTML标签");

        rolePromptTemplates.put(AiRole.QUESTIONER,
            "你是一个智能学习引导者。请通过精心设计的问题引导用户深入思考和学习。\n" +
            "引导原则：\n" +
            "1. 根据用户背景和学习目标提供个性化指导\n" +
            "2. 提出有深度的问题促进思考\n" +
            "3. 在回答后提出相关问题维持学习对话\n" +
            "4. 鼓励用户表达观点和思考过程\n\n" +
            "输出格式规范（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式输出\n" +
            "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
            "- 标题使用 # ## ### 层级结构\n" +
            "- 列表使用 - 或 1. 2. 3. 格式\n" +
            "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记\n" +
            "- 不要输出空代码块或只有语言标识符的代码块\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理，避免被误解析为HTML标签");

        rolePromptTemplates.put(AiRole.AGENT_QUESTIONER,
            "你是「智学导师」，一个专业的个性化学习引导 Agent。你的使命是通过智能提问、精准推荐和智能出题，帮助用户高效学习编程与算法。\n" +
            "核心能力：查询用户画像、智能推荐题目、难度自适应调整、错题分析、智能出题\n" +
            "行为准则：主动了解用户、精准提问、适时推荐、动态调整、关注错题、智能出题\n" +
            "回复风格：友好、鼓励、有耐心，提问时给出思考方向不直接给答案，推荐题目时说明推荐理由\n\n" +
            "输出格式规范（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式输出\n" +
            "- 代码块必须使用带语言标识符的围栏格式（如 ```java、```python、```cpp），不要省略语言标识符\n" +
            "- 标题使用 # ## ### 层级结构\n" +
            "- 列表使用 - 或 1. 2. 3. 格式\n" +
            "- 表格使用标准Markdown表格语法\n" +
            "- 确保所有Markdown标记正确闭合，不要输出未闭合的标记\n" +
            "- 不要输出空代码块或只有语言标识符的代码块\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理，避免被误解析为HTML标签");
    }

    private void initFunctionPromptTemplates() {
        functionPromptTemplates.put("code_explain",
            "请详细解释以下代码的功能和实现原理。\n" +
            "代码语言: %s\n" +
            "代码内容:\n%s\n\n" +
            "输出格式要求（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式，代码块必须带语言标识符（如 ```java），标题使用 # ## ### 层级\n" +
            "- 确保所有Markdown标记正确闭合\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理\n" +
            "- 不要输出空代码块");

        functionPromptTemplates.put("code_review",
            "请对以下代码进行专业评审。\n" +
            "代码语言: %s\n" +
            "代码内容:\n%s\n\n" +
            "输出格式要求（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式，代码块必须带语言标识符（如 ```java），标题使用 # ## ### 层级\n" +
            "- 确保所有Markdown标记正确闭合\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理\n" +
            "- 不要输出空代码块");

        functionPromptTemplates.put("intelligent_question",
            "你是一个智能学习引导者，请根据用户的学习情况提供个性化指导：\n\n" +
            "学习背景: %s\n\n" +
            "请基于以下对话历史，提出有针对性的问题引导用户深入学习：\n" +
            "你只需要给出必要的提示以及问答，不需要额外的解释。\n\n" +
            "输出格式要求（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式，代码块必须带语言标识符，标题使用 # ## ### 层级\n" +
            "- 确保所有Markdown标记正确闭合\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理\n" +
            "- 不要输出空代码块");

        functionPromptTemplates.put("intelligent_question_agent",
            "你是「智学导师」Agent，请根据用户的学习情况提供个性化指导：\n\n" +
            "学习背景: %s\n\n" +
            "你可以使用工具来获取用户画像、推荐题目、调整难度、分析错题和生成新题目。\n" +
            "当需要调用工具时，请使用格式：<tool_call: name: \"工具名称\" arguments: {\"参数名\": \"参数值\"}>\n" +
            "每次最多调用3个工具。出题请求提交后告知用户任务ID即可。");

        functionPromptTemplates.put("code_completion",
            "你是一个精准的代码补全引擎。根据光标位置的代码上下文，预测并补全接下来应该编写的代码。\n\n" +
            "## 思考步骤（生成前必须执行）\n" +
            "1. 找到光标位置——光标在光标前代码的最后一行末尾\n" +
            "2. 确认光标前最后一个词/标识符是什么\n" +
            "3. 确认补全必须从哪个位置开始（紧接光标位置，不包含光标前任何内容）\n" +
            "4. 检查光标后是否已有闭合括号，避免重复闭合\n" +
            "5. 生成补全代码\n\n" +
            "## 核心规则\n" +
            "1. 只输出光标位置之后需要补全的代码\n" +
            "2. 绝对不要重复光标前已有的任何内容——包括光标所在行光标前的内容\n" +
            "3. 补全是插入到光标位置的，所以从光标处开始续写\n" +
            "4. 补全长度1-5行，优先补全当前正在编写的语句到自然结束\n" +
            "5. 保持与上下文完全一致的代码风格（缩进宽度、命名规范、大括号位置等）\n" +
            "6. 不要添加任何注释、解释或说明\n\n" +
            "## ⚠️ 禁止重复（最重要，违反将导致代码错误）\n" +
            "补全文本会被直接插入光标位置，所以绝对不能包含光标前已有的任何字符：\n" +
            "- 光标在 `cin` 后 → 补全 ` >> a >> b;`（不要写cin）\n" +
            "- 光标在 `int a` 后 → 补全 `, b;` 或 ` = 0;`（不要写int a）\n" +
            "- 光标在 `cout <<` 后 → 补全 ` result << endl;`（不要写cout <<）\n" +
            "- 光标在 `for (` 后 → 补全 `int i = 0; i < n; i++)`（不要写for (）\n" +
            "- 光标在 `string s` 后 → 补全 ` = \"hello\";`（不要写string s）\n" +
            "- 光标在 `return` 后 → 补全 ` 0;` 或 ` result;`（不要写return）\n" +
            "- 如果光标前最后一个词是标识符的一部分，补全从该标识符之后开始\n\n" +
            "## ⚠️ 括号匹配规则（最高优先级）\n" +
            "- 所有开括号必须有对应的闭括号：{ }、[ ]、( )\n" +
            "- 如果光标前有未闭合的括号，补全必须包含对应的闭合括号\n" +
            "- 如果补全中新打开了括号，必须在补全范围内闭合\n" +
            "- 但不要重复闭合光标后已经存在的闭合括号\n" +
            "- 正确示例：光标前 `int main() {` 光标后 `return 0; }` → 补全 `\\n    ` (不需要加})\n" +
            "- 正确示例：光标前 `if (x > 0) {` 光标后无代码 → 补全必须包含 `}`\n\n" +
            "## ⚠️ 换行规则\n" +
            "- 在需要换行的地方必须换行，不要把多行代码压缩到一行\n" +
            "- 每个语句应该独占一行\n" +
            "- 左大括号 { 后面的代码必须换行\n" +
            "- 右大括号 } 必须独占一行（除非是空块 {}）\n" +
            "- if/else/for/while 等控制语句后的执行体必须换行缩进\n\n" +
            "## 代码语言\n%s\n\n" +
            "## 光标前的代码（光标在最后一行末尾）\n```\n%s\n```\n\n" +
            "## 光标后的代码（仅供参考，不要修改或重复）\n```\n%s\n```\n\n" +
            "## 返回格式（严格遵守）\n" +
            "直接返回纯JSON，不要用```包裹，不要添加任何其他文字：\n" +
            "{\"completion\": \"补全的代码\", \"isComplete\": true}\n\n" +
            "注意事项：\n" +
            "- completion字段中的换行用 \\n 表示\n" +
            "- completion字段中的双引号用 \\\" 表示\n" +
            "- completion字段中的反斜杠用 \\\\ 表示\n" +
            "- 不要在completion字段外包裹额外的引号\n" +
            "- 不要返回嵌套的JSON字符串\n" +
            "- completion字段的内容就是直接插入光标位置的文本，不要包含光标前已有的内容");
    }

    public String getRolePrompt(AiRole role) {
        String prompt = rolePromptTemplates.get(role);
        if (prompt == null) {
            log.warn("未找到角色 {} 的提示词模板，使用默认模板", role);
            return "你是一个专业的AI助手，请提供准确、有用和友好的帮助。";
        }
        return prompt;
    }

    public String getFunctionPrompt(String functionName, Object... args) {
        String template = functionPromptTemplates.get(functionName);
        if (template == null) {
            log.warn("未找到功能 {} 的提示词模板", functionName);
            return "";
        }
        try {
            return String.format(template, args);
        } catch (Exception e) {
            log.error("格式化提示词模板失败: function={}, args={}", functionName, args, e);
            return template;
        }
    }

    public String buildExplainPrompt(String language, String code) {
        return String.format(
            "请详细解释以下代码的功能和实现原理。\n" +
            "代码语言: %s\n" +
            "代码内容:\n%s",
            language, code
        );
    }

    public String buildReviewPrompt(String language, String code) {
        return String.format(
            "请对以下代码进行专业评审。\n" +
            "代码语言: %s\n" +
            "代码内容:\n%s",
            language, code
        );
    }

    public String buildQuestionerPrompt(String context) {
        if (context == null || context.isEmpty()) {
            context = "无";
        }
        return String.format(
            "你是一个智能学习引导者，请根据用户的学习情况提供个性化指导：\n\n" +
            "学习背景: %s\n\n" +
            "请基于以下对话历史，提出有针对性的问题引导用户深入学习：\n" +
            "你只需要给出必要的提示以及问答，不需要额外的解释。\n\n" +
            "输出格式要求（必须严格遵守）：\n" +
            "- 使用规范的Markdown格式，代码块必须带语言标识符，标题使用 # ## ### 层级\n" +
            "- 确保所有Markdown标记正确闭合\n" +
            "- 特殊符号（如 < > &）在非代码区域需正确处理\n" +
            "- 不要输出空代码块",
            context
        );
    }

    public String buildCompletionPrompt(String language, String prefixCode, String contextAfter) {
        String prompt = getFunctionPrompt("code_completion", language, prefixCode, contextAfter);
        
        String langLower = language != null ? language.toLowerCase() : "";
        String hint = "";
        
        if (langLower.equals("java") || langLower.equals("cpp") || langLower.equals("c")) {
            hint = "\n\n## 语言特定规则（C/C++/Java）\n" +
                "- 大括号匹配是最高优先级！所有 { 都必须有对应的 }\n" +
                "- 不要重复闭合光标后已有的 }\n" +
                "- 每条语句必须以分号 ; 结束\n" +
                "- 代码块 { } 内的代码必须缩进\n" +
                "- cin/cout/printf 等I/O语句要完整写完\n" +
                "- ⚠️ 禁止重复示例：\n" +
                "  光标在 `cin` 后面，光标后有 `return 0; }` → 补全 ` >> a >> b;`\n" +
                "  光标在 `cout <<` 后面，光标后有 `return 0; }` → 补全 ` result << endl;`\n" +
                "  光标在 `int ` 后面 → 补全 `x = 0;`（不要写 `int`）";
        } else if (langLower.equals("python")) {
            hint = "\n\n## 语言特定规则（Python）\n" +
                "- 使用缩进表示代码块，不用大括号\n" +
                "- 补全代码的缩进必须与上下文完全一致\n" +
                "- if/for/while/def/class 后的缩进体必须完整（至少一行）\n" +
                "- 冒号 : 后必须换行缩进\n" +
                "- 不要在行尾加分号";
        } else if (langLower.equals("sql")) {
            hint = "\n\n## 语言特定规则（SQL）\n" +
                "- 关键字大小写与上下文保持一致\n" +
                "- 括号必须成对闭合\n" +
                "- 语句结束处加逗号或分号视上下文而定";
        } else if (langLower.equals("javascript") || langLower.equals("typescript")) {
            hint = "\n\n## 语言特定规则（JavaScript/TypeScript）\n" +
                "- 大括号匹配是最高优先级！所有 { [ ( 都必须有对应的 } ] )\n" +
                "- 不要重复闭合光标后已有的括号\n" +
                "- 注意 async/await 和 Promise 链式调用\n" +
                "- 箭头函数的大括号必须闭合\n" +
                "- 语句以分号 ; 结束（如果上下文使用分号）";
        }
        
        return prompt + hint;
    }
}
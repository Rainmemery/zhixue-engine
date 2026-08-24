export const TOOL_DISPLAY_NAMES = {
  problem_generate: "题目生成",
  problem_recommend: "题目推荐",
  user_profile: "用户画像",
  difficulty_adapt: "难度适配",
  wrong_question: "错题分析",
  problem_validate: "题目验证",
  test_data_generate: "测试数据生成"
} as const;

export const TOOL_DESCRIPTIONS = {
  problem_generate: "AI正在为您精心设计题目",
  problem_recommend: "正在匹配最适合您的题目",
  user_profile: "正在分析您的学习特征",
  difficulty_adapt: "正在调整题目难度",
  wrong_question: "正在分析您的错题记录",
  problem_validate: "正在验证题目质量",
  test_data_generate: "正在生成测试数据"
} as const;

export type ToolName = keyof typeof TOOL_DISPLAY_NAMES;

export function getToolDisplayName(toolName?: string): string {
  if (toolName === undefined) return "工具";
  if (toolName in TOOL_DISPLAY_NAMES) {
    return TOOL_DISPLAY_NAMES[toolName as ToolName];
  }
  return toolName;
}

export function getToolDescription(toolName?: string): string {
  if (toolName === undefined) return "正在处理您的请求";
  if (toolName in TOOL_DESCRIPTIONS) {
    return TOOL_DESCRIPTIONS[toolName as ToolName];
  }
  return `${TOOL_DISPLAY_NAMES[toolName as ToolName] || toolName}中`;
}

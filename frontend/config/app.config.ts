/**
 * 应用配置文件
 * 集中管理应用级别的配置项
 */

export const appConfig = {
  // 应用基本信息
  app: {
    name: process.env.NEXT_PUBLIC_APP_NAME || "智学引擎",
    tagline: process.env.NEXT_PUBLIC_APP_TAGLINE || "AI驱动的智能学习平台",
    version: process.env.NEXT_PUBLIC_APP_VERSION || "1.0.0",
    description:
      process.env.NEXT_PUBLIC_APP_DESCRIPTION ||
      "智学引擎是一个AI驱动的智能学习平台，提供个性化学习路径、智能编程练习和AI辅助学习功能。",
  },

  // API配置
  api: {
    baseURL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1",
    timeout: parseInt(process.env.NEXT_PUBLIC_API_TIMEOUT || "30000"),
    retryAttempts: parseInt(process.env.NEXT_PUBLIC_API_RETRY_ATTEMPTS || "3"),
    retryDelay: parseInt(process.env.NEXT_PUBLIC_API_RETRY_DELAY || "1000"),
  },

  // 功能开关
  features: {
    enableRAG: process.env.NEXT_PUBLIC_ENABLE_RAG === "true",
    enableCodeExecution: process.env.NEXT_PUBLIC_ENABLE_CODE_EXECUTION === "true",
    enableStreamResponse: process.env.NEXT_PUBLIC_ENABLE_STREAM_RESPONSE !== "false",
    enableNotifications: process.env.NEXT_PUBLIC_ENABLE_NOTIFICATIONS !== "false",
  },

  // 流式响应配置
  streaming: {
    timeout: 300000, // 5分钟
    heartbeatInterval: 30000, // 30秒心跳
  },

  // 存储配置
  storage: {
    tokenKey: "token",
    refreshTokenKey: "refresh_token",
    userKey: "zhixue_user",
    languageKey: "zhixue_language",
    themeKey: "zhixue_theme",
  },

  // 分页配置
  pagination: {
    defaultPageSize: 10,
    pageSizeOptions: [10, 20, 50, 100],
  },

  // 对话配置
  conversation: {
    initialTimeout: 10 * 60 * 1000, // 10分钟
    followUpTimeout: 30 * 60 * 1000, // 30分钟
    maxMessageLength: 4000,
  },

  // 代码编辑器配置
  editor: {
    defaultLanguage: "javascript",
    supportedLanguages: [
      { value: "java", label: "Java", extension: "java" },
      { value: "python", label: "Python", extension: "py" },
      { value: "cpp", label: "C++", extension: "cpp" },
      { value: "c", label: "C", extension: "c" },
      { value: "sql", label: "SQL", extension: "sql" },
      { value: "javascript", label: "JavaScript", extension: "js" },
      { value: "typescript", label: "TypeScript", extension: "ts" },
    ],
  },
} as const;

export type AppConfig = typeof appConfig;

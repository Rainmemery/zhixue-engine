/**
 * API端点配置
 * 集中管理所有API端点路径
 */

export const apiEndpoints = {
  // 认证相关
  auth: {
    login: "/auth/login",
    register: "/auth/register",
    logout: "/auth/logout",
    refresh: "/auth/refresh",
    profile: "/auth/profile",
    validate: "/auth/validate",
    changePassword: "/auth/change-password",
  },

  // 用户相关
  user: {
    base: "/users",
    profile: "/users/profile",
    stats: "/users/stats",
    achievements: "/users/achievements",
    history: "/users/history",
  },

  // 题目相关
  problem: {
    base: "/problems",
    categories: "/problems/categories",
    tags: "/problems/tags",
    submit: "/problems/:id/submit",
    run: "/problems/:id/run",
  },

  // 学习路径相关
  learning: {
    base: "/learning-paths",
    progress: "/learning-paths/progress",
    recommend: "/learning-paths/recommend",
  },

  // AI相关
  ai: {
    chat: "/ai/stream-chat",
    explain: "/ai/explain",
    review: "/ai/review",
    questioner: "/ai/questioner",
    generate: "/ai/question",
    refactor: "/ai/refactor",
    complete: "/ai/complete",
    agentChat: "/ai/agent/chat",
    agentChatSync: "/ai/agent/chat-sync",
    agentChatAsync: "/ai/agent/chat-async",
    agentChatAsyncProgress: "/ai/agent/chat-async/{taskId}/progress",
    agentGenerationStatus: "/ai/agent/generation-status",
  },

  // 管理后台
  admin: {
    users: "/admin/users",
    problems: "/admin/problems",
    logs: "/admin/logs",
    stats: "/admin/stats",
    roles: "/admin/roles",
    permissions: "/admin/permissions",
  },

  // RAG 知识库
  rag: {
    base: "/rag",
    config: "/rag/config",
    modules: "/rag/config/modules",
    knowledge: "/rag/knowledge",
    knowledgeBase: "/rag/knowledge/:id",
    knowledgeEnabled: "/rag/knowledge/enabled",
    documents: "/rag/document",
    document: "/rag/document/:id",
    documentContent: "/rag/document/:id/content",
    documentReprocess: "/rag/document/:id/reprocess",
    upload: "/rag/document/upload",
    chunkInit: "/rag/document/chunk/init",
    chunkUpload: "/rag/document/chunk/upload",
    chunkComplete: "/rag/document/chunk/complete",
    retrievalSearch: "/rag/retrieval/search",
    retrievalContext: "/rag/retrieval/context",
    embeddingModel: "/rag/embedding-model",
    embeddingModelById: "/rag/embedding-model/:id",
    embeddingModelEnabled: "/rag/embedding-model/enabled",
    embeddingModelDefault: "/rag/embedding-model/default",
    embeddingModelValidate: "/rag/embedding-model/validate",
  },

  // 模型调度
  model: {
    instances: "/models/instances",
    groups: "/models/groups",
    scheduling: "/models/scheduling",
    monitoring: "/models/monitoring",
  },
} as const;

export type ApiEndpoints = typeof apiEndpoints;

/**
 * 构建带参数的API路径
 * @param path 路径模板
 * @param params 参数对象
 * @returns 替换后的路径
 */
export function buildPath(
  path: string,
  params: Record<string, string | number>
): string {
  let result = path;
  Object.entries(params).forEach(([key, value]) => {
    result = result.replace(`:${key}`, String(value));
  });
  return result;
}

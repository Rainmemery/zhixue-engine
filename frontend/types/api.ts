/**
 * API请求/响应专用类型定义
 */

import { z } from "zod";

// 通用API响应结构
export interface ApiResponse<T = unknown> {
  code: number;
  message?: string;
  msg?: string;
  data: T;
  timestamp: number;
  requestId?: string;
  errors?: Array<{
    field?: string;
    message: string;
  }>;
}

// 分页响应结构
export interface PaginatedResponse<T> {
  items: T[];
  pagination: {
    page: number;
    size: number;
    total: number;
    totalPages: number;
  };
}

// 分页请求参数
export interface PaginationParams {
  page?: number;
  size?: number;
}

// 列表响应结构（后端兼容格式）
export interface ListResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

// 请求状态
export type RequestState = "idle" | "loading" | "success" | "error";

// 请求错误
export interface RequestError {
  message: string;
  code?: number;
  field?: string;
}

// API端点类型
export interface ApiEndpoints {
  auth: {
    login: string;
    register: string;
    refresh: string;
    logout: string;
    validate: string;
    changePassword: string;
  };
  user: {
    base: string;
    profile: string;
    stats: string;
    achievements: string;
    history: string;
  };
  problem: {
    base: string;
    submit: string;
    diagnose: string;
  };
  ai: {
    explain: string;
    review: string;
    chat: string;
    questioner: string;
    refactor: string;
    generate: string;
  };
  learning: {
    records: string;
    paths: string;
    generatePath: string;
  };
  admin: {
    users: string;
    logs: string;
    stats: string;
    settings: string;
    backups: string;
  };
  rag: {
    knowledge: string;
    document: string;
    retrieval: string;
    config: string;
  };
  dispatcher: {
    groups: string;
    instances: string;
    monitor: string;
  };
}

// ==================== Zod Schemas ====================

// 用户Schema
export const UserSchema = z.object({
  id: z.number(),
  username: z.string(),
  email: z.string(),
  realName: z.string().optional().nullable(),
  avatarUrl: z.string().optional().nullable(),
  phone: z.string().optional().nullable(),
  school: z.string().optional().nullable(),
  major: z.string().optional().nullable(),
  grade: z.string().optional().nullable(),
  role: z.string(),
  learningLevel: z.number().default(1),
  experiencePoints: z.number().default(0),
  coins: z.number().default(0),
  achievementScore: z.number().default(0),
  dailyStreak: z.number().default(0),
  solvedProblems: z.number().default(0),
  learningPaths: z.number().default(0),
  dailyGoalMinutes: z.number().default(30),
  isActive: z.union([z.number(), z.boolean()]).default(1),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 题目Schema
export const ProblemSchema = z.object({
  id: z.number(),
  title: z.string(),
  titleEn: z.string().optional().nullable(),
  description: z.string(),
  difficulty: z.enum(["easy", "medium", "hard"]).or(z.string()),
  categoryId: z.number().optional().nullable(),
  category: z.string().optional().nullable(),
  tags: z.array(z.string()).default([]),
  initialCode: z.string().optional().nullable(),
  solutionCode: z.string().optional().nullable(),
  timeLimitMs: z.number().optional().nullable(),
  memoryLimitMb: z.number().optional().nullable(),
  acceptanceRate: z.number().optional().nullable(),
  likes: z.number().default(0),
  dislikes: z.number().default(0),
  isActive: z.boolean().default(true),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 学习记录Schema
export const LearningRecordSchema = z.object({
  id: z.number(),
  userId: z.number(),
  problemId: z.number(),
  submissionId: z.string().optional().nullable(),
  status: z.enum(["solved", "attempted", "unsolved"]),
  score: z.number().default(0),
  timeSpentMs: z.number().default(0),
  debugCount: z.number().default(0),
  attempts: z.number().default(0),
  firstSolvedAt: z.string().optional().nullable(),
  lastAttemptAt: z.string().optional().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 学习路径Schema
export const LearningPathSchema = z.object({
  id: z.union([z.number(), z.string()]),
  userId: z.number(),
  title: z.string(),
  description: z.string(),
  goal: z.string(),
  targetDays: z.number(),
  dailyMinutes: z.number(),
  focusAreas: z.union([z.array(z.string()), z.string()]).transform((val) => {
    if (typeof val === "string") {
      return val.split(",").map((s) => s.trim()).filter(Boolean);
    }
    return val;
  }).default([]),
  difficulty: z.enum(["beginner", "intermediate", "advanced", "expert"]),
  steps: z.array(z.object({
    stepNumber: z.number(),
    title: z.string(),
    description: z.string(),
    resources: z.array(z.object({
      type: z.enum(["article", "problem", "video"]),
      title: z.string(),
      url: z.string().optional(),
      problemId: z.number().optional(),
    })).default([]),
    estimatedTimeMinutes: z.number(),
    prerequisites: z.array(z.number()).default([]),
    status: z.enum(["pending", "completed", "skipped"]),
    completedAt: z.string().optional().nullable(),
    score: z.number().optional().nullable(),
    timeSpentMinutes: z.number().optional().nullable(),
  })).default([]),
  currentStep: z.number().default(0),
  progress: z.number().default(0),
  status: z.enum(["active", "completed", "paused", "cancelled"]),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 聊天消息Schema
export const ChatMessageSchema = z.object({
  id: z.string(),
  role: z.enum(["user", "assistant", "system"]),
  content: z.string(),
  timestamp: z.string(),
});

// 系统日志Schema
export const SystemLogSchema = z.object({
  id: z.number(),
  userId: z.number().optional().nullable(),
  username: z.string().optional().nullable(),
  action: z.string(),
  resource: z.string(),
  status: z.enum(["success", "failed"]),
  ipAddress: z.string().optional().nullable(),
  userAgent: z.string().optional().nullable(),
  details: z.string().optional().nullable(),
  createdAt: z.string(),
});

// Admin用户Schema
export const AdminUserSchema = z.object({
  id: z.number(),
  username: z.string(),
  email: z.string(),
  passwordHash: z.string().optional().nullable(),
  realName: z.string().optional().nullable(),
  avatarUrl: z.string().optional().nullable(),
  phone: z.string().optional().nullable(),
  school: z.string().optional().nullable(),
  major: z.string().optional().nullable(),
  grade: z.string().optional().nullable(),
  learningLevel: z.number().default(1),
  experiencePoints: z.number().default(0),
  coins: z.number().default(0),
  achievementScore: z.number().default(0),
  dailyStreak: z.number().default(0),
  lastActiveDate: z.string().optional().nullable(),
  isActive: z.union([z.number(), z.boolean()]).default(1),
  emailVerfied: z.number().optional().nullable(),
  phoneVerfied: z.number().optional().nullable(),
  createdAt: z.string(),
  updatedAt: z.string().optional().nullable(),
  deletedAt: z.string().optional().nullable(),
});

// 知识库Schema
export const KnowledgeBaseSchema = z.object({
  id: z.number(),
  name: z.string(),
  description: z.string(),
  embeddingModelId: z.number(),
  embeddingModelName: z.string(),
  embeddingDimension: z.number(),
  milvusCollection: z.string(),
  documentCount: z.number().default(0),
  chunkCount: z.number().default(0),
  status: z.boolean().default(true),
  ownerId: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 文档Schema
export const DocumentSchema = z.object({
  id: z.number(),
  knowledgeBaseId: z.number(),
  knowledgeBaseName: z.string(),
  title: z.string(),
  fileName: z.string(),
  fileType: z.string(),
  fileSize: z.number(),
  filePath: z.string(),
  chunkCount: z.number().default(0),
  status: z.number(),
  statusText: z.string(),
  errorMessage: z.string().optional().nullable(),
  createdBy: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 检索结果Schema
export const RetrievalResultSchema = z.object({
  query: z.string(),
  items: z.array(z.object({
    chunkId: z.number(),
    documentId: z.number(),
    knowledgeBaseId: z.number(),
    documentTitle: z.string(),
    knowledgeBaseName: z.string(),
    content: z.string(),
    similarity: z.number(),
    chunkIndex: z.number(),
  })).default([]),
  totalResults: z.number().default(0),
  searchTimeMs: z.number().default(0),
});

// 模型组Schema
export const ModelGroupSchema = z.object({
  id: z.number(),
  name: z.string(),
  modelType: z.enum(["OLLAMA", "OPENAI", "CUSTOM"]),
  description: z.string(),
  schedulingStrategy: z.enum(["ROUND_ROBIN", "WEIGHTED_ROUND_ROBIN", "LEAST_CONNECTIONS"]),
  defaultApiKey: z.string().optional().nullable(),
  defaultBaseUrl: z.string(),
  defaultTimeoutMs: z.number(),
  defaultMaxRetries: z.number(),
  priority: z.number(),
  enabled: z.boolean(),
  isDefault: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  totalInstances: z.number().optional(),
  healthyInstances: z.number().optional(),
  availableInstances: z.number().optional(),
});

// 模型实例Schema
export const ModelInstanceSchema = z.object({
  id: z.number(),
  groupId: z.number(),
  name: z.string(),
  apiEndpoint: z.string(),
  modelName: z.string(),
  apiKey: z.string().optional().nullable(),
  weight: z.number(),
  maxConcurrent: z.number(),
  currentConnections: z.number(),
  status: z.enum(["HEALTHY", "DEGRADED", "UNHEALTHY", "DISABLED"]),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// 导出类型
export type UserType = z.infer<typeof UserSchema>;
export type ProblemType = z.infer<typeof ProblemSchema>;
export type LearningRecordType = z.infer<typeof LearningRecordSchema>;
export type LearningPathType = z.infer<typeof LearningPathSchema>;
export type ChatMessageType = z.infer<typeof ChatMessageSchema>;
export type SystemLogType = z.infer<typeof SystemLogSchema>;
export type AdminUserType = z.infer<typeof AdminUserSchema>;
export type KnowledgeBaseType = z.infer<typeof KnowledgeBaseSchema>;
export type DocumentType = z.infer<typeof DocumentSchema>;
export type RetrievalResultType = z.infer<typeof RetrievalResultSchema>;
export type ModelGroupType = z.infer<typeof ModelGroupSchema>;
export type ModelInstanceType = z.infer<typeof ModelInstanceSchema>;

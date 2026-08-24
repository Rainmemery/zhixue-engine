/**
 * 服务层导出文件
 * 统一导出所有服务
 */

// API客户端
export { default as apiClient, createCancelToken, getBaseURL, getFullURL } from "./api";
export type { ApiResponse, RequestState } from "./api";

// AI服务
export { aiService, processSSEEvent, parseSSEStream } from "./aiService";

// 用户服务
export type { User, UserStats, LearningHistory, Achievement } from "./userService";
export { userService } from "./userService";

// 题目服务
export type {
  Problem,
  ProblemExample,
  Submission,
  ProblemCategory,
  ProblemTag,
} from "./problemService";
export { problemService } from "./problemService";

// 学习服务
export type { LearningPathStep, LearningProgress } from "./learningService";
export { learningService } from "./learningService";

// 管理服务
export type {
  AdminUser,
  CreateUserRequest,
  UpdateUserRequest,
  SystemLog,
  SystemStats,
  SystemMetrics,
  BackupRecord,
  PaginationResponse,
} from "./adminService";
export { AdminService, adminService } from "./adminService";

// 认证服务
export { authService } from "./authService";

// RAG服务
export {
  ragService,
  embeddingModelService,
  knowledgeService,
  knowledgeBaseService,
  documentService,
  retrievalService,
  configService,
  ragConfigService,
} from "./ragService";
export type {
  EmbeddingModel,
  KnowledgeBase,
  Document,
  ContentPage,
  SearchMatch,
  DocumentContent,
  RetrievalRequest,
  RetrievalItem,
  RetrievalResult,
  RagGlobalConfig,
  ModuleConfig,
  ChunkUploadInitRequest,
  ChunkUploadInitResponse,
  ChunkUploadProgress,
  ChunkUploadCompleteResponse,
} from "./ragService";

// 调度服务
export {
  dispatcherService,
  modelGroupService,
  modelInstanceService,
  schedulingMonitorService,
} from "./dispatcherService";
export type {
  ModelGroupCreateRequest,
  ModelGroupUpdateRequest,
  ModelInstanceCreateRequest,
  ModelInstanceUpdateRequest,
  InstanceHealth,
  SchedulingStatus,
  SchedulingLog,
  SchedulingStatistics,
  FailoverStatus,
  SchedulingStrategy,
  LogQueryParams,
} from "./dispatcherService";

// 数据转换工具
export {
  safeParseJSON,
  ensureArray,
  withDefault,
  safeString,
  safeNumber,
  safeBoolean,
  safeDate,
  parseCommaSeparated,
  validateData,
  safeExecute,
  transformPaginatedResponse,
  transformApiResponse,
  deepClone,
  cleanObject,
} from "@/lib/dataTransform";
export type { ValidationResult } from "@/lib/dataTransform";

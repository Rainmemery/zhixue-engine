/**
 * RAG服务层
 * 封装所有RAG相关的API调用，包括知识库、文档管理、检索等功能
 * 参照旧项目实现，统一接口定义和命名空间组织
 */

import apiClient from "./api";
import { apiEndpoints } from "@/config/api.config";
import {
  KnowledgeBaseType,
  DocumentType,
  RetrievalResultType,
  KnowledgeBaseSchema,
  DocumentSchema,
  RetrievalResultSchema,
} from "@/types/api";
import {
  ensureArray,
  validateData,
  transformPaginatedResponse,
} from "@/lib/dataTransform";

// ==================== 接口定义 ====================

/**
 * 知识库接口
 */
export interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
  embeddingModelId: number;
  embeddingModelName: string;
  embeddingDimension: number;
  milvusCollection: string;
  documentCount: number;
  chunkCount: number;
  status: boolean;
  ownerId: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 文档接口
 */
export interface Document {
  id: number;
  knowledgeBaseId: number;
  knowledgeBaseName: string;
  title: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  filePath: string;
  chunkCount: number;
  status: number;
  statusText: string;
  errorMessage: string;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 内容页接口
 */
export interface ContentPage {
  pageNumber: number;
  content: string;
  startOffset: number;
  endOffset: number;
}

/**
 * 搜索匹配接口
 */
export interface SearchMatch {
  pageNumber: number;
  startOffset: number;
  endOffset: number;
  context: string;
}

/**
 * 文档内容接口
 */
export interface DocumentContent {
  id: number;
  title: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  fullContent: string | null;
  pages: ContentPage[];
  totalPages: number;
  currentPage: number;
  pageSize: number;
  searchKeyword: string | null;
  searchMatches: SearchMatch[] | null;
  totalMatches: number;
}

/**
 * 检索请求接口
 */
export interface RetrievalRequest {
  query: string;
  knowledgeBaseIds: number[];
  topK?: number;
  similarityThreshold?: number;
  includeContent?: boolean;
}

/**
 * 检索结果项接口
 */
export interface RetrievalItem {
  chunkId: number;
  documentId: number;
  knowledgeBaseId: number;
  documentTitle: string;
  knowledgeBaseName: string;
  content: string;
  similarity: number;
  chunkIndex: number;
}

/**
 * 检索结果接口
 */
export interface RetrievalResult {
  query: string;
  items: RetrievalItem[];
  totalResults: number;
  searchTimeMs: number;
}

/**
 * RAG全局配置接口
 */
export interface RagGlobalConfig {
  rag_enabled: boolean;
  retrieval_config: {
    similarityThreshold: number;
    topK: number;
    maxContextLength: number;
  };
  embedding_config: {
    defaultModelId: number;
    allowModelSwitch: boolean;
    ollamaBaseUrl: string;
    timeout: number;
    parallelism: number;
    defaultModel: string;
    maxTextLength: number;
    dimension: number | null;
  };
  chunk_config: {
    chunkSize: number;
    chunkOverlap: number;
  };
  file_config: {
    uploadPath: string;
    maxSize: number;
    allowedTypes: string[];
  };
}

/**
 * 模块配置接口
 */
export interface ModuleConfig {
  enabled: boolean;
  knowledgeBaseIds: number[];
}

/**
 * 分片上传初始化请求接口
 */
export interface ChunkUploadInitRequest {
  fileName: string;
  fileType: string;
  fileSize: number;
  totalChunks: number;
  chunkSize: number;
  knowledgeBaseId: number;
  title?: string;
  userId?: number;
}

/**
 * 分片上传初始化响应接口
 */
export interface ChunkUploadInitResponse {
  success: boolean;
  message: string;
  uploadId: string;
  chunkSize: number;
  totalChunks: number;
}

/**
 * 分片上传进度接口
 */
export interface ChunkUploadProgress {
  success: boolean;
  message: string;
  uploadId: string;
  chunkIndex: number;
  totalChunks: number;
  uploadedChunks: number;
  completed: boolean;
  progress: number;
  missingChunks: number[];
}

/**
 * 分片上传完成响应接口
 */
export interface ChunkUploadCompleteResponse {
  success: boolean;
  message: string;
  uploadId: string;
  completed: boolean;
  document: Document | null;
}

/**
 * Embedding模型接口
 */
export interface EmbeddingModel {
  id: number;
  modelName: string;
  displayName: string;
  dimension: number;
  description?: string;
  isDefault: boolean;
  status: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 创建Embedding模型请求接口
 */
export interface EmbeddingModelCreateRequest {
  modelName: string;
  displayName: string;
  dimension: number;
  description?: string;
  status?: boolean;
}

/**
 * 更新Embedding模型请求接口
 */
export interface EmbeddingModelUpdateRequest {
  displayName?: string;
  description?: string;
  status?: boolean;
}

/**
 * Embedding模型验证结果接口
 */
export interface EmbeddingModelValidateResult {
  modelName: string;
  available: boolean;
  dimension?: number;
  message: string;
}

// ==================== 常量定义 ====================

// 分片上传配置 (5MB)
const CHUNK_SIZE = 5 * 1024 * 1024;

// ==================== 命名空间: embeddingModel ====================

/**
 * Embedding 模型管理服务
 */
export const embeddingModelService = {
  /**
   * 获取所有 Embedding 模型列表
   */
  list: async (): Promise<EmbeddingModel[]> => {
    const response = await apiClient.get(apiEndpoints.rag.embeddingModel + "/list");
    return ensureArray<EmbeddingModel>(response.data?.data, []);
  },

  /**
   * 获取启用的 Embedding 模型列表
   */
  listEnabled: async (): Promise<EmbeddingModel[]> => {
    const response = await apiClient.get(apiEndpoints.rag.embeddingModelEnabled);
    return ensureArray<EmbeddingModel>(response.data?.data, []);
  },

  /**
   * 根据 ID 获取 Embedding 模型
   */
  getById: async (id: number): Promise<EmbeddingModel | null> => {
    const response = await apiClient.get(apiEndpoints.rag.embeddingModelById.replace(":id", id.toString()));
    return response.data?.data || null;
  },

  /**
   * 获取默认 Embedding 模型
   */
  getDefault: async (): Promise<EmbeddingModel | null> => {
    const response = await apiClient.get(apiEndpoints.rag.embeddingModelDefault);
    return response.data?.data || null;
  },

  /**
   * 创建 Embedding 模型
   */
  create: async (data: EmbeddingModelCreateRequest): Promise<EmbeddingModel> => {
    const response = await apiClient.post(apiEndpoints.rag.embeddingModel, data);
    return response.data?.data;
  },

  /**
   * 更新 Embedding 模型
   */
  update: async (id: number, data: EmbeddingModelUpdateRequest): Promise<EmbeddingModel> => {
    const response = await apiClient.put(apiEndpoints.rag.embeddingModelById.replace(":id", id.toString()), data);
    return response.data?.data;
  },

  /**
   * 删除 Embedding 模型
   */
  delete: async (id: number): Promise<void> => {
    await apiClient.delete(apiEndpoints.rag.embeddingModelById.replace(":id", id.toString()));
  },

  /**
   * 设置默认 Embedding 模型
   */
  setDefault: async (id: number): Promise<void> => {
    await apiClient.post(apiEndpoints.rag.embeddingModelById.replace(":id", id.toString()) + "/set-default");
  },

  /**
   * 启用/禁用 Embedding 模型
   */
  toggle: async (id: number, enabled: boolean): Promise<void> => {
    await apiClient.post(apiEndpoints.rag.embeddingModelById.replace(":id", id.toString()) + `/toggle?enabled=${enabled}`);
  },

  /**
   * 验证 Embedding 模型可用性
   */
  validate: async (modelName: string): Promise<EmbeddingModelValidateResult> => {
    const response = await apiClient.post(apiEndpoints.rag.embeddingModelValidate, { modelName });
    return response.data?.data || { modelName, available: false, message: "验证失败" };
  },
};

// ==================== 命名空间: knowledge ====================

/**
 * 知识库管理服务
 */
export const knowledgeService = {
  /**
   * 获取知识库列表
   * GET /rag/knowledge
   */
  list: async (keyword?: string, page = 1, size = 10) => {
    const response = await apiClient.get(apiEndpoints.rag.knowledge, {
      params: { keyword, page, size },
    });
    const result = transformPaginatedResponse<KnowledgeBase, KnowledgeBase>(
      response.data?.data,
      (item) => {
        const validated = validateData(KnowledgeBaseSchema, item);
        return validated.success ? (validated.data as unknown as KnowledgeBase) : item;
      }
    );
    return result;
  },

  /**
   * 获取启用的知识库列表
   */
  listEnabled: async (): Promise<KnowledgeBase[]> => {
    const response = await apiClient.get(apiEndpoints.rag.knowledgeEnabled);
    return ensureArray<KnowledgeBase>(response.data?.data, []);
  },

  /**
   * 根据 ID 获取知识库
   */
  getById: async (id: number): Promise<KnowledgeBase | null> => {
    const response = await apiClient.get(apiEndpoints.rag.knowledgeBase.replace(":id", id.toString()));
    const data = response.data?.data;
    if (!data) return null;
    const validated = validateData(KnowledgeBaseSchema, data);
    return validated.success && validated.data 
      ? (validated.data as unknown as KnowledgeBase) 
      : null;
  },

  /**
   * 创建知识库
   * POST /rag/knowledge
   */
  create: async (data: Partial<KnowledgeBase>): Promise<KnowledgeBase> => {
    const response = await apiClient.post(apiEndpoints.rag.knowledge, data);
    return response.data?.data;
  },

  /**
   * 更新知识库
   * PUT /rag/knowledge/{id}
   */
  update: async (id: number, data: Partial<KnowledgeBase>): Promise<KnowledgeBase> => {
    const response = await apiClient.put(apiEndpoints.rag.knowledgeBase.replace(":id", id.toString()), data);
    return response.data?.data;
  },

  /**
   * 删除知识库
   * DELETE /rag/knowledge/{id}
   */
  delete: async (id: number): Promise<void> => {
    await apiClient.delete(apiEndpoints.rag.knowledgeBase.replace(":id", id.toString()));
  },

  /**
   * 启用/禁用知识库
   * POST /rag/knowledge/{id}/toggle
   */
  toggle: async (id: number, enabled: boolean): Promise<void> => {
    await apiClient.post(apiEndpoints.rag.knowledgeBase.replace(":id", id.toString()) + `/toggle?enabled=${enabled}`);
  },
};

// ==================== 命名空间: document ====================

/**
 * 文档管理服务
 */
export const documentService = {
  /**
   * 获取文档列表
   * GET /rag/document
   */
  list: async (knowledgeBaseId: number, page = 1, size = 10) => {
    const response = await apiClient.get(apiEndpoints.rag.documents, {
      params: { knowledgeBaseId, page, size },
    });
    return transformPaginatedResponse<Document, Document>(
      response.data?.data,
      (item) => {
        const validated = validateData(DocumentSchema, item);
        return validated.success ? (validated.data as unknown as Document) : item;
      }
    );
  },

  /**
   * 根据 ID 获取文档
   */
  getById: async (id: number): Promise<Document | null> => {
    const response = await apiClient.get(apiEndpoints.rag.document.replace(":id", id.toString()));
    const data = response.data?.data;
    if (!data) return null;
    const validated = validateData(DocumentSchema, data);
    return validated.success && validated.data 
      ? (validated.data as unknown as Document) 
      : null;
  },

  /**
   * 上传文档
   * POST /rag/document/upload
   */
  upload: async (knowledgeBaseId: number, file: File, title?: string, userId?: number): Promise<Document> => {
    const formData = new FormData();
    formData.append("knowledgeBaseId", knowledgeBaseId.toString());
    formData.append("file", file);
    if (title) formData.append("title", title);
    if (userId) formData.append("userId", userId.toString());

    const response = await apiClient.post(apiEndpoints.rag.upload, formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return response.data?.data;
  },

  /**
   * 删除文档
   * DELETE /rag/document/{id}
   */
  delete: async (id: number): Promise<void> => {
    await apiClient.delete(apiEndpoints.rag.document.replace(":id", id.toString()));
  },

  /**
   * 重新处理文档
   * POST /rag/document/{id}/reprocess
   */
  reprocess: async (id: number): Promise<void> => {
    await apiClient.post(apiEndpoints.rag.documentReprocess.replace(":id", id.toString()));
  },

  /**
   * 获取文档内容
   */
  getContent: async (
    id: number,
    page?: number,
    pageSize?: number,
    search?: string
  ): Promise<DocumentContent | null> => {
    const params: Record<string, string> = {};
    if (page) params.page = page.toString();
    if (pageSize) params.pageSize = pageSize.toString();
    if (search) params.search = search;

    const response = await apiClient.get(apiEndpoints.rag.documentContent.replace(":id", id.toString()), { params });
    return response.data?.data || null;
  },

  // ==================== 分片上传相关方法 ====================

  /**
   * 初始化分片上传
   */
  initChunkUpload: async (request: ChunkUploadInitRequest): Promise<ChunkUploadInitResponse> => {
    const response = await apiClient.post("/rag/document/chunk/init", request);
    return response.data?.data;
  },

  /**
   * 上传单个分片
   */
  uploadChunk: async (uploadId: string, chunkIndex: number, chunk: Blob): Promise<ChunkUploadProgress> => {
    const formData = new FormData();
    formData.append("uploadId", uploadId);
    formData.append("chunkIndex", chunkIndex.toString());
    formData.append("chunk", chunk);

    const response = await apiClient.post("/rag/document/chunk/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    return response.data?.data;
  },

  /**
   * 完成分片上传
   */
  completeChunkUpload: async (uploadId: string): Promise<ChunkUploadCompleteResponse> => {
    const response = await apiClient.post(`/rag/document/chunk/complete?uploadId=${uploadId}`);
    return response.data?.data;
  },

  /**
   * 获取上传进度
   */
  getUploadProgress: async (uploadId: string): Promise<ChunkUploadProgress> => {
    const response = await apiClient.get(`/rag/document/chunk/progress?uploadId=${uploadId}`);
    return response.data?.data;
  },

  /**
   * 取消分片上传
   */
  cancelChunkUpload: async (uploadId: string): Promise<void> => {
    await apiClient.delete(`/rag/document/chunk/cancel?uploadId=${uploadId}`);
  },

  /**
   * 使用分片上传方式上传文件（完整流程）
   */
  uploadWithChunks: async (
    knowledgeBaseId: number,
    file: File,
    title?: string,
    userId?: number,
    onProgress?: (progress: number, uploadedChunks: number, totalChunks: number) => void
  ): Promise<Document> => {
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
    const fileType = file.name.split(".").pop()?.toUpperCase() || "UNKNOWN";

    const initResponse = await documentService.initChunkUpload({
      fileName: file.name,
      fileType,
      fileSize: file.size,
      totalChunks,
      chunkSize: CHUNK_SIZE,
      knowledgeBaseId,
      title,
      userId,
    });

    if (!initResponse.success) {
      throw new Error(initResponse.message || "初始化分片上传失败");
    }

    const uploadId = initResponse.uploadId;

    for (let i = 0; i < totalChunks; i++) {
      const start = i * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      const chunk = file.slice(start, end);

      const progressResponse = await documentService.uploadChunk(uploadId, i, chunk);

      if (!progressResponse.success) {
        throw new Error(progressResponse.message || `上传分片 ${i + 1} 失败`);
      }

      if (onProgress) {
        onProgress(progressResponse.progress, progressResponse.uploadedChunks, totalChunks);
      }
    }

    const completeResponse = await documentService.completeChunkUpload(uploadId);

    if (!completeResponse.success || !completeResponse.document) {
      throw new Error(completeResponse.message || "完成上传失败");
    }

    return completeResponse.document;
  },
};

// ==================== 命名空间: retrieval ====================

/**
 * 检索服务
 */
export const retrievalService = {
  /**
   * 执行检索
   * POST /rag/retrieval/search
   */
  search: async (request: RetrievalRequest): Promise<RetrievalResult> => {
    const response = await apiClient.post("/rag/retrieval/search", {
      ...request,
      topK: request.topK || 5,
      similarityThreshold: request.similarityThreshold || 0.7,
    });
    const data = response.data?.data;
    const validated = validateData(RetrievalResultSchema, data);
    return validated.success 
      ? (validated.data as unknown as RetrievalResult) 
      : data;
  },

  /**
   * 构建检索上下文
   */
  buildContext: async (request: RetrievalRequest): Promise<string> => {
    const response = await apiClient.post("/rag/retrieval/context", request);
    return response.data?.data || "";
  },
};

// ==================== 命名空间: config ====================

/**
 * RAG配置服务
 */
export const configService = {
  /**
   * 获取全局配置
   * GET /rag/config/global
   */
  getGlobal: async (): Promise<RagGlobalConfig | null> => {
    const response = await apiClient.get("/rag/config/global");
    return response.data?.data || null;
  },

  /**
   * 更新全局配置
   * PUT /rag/config/global
   */
  updateGlobal: async (config: Partial<RagGlobalConfig>): Promise<RagGlobalConfig> => {
    const response = await apiClient.put("/rag/config/global", config);
    return response.data?.data;
  },

  /**
   * 启用/禁用RAG功能
   */
  toggleRag: async (enabled: boolean): Promise<void> => {
    await apiClient.post(`/rag/config/global/toggle?enabled=${enabled}`);
  },

  /**
   * 获取所有模块配置
   */
  getModules: async (): Promise<Record<string, ModuleConfig>> => {
    const response = await apiClient.get("/rag/config/modules");
    const data = response.data?.data;
    if (Array.isArray(data)) {
      const result: Record<string, ModuleConfig> = {};
      data.forEach((item: { moduleCode?: string; config?: ModuleConfig }) => {
        if (item.moduleCode) {
          result[item.moduleCode] = item.config || { enabled: false, knowledgeBaseIds: [] };
        }
      });
      return result;
    }
    if (data && typeof data === "object" && !Array.isArray(data)) {
      return data as Record<string, ModuleConfig>;
    }
    return {};
  },

  /**
   * 获取指定模块配置
   */
  getModule: async (moduleCode: string): Promise<ModuleConfig | null> => {
    const response = await apiClient.get(`/rag/config/modules/${moduleCode}`);
    const data = response.data?.data;
    if (data && typeof data === "object") {
      if (data.config && typeof data.config === "object") {
        return data.config as ModuleConfig;
      }
      if (typeof data.enabled === "boolean") {
        return data as ModuleConfig;
      }
    }
    return null;
  },

  /**
   * 更新模块配置
   */
  updateModule: async (moduleCode: string, config: ModuleConfig): Promise<ModuleConfig> => {
    const response = await apiClient.put(`/rag/config/modules/${moduleCode}`, config);
    return response.data?.data;
  },
};

// ==================== 统一服务导出 ====================

/**
 * RAG统一服务
 * 通过命名空间组织各类RAG相关功能
 */
export const ragService = {
  /** Embedding模型管理 */
  embeddingModel: embeddingModelService,
  /** 知识库管理 */
  knowledge: knowledgeService,
  /** 文档管理 */
  document: documentService,
  /** 检索功能 */
  retrieval: retrievalService,
  /** 配置管理 */
  config: configService,
};

// 兼容旧版本导出（保持向后兼容）
export const knowledgeBaseService = knowledgeService;
export const ragConfigService = configService;

export default ragService;

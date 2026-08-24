/**
 * AI服务层
 * 封装所有AI相关的API调用，包括流式响应处理
 * 与旧项目保持一致的实现
 */

import { apiEndpoints } from "@/config/api.config";
import { appConfig } from "@/config/app.config";
import apiClient, { ApiResponse } from "./api";
import { processSSEEvent, parseSSEData } from "@/lib/sseParser";
import axios from "axios";

export interface CompletionContext {
  code: string;
  language: string;
  cursorLine: number;
  cursorColumn: number;
  contextBefore?: string;
  contextAfter?: string;
  fullCode?: string;
  totalLines?: number;
}

export interface CompletionResult {
  success: boolean;
  data?: {
    completion: string;
    language: string;
    isComplete: boolean;
  };
  error?: string;
}

const getToken = (): string | null => {
  if (typeof window !== "undefined") {
    return localStorage.getItem(appConfig.storage.tokenKey);
  }
  return null;
};

// 流式超时时间（5分钟）
const STREAM_TIMEOUT = appConfig.streaming.timeout;

/**
 * 创建带超时的fetch请求
 * @param timeout 超时时间（毫秒）
 * @returns fetch函数
 */
function createFetchWithTimeout(timeout: number) {
  return async (url: string, options: RequestInit): Promise<Response> => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
      });
      return response;
    } finally {
      clearTimeout(timeoutId);
    }
  };
}

const fetchWithTimeout = createFetchWithTimeout(STREAM_TIMEOUT);

/**
 * SSE事件处理器类型
 * 与参考实现保持一致的简洁版本
 */
export interface SSEEventHandlers {
  onText?: (delta: string) => void;
  onComplete?: (content?: string) => void;
  onThinking?: (thinking: string) => void;
  onToolCall?: (toolName: string, toolArgs: Record<string, unknown>, data: Record<string, unknown>) => void;
  onToolResult?: (toolName: string, success: boolean, data: Record<string, unknown>, fullData: Record<string, unknown>) => void;
  onError?: (error: Error) => void;
}

export interface MessageSegment {
  type: 'text' | 'tool_call' | 'tool_result';
  content?: string;
  toolName?: string;
  displayName?: string;
  status?: 'running' | 'completed' | 'failed';
  success?: boolean;
  data?: Record<string, unknown>;
  sequenceNumber?: number;
  checksum?: string;
  sendStatus?: string;
  retryCount?: number;
  timestamp?: number;
}

export interface ProblemInfo {
  problemId?: number;
  title: string;
  difficulty: string;
  isRecommended: boolean;
  saveFailed: boolean;
  saveError?: string;
  acceptanceRate?: number;
  problemType?: string;
}

export interface AgentChatSyncResult {
  success: boolean;
  error?: string;
  segments: MessageSegment[];
  problems: ProblemInfo[];
}

export interface AgentTaskCreateResult {
  taskId: string;
  status: string;
}

export interface AgentTaskProgress {
  taskId: string;
  status: string;
  newSegments: MessageSegment[];
  allProblems: ProblemInfo[];
  totalSegments: number;
  completed: boolean;
  error: string | null;
}

export interface AcknowledgmentResult {
  success: boolean;
  taskId: string;
  sequenceNumber?: number;
  acknowledgedCount?: number;
  failedNumbers?: number[];
  timestamp: number;
  error?: string;
}

export interface UnacknowledgedSegmentsResult {
  success: boolean;
  taskId: string;
  segments: MessageSegment[];
  stats: {
    totalSegments: number;
    acknowledgedCount: number;
    sentCount: number;
    pendingCount: number;
    failedCount: number;
  };
  timestamp: number;
  error?: string;
}

export interface RecoveryResult {
  success: boolean;
  taskId: string;
  fromSequenceNumber: number;
  segments: MessageSegment[];
  totalSegments: number;
  recoveredCount: number;
  skippedCount: number;
  timestamp: number;
  error?: string;
}

export interface RecoveryStatusResult {
  success: boolean;
  taskId: string;
  acknowledgedIndex: number;
  totalSegments: number;
  acknowledgedCount: number;
  unacknowledgedCount: number;
  unacknowledgedSegments: MessageSegment[];
  canRecover: boolean;
  timestamp: number;
  error?: string;
}

/**
 * 解析SSE流数据 - 使用健壮的JSON解析
 * 与参考实现保持一致的简洁版本
 * @param reader ReadableStreamDefaultReader
 * @param handlers 事件处理器
 */
export async function parseSSEStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  handlers: SSEEventHandlers
): Promise<void> {
  const decoder = new TextDecoder("utf-8", { stream: true } as TextDecoderOptions);
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const decodedChunk = decoder.decode(value, { stream: true });
      buffer += decodedChunk;

      // 处理SSE事件边界（双换行分隔）
      let boundaryIndex;
      while ((boundaryIndex = buffer.indexOf("\n\n")) !== -1) {
        const eventChunk = buffer.substring(0, boundaryIndex).trim();
        buffer = buffer.substring(boundaryIndex + 2);

        let jsonData = "";
        if (eventChunk.startsWith("data:data: ")) {
          jsonData = eventChunk.substring(11).trim();
          // 处理嵌套data:前缀
          while (jsonData.startsWith("data: ")) {
            jsonData = jsonData.substring(6).trim();
          }
        } else if (eventChunk.startsWith("data: ")) {
          jsonData = eventChunk.substring(6).trim();
        } else if (eventChunk.startsWith("data:")) {
          jsonData = eventChunk.substring(5).trim();
        } else {
          continue;
        }

        if (!jsonData || jsonData === "{}" || jsonData === "") {
          continue;
        }

        // 使用健壮的SSE事件处理
        processSSEEvent(jsonData, {
          onText: (delta) => {
            if (handlers.onText) {
              handlers.onText(delta);
            }
          },
          onComplete: (content) => {
            if (handlers.onComplete) {
              handlers.onComplete(content);
            }
          },
          onThinking: (thinking) => {
            if (handlers.onThinking) {
              handlers.onThinking(thinking);
            }
          },
          onToolCall: (toolName, toolArgs, data) => {
            if (handlers.onToolCall) {
              handlers.onToolCall(toolName, toolArgs, data);
            }
          },
          onToolResult: (toolName, success, data, fullData) => {
            if (handlers.onToolResult) {
              handlers.onToolResult(toolName, success, data, fullData);
            }
          },
          onError: (errorMessage) => {
            if (handlers.onError) {
              handlers.onError(new Error(errorMessage));
            }
          },
        });
      }
    }

    // 处理残留数据
    if (buffer.trim()) {
      const trimmedBuffer = buffer.trim();
      let jsonData = "";

      if (trimmedBuffer.startsWith("data:data: ")) {
        jsonData = trimmedBuffer.substring(11).trim();
      } else if (trimmedBuffer.startsWith("data: ")) {
        jsonData = trimmedBuffer.substring(6).trim();
      } else if (trimmedBuffer.startsWith("data:")) {
        jsonData = trimmedBuffer.substring(5).trim();
      } else {
        jsonData = trimmedBuffer;
      }

      if (jsonData && jsonData !== "{}") {
        processSSEEvent(jsonData, {
          onText: (delta) => {
            if (handlers.onText) {
              handlers.onText(delta);
            }
          },
          onComplete: (content) => {
            if (handlers.onComplete) {
              handlers.onComplete(content);
            }
          },
          onToolCall: (toolName, toolArgs, data) => {
            if (handlers.onToolCall) {
              handlers.onToolCall(toolName, toolArgs, data);
            }
          },
          onToolResult: (toolName, success, data, fullData) => {
            if (handlers.onToolResult) {
              handlers.onToolResult(toolName, success, data, fullData);
            }
          },
        });
      }
    }
  } catch (streamError) {
    console.error("[SSE] 流式读取错误:", streamError);
    if (handlers.onError) {
      handlers.onError(streamError as Error);
    }
  }
}

// 导出SSE解析工具
export { processSSEEvent, parseSSEData };

/**
 * AI服务
 */
export const aiService = {
  /**
   * 流式代码解释
   * @param code 代码内容
   * @param language 编程语言
   * @param selection 选中代码范围
   * @param explanationType 解释类型
   * @param detailLevel 详细程度
   * @returns ReadableStreamDefaultReader
   */
  streamExplainCode: async (
    code: string,
    language: string,
    selection?: { startLine: number; endLine: number; selectedText: string },
    explanationType?: string,
    detailLevel?: string
  ): Promise<ReadableStreamDefaultReader<Uint8Array>> => {
    const body: Record<string, unknown> = {
      code,
      language,
      explanationType: explanationType || "algorithm_steps",
      detailLevel: detailLevel || "detailed",
      stream: true,
    };

    if (selection) {
      body.selection = selection;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}${apiEndpoints.ai.explain}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          Accept: "text/event-stream",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    if (!response.body) {
      throw new Error("Response body is null");
    }

    return response.body.getReader();
  },

  /**
   * 流式代码评审
   * @param code 代码内容
   * @param language 编程语言
   * @param selection 选中代码范围
   * @param reviewType 评审类型
   * @param includeSuggestions 是否包含建议
   * @returns ReadableStreamDefaultReader
   */
  streamReviewCode: async (
    code: string,
    language: string,
    selection?: { startLine: number; endLine: number; selectedText: string },
    reviewType?: string,
    includeSuggestions?: boolean
  ): Promise<ReadableStreamDefaultReader<Uint8Array>> => {
    const body: Record<string, unknown> = {
      code,
      language,
      reviewType: reviewType || "comprehensive",
      includeSuggestions: includeSuggestions !== false,
      stream: true,
    };

    if (selection) {
      body.selection = selection;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}${apiEndpoints.ai.review}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          Accept: "text/event-stream",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    if (!response.body) {
      throw new Error("Response body is null");
    }

    return response.body.getReader();
  },

  /**
   * 流式AI对话
   * @param messages 消息历史
   * @param role AI角色
   * @param temperature 温度参数
   * @param maxTokens 最大token数
   * @param ragOptions RAG选项
   * @returns ReadableStreamDefaultReader
   */
  streamChat: async (
    messages: Array<{ role: string; content: string }>,
    role?: string,
    temperature?: number,
    maxTokens?: number,
    ragOptions?: {
      moduleCode?: string;
      knowledgeBaseIds?: number[];
      enableRag?: boolean;
    }
  ): Promise<ReadableStreamDefaultReader<Uint8Array>> => {
    const body: Record<string, unknown> = {
      messages,
      stream: true,
    };

    if (role) body.role = role;
    if (temperature !== undefined) body.temperature = temperature;
    if (maxTokens !== undefined) body.maxTokens = maxTokens;
    if (ragOptions) {
      if (ragOptions.moduleCode) body.moduleCode = ragOptions.moduleCode;
      if (ragOptions.knowledgeBaseIds) body.knowledgeBaseIds = ragOptions.knowledgeBaseIds;
      if (ragOptions.enableRag !== undefined) body.enableRag = ragOptions.enableRag;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}${apiEndpoints.ai.chat}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          Accept: "text/event-stream",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    if (!response.body) {
      throw new Error("Response body is null");
    }

    return response.body.getReader();
  },

  /**
   * 流式智能提问
   * @param messages 消息历史
   * @param context 上下文
   * @param ragOptions RAG选项
   * @returns ReadableStreamDefaultReader
   */
  streamQuestioner: async (
    messages: Array<{ role: string; content: string }>,
    context?: string,
    ragOptions?: {
      moduleCode?: string;
      knowledgeBaseIds?: number[];
      enableRag?: boolean;
    }
  ): Promise<ReadableStreamDefaultReader<Uint8Array>> => {
    const body: Record<string, unknown> = {
      messages,
      stream: true,
    };

    if (context) body.context = context;
    if (ragOptions) {
      if (ragOptions.moduleCode) body.moduleCode = ragOptions.moduleCode;
      if (ragOptions.knowledgeBaseIds && ragOptions.knowledgeBaseIds.length > 0) {
        body.knowledgeBaseIds = ragOptions.knowledgeBaseIds;
      }
      if (ragOptions.enableRag !== undefined) body.enableRag = ragOptions.enableRag;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}${apiEndpoints.ai.questioner}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          Accept: "text/event-stream",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    if (!response.body) {
      throw new Error("Response body is null");
    }

    return response.body.getReader();
  },

  /**
   * 流式Agent对话
   * @param messages 消息历史
   * @param context 上下文
   * @param ragOptions RAG选项
   * @returns ReadableStreamDefaultReader
   */
  streamAgentChat: async (
    messages: Array<{ role: string; content: string }>,
    context?: string,
    ragOptions?: {
      moduleCode?: string;
      knowledgeBaseIds?: number[];
      enableRag?: boolean;
    }
  ): Promise<ReadableStreamDefaultReader<Uint8Array>> => {
    const { getUserId } = await import("@/lib/auth");
    const userId = getUserId();

    const body: Record<string, unknown> = {
      messages,
      stream: true,
    };

    if (userId) body.userId = userId;
    if (context) body.context = context;
    if (ragOptions) {
      if (ragOptions.moduleCode) body.moduleCode = ragOptions.moduleCode;
      if (ragOptions.knowledgeBaseIds && ragOptions.knowledgeBaseIds.length > 0) {
        body.knowledgeBaseIds = ragOptions.knowledgeBaseIds;
      }
      if (ragOptions.enableRag !== undefined) body.enableRag = ragOptions.enableRag;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}${apiEndpoints.ai.agentChat}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          Accept: "text/event-stream",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    if (!response.body) {
      throw new Error("Response body is null");
    }

    return response.body.getReader();
  },

  /**
   * 同步Agent对话
   */
  agentChatSync: async (
    messages: Array<{ role: string; content: string }>,
    context?: string,
    ragOptions?: {
      moduleCode?: string;
      knowledgeBaseIds?: number[];
      enableRag?: boolean;
    }
  ): Promise<AgentChatSyncResult> => {
    const { getUserId } = await import("@/lib/auth");
    const userId = getUserId();

    const body: Record<string, unknown> = {
      messages,
      stream: false,
    };

    if (userId) body.userId = userId;
    if (context) body.context = context;
    if (ragOptions) {
      if (ragOptions.moduleCode) body.moduleCode = ragOptions.moduleCode;
      if (ragOptions.knowledgeBaseIds && ragOptions.knowledgeBaseIds.length > 0) {
        body.knowledgeBaseIds = ragOptions.knowledgeBaseIds;
      }
      if (ragOptions.enableRag !== undefined) body.enableRag = ragOptions.enableRag;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/chat-sync`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 创建Agent任务
   */
  createAgentTask: async (
    messages: Array<{ role: string; content: string }>,
    context?: string,
    ragOptions?: {
      moduleCode?: string;
      knowledgeBaseIds?: number[];
      enableRag?: boolean;
    }
  ): Promise<AgentTaskCreateResult> => {
    const { getUserId } = await import("@/lib/auth");
    const userId = getUserId();

    const body: Record<string, unknown> = {
      messages,
      stream: false,
    };

    if (userId) body.userId = userId;
    if (context) body.context = context;
    if (ragOptions) {
      if (ragOptions.moduleCode) body.moduleCode = ragOptions.moduleCode;
      if (ragOptions.knowledgeBaseIds && ragOptions.knowledgeBaseIds.length > 0) {
        body.knowledgeBaseIds = ragOptions.knowledgeBaseIds;
      }
      if (ragOptions.enableRag !== undefined) body.enableRag = ragOptions.enableRag;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/chat-async`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 轮询Agent任务进度
   */
  pollAgentProgress: async (
    taskId: string,
    afterIndex: number
  ): Promise<AgentTaskProgress> => {
    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/chat-async/${taskId}/progress?after=${afterIndex}`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
        },
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 获取生成状态
   */
  getGenerationStatus: async (
    taskId: string
  ): Promise<{
    success: boolean;
    status: string;
    progress: number;
    problemId?: number;
  }> => {
    const response = await axios.get(
      `${appConfig.api.baseURL}${apiEndpoints.ai.agentGenerationStatus}/${taskId}`,
      {
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
        },
        timeout: appConfig.api.timeout,
      }
    );

    return response.data;
  },

  /**
   * 确认消息段
   */
  acknowledgeSegment: async (
    taskId: string,
    sequenceNumber: number,
    checksum?: string
  ): Promise<AcknowledgmentResult> => {
    const body: Record<string, unknown> = {};
    if (checksum) {
      body.checksum = checksum;
    }

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/acknowledge/${taskId}/${sequenceNumber}`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 批量确认消息段
   */
  acknowledgeSegments: async (
    taskId: string,
    sequenceNumbers: number[]
  ): Promise<AcknowledgmentResult> => {
    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/acknowledge/${taskId}/batch`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ sequenceNumbers }),
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 获取未确认的消息段
   */
  getUnacknowledgedSegments: async (
    taskId: string
  ): Promise<UnacknowledgedSegmentsResult> => {
    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/unacknowledged/${taskId}`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
        },
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 恢复消息段
   */
  recoverSegments: async (
    taskId: string,
    fromSequenceNumber: number
  ): Promise<RecoveryResult> => {
    console.info(`[恢复] 开始恢复消息段: taskId=${taskId}, fromSeq=${fromSequenceNumber}`);

    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/recover/${taskId}?fromSeq=${fromSequenceNumber}`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
        },
      }
    );

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`[恢复失败] HTTP错误: status=${response.status}, body=${errorText}`);
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const result: RecoveryResult = await response.json();

    if (result.success) {
      console.info(`[恢复成功] taskId=${taskId}, recovered=${result.recoveredCount}, total=${result.totalSegments}`);
    } else {
      console.error(`[恢复失败] taskId=${taskId}, error=${result.error}`);
    }

    return result;
  },

  /**
   * 获取恢复状态
   */
  getRecoveryStatus: async (
    taskId: string
  ): Promise<RecoveryStatusResult> => {
    const response = await fetchWithTimeout(
      `${appConfig.api.baseURL}/ai/agent/recovery-status/${taskId}`,
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${getToken() || ""}`,
        },
      }
    );

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  },

  /**
   * 创建恢复处理器
   */
  createRecoveryHandler: (
    taskId: string,
    options?: {
      maxRetries?: number;
      retryDelay?: number;
      onRecoveryStart?: () => void;
      onRecoverySuccess?: (recoveredCount: number) => void;
      onRecoveryFailed?: (error: Error) => void;
      onSegmentsRecovered?: (segments: MessageSegment[]) => void;
    }
  ) => {
    const maxRetries = options?.maxRetries || 3;
    const retryDelay = options?.retryDelay || 1000;
    let lastAcknowledgedIndex = -1;
    let isRecovering = false;

    const attemptRecovery = async (fromSeq: number): Promise<RecoveryResult | null> => {
      if (isRecovering) {
        console.warn('[恢复处理器] 已有恢复任务在进行中');
        return null;
      }

      isRecovering = true;
      options?.onRecoveryStart?.();

      for (let attempt = 0; attempt < maxRetries; attempt++) {
        try {
          const result = await aiService.recoverSegments(taskId, fromSeq);

          if (result.success) {
            lastAcknowledgedIndex = fromSeq + result.recoveredCount;
            options?.onRecoverySuccess?.(result.recoveredCount);

            if (result.segments.length > 0) {
              options?.onSegmentsRecovered?.(result.segments);
            }

            isRecovering = false;
            return result;
          } else {
            console.warn(`[恢复处理器] 恢复失败，尝试 ${attempt + 1}/${maxRetries}: ${result.error}`);
            if (attempt < maxRetries - 1) {
              await new Promise(resolve => setTimeout(resolve, retryDelay * (attempt + 1)));
            }
          }
        } catch (error) {
          console.error(`[恢复处理器] 恢复异常，尝试 ${attempt + 1}/${maxRetries}:`, error);
          if (attempt < maxRetries - 1) {
            await new Promise(resolve => setTimeout(resolve, retryDelay * (attempt + 1)));
          }
        }
      }

      const error = new Error(`恢复失败，已达到最大重试次数 ${maxRetries}`);
      options?.onRecoveryFailed?.(error);
      isRecovering = false;
      return null;
    };

    return {
      recoverFromIndex: async (fromSeq: number) => {
        return attemptRecovery(fromSeq);
      },
      recoverFromLastAcknowledged: async () => {
        try {
          const status = await aiService.getRecoveryStatus(taskId);
          if (status.canRecover) {
            return attemptRecovery(status.acknowledgedIndex);
          }
          return null;
        } catch (error) {
          console.error('[恢复处理器] 获取恢复状态失败:', error);
          return null;
        }
      },
      getLastAcknowledgedIndex: () => lastAcknowledgedIndex,
      isRecovering: () => isRecovering,
      checkAndRecover: async () => {
        try {
          const status = await aiService.getRecoveryStatus(taskId);
          if (status.canRecover && status.unacknowledgedCount > 0) {
            console.info(`[恢复处理器] 检测到 ${status.unacknowledgedCount} 个未确认消息段，开始恢复`);
            return attemptRecovery(status.acknowledgedIndex);
          }
          return null;
        } catch (error) {
          console.error('[恢复处理器] 检查恢复状态失败:', error);
          return null;
        }
      },
    };
  },

  /**
   * 创建确认处理器
   */
  createAcknowledgmentHandler: (
    taskId: string,
    options?: {
      batchSize?: number;
      batchDelay?: number;
      onAcknowledge?: (sequenceNumber: number) => void;
      onError?: (sequenceNumber: number, error: Error) => void;
    }
  ) => {
    const batchSize = options?.batchSize || 5;
    const batchDelay = options?.batchDelay || 100;
    const pendingAcknowledgments: Set<number> = new Set();
    let batchTimeout: NodeJS.Timeout | null = null;
    let isProcessing = false;

    const processBatch = async () => {
      if (isProcessing || pendingAcknowledgments.size === 0) {
        return;
      }

      isProcessing = true;
      const toAcknowledge = Array.from(pendingAcknowledgments);
      pendingAcknowledgments.clear();

      try {
        if (toAcknowledge.length === 1) {
          const result = await aiService.acknowledgeSegment(taskId, toAcknowledge[0]);
          if (result.success && options?.onAcknowledge) {
            options.onAcknowledge(toAcknowledge[0]);
          }
        } else {
          const result = await aiService.acknowledgeSegments(taskId, toAcknowledge);
          if (result.success) {
            toAcknowledge.forEach((seq) => {
              if (options?.onAcknowledge) {
                options.onAcknowledge(seq);
              }
            });
          }
          if (result.failedNumbers && result.failedNumbers.length > 0) {
            result.failedNumbers.forEach((seq) => {
              if (options?.onError) {
                options.onError(seq, new Error("Acknowledgment failed"));
              }
            });
          }
        }
      } catch (error) {
        toAcknowledge.forEach((seq) => {
          if (options?.onError) {
            options.onError(seq, error as Error);
          }
        });
      } finally {
        isProcessing = false;
        if (pendingAcknowledgments.size > 0) {
          scheduleBatch();
        }
      }
    };

    const scheduleBatch = () => {
      if (batchTimeout) {
        clearTimeout(batchTimeout);
      }
      batchTimeout = setTimeout(processBatch, batchDelay);
    };

    return {
      acknowledge: (sequenceNumber: number) => {
        pendingAcknowledgments.add(sequenceNumber);
        if (pendingAcknowledgments.size >= batchSize) {
          processBatch();
        } else {
          scheduleBatch();
        }
      },
      flush: async () => {
        if (batchTimeout) {
          clearTimeout(batchTimeout);
        }
        await processBatch();
      },
      getPendingCount: () => pendingAcknowledgments.size,
    };
  },

  /**
   * 智能重构代码
   * @param code 代码内容
   * @param language 编程语言
   * @param selection 选中代码范围（包含上下文）
   * @returns 重构结果
   */
  refactorCode: async (
    code: string,
    language: string,
    selection?: {
      startLine: number;
      endLine: number;
      selectedText: string;
      contextBefore?: string;
      contextAfter?: string;
      fullCode?: string;
      totalLines?: number;
    }
  ): Promise<{ data: { refactoredCode: string; explanation: string }; success: boolean }> => {
    const body: Record<string, unknown> = {
      code,
      language,
    };

    if (selection) {
      body.selection = selection;
    }

    let token = null;
    if (typeof window !== "undefined") {
      token = localStorage.getItem("token");
    }

    let response;
    try {
      response = await axios.post(
        `${appConfig.api.baseURL}${apiEndpoints.ai.refactor}`,
        body,
        {
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          timeout: appConfig.api.timeout,
        }
      );
    } catch (error: unknown) {
      if (error && typeof error === "object" && "code" in error) {
        const axiosError = error as { code?: string; message?: string };
        if (axiosError.code === "ECONNABORTED" || axiosError.code === "ERR_TIMEOUT") {
          throw new Error("重构请求超时，请稍后重试");
        }
        if (axiosError.code === "ERR_NETWORK") {
          throw new Error("网络连接失败，请检查网络后重试");
        }
      }
      if (error && typeof error === "object" && "response" in error) {
        const httpError = error as { response?: { status?: number; data?: { error?: string } } };
        if (httpError.response?.status === 401) {
          throw new Error("登录已过期，请重新登录");
        }
        if (httpError.response?.status === 400) {
          throw new Error(httpError.response.data?.error || "请求参数错误");
        }
        if (httpError.response?.status && httpError.response.status >= 500) {
          throw new Error("服务器暂时不可用，请稍后重试");
        }
      }
      throw new Error("重构请求失败，请稍后重试");
    }

    const result = response.data;

    if (result.success === false) {
      throw new Error(result.error || "重构失败");
    }

    if (result.success === true && result.data) {
      if (!result.data.refactoredCode || result.data.refactoredCode.trim().length === 0) {
        throw new Error("重构返回了空代码");
      }
      return result;
    }

    if (result.data && result.data.refactoredCode) {
      return result;
    }

    throw new Error("重构响应格式异常");
  },

  completeCode: async (
    context: CompletionContext,
    options?: { timeout?: number; signal?: AbortSignal }
  ): Promise<CompletionResult> => {
    const token = getToken();

    const response = await axios.post(
      `${appConfig.api.baseURL}${apiEndpoints.ai.complete}`,
      {
        code: context.code,
        language: context.language,
        selection: {
          cursorLine: context.cursorLine,
          cursorColumn: context.cursorColumn,
          contextBefore: context.contextBefore || "",
          contextAfter: context.contextAfter || "",
          fullCode: context.fullCode || "",
          totalLines: context.totalLines || 0,
        },
        maxTokens: 256,
        temperature: 0.2,
      },
      {
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        timeout: options?.timeout || 5000,
        signal: options?.signal,
      }
    );

    return response.data;
  },

  /**
   * 生成问题
   * @param userId 用户ID
   * @param topic 主题
   * @param difficulty 难度
   * @param questionType 问题类型
   * @param context 上下文
   * @returns 生成的问题
   */
  generateQuestion: (
    userId: number,
    topic: string,
    difficulty: string,
    questionType?: string,
    context?: string
  ): Promise<ApiResponse<unknown>> => {
    return apiClient.post(apiEndpoints.ai.generate, {
      userId,
      topic,
      difficulty,
      questionType: questionType || "coding_problem",
      context,
    });
  },
};

export default aiService;

// 健壮的JSON解析工具函数
// 处理单个或多个JSON对象的解析

const TOOL_TAG_START_REGEX = /<tool_call:\s*name:\s*"[^"]*"\s*arguments:\s*\{|<tool_result:\s*name:\s*"[^"]*"\s*result:\s*\{/g;

// ==================== 数据分片缓存机制 ====================

export interface DataChunk {
  id: string;
  sequence: number;
  data: string;
  timestamp: number;
  checksum?: string;
  isComplete: boolean;
}

export interface ChunkCache {
  chunks: Map<string, DataChunk>;
  expectedSequence: number;
  totalChunks: number | null;
  lastReceivedTime: number;
  sessionId: string;
}

const chunkCaches = new Map<string, ChunkCache>();

export function createChunkCache(sessionId: string): ChunkCache {
  const cache: ChunkCache = {
    chunks: new Map(),
    expectedSequence: 0,
    totalChunks: null,
    lastReceivedTime: Date.now(),
    sessionId,
  };
  chunkCaches.set(sessionId, cache);
  return cache;
}

export function getChunkCache(sessionId: string): ChunkCache | undefined {
  return chunkCaches.get(sessionId);
}

export function clearChunkCache(sessionId: string): void {
  chunkCaches.delete(sessionId);
}

export function addChunkToCache(sessionId: string, chunk: DataChunk): {
  success: boolean;
  isComplete: boolean;
  missingSequences: number[];
} {
  let cache = chunkCaches.get(sessionId);
  if (!cache) {
    cache = createChunkCache(sessionId);
  }

  cache.lastReceivedTime = Date.now();
  cache.chunks.set(chunk.id, chunk);

  if (chunk.isComplete) {
    cache.totalChunks = chunk.sequence + 1;
  }

  const missingSequences: number[] = [];
  for (let i = cache.expectedSequence; i < (cache.totalChunks || cache.expectedSequence + 100); i++) {
    const found = Array.from(cache.chunks.values()).some(c => c.sequence === i);
    if (!found) {
      missingSequences.push(i);
    } else if (i === cache.expectedSequence) {
      cache.expectedSequence++;
    } else {
      break;
    }
  }

  const isComplete = cache.totalChunks !== null && 
    cache.chunks.size >= cache.totalChunks &&
    missingSequences.length === 0;

  return {
    success: true,
    isComplete,
    missingSequences,
  };
}

export function assembleChunks(sessionId: string): string | null {
  const cache = chunkCaches.get(sessionId);
  if (!cache) return null;

  const sortedChunks = Array.from(cache.chunks.values())
    .sort((a, b) => a.sequence - b.sequence);

  return sortedChunks.map(c => c.data).join('');
}

// ==================== 数据分片校验逻辑 ====================

export interface ChunkValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export function validateChunk(chunk: DataChunk): ChunkValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (!chunk.id || typeof chunk.id !== 'string') {
    errors.push('分片ID无效或缺失');
  }

  if (typeof chunk.sequence !== 'number' || chunk.sequence < 0) {
    errors.push('分片序列号无效');
  }

  if (!chunk.data && chunk.data !== '') {
    errors.push('分片数据缺失');
  }

  if (typeof chunk.timestamp !== 'number' || chunk.timestamp <= 0) {
    warnings.push('分片时间戳无效');
  }

  if (chunk.checksum) {
    const calculatedChecksum = calculateChecksum(chunk.data);
    if (calculatedChecksum !== chunk.checksum) {
      errors.push('分片校验和不匹配');
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

export function calculateChecksum(data: string): string {
  let hash = 0;
  for (let i = 0; i < data.length; i++) {
    const char = data.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash = hash & hash;
  }
  return Math.abs(hash).toString(16);
}

// ==================== 断点续传请求逻辑 ====================

export interface ResumePoint {
  sessionId: string;
  lastSequence: number;
  timestamp: number;
  endpoint: string;
  requestBody?: Record<string, unknown>;
}

const resumePoints = new Map<string, ResumePoint>();

export function saveResumePoint(
  sessionId: string,
  lastSequence: number,
  endpoint: string,
  requestBody?: Record<string, unknown>
): void {
  resumePoints.set(sessionId, {
    sessionId,
    lastSequence,
    timestamp: Date.now(),
    endpoint,
    requestBody,
  });
}

export function getResumePoint(sessionId: string): ResumePoint | undefined {
  return resumePoints.get(sessionId);
}

export function clearResumePoint(sessionId: string): void {
  resumePoints.delete(sessionId);
}

export function canResume(sessionId: string, maxAgeMs: number = 30 * 60 * 1000): boolean {
  const point = resumePoints.get(sessionId);
  if (!point) return false;
  
  const age = Date.now() - point.timestamp;
  return age <= maxAgeMs;
}

export interface ResumeRequest {
  sessionId: string;
  fromSequence: number;
  endpoint: string;
  requestBody?: Record<string, unknown>;
}

export function createResumeRequest(sessionId: string): ResumeRequest | null {
  const point = resumePoints.get(sessionId);
  if (!point) return null;

  return {
    sessionId: point.sessionId,
    fromSequence: point.lastSequence + 1,
    endpoint: point.endpoint,
    requestBody: point.requestBody,
  };
}

// ==================== 数据完整性验证 ====================

export interface IntegrityCheckResult {
  isValid: boolean;
  missingData: boolean;
  corruptedData: boolean;
  sequenceGaps: number[];
  checksumErrors: string[];
  details: string[];
}

export function checkDataIntegrity(sessionId: string): IntegrityCheckResult {
  const cache = chunkCaches.get(sessionId);
  const result: IntegrityCheckResult = {
    isValid: true,
    missingData: false,
    corruptedData: false,
    sequenceGaps: [],
    checksumErrors: [],
    details: [],
  };

  if (!cache) {
    result.isValid = false;
    result.missingData = true;
    result.details.push('未找到缓存数据');
    return result;
  }

  const chunks = Array.from(cache.chunks.values()).sort((a, b) => a.sequence - b.sequence);
  
  if (chunks.length === 0) {
    result.isValid = false;
    result.missingData = true;
    result.details.push('缓存中没有数据分片');
    return result;
  }

  for (let i = 0; i < chunks.length - 1; i++) {
    const current = chunks[i];
    const next = chunks[i + 1];
    
    if (next.sequence - current.sequence > 1) {
      for (let seq = current.sequence + 1; seq < next.sequence; seq++) {
        result.sequenceGaps.push(seq);
      }
      result.missingData = true;
      result.isValid = false;
    }
  }

  for (const chunk of chunks) {
    if (chunk.checksum) {
      const calculatedChecksum = calculateChecksum(chunk.data);
      if (calculatedChecksum !== chunk.checksum) {
        result.checksumErrors.push(chunk.id);
        result.corruptedData = true;
        result.isValid = false;
      }
    }
  }

  if (cache.totalChunks !== null && chunks.length < cache.totalChunks) {
    result.missingData = true;
    result.isValid = false;
    result.details.push(`期望 ${cache.totalChunks} 个分片，实际收到 ${chunks.length} 个`);
  }

  if (result.isValid) {
    result.details.push('数据完整性验证通过');
  }

  return result;
}

export function generateSessionId(): string {
  return `session_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
}

function findMatchingBrace(text: string, startIndex: number): number {
  if (startIndex < 0 || startIndex >= text.length || text[startIndex] !== '{') {
    return -1;
  }
  let braceCount = 0;
  let inString = false;
  let escapeNext = false;
  for (let i = startIndex; i < text.length; i++) {
    const c = text[i];
    if (escapeNext) { escapeNext = false; continue; }
    if (c === '\\' && inString) { escapeNext = true; continue; }
    if (c === '"') { inString = !inString; continue; }
    if (inString) continue;
    if (c === '{') { braceCount++; }
    else if (c === '}') {
      braceCount--;
      if (braceCount === 0) return i;
    }
  }
  return -1;
}

export function filterToolCallTags(text: string | undefined | null): string {
  if (!text) return '';
  let result = '';
  let searchStart = 0;
  TOOL_TAG_START_REGEX.lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = TOOL_TAG_START_REGEX.exec(text)) !== null) {
    result += text.substring(searchStart, match.index);
    const jsonStart = match.index + match[0].length - 1;
    const jsonEnd = findMatchingBrace(text, jsonStart);
    if (jsonEnd >= 0) {
      const tagEnd = text.indexOf('>', jsonEnd + 1);
      searchStart = tagEnd >= 0 ? tagEnd + 1 : jsonEnd + 1;
    } else {
      searchStart = match.index + match[0].length;
    }
    TOOL_TAG_START_REGEX.lastIndex = searchStart;
  }
  if (searchStart < text.length) {
    result += text.substring(searchStart);
  }
  return result.trim();
}

// SSE 事件数据类型定义
interface SSEEventData {
  type: string;
  delta?: string;
  content?: string;
  toolName?: string;
  toolArgs?: Record<string, unknown>;
  success?: boolean;
  data?: Record<string, unknown>;
  [key: string]: unknown;
}

// SSE 事件回调函数类型 - 与参考实现保持一致的2参数格式
type SSEEventCallback = (content: string, data: SSEEventData) => void;

// tool_call 回调函数类型
type ToolCallCallback = (toolName: string, toolArgs: Record<string, unknown>, data: SSEEventData) => void;

// tool_result 回调函数类型
type ToolResultCallback = (toolName: string, success: boolean, data: Record<string, unknown>, fullData: SSEEventData) => void;

/**
 * 统一处理data:前缀移除
 * @param {string} data - 原始数据
 * @returns {string} 移除data:前缀后的数据
 */
function removeDataPrefix(data: string): string {
  let result = data.trim();

  // 递归移除所有重复的data:前缀
  while (result.startsWith('data:')) {
    result = result.substring(5).trim();
    // 处理可能的额外空格
    while (result.startsWith(' ')) {
      result = result.substring(1);
    }
  }

  // 处理特殊模式 "data:\ndata:"
  result = result.replace(/data:\s*\n\s*data:/g, '');

  // 处理嵌套的data:前缀模式
  result = result.replace(/data:\s*data:/g, 'data:');

  return result;
}

/**
 * 解析SSE响应数据
 * @param {string} rawData - 原始响应数据（可能包含data:前缀）
 * @returns {Array} 解析后的JSON对象数组
 */
export function parseSSEData(rawData: string) {
  if (!rawData) {
    return [];
  }

  // 统一处理data:前缀移除
  const jsonData = removeDataPrefix(rawData);

  if (!jsonData || jsonData === '{}' || jsonData === '') {
    return [];
  }

  const results = [];
  let remainingData = jsonData;

  // 处理多个JSON对象拼接的情况
  while (remainingData.length > 0) {
    // 清理前导空白字符
    remainingData = remainingData.trimStart();

    if (!remainingData) break;

    // 首先尝试完整的解析
    try {
      const parsed = JSON.parse(remainingData);
      results.push(parsed);
      break; // 完全解析成功
    } catch (error) {
      // 使用边界检测来处理
      const parsedObject = tryParseJsonSegment(remainingData);
      if (parsedObject.parsed && parsedObject.content) {
        try {
          const parsedResult = JSON.parse(parsedObject.content);
          results.push(parsedResult);
          remainingData = remainingData.substring(parsedObject.contentLength || parsedObject.content.length).trim();

          // 跳过分隔符
          while (remainingData.startsWith('\n') || remainingData.startsWith('\r') || remainingData.startsWith(' ')) {
            remainingData = remainingData.substring(1);
          }
        } catch (innerError) {
          console.warn('[JSON解析] 对象解析失败:', parsedObject.content.substring(0, Math.min(100, parsedObject.content.length)));
          break;
        }
      } else {
        // 尝试处理剩余的简单情况
        const simpleResult = trySimpleParse(remainingData);
        if (simpleResult.parsed) {
          results.push(simpleResult.data);
          break;
        }

        // 检查是否有不完整的JSON对象
        const incompleteJson = tryParseIncompleteJson(remainingData);
        if (incompleteJson && incompleteJson.length > 0) {
          try {
            const parsedResult = JSON.parse(incompleteJson);
            results.push(parsedResult);
            remainingData = remainingData.substring(incompleteJson.length).trim();
            continue;
          } catch (incompleteError) {
            // 忽略不完整JSON的解析错误
            console.debug('[JSON解析] 不完整JSON跳过:', incompleteJson.substring(0, Math.min(100, incompleteJson.length)));
          }
        }

        // 如果是空数据或无效数据，直接跳过而不是报错
        if (remainingData.trim() === '' || remainingData.trim() === '{}' || remainingData.trim().startsWith('data:')) {
          break;
        }

        console.warn('[JSON解析] 无法解析数据:', remainingData.substring(0, Math.min(100, remainingData.length)));
        break;
      }
    }
  }

  return results;
}

/**
 * 尝试解析JSON片段
 * @param {string} data - 待解析的数据
 * @returns {object} 解析结果
 */
function tryParseJsonSegment(data: string) {
  let braceCount = 0;
  let inString = false;
  let escapeNext = false;
  let objectStart = -1;
  let objectEnd = -1;

  // 首先找到JSON对象的开始位置
  let startIndex = 0;
  while (startIndex < data.length && data[startIndex] !== '{') {
    startIndex++;
  }

  if (startIndex >= data.length) {
    return {
      parsed: false,
      content: null,
      contentLength: 0
    };
  }

  objectStart = startIndex;
  braceCount = 1;

  for (let i = startIndex + 1; i < data.length; i++) {
    const char = data[i];

    if (escapeNext) {
      escapeNext = false;
      continue;
    }

    if (char === '\\') {
      escapeNext = true;
      continue;
    }

    if (char === '"' && !escapeNext) {
      inString = !inString;
      continue;
    }

    if (inString) {
      continue;
    }

    if (char === '{') {
      braceCount++;
    } else if (char === '}') {
      braceCount--;
      if (braceCount === 0) {
        objectEnd = i;
        // 找到完整的JSON对象
        const content = data.substring(objectStart, objectEnd + 1);
        return {
          parsed: true,
          content: content,
          contentLength: objectEnd + 1
        };
      }
    }
  }

  // 如果没有找到完整的对象，但有开始的{
  if (objectStart !== -1) {
    // 返回从开始到数据末尾的部分，供后续处理
    const content = data.substring(objectStart);
    return {
      parsed: false,
      content: content,
      contentLength: data.length
    };
  }

  return {
    parsed: false,
    content: null,
    contentLength: 0
  };
}

/**
 * 尝试简单解析剩余数据
 * @param {string} data - 待解析的数据
 * @returns {object} 解析结果
 */
function trySimpleParse(data: string) {
  const trimmed = data.trim();

  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const parsed = JSON.parse(trimmed);
      return {
        parsed: true,
        data: parsed
      };
    } catch (e) {
      // 解析失败，尝试修复常见的JSON格式问题
      const fixedData = fixCommonJsonIssues(trimmed);
      if (fixedData !== trimmed) {
        try {
          const parsed = JSON.parse(fixedData);
          return {
            parsed: true,
            data: parsed
          };
        } catch (fixError) {
          console.debug('[JSON解析] 简单修复失败:', fixError);
        }
      }
    }
  }

  return {
    parsed: false,
    data: null
  };
}

/**
 * 修复常见的JSON格式问题
 * @param {string} jsonStr - 待修复的JSON字符串
 * @returns {string} 修复后的JSON字符串
 */
function fixCommonJsonIssues(jsonStr: string): string {
  let fixed = jsonStr.trim();

  // 移除末尾的逗号
  if (fixed.endsWith(',')) {
    fixed = fixed.slice(0, -1);
  }

  // 修复不完整的对象结尾
  if (fixed.endsWith(':') || fixed.endsWith('{')) {
    fixed = fixed + '""';
  }

  // 确保对象正确闭合
  const openBraces = (fixed.match(/\{/g) || []).length;
  const closeBraces = (fixed.match(/\}/g) || []).length;

  if (openBraces > closeBraces) {
    fixed = fixed + '}'.repeat(openBraces - closeBraces);
  }

  return fixed;
}

/**
 * 尝试解析不完整的JSON对象
 * @param {string} data - 待解析的数据
 * @returns {string | null} 解析到的JSON字符串，如果无法解析则返回null
 */
function tryParseIncompleteJson(data: string): string | null {
  if (!data) return null;

  // 查找可能的JSON对象开始
  const startIndex = data.indexOf('{');
  if (startIndex === -1) return null;

  let braceCount = 0;
  let inString = false;
  let escapeNext = false;

  for (let i = startIndex; i < data.length; i++) {
    const char = data[i];

    if (escapeNext) {
      escapeNext = false;
      continue;
    }

    if (char === '\\') {
      escapeNext = true;
      continue;
    }

    if (char === '"' && !escapeNext) {
      inString = !inString;
      continue;
    }

    if (inString) {
      continue;
    }

    if (char === '{') {
      braceCount++;
    } else if (char === '}') {
      braceCount--;
      if (braceCount === 0) {
        // 找到了一个完整的对象
        return data.substring(startIndex, i + 1);
      }
    }
  }

  // 如果到达末尾且仍有未闭合的大括号，尝试返回尽可能完整的部分
  if (braceCount > 0 && data.length > startIndex) {
    const incompletePart = data.substring(startIndex);
    // 尝试修复不完整的JSON
    const fixedPart = fixCommonJsonIssues(incompletePart);
    if (fixedPart !== incompletePart) {
      return fixedPart;
    }
    return incompletePart;
  }

  return null;
}

/**
 * 处理SSE事件数据的通用函数
 * 与参考实现保持一致的简洁版本
 * @param {string} jsonData - JSON数据字符串
 * @param {Function} onText - 处理text类型数据的回调
 * @param {Function} onComplete - 处理complete类型数据的回调
 * @param {Function} onThinking - 处理thinking类型数据的回调
 * @param {Function} onToolCall - 处理tool_call类型数据的回调
 * @param {Function} onToolResult - 处理tool_result类型数据的回调
 * @param {Function} onError - 处理错误信息的回调
 *
 * 注意：complete事件仅作为流程结束信号，不自动追加内容到显示区域
 * 如需在complete事件中处理特定内容，请在回调中自行决定是否使用content参数
 */
export function processSSEEvent(
  jsonData: string,
  { onText, onComplete, onThinking, onToolCall, onToolResult, onError }: {
    onText?: SSEEventCallback;
    onComplete?: SSEEventCallback;
    onThinking?: SSEEventCallback;
    onToolCall?: ToolCallCallback;
    onToolResult?: ToolResultCallback;
    onError?: (errorMessage: string, data: SSEEventData) => void;
  }
) {
  const parsedObjects = parseSSEData(jsonData);

  parsedObjects.forEach(data => {
    switch (data.type) {
      case 'text':
        if (onText && data.delta) {
          onText(data.delta, data);
        }
        break;

      case 'complete':
        // complete事件仅作为流程结束信号
        // content参数可用于调试或特殊处理，但不自动追加到显示内容
        // 检查是否有错误信息
        const errorField = data.error || (data as Record<string, unknown>).errorMessage;
        if (errorField && typeof errorField === 'string' && errorField.trim()) {
          if (onError) {
            onError(errorField, data);
          }
        }
        if (onComplete) {
          onComplete(data.content || data.delta || '', data);
        }
        break;

      case 'thinking':
        if (onThinking) {
          onThinking(data.content || '', data);
        }
        break;

      case 'tool_call':
        if (onToolCall) {
          onToolCall(data.toolName || '', data.toolArgs || {}, data);
        }
        break;

      case 'tool_result':
        if (onToolResult) {
          onToolResult(data.toolName || '', data.success ?? false, data.data || {}, data);
        }
        break;

      default:
        console.warn('[SSE处理] 未知事件类型:', data.type, data);
    }
  });
}

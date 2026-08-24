/**
 * 数据转换和验证工具
 * 提供健壮的数据处理机制，处理后端返回的各种数据格式
 */

import { z } from "zod";

/**
 * 安全解析JSON - 增强版，带日志记录
 * @param value 可能为JSON字符串的值
 * @param defaultValue 解析失败时的默认值
 * @param context 解析上下文（用于日志）
 * @returns 解析后的值或默认值
 */
export function safeParseJSON<T>(value: unknown, defaultValue: T, context?: string): T {
  if (value === null || value === undefined) {
    return defaultValue;
  }

  if (typeof value === "object") {
    return value as T;
  }

  if (typeof value === "string") {
    try {
      const trimmed = value.trim();
      if (trimmed === "" || trimmed === "null" || trimmed === "undefined") {
        return defaultValue;
      }
      return JSON.parse(trimmed) as T;
    } catch (error) {
      // 记录详细的解析错误日志
      console.error(`[safeParseJSON] JSON解析失败${context ? ` (${context})` : ""}:`, {
        input: value,
        error: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
      });
      return defaultValue;
    }
  }

  return defaultValue;
}

/**
 * 尝试解析JSON并返回详细结果
 * @param value 可能为JSON字符串的值
 * @returns 解析结果对象，包含成功状态和解析后的数据或错误信息
 */
export function tryParseJSON<T>(value: unknown): { success: boolean; data?: T; error?: string } {
  if (value === null || value === undefined) {
    return { success: false, error: "Value is null or undefined" };
  }

  if (typeof value === "object") {
    return { success: true, data: value as T };
  }

  if (typeof value === "string") {
    try {
      const trimmed = value.trim();
      if (trimmed === "" || trimmed === "null" || trimmed === "undefined") {
        return { success: false, error: `Value is empty or null string: "${trimmed}"` };
      }
      const parsed = JSON.parse(trimmed) as T;
      return { success: true, data: parsed };
    } catch (error) {
      return { 
        success: false, 
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  return { success: false, error: `Unsupported type: ${typeof value}` };
}

/**
 * 确保值为数组
 * @param value 可能的数组值
 * @param defaultValue 默认值
 * @returns 数组
 */
export function ensureArray<T>(value: unknown, defaultValue: T[] = []): T[] {
  if (value === null || value === undefined) {
    return defaultValue;
  }

  if (Array.isArray(value)) {
    return value;
  }

  // 如果是字符串，尝试解析为JSON数组
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed)) {
        return parsed;
      }
    } catch {
      // 解析失败，返回默认值
    }
  }

  return defaultValue;
}

/**
 * 获取默认值
 * @param value 值
 * @param defaultValue 默认值
 * @returns 值或默认值
 */
export function withDefault<T>(value: T | null | undefined, defaultValue: T): T {
  if (value === null || value === undefined) {
    return defaultValue;
  }
  return value;
}

/**
 * 安全获取字符串
 * @param value 可能的字符串值
 * @param defaultValue 默认值
 * @returns 字符串
 */
export function safeString(value: unknown, defaultValue = ""): string {
  if (value === null || value === undefined) {
    return defaultValue;
  }

  if (typeof value === "string") {
    return value;
  }

  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }

  return defaultValue;
}

/**
 * 安全获取数字
 * @param value 可能的数字值
 * @param defaultValue 默认值
 * @returns 数字
 */
export function safeNumber(value: unknown, defaultValue = 0): number {
  if (value === null || value === undefined) {
    return defaultValue;
  }

  if (typeof value === "number") {
    return isNaN(value) ? defaultValue : value;
  }

  if (typeof value === "string") {
    const parsed = parseFloat(value);
    return isNaN(parsed) ? defaultValue : parsed;
  }

  if (typeof value === "boolean") {
    return value ? 1 : 0;
  }

  return defaultValue;
}

/**
 * 安全获取布尔值
 * @param value 可能的布尔值
 * @param defaultValue 默认值
 * @returns 布尔值
 */
export function safeBoolean(value: unknown, defaultValue = false): boolean {
  if (value === null || value === undefined) {
    return defaultValue;
  }

  if (typeof value === "boolean") {
    return value;
  }

  if (typeof value === "number") {
    return value !== 0;
  }

  if (typeof value === "string") {
    const lower = value.toLowerCase().trim();
    return lower === "true" || lower === "1" || lower === "yes";
  }

  return defaultValue;
}

/**
 * 安全获取日期
 * @param value 可能的日期值
 * @param defaultValue 默认值
 * @returns Date对象
 */
export function safeDate(value: unknown, defaultValue?: Date): Date {
  if (value === null || value === undefined) {
    return defaultValue || new Date();
  }

  if (value instanceof Date) {
    return isNaN(value.getTime()) ? (defaultValue || new Date()) : value;
  }

  if (typeof value === "number") {
    const date = new Date(value);
    return isNaN(date.getTime()) ? (defaultValue || new Date()) : date;
  }

  if (typeof value === "string") {
    const date = new Date(value);
    return isNaN(date.getTime()) ? (defaultValue || new Date()) : date;
  }

  return defaultValue || new Date();
}

/**
 * 解析逗号分隔的字符串为数组
 * @param value 逗号分隔的字符串
 * @returns 数组
 */
export function parseCommaSeparated(value: unknown): string[] {
  if (value === null || value === undefined) {
    return [];
  }

  if (Array.isArray(value)) {
    return value.map(String);
  }

  if (typeof value === "string") {
    if (value.trim() === "") {
      return [];
    }
    return value.split(",").map((s) => s.trim()).filter(Boolean);
  }

  return [];
}

/**
 * 数据验证结果
 */
export interface ValidationResult<T> {
  success: boolean;
  data?: T;
  errors?: string[];
}

/**
 * 使用Zod schema验证数据
 * @param schema Zod schema
 * @param data 待验证数据
 * @returns 验证结果
 */
export function validateData<T>(schema: z.ZodType<T>, data: unknown): ValidationResult<T> {
  try {
    const result = schema.parse(data);
    return { success: true, data: result };
  } catch (error) {
    if (error instanceof z.ZodError) {
      // Zod v4 uses 'issues' instead of 'errors'
      const issues = (error as z.ZodError<unknown> & { issues?: Array<{ path: (string | number)[]; message: string }> }).issues || [];
      const errors = issues.map((e) => `${e.path.join(".")}: ${e.message}`);
      return { success: false, errors };
    }
    return { success: false, errors: ["Unknown validation error"] };
  }
}

/**
 * 安全执行函数
 * @param fn 待执行函数
 * @param defaultValue 默认值
 * @returns 函数返回值或默认值
 */
export function safeExecute<T>(fn: () => T, defaultValue: T): T {
  try {
    return fn();
  } catch {
    return defaultValue;
  }
}

/**
 * 转换分页响应数据
 * @param data 后端返回的分页数据
 * @param itemTransform 单项转换函数
 * @returns 标准化的分页数据
 */
export function transformPaginatedResponse<T, R>(
  data: unknown,
  itemTransform: (item: T) => R
): { items: R[]; total: number; page: number; size: number } {
  if (!data || typeof data !== "object") {
    return { items: [], total: 0, page: 1, size: 10 };
  }

  const d = data as Record<string, unknown>;

  const items = ensureArray<T>(d.items || d.data || d.list || d.records, []);
  const total = safeNumber(d.total || d.totalCount || d.totalElements, 0);
  const page = safeNumber(d.page || d.current || d.pageNum || d.pageNo, 1);
  const size = safeNumber(d.size || d.pageSize || d.limit || d.pageLimit, 10);

  return {
    items: items.map(itemTransform),
    total,
    page,
    size,
  };
}

/**
 * 转换API响应数据
 * @param response API响应
 * @param dataTransform 数据转换函数
 * @returns 转换后的数据
 */
export function transformApiResponse<T, R>(
  response: { data?: { code?: number; data?: T; message?: string; msg?: string } },
  dataTransform: (data: T) => R
): R | null {
  if (!response?.data) {
    return null;
  }

  const { code, data, message, msg } = response.data;

  // 成功的状态码
  if (code === 200 || code === 201) {
    if (data === null || data === undefined) {
      return null;
    }
    return dataTransform(data as T);
  }

  // 业务错误
  const errorMessage = message || msg || "请求失败";
  console.error("API错误:", errorMessage);
  return null;
}

/**
 * 深拷贝对象
 * @param obj 对象
 * @returns 深拷贝后的对象
 */
export function deepClone<T>(obj: T): T {
  if (obj === null || typeof obj !== "object") {
    return obj;
  }

  if (obj instanceof Date) {
    return new Date(obj.getTime()) as unknown as T;
  }

  if (Array.isArray(obj)) {
    return obj.map((item) => deepClone(item)) as unknown as T;
  }

  const cloned = {} as T;
  for (const key in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      cloned[key] = deepClone(obj[key]);
    }
  }

  return cloned;
}

/**
 * 清理对象中的空值
 * @param obj 对象
 * @param keepEmptyString 是否保留空字符串
 * @returns 清理后的对象
 */
export function cleanObject<T extends Record<string, unknown>>(
  obj: T,
  keepEmptyString = false
): Partial<T> {
  const result: Partial<T> = {};

  for (const key in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      const value = obj[key];

      if (value === null || value === undefined) {
        continue;
      }

      if (value === "" && !keepEmptyString) {
        continue;
      }

      if (Array.isArray(value) && value.length === 0) {
        continue;
      }

      result[key] = value as typeof result[typeof key];
    }
  }

  return result;
}

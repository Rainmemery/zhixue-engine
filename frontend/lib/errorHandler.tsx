/**
 * 统一错误处理工具函数
 * 提供错误提取、重试机制、网络状态检测等功能
 */

import { AxiosError } from "axios";
import { message } from "antd";

/**
 * 错误信息选项
 */
interface ErrorOptions {
  /** 自定义错误消息 */
  message?: string;
  /** 是否显示错误提示 */
  showError?: boolean;
  /** 错误提示持续时间 (ms) */
  duration?: number;
  /** 是否包含重试按钮 */
  showRetry?: boolean;
  /** 重试回调 */
  onRetry?: () => void;
}

/**
 * 重试配置
 */
interface RetryConfig {
  /** 最大重试次数 */
  maxRetries?: number;
  /** 重试延迟 (ms) */
  delay?: number;
  /** 延迟增长倍数 */
  backoffMultiplier?: number;
  /** 最大延迟 (ms) */
  maxDelay?: number;
  /** 可重试的状态码 */
  retryableStatusCodes?: number[];
  /** 是否重试网络错误 */
  retryOnNetworkError?: boolean;
  /** 错误回调 */
  onError?: (error: Error, attempt: number) => void;
}

/**
 * 网络状态
 */
export interface NetworkStatus {
  /** 是否在线 */
  isOnline: boolean;
  /** 是否慢速连接 */
  isSlow: boolean;
  /** 网络类型 */
  type?: string;
  /** 下行速度 (Mbps) */
  downlink?: number;
  /** 往返时延 (ms) */
  rtt?: number;
}

/**
 * 从 Axios 错误中提取错误消息
 * @param error Axios 错误对象
 * @param defaultMessage 默认错误消息
 * @returns 错误消息
 */
export const extractErrorMessage = (
  error: unknown,
  defaultMessage = "操作失败"
): string => {
  if (error instanceof AxiosError) {
    const responseData = error.response?.data;
    if (responseData) {
      return (
        responseData.message ||
        responseData.msg ||
        responseData.error ||
        responseData.detail ||
        defaultMessage
      );
    }
    if (error.message) {
      return error.message;
    }
    return defaultMessage;
  }

  if (error instanceof Error) {
    return error.message || defaultMessage;
  }

  if (typeof error === "string") {
    return error || defaultMessage;
  }

  return defaultMessage;
};

/**
 * 显示错误提示
 * @param error 错误对象
 * @param options 配置选项
 */
export const showError = (error: unknown, options: ErrorOptions = {}): void => {
  const {
    message: customMessage,
    showError = true,
    duration = 3000,
    showRetry = false,
    onRetry,
  } = options;

  if (!showError) return;

  const errorMessage = customMessage || extractErrorMessage(error);

  if (showRetry && onRetry) {
    message.error({
      content: errorMessage,
      duration,
      icon: <span onClick={() => { onRetry(); message.destroy(); }} style={{ cursor: "pointer", textDecoration: "underline", color: "inherit" }}>重试</span>,
    });
  } else {
    message.error({
      content: errorMessage,
      duration,
    });
  }
};

/**
 * 显示成功提示
 * @param messageText 消息内容
 * @param duration 持续时间
 */
export const showSuccess = (messageText: string, duration = 3000): void => {
  message.success({ content: messageText, duration });
};

/**
 * 带重试的异步函数执行
 * @param fn 要执行的异步函数
 * @param config 重试配置
 * @returns 执行结果
 */
export async function executeWithRetry<T>(
  fn: () => Promise<T>,
  config: RetryConfig = {}
): Promise<T> {
  const {
    maxRetries = 3,
    delay = 1000,
    backoffMultiplier = 2,
    maxDelay = 10000,
    retryableStatusCodes = [408, 429, 500, 502, 503, 504],
    retryOnNetworkError = true,
    onError,
  } = config;

  let lastError: Error | null = null;
  let currentDelay = delay;

  for (let attempt = 1; attempt <= maxRetries + 1; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      
      // 调用错误回调
      onError?.(lastError, attempt);

      // 判断是否可重试
      let isRetryable = false;

      if (retryOnNetworkError && !lastError) {
        isRetryable = true;
      } else if (error instanceof AxiosError && error.response) {
        isRetryable = retryableStatusCodes.includes(error.response.status);
      }

      // 不可重试或已达最大重试次数，抛出错误
      if (!isRetryable || attempt > maxRetries) {
        throw lastError;
      }

      // 等待后重试
      await new Promise((resolve) => setTimeout(resolve, currentDelay));
      currentDelay = Math.min(currentDelay * backoffMultiplier, maxDelay);
    }
  }

  throw lastError;
}

/**
 * 获取网络状态
 * @returns 网络状态信息
 */
export const getNetworkStatus = (): NetworkStatus => {
  const defaultStatus: NetworkStatus = {
    isOnline: typeof navigator !== "undefined" ? navigator.onLine : true,
    isSlow: false,
  };

  if (typeof window === "undefined") {
    return defaultStatus;
  }

  const connection = (navigator as any).connection;

  if (!connection) {
    return defaultStatus;
  }

  return {
    isOnline: navigator.onLine,
    isSlow: (connection.downlink || 10) < 1,
    type: connection.type,
    downlink: connection.downlink,
    rtt: connection.rtt,
  };
};

/**
 * 监听网络状态变化
 * @param callback 回调函数
 * @returns 取消监听函数
 */
export const watchNetworkStatus = (
  callback: (status: NetworkStatus) => void
): (() => void) => {
  if (typeof window === "undefined") {
    return () => {};
  }

  const handleOnline = () => {
    callback(getNetworkStatus());
    message.success("网络连接已恢复");
  };

  const handleOffline = () => {
    callback(getNetworkStatus());
    message.warning("网络连接已断开");
  };

  window.addEventListener("online", handleOnline);
  window.addEventListener("offline", handleOffline);

  // 初始调用一次
  callback(getNetworkStatus());

  return () => {
    window.removeEventListener("online", handleOnline);
    window.removeEventListener("offline", handleOffline);
  };
};

/**
 * 判断是否为网络错误
 * @param error 错误对象
 * @returns 是否为网络错误
 */
export const isNetworkError = (error: unknown): boolean => {
  if (error instanceof AxiosError) {
    return !error.response || error.code === "ECONNABORTED";
  }
  return false;
};

/**
 * 判断是否为服务器错误
 * @param error 错误对象
 * @returns 是否为服务器错误
 */
export const isServerError = (error: unknown): boolean => {
  if (error instanceof AxiosError && error.response) {
    const status = error.response.status;
    return status >= 500 && status < 600;
  }
  return false;
};

/**
 * 判断是否为客户端错误
 * @param error 错误对象
 * @returns 是否为客户端错误
 */
export const isClientError = (error: unknown): boolean => {
  if (error instanceof AxiosError && error.response) {
    const status = error.response.status;
    return status >= 400 && status < 500;
  }
  return false;
};

/**
 * 格式化错误信息用于显示
 * @param error 错误对象
 * @param context 错误上下文
 * @returns 格式化的错误信息
 */
export const formatErrorMessage = (
  error: unknown,
  context = "操作"
): string => {
  const baseMessage = extractErrorMessage(error);

  if (isNetworkError(error)) {
    return `${context}失败，网络连接失败，请检查网络后重试`;
  }

  if (isServerError(error)) {
    return `${context}失败，服务器错误，请稍后重试`;
  }

  if (isClientError(error)) {
    return `${context}失败，${baseMessage}`;
  }

  return baseMessage;
};

/**
 * 错误处理类
 * 提供链式调用和更优雅的错误处理
 */
export class ErrorHandler {
  private error: unknown;
  private context = "操作";
  private shouldShowError = true;
  private customMessage?: string;
  private retryCallback?: () => void;

  constructor(error: unknown) {
    this.error = error;
  }

  /**
   * 设置错误上下文
   */
  setContext(context: string): this {
    this.context = context;
    return this;
  }

  /**
   * 设置是否显示错误
   */
  setShowError(show: boolean): this {
    this.shouldShowError = show;
    return this;
  }

  /**
   * 设置自定义错误消息
   */
  setMessage(message: string): this {
    this.customMessage = message;
    return this;
  }

  /**
   * 设置重试回调
   */
  setRetryCallback(callback: () => void): this {
    this.retryCallback = callback;
    return this;
  }

  /**
   * 处理错误并显示
   */
  handle(): string {
    const errorMessage = this.customMessage || formatErrorMessage(this.error, this.context);

    if (this.shouldShowError) {
      showError(this.error, {
        message: errorMessage,
        showRetry: !!this.retryCallback,
        onRetry: this.retryCallback,
      });
    }

    return errorMessage;
  }

  /**
   * 获取原始错误对象
   */
  getError(): unknown {
    return this.error;
  }

  /**
   * 静态方法：快速处理错误
   */
  static handle(error: unknown, context?: string): string {
    const handler = new ErrorHandler(error);
    if (context) {
      handler.setContext(context);
    }
    return handler.handle();
  }
}

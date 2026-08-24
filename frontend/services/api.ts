/**
 * 增强版API客户端
 * 统一处理请求拦截、响应拦截、错误处理、请求重试等功能
 */

import axios, {
  AxiosInstance,
  AxiosError,
  InternalAxiosRequestConfig,
  AxiosResponse,
} from "axios";
import { message } from "antd";
import { useAuthStore } from "@/stores/authStore";
import { appConfig } from "@/config/app.config";
import { apiEndpoints } from "@/config/api.config";
import { HttpStatus, BusinessCode } from "@/constants/status";

// API响应类型定义
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

// 请求配置扩展类型
interface ExtendedRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  _retryCount?: number;
  _skipErrorHandler?: boolean;
}

// 请求状态类型
export type RequestState = "idle" | "loading" | "success" | "error";

/**
 * 统一提取错误消息
 * 优先使用 message 字段，其次使用 msg 字段
 */
const extractErrorMessage = (data: ApiResponse | undefined, defaultMessage: string): string => {
  if (!data) return defaultMessage;
  return data.message || data.msg || defaultMessage;
};

/**
 * 清除所有认证信息并跳转到登录页
 */
const clearAuthAndRedirect = (): void => {
  useAuthStore.getState().clearAuth();
  if (typeof window !== "undefined") {
    localStorage.removeItem("token");
    localStorage.removeItem("refreshToken");
    window.location.href = "/login";
  }
};

// 创建axios实例
const apiClient: AxiosInstance = axios.create({
  baseURL: appConfig.api.baseURL,
  timeout: appConfig.api.timeout,
  headers: {
    "Content-Type": "application/json",
  },
});

/**
 * 请求重试逻辑 - 指数退避策略
 * @param retryCount 当前重试次数
 * @returns 延迟时间(ms)
 */
const getRetryDelay = (retryCount: number): number => {
  const delay = appConfig.api.retryDelay * Math.pow(2, retryCount);
  return Math.min(delay, 10000); // 最大延迟10秒
};

/**
 * 判断是否可重试的错误
 * @param error Axios错误对象
 * @returns 是否可重试
 */
const isRetryableError = (error: AxiosError): boolean => {
  if (!error.response) return true; // 网络错误可重试
  const status = error.response.status;
  return (
    status === HttpStatus.BAD_GATEWAY ||
    status === HttpStatus.SERVICE_UNAVAILABLE ||
    status === HttpStatus.GATEWAY_TIMEOUT ||
    status === HttpStatus.TOO_MANY_REQUESTS
  );
};

/**
 * 开发环境日志输出
 * @param type 日志类型
 * @param data 日志数据
 */
const devLog = (type: "request" | "response" | "error", data: unknown) => {
  if (process.env.NODE_ENV === "development") {
    const styles = {
      request: "color: #1890ff; font-weight: bold;",
      response: "color: #52c41a; font-weight: bold;",
      error: "color: #f5222d; font-weight: bold;",
    };
    console.log(`%c[API ${type.toUpperCase()}]`, styles[type], data);
  }
};

// 请求拦截器
apiClient.interceptors.request.use(
  (config: ExtendedRequestConfig) => {
    // 添加认证token - 优先从localStorage获取（与旧项目保持一致）
    let token = null;
    if (typeof window !== "undefined") {
      token = localStorage.getItem("token");
    }
    // 如果localStorage中没有，则尝试从authStore获取
    if (!token) {
      token = useAuthStore.getState().token;
    }
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 添加请求ID
    config.headers["X-Request-ID"] = `req_${Date.now()}_${Math.random()
      .toString(36)
      .substr(2, 9)}`;

    // 添加时间戳防止缓存
    if (config.method?.toLowerCase() === "get") {
      config.params = { ...config.params, _t: Date.now() };
    }

    // 对于FormData，让浏览器自动设置Content-Type（包含boundary）
    if (config.data instanceof FormData) {
      delete config.headers["Content-Type"];
    }

    // 开发环境日志
    devLog("request", {
      method: config.method,
      url: config.url,
      params: config.params,
      data: config.data instanceof FormData ? "[FormData]" : config.data,
    });

    return config;
  },
  (error: AxiosError) => {
    devLog("error", { type: "request_interceptor", error: error.message });
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    devLog("response", {
      status: response.status,
      url: response.config.url,
      data: response.data,
    });

    const { code, data } = response.data;
    const actualMsg = extractErrorMessage(response.data, "");

    if (process.env.NODE_ENV === "development") {
      console.log("[API Response] 数据结构分析:", {
        url: response.config.url,
        code,
        dataType: typeof data,
        dataIsArray: Array.isArray(data),
        dataKeys: data && typeof data === "object" ? Object.keys(data) : null,
      });
    }

    if (code === BusinessCode.SUCCESS || code === BusinessCode.CREATED) {
      return response;
    } else if (code === BusinessCode.UNAUTHORIZED) {
      message.error(actualMsg || "未认证，请重新登录");
      clearAuthAndRedirect();
      return Promise.reject(new Error(actualMsg || "未认证，请重新登录"));
    } else if (code === BusinessCode.FORBIDDEN) {
      message.error(actualMsg || "权限不足");
      return Promise.reject(new Error(actualMsg || "权限不足"));
    } else {
      message.error(actualMsg || "请求失败");
      return Promise.reject(new Error(actualMsg || "请求失败"));
    }
  },
  async (error: AxiosError) => {
    devLog("error", {
      type: "response_interceptor",
      status: error.response?.status,
      message: error.message,
    });

    const originalRequest = error.config as ExtendedRequestConfig;

    if (!originalRequest) {
      return Promise.reject(error);
    }

    if (
      isRetryableError(error) &&
      (originalRequest._retryCount || 0) < appConfig.api.retryAttempts &&
      !originalRequest._retry
    ) {
      originalRequest._retryCount = (originalRequest._retryCount || 0) + 1;
      const delay = getRetryDelay(originalRequest._retryCount);

      await new Promise((resolve) => setTimeout(resolve, delay));
      return apiClient(originalRequest);
    }

    if (
      error.response?.status === HttpStatus.UNAUTHORIZED &&
      !originalRequest._retry
    ) {
      originalRequest._retry = true;

      try {
        const refreshToken = useAuthStore.getState().refreshToken;
        if (refreshToken) {
          const response = await axios.post<ApiResponse<{ token: string; refreshToken: string }>>(
            `${appConfig.api.baseURL}${apiEndpoints.auth.refresh}`,
            {},
            {
              headers: { Authorization: `Bearer ${refreshToken}` },
            }
          );

          const { token, refreshToken: newRefreshToken } = response.data.data;
          useAuthStore.setState({ token, refreshToken: newRefreshToken });

          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        }
      } catch (refreshError) {
        message.error("登录已过期，请重新登录");
        clearAuthAndRedirect();
      }
    }

    let errorMessage = "请求失败";

    if (error.response) {
      const { status, data } = error.response;
      const responseData = data as ApiResponse | undefined;

      switch (status) {
        case HttpStatus.BAD_REQUEST:
          errorMessage = extractErrorMessage(responseData, "参数错误");
          break;
        case HttpStatus.UNAUTHORIZED:
          errorMessage = "未认证，请重新登录";
          clearAuthAndRedirect();
          break;
        case HttpStatus.FORBIDDEN:
          errorMessage = "权限不足";
          break;
        case HttpStatus.NOT_FOUND:
          errorMessage = "资源不存在";
          break;
        case HttpStatus.TOO_MANY_REQUESTS:
          errorMessage = "请求过于频繁，请稍后再试";
          break;
        case HttpStatus.INTERNAL_SERVER_ERROR:
          errorMessage = "服务器内部错误";
          break;
        case HttpStatus.BAD_GATEWAY:
          errorMessage = "网关错误";
          break;
        case HttpStatus.SERVICE_UNAVAILABLE:
          errorMessage = "服务不可用";
          break;
        case HttpStatus.GATEWAY_TIMEOUT:
          errorMessage = "请求超时";
          break;
        default:
          errorMessage = extractErrorMessage(responseData, `网络错误 (${status})`);
      }

      if (!originalRequest._skipErrorHandler) {
        message.error(errorMessage);
      }
    } else if (error.request) {
      errorMessage = "网络连接失败，请检查网络";
      if (!originalRequest._skipErrorHandler) {
        message.error(errorMessage);
      }
    } else {
      errorMessage = "请求配置错误";
      if (!originalRequest._skipErrorHandler) {
        message.error(errorMessage);
      }
    }

    const enhancedError = new Error(errorMessage) as Error & {
      originalError: AxiosError;
      status?: number;
    };
    enhancedError.originalError = error;
    enhancedError.status = error.response?.status;

    return Promise.reject(enhancedError);
  }
);

/**
 * 创建请求取消令牌
 * @returns { cancelToken: AbortController, cancel: () => void }
 */
export function createCancelToken() {
  const controller = new AbortController();
  return {
    cancelToken: controller,
    cancel: () => controller.abort(),
  };
}

/**
 * 获取API基础URL
 * @returns API基础URL
 */
export function getBaseURL(): string {
  return appConfig.api.baseURL;
}

/**
 * 获取完整API URL
 * @param endpoint 端点路径
 * @returns 完整URL
 */
export function getFullURL(endpoint: string): string {
  return `${appConfig.api.baseURL}${endpoint}`;
}

export default apiClient;

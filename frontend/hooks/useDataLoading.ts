/**
 * 数据加载自定义 Hook
 * 提供统一的数据加载、错误处理、重试机制等功能
 */

import { useState, useEffect, useCallback, useRef } from "react";
import {
  executeWithRetry,
  ErrorHandler,
  watchNetworkStatus,
  type NetworkStatus,
} from "@/lib/errorHandler";

/**
 * 数据加载配置
 */
interface UseDataLoadingConfig<T> {
  /** 数据加载函数 */
  loadData: () => Promise<T>;
  /** 是否立即加载 */
  immediate?: boolean;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 重试延迟 (ms) */
  retryDelay?: number;
  /** 是否显示错误 */
  showError?: boolean;
  /** 错误上下文 */
  errorContext?: string;
  /** 数据加载成功回调 */
  onSuccess?: (data: T) => void;
  /** 数据加载失败回调 */
  onError?: (error: unknown) => void;
  /** 网络状态变化回调 */
  onNetworkChange?: (status: NetworkStatus) => void;
}

/**
 * 数据加载返回
 */
interface UseDataLoadingReturn<T> {
  /** 数据 */
  data: T | null;
  /** 是否正在加载 */
  loading: boolean;
  /** 错误信息 */
  error: string | null;
  /** 重试次数 */
  retryCount: number;
  /** 是否首次加载 */
  isFirstLoad: boolean;
  /** 网络状态 */
  networkStatus: NetworkStatus | null;
  /** 重新加载数据 */
  reload: () => Promise<void>;
  /** 清除错误 */
  clearError: () => void;
  /** 清除数据 */
  clearData: () => void;
}

/**
 * 数据加载 Hook
 * @param config 配置项
 * @returns 加载状态和方法
 */
export function useDataLoading<T>({
  loadData,
  immediate = true,
  maxRetries = 3,
  retryDelay = 1000,
  showError = true,
  errorContext = "加载数据",
  onSuccess,
  onError,
  onNetworkChange,
}: UseDataLoadingConfig<T>): UseDataLoadingReturn<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);
  const [isFirstLoad, setIsFirstLoad] = useState(true);
  const [networkStatus, setNetworkStatus] = useState<NetworkStatus | null>(null);
  
  const loadDataRef = useRef(loadData);
  const mountedRef = useRef(false);

  // 更新 loadData 引用
  useEffect(() => {
    loadDataRef.current = loadData;
  }, [loadData]);

  // 监听网络状态
  useEffect(() => {
    if (!onNetworkChange) return;

    const unsubscribe = watchNetworkStatus((status) => {
      setNetworkStatus(status);
      onNetworkChange(status);
    });

    return unsubscribe;
  }, [onNetworkChange]);

  // 加载数据函数
  const executeLoad = useCallback(async (isRetry = false) => {
    setLoading(true);
    setError(null);

    try {
      const result = await executeWithRetry(() => loadDataRef.current(), {
        maxRetries,
        delay: retryDelay,
        retryOnNetworkError: true,
        onError: (err, attempt) => {
          setRetryCount(attempt);
          if (!isRetry) {
            console.warn(`${errorContext}失败，第 ${attempt} 次重试:`, err);
          }
        },
      });

      setData(result);
      setIsFirstLoad(false);
      setRetryCount(0);
      onSuccess?.(result);
    } catch (err) {
      const errorMessage = new ErrorHandler(err)
        .setContext(errorContext)
        .setShowError(showError)
        .handle();

      setError(errorMessage);
      setIsFirstLoad(false);
      onError?.(err);
    } finally {
      setLoading(false);
    }
  }, [maxRetries, retryDelay, showError, errorContext, onSuccess, onError]);

  // 初始加载
  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      if (immediate) {
        executeLoad();
      }
    }
  }, [immediate, executeLoad]);

  // 重新加载
  const reload = useCallback(async () => {
    await executeLoad(true);
  }, [executeLoad]);

  // 清除错误
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // 清除数据
  const clearData = useCallback(() => {
    setData(null);
    setError(null);
    setRetryCount(0);
  }, []);

  return {
    data,
    loading,
    error,
    retryCount,
    isFirstLoad,
    networkStatus,
    reload,
    clearError,
    clearData,
  };
}

/**
 * 多数据源并行加载配置
 */
interface UseParallelDataLoadingConfig<T extends Record<string, () => Promise<unknown>>> {
  /** 数据加载函数映射 */
  loaders: T;
  /** 是否立即加载 */
  immediate?: boolean;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 重试延迟 (ms) */
  retryDelay?: number;
  /** 是否显示错误 */
  showError?: boolean;
  /** 加载成功回调 */
  onSuccess?: (results: { [K in keyof T]: Awaited<ReturnType<T[K]>> }) => void;
  /** 加载失败回调 */
  onError?: (errors: Partial<Record<keyof T, string>>) => void;
}

/**
 * 多数据源并行加载返回
 */
interface UseParallelDataLoadingReturn<T extends Record<string, () => Promise<unknown>>> {
  /** 数据映射 */
  data: { [K in keyof T]?: Awaited<ReturnType<T[K]>> };
  /** 加载状态映射 */
  loading: { [K in keyof T]: boolean };
  /** 错误映射 */
  errors: Partial<Record<keyof T, string>>;
  /** 是否首次加载 */
  isFirstLoad: boolean;
  /** 重新加载所有数据 */
  reload: () => Promise<void>;
  /** 重新加载指定数据 */
  reloadKey: <K extends keyof T>(key: K) => Promise<void>;
}

/**
 * 多数据源并行加载 Hook
 * @param config 配置项
 * @returns 加载状态和方法
 */
export function useParallelDataLoading<T extends Record<string, () => Promise<unknown>>>({
  loaders,
  immediate = true,
  maxRetries = 3,
  retryDelay = 1000,
  showError = true,
  onSuccess,
  onError,
}: UseParallelDataLoadingConfig<T>): UseParallelDataLoadingReturn<T> {
  const [data, setData] = useState<{ [K in keyof T]?: Awaited<ReturnType<T[K]>> }>({});
  const [loading, setLoading] = useState<{ [K in keyof T]: boolean }>(
    Object.keys(loaders).reduce((acc, key) => {
      acc[key as keyof T] = false;
      return acc;
    }, {} as { [K in keyof T]: boolean })
  );
  const [errors, setErrors] = useState<Partial<Record<keyof T, string>>>({});
  const [isFirstLoad, setIsFirstLoad] = useState(true);

  const loadersRef = useRef(loaders);
  const mountedRef = useRef(false);

  // 更新 loaders 引用
  useEffect(() => {
    loadersRef.current = loaders;
  }, [loaders]);

  // 加载单个数据源
  const loadKey = useCallback(async <K extends keyof T>(
    key: K,
    isRetry = false
  ): Promise<void> => {
    const loader = loadersRef.current[key];
    if (!loader) return;

    setLoading((prev) => ({ ...prev, [key]: true }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));

    try {
      const result = await executeWithRetry(() => loader(), {
        maxRetries,
        delay: retryDelay,
        retryOnNetworkError: true,
      });

      setData((prev) => ({ ...prev, [key]: result }));
    } catch (err) {
      const errorMessage = new ErrorHandler(err)
        .setContext(`加载${String(key)}`)
        .setShowError(showError)
        .handle();

      setErrors((prev) => ({ ...prev, [key]: errorMessage }));
    } finally {
      setLoading((prev) => ({ ...prev, [key]: false }));
    }
  }, [maxRetries, retryDelay, showError]);

  // 加载所有数据
  const executeLoad = useCallback(async (isRetry = false) => {
    const keys = Object.keys(loadersRef.current) as (keyof T)[];
    await Promise.all(keys.map((key) => loadKey(key, isRetry)));
    setIsFirstLoad(false);
  }, [loadKey]);

  // 初始加载
  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      if (immediate) {
        executeLoad();
      }
    }
  }, [immediate, executeLoad]);

  // 重新加载所有数据
  const reload = useCallback(async () => {
    await executeLoad(true);
    onSuccess?.(data as { [K in keyof T]: Awaited<ReturnType<T[K]>> });
  }, [executeLoad, onSuccess, data]);

  // 重新加载指定数据
  const reloadKey = useCallback(async <K extends keyof T>(key: K) => {
    await loadKey(key, true);
  }, [loadKey]);

  return {
    data,
    loading,
    errors,
    isFirstLoad,
    reload,
    reloadKey,
  };
}

/**
 * 分页数据加载配置
 */
interface UsePaginatedDataLoadingConfig<T> {
  /** 数据加载函数 (接收页码和页大小) */
  loadData: (page: number, pageSize: number) => Promise<{ items: T[]; total: number }>;
  /** 初始页码 */
  initialPage?: number;
  /** 初始页大小 */
  initialPageSize?: number;
  /** 最大重试次数 */
  maxRetries?: number;
  /** 是否显示错误 */
  showError?: boolean;
  /** 错误上下文 */
  errorContext?: string;
}

/**
 * 分页数据加载返回
 */
interface UsePaginatedDataLoadingReturn<T> {
  /** 数据列表 */
  items: T[];
  /** 总数 */
  total: number;
  /** 当前页码 */
  currentPage: number;
  /** 页大小 */
  pageSize: number;
  /** 总页数 */
  totalPages: number;
  /** 是否正在加载 */
  loading: boolean;
  /** 错误信息 */
  error: string | null;
  /** 跳转页码 */
  setPage: (page: number) => Promise<void>;
  /** 设置页大小 */
  setPageSize: (size: number) => Promise<void>;
  /** 刷新当前页 */
  refresh: () => Promise<void>;
}

/**
 * 分页数据加载 Hook
 * @param config 配置项
 * @returns 加载状态和方法
 */
export function usePaginatedDataLoading<T>({
  loadData,
  initialPage = 1,
  initialPageSize = 10,
  maxRetries = 3,
  showError = true,
  errorContext = "加载数据",
}: UsePaginatedDataLoadingConfig<T>): UsePaginatedDataLoadingReturn<T> {
  const [items, setItems] = useState<T[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(initialPage);
  const [pageSize, setPageSizeState] = useState(initialPageSize);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDataRef = useRef(loadData);
  const currentPageRef = useRef(currentPage);
  const pageSizeRef = useRef(pageSize);

  // 更新引用
  useEffect(() => {
    loadDataRef.current = loadData;
  }, [loadData]);

  useEffect(() => {
    currentPageRef.current = currentPage;
  }, [currentPage]);

  useEffect(() => {
    pageSizeRef.current = pageSize;
  }, [pageSize]);

  // 加载数据
  const executeLoad = useCallback(async (page: number, size: number) => {
    setLoading(true);
    setError(null);

    try {
      const result = await executeWithRetry(
        () => loadDataRef.current(page, size),
        {
          maxRetries,
          retryOnNetworkError: true,
        }
      );

      setItems(result.items || []);
      setTotal(result.total || 0);
    } catch (err) {
      const errorMessage = new ErrorHandler(err)
        .setContext(errorContext)
        .setShowError(showError)
        .handle();

      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [maxRetries, showError, errorContext]);

  // 跳转页码
  const setPage = useCallback(async (page: number) => {
    setCurrentPage(page);
    await executeLoad(page, pageSizeRef.current);
  }, [executeLoad]);

  // 设置页大小
  const setPageSize = useCallback(async (size: number) => {
    setPageSizeState(size);
    setCurrentPage(1);
    await executeLoad(1, size);
  }, [executeLoad]);

  // 刷新当前页
  const refresh = useCallback(async () => {
    await executeLoad(currentPageRef.current, pageSizeRef.current);
  }, [executeLoad]);

  // 初始加载
  useEffect(() => {
    executeLoad(initialPage, initialPageSize);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // 计算总页数
  const totalPages = Math.ceil(total / pageSize);

  return {
    items,
    total,
    currentPage,
    pageSize,
    totalPages,
    loading,
    error,
    setPage,
    setPageSize,
    refresh,
  };
}

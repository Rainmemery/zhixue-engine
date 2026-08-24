/**
 * 调度服务层
 * 封装所有模型调度和监控相关的API调用
 * 整合了旧项目的 dispatcherService、modelInstanceService、schedulingMonitorService
 */

import apiClient from "./api";
import {
  ModelGroupType,
  ModelInstanceType,
  ModelGroupSchema,
  ModelInstanceSchema,
} from "@/types/api";
import {
  ensureArray,
  validateData,
  safeNumber,
} from "@/lib/dataTransform";

// ==================== 类型定义 ====================

export type InstanceStatus = "HEALTHY" | "DEGRADED" | "UNHEALTHY" | "DISABLED";
export type HealthState = "HEALTHY" | "DEGRADED" | "UNHEALTHY" | "UNKNOWN";
export type HealthStateLower = "healthy" | "degraded" | "unhealthy";

// ==================== 接口定义 ====================

/**
 * 模型组接口
 */
export interface ModelGroup {
  id: number;
  name: string;
  modelType: "OLLAMA" | "OPENAI" | "CUSTOM";
  description: string;
  schedulingStrategy: "ROUND_ROBIN" | "WEIGHTED_ROUND_ROBIN" | "LEAST_CONNECTIONS";
  defaultApiKey?: string;
  defaultBaseUrl: string;
  defaultTimeoutMs: number;
  defaultMaxRetries: number;
  priority: number;
  enabled: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
  totalInstances?: number;
  healthyInstances?: number;
  availableInstances?: number;
}

/**
 * 模型组DTO（带扩展信息）
 */
export interface ModelGroupDTO extends ModelGroup {
  totalInstances?: number;
  healthyInstances?: number;
  availableInstances?: number;
}

/**
 * 模型实例接口
 */
export interface ModelInstance {
  id: number;
  groupId: number;
  name: string;
  apiEndpoint: string;
  modelName: string;
  apiKey?: string;
  weight: number;
  maxConcurrent: number;
  currentConnections: number;
  status: InstanceStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * 模型实例DTO（带扩展信息）
 */
export interface ModelInstanceDTO extends ModelInstance {
  groupName?: string;
  healthStatus?: InstanceHealthStatus;
  connectionUsageRate?: number;
}

/**
 * 实例健康状态接口（基础版）
 */
export interface InstanceHealth {
  id: number;
  instanceId: number;
  healthState: HealthState;
  responseTimeMs: number;
  consecutiveFailures: number;
  consecutiveSuccesses: number;
  lastCheckTime: string;
  lastHealthyTime: string;
  errorMessage: string;
}

/**
 * 实例健康状态接口（详细版）
 */
export interface InstanceHealthStatus {
  id: number;
  instanceId: number;
  healthState: HealthStateLower;
  responseTimeMs: number;
  consecutiveFailures: number;
  consecutiveSuccesses: number;
  lastCheckTime: string;
  lastSuccessTime?: string;
  lastFailureTime?: string;
  lastErrorMessage?: string;
}

/**
 * 实例健康信息（监控用详细版）
 */
export interface InstanceHealthInfo {
  id: number;
  modelGroupId: number;
  modelGroupName: string;
  instanceName: string;
  modelType: string;
  modelName: string;
  healthState: HealthState;
  available: boolean;
  responseTimeMs: number | null;
  consecutiveFailures: number;
  currentConnections: number;
  maxConcurrent: number;
  lastCheckTime: string | null;
  lastErrorMessage: string | null;
  endpoint: string;
}

/**
 * 健康检查结果
 */
export interface HealthCheckResult {
  instanceId: number;
  instanceName: string;
  healthy: boolean;
  responseTimeMs: number;
  errorMessage?: string;
  checkTime: string;
}

/**
 * 调度日志接口
 */
export interface SchedulingLog {
  id: number;
  groupId: number;
  instanceId: number;
  requestId: string;
  action: "SELECT" | "FAILOVER" | "RECOVERY" | "HEALTH_CHECK";
  status: "SUCCESS" | "FAILURE";
  errorMessage: string;
  durationMs: number;
  createdAt: string;
}

/**
 * 调度日志（详细版）
 */
export interface SchedulingLogDetail {
  id: number;
  timestamp: string;
  modelGroupId: number;
  modelGroupName: string;
  instanceId: number;
  instanceName: string;
  operationType: "SELECT" | "SWITCH" | "FAILOVER" | "HEALTH_CHECK" | "RECOVERY";
  status: "SUCCESS" | "FAILED" | "TIMEOUT";
  durationMs: number;
  errorMessage: string | null;
  details: Record<string, any> | null;
}

/**
 * 调度状态接口
 */
export interface SchedulingStatus {
  totalGroups: number;
  enabledGroups: number;
  totalInstances: number;
  healthyInstances: number;
  unhealthyInstances: number;
  activeConnections: number;
  todayTotal: number;
  todaySuccess: number;
  todayFailure: number;
  successRate: number;
}

/**
 * 调度概览（详细版）
 */
export interface SchedulingOverview {
  totalModelGroups: number;
  enabledModelGroups: number;
  totalInstances: number;
  healthyInstances: number;
  unhealthyInstances: number;
  activeConnections: number;
  todayScheduling: {
    total: number;
    success: number;
    failed: number;
    successRate: number;
  };
}

/**
 * 故障转移状态接口
 */
export interface FailoverStatus {
  instanceId: number;
  instanceName: string;
  groupId: number;
  groupName: string;
  status: string;
  lastFailoverTime: string;
  failoverCount: number;
  recoveryAttempts: number;
}

/**
 * 故障转移状态（详细版）
 */
export interface FailoverState {
  currentActiveModel: string;
  currentActiveInstance: string;
  fallbackModel: string | null;
  failedInstances: Array<{
    instanceId: number;
    instanceName: string;
    modelType: string;
    failedAt: string;
    failureReason: string;
    recoveryAttempts: number;
  }>;
  recoveryInProgress: boolean;
  lastFailoverTime: string | null;
}

/**
 * 调度统计接口
 */
export interface SchedulingStatistics {
  date: string;
  totalRequests: number;
  successRequests: number;
  failedRequests: number;
  avgResponseTime: number;
  successRate: number;
}

/**
 * 调度策略接口
 */
export interface SchedulingStrategy {
  id: number;
  name: string;
  description: string;
  isActive: boolean;
  configJson: string;
}

/**
 * 模型组创建请求接口
 */
export interface ModelGroupCreateRequest {
  name: string;
  modelType?: "OLLAMA" | "OPENAI" | "CUSTOM";
  description?: string;
  schedulingStrategy?: "ROUND_ROBIN" | "WEIGHTED_ROUND_ROBIN" | "LEAST_CONNECTIONS";
  defaultApiKey?: string;
  defaultBaseUrl?: string;
  defaultTimeoutMs?: number;
  defaultMaxRetries?: number;
  priority?: number;
  enabled?: boolean;
  isDefault?: boolean;
}

/**
 * 模型组更新请求接口
 */
export interface ModelGroupUpdateRequest {
  name?: string;
  modelType?: "OLLAMA" | "OPENAI" | "CUSTOM";
  description?: string;
  schedulingStrategy?: "ROUND_ROBIN" | "WEIGHTED_ROUND_ROBIN" | "LEAST_CONNECTIONS";
  defaultApiKey?: string;
  defaultBaseUrl?: string;
  defaultTimeoutMs?: number;
  defaultMaxRetries?: number;
  priority?: number;
  enabled?: boolean;
  isDefault?: boolean;
}

/**
 * 模型实例创建请求接口
 */
export interface ModelInstanceCreateRequest {
  groupId: number;
  name: string;
  apiEndpoint: string;
  modelName: string;
  apiKey?: string;
  weight?: number;
  maxConcurrent?: number;
  status?: InstanceStatus;
}

/**
 * 模型实例更新请求接口
 */
export interface ModelInstanceUpdateRequest {
  name?: string;
  apiEndpoint?: string;
  modelName?: string;
  apiKey?: string;
  weight?: number;
  maxConcurrent?: number;
  status?: InstanceStatus;
}

/**
 * 日志查询参数接口
 */
export interface LogQueryParams {
  groupId?: number;
  instanceId?: number;
  action?: "SELECT" | "FAILOVER" | "RECOVERY" | "HEALTH_CHECK";
  status?: "SUCCESS" | "FAILURE";
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

/**
 * 调度日志过滤器
 */
export interface SchedulingLogFilter {
  page?: number;
  size?: number;
  modelGroupId?: number;
  instanceId?: number;
  operationType?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

/**
 * 分页响应接口
 */
export interface PaginationResponse<T> {
  items: T[];
  total: number;
  page?: number;
  size?: number;
}

// ==================== 统一响应处理函数 ====================

/**
 * 统一处理API响应
 */
async function handleResponse<T>(promise: Promise<any>): Promise<T> {
  const response = await promise;
  return response.data?.data;
}

/**
 * 处理API响应（带错误检查）
 */
async function handleApiResponse<T>(promise: Promise<any>): Promise<T> {
  const response = await promise;
  if (response.data?.code === 200 || response.data?.code === 201) {
    return response.data.data;
  } else {
    throw new Error(response.data?.message || "API调用失败");
  }
}

// ==================== 模型组服务 ====================

export const ModelGroupService = {
  /**
   * 获取所有模型组
   */
  getAll: async (): Promise<ModelGroup[]> => {
    return handleResponse<ModelGroup[]>(apiClient.get("/dispatcher/groups"));
  },

  /**
   * 获取模型组列表（兼容分页格式）
   */
  list: async (): Promise<ModelGroup[]> => {
    const response = await apiClient.get("/dispatcher/groups");
    const data = response.data?.data;
    return data?.items || data || [];
  },

  /**
   * 获取所有模型组（分页格式）
   */
  getAllGroups: async (): Promise<{ items: ModelGroupDTO[]; total: number }> => {
    return handleApiResponse<{ items: ModelGroupDTO[]; total: number }>(
      apiClient.get("/dispatcher/groups")
    );
  },

  /**
   * 根据ID获取模型组
   */
  getById: async (id: number): Promise<ModelGroup> => {
    return handleResponse<ModelGroup>(apiClient.get(`/dispatcher/groups/${id}`));
  },

  /**
   * 根据ID获取模型组（DTO格式）
   */
  getGroupById: async (id: number): Promise<ModelGroupDTO> => {
    return handleApiResponse<ModelGroupDTO>(apiClient.get(`/dispatcher/groups/${id}`));
  },

  /**
   * 创建模型组
   */
  create: async (data: ModelGroupCreateRequest): Promise<ModelGroup> => {
    return handleResponse<ModelGroup>(apiClient.post("/dispatcher/groups", data));
  },

  /**
   * 更新模型组
   */
  update: async (id: number, data: ModelGroupUpdateRequest): Promise<ModelGroup> => {
    return handleResponse<ModelGroup>(apiClient.put(`/dispatcher/groups/${id}`, data));
  },

  /**
   * 删除模型组
   */
  delete: async (id: number): Promise<void> => {
    await handleResponse<void>(apiClient.delete(`/dispatcher/groups/${id}`));
  },

  /**
   * 启用/禁用模型组
   */
  enable: async (id: number, enabled: boolean): Promise<void> => {
    await handleResponse<void>(apiClient.put(`/dispatcher/groups/${id}/enable`, { enabled }));
  },

  /**
   * 切换模型组启用状态
   */
  toggle: async (id: number, enabled: boolean): Promise<void> => {
    await handleResponse<void>(apiClient.put(`/dispatcher/groups/${id}/enable`, { enabled }));
  },

  /**
   * 设置默认模型组
   */
  setDefault: async (id: number): Promise<void> => {
    await handleResponse<void>(apiClient.put(`/dispatcher/groups/${id}/set-default`));
  },

  /**
   * 获取模型组下的所有实例
   */
  getInstances: async (groupId: number): Promise<ModelInstance[]> => {
    return handleResponse<ModelInstance[]>(apiClient.get(`/dispatcher/groups/${groupId}/instances`));
  },

  /**
   * 获取模型组下的实例（详细格式）
   */
  getGroupInstances: async (id: number): Promise<{
    groupId: number;
    groupName: string;
    items: ModelInstanceDTO[];
    total: number;
  }> => {
    return handleApiResponse<{
      groupId: number;
      groupName: string;
      items: ModelInstanceDTO[];
      total: number;
    }>(apiClient.get(`/dispatcher/groups/${id}/instances`));
  },

  /**
   * 检查模型组是否可以删除
   */
  checkCanDelete: async (id: number): Promise<{ canDelete: boolean; message?: string }> => {
    try {
      const response = await apiClient.get(`/dispatcher/groups/${id}/instances`);
      const instances = response.data?.data?.items || response.data?.data || [];
      if (instances.length > 0) {
        return { canDelete: false, message: "该模型组下存在实例，请先删除实例" };
      }
      return { canDelete: true };
    } catch {
      return { canDelete: false, message: "检查失败" };
    }
  },
};

// ==================== 模型实例服务 ====================

export const ModelInstanceService = {
  /**
   * 获取所有模型实例
   */
  getAll: async (): Promise<ModelInstance[]> => {
    return handleResponse<ModelInstance[]>(apiClient.get("/dispatcher/instances"));
  },

  /**
   * 获取所有模型实例（分页格式）
   */
  getAllInstances: async (): Promise<{ items: ModelInstance[]; total: number }> => {
    return handleApiResponse<{ items: ModelInstance[]; total: number }>(
      apiClient.get("/dispatcher/instances")
    );
  },

  /**
   * 根据ID获取模型实例
   */
  getById: async (id: number): Promise<ModelInstance> => {
    return handleResponse<ModelInstance>(apiClient.get(`/dispatcher/instances/${id}`));
  },

  /**
   * 根据ID获取模型实例（别名）
   */
  getInstanceById: async (id: number): Promise<ModelInstance> => {
    return handleApiResponse<ModelInstance>(apiClient.get(`/dispatcher/instances/${id}`));
  },

  /**
   * 根据组ID获取模型实例
   */
  getByGroupId: async (groupId: number): Promise<ModelInstance[]> => {
    return handleResponse<ModelInstance[]>(apiClient.get(`/dispatcher/groups/${groupId}/instances`));
  },

  /**
   * 根据组ID获取模型实例（分页格式）
   */
  getInstancesByGroup: async (groupId: number): Promise<{ items: ModelInstance[]; total: number }> => {
    return handleApiResponse<{ items: ModelInstance[]; total: number }>(
      apiClient.get(`/dispatcher/instances/group/${groupId}`)
    );
  },

  /**
   * 创建模型实例
   */
  create: async (data: ModelInstanceCreateRequest): Promise<ModelInstance> => {
    return handleResponse<ModelInstance>(apiClient.post("/dispatcher/instances", data));
  },

  /**
   * 创建模型实例（别名）
   */
  createInstance: async (request: ModelInstanceCreateRequest): Promise<ModelInstance> => {
    return handleApiResponse<ModelInstance>(apiClient.post("/dispatcher/instances", request));
  },

  /**
   * 更新模型实例
   */
  update: async (id: number, data: ModelInstanceUpdateRequest): Promise<ModelInstance> => {
    return handleResponse<ModelInstance>(apiClient.put(`/dispatcher/instances/${id}`, data));
  },

  /**
   * 更新模型实例（别名）
   */
  updateInstance: async (id: number, request: ModelInstanceUpdateRequest): Promise<ModelInstance> => {
    return handleApiResponse<ModelInstance>(apiClient.put(`/dispatcher/instances/${id}`, request));
  },

  /**
   * 删除模型实例
   */
  delete: async (id: number): Promise<void> => {
    await handleResponse<void>(apiClient.delete(`/dispatcher/instances/${id}`));
  },

  /**
   * 删除模型实例（别名）
   */
  deleteInstance: async (id: number): Promise<void> => {
    return handleApiResponse<void>(apiClient.delete(`/dispatcher/instances/${id}`));
  },

  /**
   * 更新模型实例状态
   */
  updateStatus: async (id: number, status: string): Promise<void> => {
    await handleResponse<void>(apiClient.put(`/dispatcher/instances/${id}/status`, { status }));
  },

  /**
   * 更新模型实例状态（返回更新结果）
   */
  updateInstanceStatus: async (id: number, status: string): Promise<{ id: number; status: string }> => {
    return handleApiResponse<{ id: number; status: string }>(
      apiClient.put(`/dispatcher/instances/${id}/status`, { status })
    );
  },

  /**
   * 获取实例健康状态
   */
  getHealth: async (id: number): Promise<InstanceHealth> => {
    return handleResponse<InstanceHealth>(apiClient.get(`/dispatcher/instances/${id}/health`));
  },

  /**
   * 获取实例健康状态（详细版）
   */
  getInstanceHealth: async (id: number): Promise<InstanceHealthStatus> => {
    return handleApiResponse<InstanceHealthStatus>(
      apiClient.get(`/dispatcher/instances/${id}/health`)
    );
  },

  /**
   * 执行健康检查
   */
  healthCheck: async (id: number): Promise<InstanceHealth> => {
    return handleResponse<InstanceHealth>(apiClient.post(`/dispatcher/instances/${id}/health-check`));
  },

  /**
   * 执行健康检查（返回详细结果）
   */
  performHealthCheck: async (id: number): Promise<HealthCheckResult> => {
    return handleApiResponse<HealthCheckResult>(
      apiClient.post(`/dispatcher/instances/${id}/health-check`)
    );
  },
};

// ==================== 调度监控服务 ====================

export const SchedulingMonitorService = {
  /**
   * 获取调度整体状态
   */
  getStatus: async (): Promise<SchedulingStatus> => {
    return handleResponse<SchedulingStatus>(apiClient.get("/dispatcher/monitor/status"));
  },

  /**
   * 获取调度概览（详细版）
   */
  getOverview: async (): Promise<SchedulingOverview> => {
    const response = await apiClient.get("/dispatcher/monitor/status");
    const data = response.data.data;
    return {
      totalModelGroups: data?.totalModelGroups || data?.totalGroups || 0,
      enabledModelGroups: data?.enabledModelGroups || data?.enabledGroups || 0,
      totalInstances: data?.totalInstances || 0,
      healthyInstances: data?.healthyInstances || 0,
      unhealthyInstances: data?.unhealthyInstances || 0,
      activeConnections: data?.activeConnections || 0,
      todayScheduling: {
        total: data?.todayScheduling?.total || data?.todayTotal || 0,
        success: data?.todayScheduling?.success || data?.todaySuccess || 0,
        failed: data?.todayScheduling?.failed || data?.todayFailure || 0,
        successRate: data?.todayScheduling?.successRate || data?.successRate || 0,
      },
    };
  },

  /**
   * 获取所有实例健康状态
   */
  getAllHealth: async (): Promise<Record<number, InstanceHealth>> => {
    return handleResponse<Record<number, InstanceHealth>>(apiClient.get("/dispatcher/monitor/health"));
  },

  /**
   * 获取实例健康列表（详细版）
   */
  getInstanceHealth: async (): Promise<InstanceHealthInfo[]> => {
    const response = await apiClient.get("/dispatcher/monitor/health");
    const data = response.data.data;
    const instances = data?.instances || [];
    return instances.map((item: any) => ({
      id: item.instanceId || item.id,
      modelGroupId: item.groupId || item.modelGroupId,
      modelGroupName: item.groupName || item.modelGroupName || "",
      instanceName: item.instanceName || item.name,
      modelType: item.modelType || "",
      modelName: item.modelName || "",
      healthState: item.healthState || item.healthStatus || "UNKNOWN",
      available: item.available !== false,
      responseTimeMs: item.responseTimeMs || item.responseTime || null,
      consecutiveFailures: item.consecutiveFailures || 0,
      currentConnections: item.currentConnections || 0,
      maxConcurrent: item.maxConcurrent || 10,
      lastCheckTime: item.lastCheckTime || item.lastHealthCheckTime || null,
      lastErrorMessage: item.lastErrorMessage || item.errorMessage || null,
      endpoint: item.endpoint || item.baseUrl || "",
    }));
  },

  /**
   * 检查单个实例健康状态
   */
  checkInstanceHealth: async (instanceId: number): Promise<InstanceHealthInfo> => {
    const response = await apiClient.post(`/dispatcher/instances/${instanceId}/health-check`);
    const item = response.data.data;
    return {
      id: item.instanceId || item.id,
      modelGroupId: item.groupId || item.modelGroupId,
      modelGroupName: item.groupName || item.modelGroupName || "",
      instanceName: item.instanceName || item.name,
      modelType: item.modelType || "",
      modelName: item.modelName || "",
      healthState: item.healthState || item.healthStatus || "UNKNOWN",
      available: item.available !== false,
      responseTimeMs: item.responseTimeMs || item.responseTime || null,
      consecutiveFailures: item.consecutiveFailures || 0,
      currentConnections: item.currentConnections || 0,
      maxConcurrent: item.maxConcurrent || 10,
      lastCheckTime: item.lastCheckTime || item.lastHealthCheckTime || null,
      lastErrorMessage: item.lastErrorMessage || item.errorMessage || null,
      endpoint: item.endpoint || item.baseUrl || "",
    };
  },

  /**
   * 执行全局健康检查
   */
  globalHealthCheck: async (): Promise<void> => {
    await handleResponse<void>(apiClient.post("/dispatcher/monitor/health-check"));
  },

  /**
   * 获取调度日志
   */
  getLogs: async (params: LogQueryParams): Promise<{ items: SchedulingLog[]; total: number }> => {
    const response = await apiClient.get("/dispatcher/monitor/logs", { params });
    return response.data?.data;
  },

  /**
   * 获取调度日志（分页格式）
   */
  getSchedulingLogs: async (filter: SchedulingLogFilter): Promise<PaginationResponse<SchedulingLogDetail>> => {
    const params: any = {};
    if (filter.page !== undefined) params.page = filter.page;
    if (filter.size !== undefined) params.size = filter.size;
    if (filter.modelGroupId !== undefined) params.groupId = filter.modelGroupId;
    if (filter.instanceId !== undefined) params.instanceId = filter.instanceId;
    if (filter.operationType) params.action = filter.operationType;
    if (filter.status) params.status = filter.status;
    if (filter.startTime) params.startTime = filter.startTime;
    if (filter.endTime) params.endTime = filter.endTime;

    const response = await apiClient.get("/dispatcher/monitor/logs", { params });
    const data = response.data.data;
    return {
      items: (data?.content || data?.items || []).map((item: any) => ({
        id: item.id,
        timestamp: item.timestamp || item.createdAt,
        modelGroupId: item.groupId || item.modelGroupId,
        modelGroupName: item.groupName || item.modelGroupName || "",
        instanceId: item.instanceId,
        instanceName: item.instanceName || "",
        operationType: item.action || item.operationType || "SELECT",
        status: item.status || "SUCCESS",
        durationMs: item.durationMs || item.duration || 0,
        errorMessage: item.errorMessage || null,
        details: item.details || null,
      })),
      total: data?.totalElements || data?.total || 0,
      page: data?.currentPage || data?.page || filter.page || 0,
      size: data?.pageSize || data?.size || filter.size || 20,
    };
  },

  /**
   * 获取调度统计数据
   */
  getStatistics: async (startDate?: string, endDate?: string): Promise<SchedulingStatistics[]> => {
    const params: Record<string, string> = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    return handleResponse<SchedulingStatistics[]>(apiClient.get("/dispatcher/monitor/statistics", { params }));
  },

  /**
   * 获取故障转移状态
   */
  getFailoverStatus: async (): Promise<FailoverStatus[]> => {
    return handleResponse<FailoverStatus[]>(apiClient.get("/dispatcher/monitor/failover-status"));
  },

  /**
   * 获取故障转移状态（详细版）
   */
  getFailoverState: async (): Promise<FailoverState> => {
    const response = await apiClient.get("/dispatcher/monitor/failover");
    const data = response.data.data;
    return {
      currentActiveModel: data?.currentActiveModel || "",
      currentActiveInstance: data?.currentActiveInstance || "",
      fallbackModel: data?.fallbackModel || null,
      failedInstances: (data?.instances || data?.failedInstances || [])
        .filter((i: any) => i.failed || i.status === "UNHEALTHY")
        .map((i: any) => ({
          instanceId: i.instanceId || i.id,
          instanceName: i.instanceName || i.name,
          modelType: i.modelType || "",
          failedAt: i.failureTime || i.failedAt || "",
          failureReason: i.failureReason || i.errorMessage || "",
          recoveryAttempts: i.failoverAttempts || i.recoveryAttempts || 0,
        })),
      recoveryInProgress: data?.recoveryInProgress || false,
      lastFailoverTime: data?.timestamp || data?.lastFailoverTime || null,
    };
  },

  /**
   * 手动恢复实例
   */
  manualRecover: async (instanceId: number): Promise<void> => {
    await handleResponse<void>(apiClient.post(`/dispatcher/monitor/recover/${instanceId}`));
  },

  /**
   * 恢复实例（返回结果）
   */
  recoverInstance: async (instanceId: number): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post(`/dispatcher/monitor/failover/recover/${instanceId}`);
    const data = response.data.data;
    return {
      success: data?.recovered || data?.success || false,
      message: data?.message || "操作完成",
    };
  },

  /**
   * 获取调度策略列表
   */
  getStrategies: async (): Promise<SchedulingStrategy[]> => {
    return handleResponse<SchedulingStrategy[]>(apiClient.get("/dispatcher/monitor/strategies"));
  },

  /**
   * 激活指定调度策略
   */
  activateStrategy: async (name: string): Promise<void> => {
    await handleResponse<void>(apiClient.put(`/dispatcher/monitor/strategies/${name}/activate`));
  },

  /**
   * 获取模型组列表（简化版）
   */
  getModelGroups: async (): Promise<Array<{ id: number; name: string }>> => {
    const response = await apiClient.get("/dispatcher/groups");
    const data = response.data.data;
    return (data?.items || data || []).map((item: any) => ({
      id: item.id,
      name: item.name,
    }));
  },

  /**
   * 获取实例列表
   */
  getInstances: async (modelGroupId?: number): Promise<Array<{ id: number; name: string; modelGroupId: number }>> => {
    const response = await apiClient.get("/dispatcher/instances");
    const data = response.data.data;
    let instances = data?.items || data || [];
    if (modelGroupId !== undefined) {
      instances = instances.filter((i: any) => i.groupId === modelGroupId || i.modelGroupId === modelGroupId);
    }
    return instances.map((item: any) => ({
      id: item.id,
      name: item.name || item.instanceName,
      modelGroupId: item.groupId || item.modelGroupId,
    }));
  },
};

// ==================== 小驼峰命名导出（与新项目风格兼容）====================

export const modelGroupService = ModelGroupService;
export const modelInstanceService = ModelInstanceService;
export const schedulingMonitorService = SchedulingMonitorService;

// ==================== 统一服务导出 ====================

export const dispatcherService = {
  modelGroup: ModelGroupService,
  modelInstance: ModelInstanceService,
  monitor: SchedulingMonitorService,
};

export default dispatcherService;

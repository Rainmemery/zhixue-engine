import apiClient from './api';

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

export interface InstanceHealth {
  id: number;
  modelGroupId: number;
  modelGroupName: string;
  instanceName: string;
  modelType: string;
  modelName: string;
  healthState: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'UNKNOWN';
  available: boolean;
  responseTimeMs: number | null;
  consecutiveFailures: number;
  lastCheckTime: string | null;
  lastErrorMessage: string | null;
  endpoint: string;
}

export interface SchedulingLog {
  id: number;
  timestamp: string;
  modelGroupId: number;
  modelGroupName: string;
  instanceId: number;
  instanceName: string;
  operationType: 'SELECT' | 'SWITCH' | 'FAILOVER' | 'HEALTH_CHECK' | 'RECOVERY';
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT';
  durationMs: number;
  errorMessage: string | null;
  details: Record<string, any> | null;
}

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

export interface PaginationResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

async function handleApiResponse<T>(promise: Promise<any>): Promise<T> {
  const response = await promise;
  if (response.data?.code === 200 || response.data?.code === 201) {
    return response.data.data;
  } else {
    throw new Error(response.data?.message || response.data?.msg || 'API调用失败');
  }
}

export const SchedulingMonitorService = {
  getOverview: async (): Promise<SchedulingOverview> => {
    const response = await apiClient.get('/dispatcher/monitor/status');
    const data = response.data?.data;
    return {
      totalModelGroups: data?.totalModelGroups || 0,
      enabledModelGroups: data?.enabledModelGroups || 0,
      totalInstances: data?.totalInstances || 0,
      healthyInstances: data?.healthyInstances || 0,
      unhealthyInstances: data?.unhealthyInstances || 0,
      activeConnections: data?.activeConnections || 0,
      todayScheduling: {
        total: data?.todayScheduling?.total || 0,
        success: data?.todayScheduling?.success || 0,
        failed: data?.todayScheduling?.failed || 0,
        successRate: data?.todayScheduling?.successRate || 0,
      },
    };
  },

  getInstanceHealth: async (): Promise<InstanceHealth[]> => {
    const response = await apiClient.get('/dispatcher/monitor/health');
    const data = response.data?.data;
    const instances = data?.instances || [];
    return instances.map((item: any) => ({
      id: item.instanceId || item.id,
      modelGroupId: item.groupId || item.modelGroupId,
      modelGroupName: item.groupName || item.modelGroupName || '',
      instanceName: item.instanceName || item.name,
      modelType: item.modelType || '',
      modelName: item.modelName || '',
      healthState: item.healthState || item.healthStatus || 'UNKNOWN',
      available: item.available !== false,
      responseTimeMs: item.responseTimeMs || item.responseTime || null,
      consecutiveFailures: item.consecutiveFailures || 0,
      lastCheckTime: item.lastCheckTime || item.lastHealthCheckTime || null,
      lastErrorMessage: item.lastErrorMessage || item.errorMessage || null,
      endpoint: item.endpoint || item.baseUrl || '',
    }));
  },

  checkInstanceHealth: async (instanceId: number): Promise<InstanceHealth> => {
    const response = await apiClient.post(`/dispatcher/instances/${instanceId}/health-check`);
    const item = response.data?.data;
    return {
      id: item.instanceId || item.id,
      modelGroupId: item.groupId || item.modelGroupId,
      modelGroupName: item.groupName || item.modelGroupName || '',
      instanceName: item.instanceName || item.name,
      modelType: item.modelType || '',
      modelName: item.modelName || '',
      healthState: item.healthState || item.healthStatus || 'UNKNOWN',
      available: item.available !== false,
      responseTimeMs: item.responseTimeMs || item.responseTime || null,
      consecutiveFailures: item.consecutiveFailures || 0,
      lastCheckTime: item.lastCheckTime || item.lastHealthCheckTime || null,
      lastErrorMessage: item.lastErrorMessage || item.errorMessage || null,
      endpoint: item.endpoint || item.baseUrl || '',
    };
  },

  getSchedulingLogs: async (filter: SchedulingLogFilter): Promise<PaginationResponse<SchedulingLog>> => {
    const params: any = {};
    if (filter.page !== undefined) params.page = filter.page;
    if (filter.size !== undefined) params.size = filter.size;
    if (filter.modelGroupId !== undefined) params.groupId = filter.modelGroupId;
    if (filter.instanceId !== undefined) params.instanceId = filter.instanceId;
    if (filter.operationType) params.action = filter.operationType;
    if (filter.status) params.status = filter.status;
    if (filter.startTime) params.startTime = filter.startTime;
    if (filter.endTime) params.endTime = filter.endTime;

    const response = await apiClient.get('/dispatcher/monitor/logs', { params });
    const data = response.data?.data;
    return {
      items: (data?.content || data?.items || []).map((item: any) => ({
        id: item.id,
        timestamp: item.timestamp || item.createdAt,
        modelGroupId: item.groupId || item.modelGroupId,
        modelGroupName: item.groupName || item.modelGroupName || '',
        instanceId: item.instanceId,
        instanceName: item.instanceName || '',
        operationType: item.action || item.operationType || 'SELECT',
        status: item.status || 'SUCCESS',
        durationMs: item.durationMs || item.duration || 0,
        errorMessage: item.errorMessage || null,
        details: item.details || null,
      })),
      total: data?.totalElements || data?.total || 0,
      page: data?.currentPage || data?.page || filter.page || 0,
      size: data?.pageSize || data?.size || filter.size || 20,
    };
  },

  getFailoverState: async (): Promise<FailoverState> => {
    const response = await apiClient.get('/dispatcher/monitor/failover');
    const data = response.data?.data;
    return {
      currentActiveModel: data?.currentActiveModel || '',
      currentActiveInstance: data?.currentActiveInstance || '',
      fallbackModel: data?.fallbackModel || null,
      failedInstances: (data?.instances || data?.failedInstances || [])
        .filter((i: any) => i.failed || i.status === 'UNHEALTHY')
        .map((i: any) => ({
          instanceId: i.instanceId || i.id,
          instanceName: i.instanceName || i.name,
          modelType: i.modelType || '',
          failedAt: i.failureTime || i.failedAt || '',
          failureReason: i.failureReason || i.errorMessage || '',
          recoveryAttempts: i.failoverAttempts || i.recoveryAttempts || 0,
        })),
      recoveryInProgress: data?.recoveryInProgress || false,
      lastFailoverTime: data?.timestamp || data?.lastFailoverTime || null,
    };
  },

  recoverInstance: async (instanceId: number): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post(`/dispatcher/monitor/failover/recover/${instanceId}`);
    const data = response.data?.data;
    return {
      success: data?.recovered || data?.success || false,
      message: data?.message || '操作完成',
    };
  },

  getModelGroups: async (): Promise<Array<{ id: number; name: string }>> => {
    const response = await apiClient.get('/dispatcher/groups');
    const data = response.data?.data;
    return (data?.items || data || []).map((item: any) => ({
      id: item.id,
      name: item.name,
    }));
  },

  getInstances: async (modelGroupId?: number): Promise<Array<{ id: number; name: string; modelGroupId: number }>> => {
    const response = await apiClient.get('/dispatcher/instances');
    const data = response.data?.data;
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

export default SchedulingMonitorService;

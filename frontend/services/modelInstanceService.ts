import apiClient from './api';

export type InstanceStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'DISABLED';

export type HealthState = 'healthy' | 'degraded' | 'unhealthy';

export interface ModelInstance {
  id: number;
  groupId: number;
  name: string;
  apiEndpoint: string;
  modelName: string;
  weight: number;
  maxConcurrent: number;
  currentConnections: number;
  status: InstanceStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface InstanceHealthStatus {
  id: number;
  instanceId: number;
  healthState: HealthState;
  responseTimeMs: number;
  consecutiveFailures: number;
  consecutiveSuccesses: number;
  lastCheckTime: string;
  lastSuccessTime?: string;
  lastFailureTime?: string;
  lastErrorMessage?: string;
}

export interface ModelInstanceDTO extends ModelInstance {
  groupName?: string;
  healthStatus?: InstanceHealthStatus;
  connectionUsageRate?: number;
}

export interface ModelGroup {
  id: number;
  name: string;
  description?: string;
  schedulingStrategy?: string;
  priority?: number;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ModelGroupDTO extends ModelGroup {
  totalInstances?: number;
  healthyInstances?: number;
  availableInstances?: number;
}

export interface ModelInstanceCreateRequest {
  groupId: number;
  name: string;
  apiEndpoint: string;
  modelName: string;
  apiKey?: string;
  weight?: number;
  maxConcurrent?: number;
}

export interface ModelInstanceUpdateRequest {
  name?: string;
  apiEndpoint?: string;
  modelName?: string;
  apiKey?: string;
  weight?: number;
  maxConcurrent?: number;
}

export interface HealthCheckResult {
  instanceId: number;
  instanceName: string;
  healthy: boolean;
  responseTimeMs: number;
  errorMessage?: string;
  checkTime: string;
}

async function handleApiResponse<T>(promise: Promise<any>): Promise<T> {
  const response = await promise;
  if (response.data?.code === 200 || response.data?.code === 201) {
    return response.data.data;
  } else {
    throw new Error(response.data?.message || response.data?.msg || 'API调用失败');
  }
}

export const ModelInstanceService = {
  getAllInstances: async (): Promise<{ items: ModelInstance[]; total: number }> => {
    return handleApiResponse<{ items: ModelInstance[]; total: number }>(
      apiClient.get('/dispatcher/instances')
    );
  },

  getInstanceById: async (id: number): Promise<ModelInstance> => {
    return handleApiResponse<ModelInstance>(
      apiClient.get(`/dispatcher/instances/${id}`)
    );
  },

  getInstancesByGroup: async (groupId: number): Promise<{ items: ModelInstance[]; total: number }> => {
    return handleApiResponse<{ items: ModelInstance[]; total: number }>(
      apiClient.get(`/dispatcher/instances/group/${groupId}`)
    );
  },

  createInstance: async (request: ModelInstanceCreateRequest): Promise<ModelInstance> => {
    return handleApiResponse<ModelInstance>(
      apiClient.post('/dispatcher/instances', request)
    );
  },

  updateInstance: async (id: number, request: ModelInstanceUpdateRequest): Promise<ModelInstance> => {
    return handleApiResponse<ModelInstance>(
      apiClient.put(`/dispatcher/instances/${id}`, request)
    );
  },

  deleteInstance: async (id: number): Promise<void> => {
    return handleApiResponse<void>(
      apiClient.delete(`/dispatcher/instances/${id}`)
    );
  },

  updateInstanceStatus: async (id: number, status: string): Promise<{ id: number; status: string }> => {
    return handleApiResponse<{ id: number; status: string }>(
      apiClient.put(`/dispatcher/instances/${id}/status`, { status })
    );
  },

  getInstanceHealth: async (id: number): Promise<InstanceHealthStatus> => {
    return handleApiResponse<InstanceHealthStatus>(
      apiClient.get(`/dispatcher/instances/${id}/health`)
    );
  },

  performHealthCheck: async (id: number): Promise<HealthCheckResult> => {
    return handleApiResponse<HealthCheckResult>(
      apiClient.post(`/dispatcher/instances/${id}/health-check`)
    );
  },
};

export const ModelGroupService = {
  getAllGroups: async (): Promise<{ items: ModelGroupDTO[]; total: number }> => {
    return handleApiResponse<{ items: ModelGroupDTO[]; total: number }>(
      apiClient.get('/dispatcher/groups')
    );
  },

  getGroupById: async (id: number): Promise<ModelGroupDTO> => {
    return handleApiResponse<ModelGroupDTO>(
      apiClient.get(`/dispatcher/groups/${id}`)
    );
  },

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
};

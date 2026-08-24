import apiClient from './api';

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

export interface EmbeddingModelCreateRequest {
  modelName: string;
  displayName: string;
  dimension: number;
  description?: string;
  status?: boolean;
}

export interface EmbeddingModelUpdateRequest {
  displayName?: string;
  description?: string;
  status?: boolean;
}

export interface EmbeddingModelValidateResult {
  modelName: string;
  available: boolean;
  dimension?: number;
  message: string;
}

async function handleApiResponse<T>(promise: Promise<any>): Promise<T> {
  const response = await promise;
  if (response.data?.code === 200 || response.data?.code === 201) {
    return response.data.data;
  } else {
    throw new Error(response.data?.message || response.data?.msg || 'API调用失败');
  }
}

export class EmbeddingModelService {
  static async list(): Promise<EmbeddingModel[]> {
    return handleApiResponse<EmbeddingModel[]>(
      apiClient.get('/rag/embedding-model/list')
    );
  }

  static async listEnabled(): Promise<EmbeddingModel[]> {
    return handleApiResponse<EmbeddingModel[]>(
      apiClient.get('/rag/embedding-model/enabled')
    );
  }

  static async getById(id: number): Promise<EmbeddingModel> {
    return handleApiResponse<EmbeddingModel>(
      apiClient.get(`/rag/embedding-model/${id}`)
    );
  }

  static async getDefault(): Promise<EmbeddingModel> {
    return handleApiResponse<EmbeddingModel>(
      apiClient.get('/rag/embedding-model/default')
    );
  }

  static async create(data: EmbeddingModelCreateRequest): Promise<EmbeddingModel> {
    return handleApiResponse<EmbeddingModel>(
      apiClient.post('/rag/embedding-model', data)
    );
  }

  static async update(id: number, data: EmbeddingModelUpdateRequest): Promise<EmbeddingModel> {
    return handleApiResponse<EmbeddingModel>(
      apiClient.put(`/rag/embedding-model/${id}`, data)
    );
  }

  static async delete(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/rag/embedding-model/${id}`)
    );
  }

  static async setDefault(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.post(`/rag/embedding-model/${id}/set-default`)
    );
  }

  static async toggle(id: number, enabled: boolean): Promise<void> {
    return handleApiResponse<void>(
      apiClient.post(`/rag/embedding-model/${id}/toggle?enabled=${enabled}`)
    );
  }

  static async validate(modelName: string): Promise<EmbeddingModelValidateResult> {
    return handleApiResponse<EmbeddingModelValidateResult>(
      apiClient.post('/rag/embedding-model/validate', { modelName })
    );
  }
}

export const embeddingModelService = new EmbeddingModelService();
export default embeddingModelService;

import apiClient from './api';
import { buildPath } from '@/config/api.config';
import { Problem } from '@/types';

export interface CreateProblemRequest {
  title: string;
  titleEn?: string;
  description: string;
  inputDescription?: string;
  outputDescription?: string;
  hint?: string;
  source?: string;
  difficulty: 'easy' | 'medium' | 'hard';
  problemType?: 'traditional' | 'interactive' | 'special_judge';
  categoryId?: number;
  tagIds?: number[];
  samples?: { input: string; output: string; explanation?: string }[];
  templateCode?: Record<string, string>;
  solutionCode?: Record<string, string>;
  timeLimitMs?: number;
  memoryLimitMb?: number;
  isPublic?: boolean;
}

export type UpdateProblemRequest = Partial<CreateProblemRequest>;

export interface ProblemCategory {
  id: number;
  name: string;
  nameEn?: string;
  description?: string;
  icon?: string;
  parentId?: number;
  sortOrder?: number;
  problemCount?: number;
  isActive?: boolean;
}

export interface ProblemTag {
  id: number;
  name: string;
  nameEn?: string;
  color?: string;
  problemCount?: number;
}

export interface TestcaseInfo {
  id: number;
  problemId: number;
  testcaseName: string;
  inputFilePath: string;
  outputFilePath: string;
  inputFileSize: number;
  outputFileSize: number;
  timeLimitMs: number;
  memoryLimitMb: number;
  isSample: boolean;
  sortOrder: number;
}

export interface PaginationResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

async function handleApiResponse<T>(promise: Promise<unknown>): Promise<T> {
  const response = await promise as { data?: { code?: number; data?: T; message?: string; msg?: string } };
  if (response.data?.code === 200 || response.data?.code === 201) {
    return response.data.data as T;
  } else {
    throw new Error(response.data?.message || response.data?.msg || 'API 调用失败');
  }
}

export class ProblemAdminService {
  static async getProblems(params?: {
    page?: number;
    size?: number;
    search?: string;
    difficulty?: 'easy' | 'medium' | 'hard';
    categoryId?: number;
  }): Promise<PaginationResponse<Problem>> {
    return handleApiResponse<PaginationResponse<Problem>>(
      apiClient.get('/admin/problems', { params })
    );
  }

  static async getProblemById(id: number): Promise<Problem> {
    return handleApiResponse<Problem>(
      apiClient.get(`/admin/problems/${id}`)
    );
  }

  static async createProblem(data: CreateProblemRequest): Promise<Problem> {
    return handleApiResponse<Problem>(
      apiClient.post('/admin/problems', data)
    );
  }

  static async updateProblem(id: number, data: UpdateProblemRequest): Promise<Problem> {
    return handleApiResponse<Problem>(
      apiClient.put(`/admin/problems/${id}`, data)
    );
  }

  static async deleteProblem(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/problems/${id}`)
    );
  }

  static async batchDeleteProblems(ids: number[]): Promise<void> {
    return handleApiResponse<void>(
      apiClient.post('/admin/problems/batch-delete', { ids })
    );
  }

  static async getCategories(): Promise<ProblemCategory[]> {
    return handleApiResponse<ProblemCategory[]>(
      apiClient.get('/admin/problems/categories')
    );
  }

  static async createCategory(data: Partial<ProblemCategory>): Promise<ProblemCategory> {
    return handleApiResponse<ProblemCategory>(
      apiClient.post('/admin/problems/categories', data)
    );
  }

  static async updateCategory(id: number, data: Partial<ProblemCategory>): Promise<ProblemCategory> {
    return handleApiResponse<ProblemCategory>(
      apiClient.put(`/admin/problems/categories/${id}`, data)
    );
  }

  static async deleteCategory(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/problems/categories/${id}`)
    );
  }

  static async getTags(): Promise<ProblemTag[]> {
    return handleApiResponse<ProblemTag[]>(
      apiClient.get('/admin/problems/tags')
    );
  }

  static async createTag(data: Partial<ProblemTag>): Promise<ProblemTag> {
    return handleApiResponse<ProblemTag>(
      apiClient.post('/admin/problems/tags', data)
    );
  }

  static async updateTag(id: number, data: Partial<ProblemTag>): Promise<ProblemTag> {
    return handleApiResponse<ProblemTag>(
      apiClient.put(`/admin/problems/tags/${id}`, data)
    );
  }

  static async deleteTag(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/problems/tags/${id}`)
    );
  }

  static async uploadTestcases(problemId: number, files: File[], defaultTimeLimitMs?: number, defaultMemoryLimitMb?: number): Promise<TestcaseInfo[]> {
    const formData = new FormData();
    files.forEach(f => formData.append('files', f));
    const params: any = {};
    if (defaultTimeLimitMs) params.defaultTimeLimitMs = defaultTimeLimitMs;
    if (defaultMemoryLimitMb) params.defaultMemoryLimitMb = defaultMemoryLimitMb;
    return handleApiResponse<TestcaseInfo[]>(
      apiClient.post(`/admin/problems/${problemId}/testcases/upload`, formData, {
        params,
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    );
  }

  static async getTestcases(problemId: number): Promise<TestcaseInfo[]> {
    return handleApiResponse<TestcaseInfo[]>(
      apiClient.get(`/admin/problems/${problemId}/testcases`)
    );
  }

  static async updateTestcase(problemId: number, testcaseId: number, data: { timeLimitMs?: number; memoryLimitMb?: number }): Promise<TestcaseInfo> {
    return handleApiResponse<TestcaseInfo>(
      apiClient.put(`/admin/problems/${problemId}/testcases/${testcaseId}`, data)
    );
  }

  static async deleteTestcase(problemId: number, testcaseId: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/problems/${problemId}/testcases/${testcaseId}`)
    );
  }

  static async batchDeleteTestcases(problemId: number, ids: number[]): Promise<void> {
    return handleApiResponse<void>(
      apiClient.post(`/admin/problems/${problemId}/testcases/batch-delete`, { ids })
    );
  }
}

export const problemAdminService = new ProblemAdminService();

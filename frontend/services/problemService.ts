/**
 * 题目服务层
 * 封装所有编程题目相关的API调用
 */

import { apiEndpoints, buildPath } from "@/config/api.config";
import apiClient, { ApiResponse } from "./api";

// 题目类型定义
export interface Problem {
  id: number;
  title: string;
  titleEn?: string;
  description: string;
  inputDescription?: string;
  outputDescription?: string;
  hint?: string;
  source?: string;
  difficulty: string;
  category: string;
  problemType?: string;
  categoryId?: number;
  categoryName?: string;
  tags: string[];
  acceptanceRate: number;
  submitCount: number;
  acceptedCount: number;
  timeLimit?: number;
  timeLimitMs?: number;
  memoryLimit?: number;
  memoryLimitMb?: number;
  examples?: ProblemExample[];
  samples?: ProblemSample[];
  constraints?: string;
  templateCode?: Record<string, string>;
  userStatus?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt?: string;
}

// 题目示例
export interface ProblemExample {
  id: number;
  input: string;
  output: string;
  explanation?: string;
}

export interface ProblemSample {
  id: number;
  input: string;
  output: string;
  explanation?: string;
}

// 提交记录
export interface Submission {
  id: number;
  userId: number;
  problemId: number;
  problemTitle?: string;
  language: string;
  code: string;
  status: string;
  score?: number;
  totalTimeMs?: number;
  maxMemoryKb?: number;
  errorMessage?: string;
  passedCount?: number;
  totalCount?: number;
  createdAt: string;
}

// 题目分类
export interface SubmitCodeResponse {
  submissionId: number;
  status: string;
  engine?: string;
}

export interface ProblemCategory {
  id: number;
  name: string;
  nameEn?: string;
  description?: string;
  problemCount?: number;
}

// 题目标签
export interface ProblemTag {
  id: number;
  name: string;
  nameEn?: string;
  color?: string;
  problemCount?: number;
}

/**
 * 题目服务
 */
export const problemService = {
  /**
   * 获取题目列表
   * @param params 查询参数
   * @returns 题目列表
   */
  getProblems: (params?: {
    page?: number;
    size?: number;
    difficulty?: string;
    category?: string;
    tags?: string[];
    search?: string;
    tagId?: number;
    problemType?: string;
    sort?: string;
    userId?: number;
  }): Promise<ApiResponse<{ items: Problem[]; total: number }>> => {
    return apiClient
      .get(apiEndpoints.problem.base, { params })
      .then((res) => res.data);
  },

  /**
   * 获取单个题目
   * @param id 题目ID
   * @returns 题目详情
   */
  getProblemById: (id: number): Promise<ApiResponse<Problem>> => {
    return apiClient
      .get(`${apiEndpoints.problem.base}/${id}`)
      .then((res) => res.data);
  },

  getProblemDetail: (id: number, userId?: number): Promise<ApiResponse<Problem>> => {
    const params: any = {};
    if (userId) params.userId = userId;
    return apiClient
      .get(`${apiEndpoints.problem.base}/${id}`, { params })
      .then((res) => res.data);
  },

  /**
   * 获取题目分类列表
   * @returns 分类列表
   */
  getCategories: (): Promise<ApiResponse<ProblemCategory[]>> => {
    return apiClient
      .get(apiEndpoints.problem.categories)
      .then((res) => res.data);
  },

  /**
   * 获取题目标签列表
   * @returns 标签列表
   */
  getTags: (): Promise<ApiResponse<ProblemTag[]>> => {
    return apiClient.get(apiEndpoints.problem.tags).then((res) => res.data);
  },

  /**
   * 提交代码
   * @param problemId 题目ID
   * @param language 编程语言
   * @param code 代码
   * @returns 提交结果
   */
  submitCode: (
    problemId: number,
    language: string,
    code: string
  ): Promise<ApiResponse<SubmitCodeResponse>> => {
    const path = buildPath(apiEndpoints.problem.submit, { id: problemId });
    return apiClient.post(path, { language, code }).then((res) => res.data);
  },

  /**
   * 运行测试
   * @param problemId 题目ID
   * @param language 编程语言
   * @param code 代码
   * @returns 运行结果
   */
  runCode: (
    problemId: number,
    language: string,
    code: string,
    customInput?: string,
    customExpectedOutput?: string
  ): Promise<ApiResponse<Submission>> => {
    const path = buildPath(apiEndpoints.problem.run, { id: problemId });
    const data: Record<string, string> = { language, code };
    if (customInput) data.customInput = customInput;
    if (customExpectedOutput) data.customExpectedOutput = customExpectedOutput;
    return apiClient.post(path, data).then((res) => res.data);
  },

  /**
   * 获取提交记录
   * @param params 查询参数
   * @returns 提交记录列表
   */
  getSubmissions: (params?: {
    page?: number;
    size?: number;
    problemId?: number;
    status?: string;
  }): Promise<ApiResponse<{ items: Submission[]; total: number }>> => {
    return apiClient
      .get(`${apiEndpoints.problem.base}/submissions`, { params })
      .then((res) => res.data);
  },

  getProblemSubmissions: (problemId: number, userId?: number, page: number = 1, size: number = 10) => {
    const params: Record<string, any> = { problemId, page, size };
    if (userId) params.userId = userId;
    return apiClient
      .get(`/submissions`, { params })
      .then((res) => res.data);
  },

  getSubmissionList: (params?: {
    page?: number;
    size?: number;
    problemId?: number;
    userId?: number;
    language?: string;
    status?: string;
  }): Promise<ApiResponse<{ items: Submission[]; total: number }>> => {
    return apiClient
      .get("/submissions", { params })
      .then((res) => res.data);
  },

  getSubmissionDetail: (id: number): Promise<ApiResponse<Submission>> => {
    return apiClient
      .get(`/submissions/${id}`)
      .then((res) => res.data);
  },

  uploadTestcases: (problemId: number, file: File): Promise<ApiResponse<any>> => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient
      .post(`/admin/problems/${problemId}/testcases/upload`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })
      .then((res) => res.data);
  },

  getTestcases: (problemId: number): Promise<ApiResponse<any[]>> => {
    return apiClient
      .get(`/admin/problems/${problemId}/testcases`)
      .then((res) => res.data);
  },

  updateTestcase: (problemId: number, testcaseId: number, data: any): Promise<ApiResponse<any>> => {
    return apiClient
      .put(`/admin/problems/${problemId}/testcases/${testcaseId}`, data)
      .then((res) => res.data);
  },

  deleteTestcase: (problemId: number, testcaseId: number): Promise<ApiResponse<void>> => {
    return apiClient
      .delete(`/admin/problems/${problemId}/testcases/${testcaseId}`)
      .then((res) => res.data);
  },

  /**
   * 创建题目（管理员）
   * @param data 题目数据
   * @returns 创建的题目
   */
  createProblem: (data: Partial<Problem>): Promise<ApiResponse<Problem>> => {
    return apiClient.post(apiEndpoints.problem.base, data).then((res) => res.data);
  },

  /**
   * 更新题目（管理员）
   * @param id 题目ID
   * @param data 题目数据
   * @returns 更新后的题目
   */
  updateProblem: (id: number, data: Partial<Problem>): Promise<ApiResponse<Problem>> => {
    return apiClient
      .put(`${apiEndpoints.problem.base}/${id}`, data)
      .then((res) => res.data);
  },

  /**
   * 删除题目（管理员）
   * @param id 题目ID
   */
  deleteProblem: (id: number): Promise<ApiResponse<void>> => {
    return apiClient
      .delete(`${apiEndpoints.problem.base}/${id}`)
      .then((res) => res.data);
  },

  /**
   * 批量删除题目（管理员）
   * @param ids 题目ID列表
   */
  batchDeleteProblems: (ids: number[]): Promise<ApiResponse<void>> => {
    return apiClient
      .post(`${apiEndpoints.problem.base}/batch-delete`, { ids })
      .then((res) => res.data);
  },

  /**
   * 创建分类（管理员）
   * @param data 分类数据
   * @returns 创建的分类
   */
  createCategory: (data: Partial<ProblemCategory>): Promise<ApiResponse<ProblemCategory>> => {
    return apiClient
      .post(apiEndpoints.problem.categories, data)
      .then((res) => res.data);
  },

  /**
   * 更新分类（管理员）
   * @param id 分类ID
   * @param data 分类数据
   * @returns 更新后的分类
   */
  updateCategory: (id: number, data: Partial<ProblemCategory>): Promise<ApiResponse<ProblemCategory>> => {
    return apiClient
      .put(`${apiEndpoints.problem.categories}/${id}`, data)
      .then((res) => res.data);
  },

  /**
   * 删除分类（管理员）
   * @param id 分类ID
   */
  deleteCategory: (id: number): Promise<ApiResponse<void>> => {
    return apiClient
      .delete(`${apiEndpoints.problem.categories}/${id}`)
      .then((res) => res.data);
  },
};

export default problemService;

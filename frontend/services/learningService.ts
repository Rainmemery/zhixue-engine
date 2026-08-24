/**
 * 学习路径服务层
 * 封装所有学习路径相关的API调用
 */

import apiClient, { ApiResponse } from "./api";
import {
  LearningRecord,
  LearningPath,
  LearningStep,
} from "@/types";

export interface LearningPathStep {
  stepNumber: number;
  problemId: number;
  problemTitle: string;
  estimatedTimeMinutes: number;
  prerequisiteIds: number[];
  status: "pending" | "in_progress" | "completed";
  score?: number;
  timeSpentMinutes?: number;
}

export interface LearningProgress {
  pathId: number | string;
  pathTitle: string;
  totalProblems: number;
  completedProblems: number;
  progress: number;
  currentStage: number;
  estimatedRemainingHours: number;
  lastStudiedAt?: string;
}

const transformLearningPath = (path: any): LearningPath => {
  return {
    ...path,
    focusAreas:
      typeof path.focusAreas === "string"
        ? path.focusAreas.split(",").map((s: string) => s.trim())
        : path.focusAreas,
  };
};

export const learningService = {
  getLearningRecords: (params?: {
    userId?: number;
    problemId?: number;
    status?: "solved" | "attempted" | "unsolved";
    startDate?: string;
    endDate?: string;
    page?: number;
    size?: number;
  }): Promise<
    ApiResponse<{
      items: LearningRecord[];
      total: number;
      page: number;
      size: number;
    }>
  > => {
    return apiClient.get("/learning/records", { params });
  },

  createLearningRecord: (
    recordData: Omit<LearningRecord, "id" | "createdAt" | "updatedAt">
  ): Promise<ApiResponse<LearningRecord>> => {
    return apiClient.post("/learning/records", recordData);
  },

  generateLearningPath: (pathData: {
    userId: number;
    goal: string;
    targetDays: number;
    dailyMinutes: number;
    focusAreas: string;
  }): Promise<ApiResponse<LearningPath>> => {
    return apiClient.post("/learning/paths/generate", pathData);
  },

  getLearningPath: async (pathId: string): Promise<ApiResponse<LearningPath>> => {
    const response = await apiClient.get(`/learning/paths/${pathId}`);
    if (response.data?.code === 200 && response.data?.data) {
      return { ...response.data, data: transformLearningPath(response.data.data) };
    }
    return response.data;
  },

  updateLearningPathProgress: (
    pathId: string,
    stepData: {
      stepNumber: number;
      status: "pending" | "in_progress" | "completed";
      score?: number;
      timeSpentMinutes?: number;
    }
  ): Promise<ApiResponse<LearningPathStep>> => {
    return apiClient.put(`/learning/paths/${pathId}/progress`, stepData);
  },

  getUserLearningPaths: async (
    userId: number
  ): Promise<ApiResponse<{ items: LearningPath[]; total: number }>> => {
    const response = await apiClient.get(`/learning/paths/user/${userId}`);
    if (response.data?.code === 200 && response.data?.data) {
      const paths = Array.isArray(response.data.data) ? response.data.data : [];
      const items = paths.map(transformLearningPath);
      return { ...response.data, data: { items, total: items.length } };
    }
    return response.data;
  },

  startLearningPath: async (pathId: string | number): Promise<ApiResponse<LearningPath>> => {
    const response = await apiClient.post(`/learning/paths/${pathId}/start`);
    if (response.data?.code === 200 && response.data?.data) {
      return { ...response.data, data: transformLearningPath(response.data.data) };
    }
    return response.data;
  },

  pauseLearningPath: async (pathId: string): Promise<ApiResponse<LearningPath>> => {
    const response = await apiClient.post(`/learning/paths/${pathId}/pause`);
    if (response.data?.code === 200 && response.data?.data) {
      return { ...response.data, data: transformLearningPath(response.data.data) };
    }
    return response.data;
  },

  completeLearningPath: async (pathId: string): Promise<ApiResponse<LearningPath>> => {
    const response = await apiClient.post(`/learning/paths/${pathId}/complete`);
    if (response.data?.code === 200 && response.data?.data) {
      return { ...response.data, data: transformLearningPath(response.data.data) };
    }
    return response.data;
  },

  continueLearningPath: async (pathId: string): Promise<ApiResponse<LearningPath>> => {
    const response = await apiClient.post(`/learning/paths/${pathId}/continue`);
    if (response.data?.code === 200 && response.data?.data) {
      return { ...response.data, data: transformLearningPath(response.data.data) };
    }
    return response.data;
  },

  getRecommendedPaths: async (): Promise<
    ApiResponse<{ items: LearningPath[]; total: number }>
  > => {
    const response = await apiClient.get("/learning/paths/recommended");
    if (response.data?.code === 200 && response.data?.data) {
      const paths = Array.isArray(response.data.data) ? response.data.data : [];
      const items = paths.map(transformLearningPath);
      return { ...response.data, data: { items, total: items.length } };
    }
    return response.data;
  },

  getLearningPaths: (params?: {
    page?: number;
    size?: number;
    category?: string;
    difficulty?: string;
  }): Promise<ApiResponse<{ items: LearningPath[]; total: number }>> => {
    return apiClient
      .get("/learning-paths", { params })
      .then((res) => res.data);
  },

  getProgress: (): Promise<ApiResponse<LearningProgress[]>> => {
    return apiClient.get("/learning-paths/progress").then((res) => res.data);
  },
};

export default learningService;

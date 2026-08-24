/**
 * 用户服务层
 * 封装所有用户相关的API调用
 */

import { apiEndpoints } from "@/config/api.config";
import apiClient, { ApiResponse } from "./api";

export interface User {
  id: number;
  username: string;
  email: string;
  realName?: string;
  phone?: string;
  avatarUrl?: string;
  school?: string;
  major?: string;
  grade?: string;
  role: string;
  isActive: number;
  learningLevel?: number;
  experiencePoints?: number;
  coins?: number;
  achievementScore?: number;
  dailyStreak?: number;
  lastActiveDate?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface UserProfile {
  id: number;
  userId: number;
  bio?: string;
  interests?: string[];
  goals?: string[];
  preferredLanguage?: string;
  timezone?: string;
  programmingLanguage?: string;
  difficultyPreference?: 'easy' | 'medium' | 'hard' | 'adaptive';
  dailyGoalMinutes?: number;
  learningStyle?: 'visual' | 'auditory' | 'reading' | 'kinesthetic';
  weeklyLearningDays?: number;
  notificationSettings?: {
    email: boolean;
    push: boolean;
    weekly: boolean;
  };
  createdAt: string;
  updatedAt?: string;
}

export interface UserStats {
  solvedProblems: number;
  totalSubmissions: number;
  acceptanceRate: number;
  learningStreak: number;
  experiencePoints: number;
  coins: number;
  achievements: number;
}

export interface LearningHistory {
  id: number;
  userId: number;
  problemId?: number;
  problemTitle?: string;
  type: string;
  status: string;
  score?: number;
  timeSpent?: number;
  createdAt: string;
}

export interface Achievement {
  id: number;
  name: string;
  description: string;
  iconUrl?: string;
  category: string;
  rarity: string;
  pointsAwarded: number;
  coinsAwarded: number;
  badgeUrl?: string;
  conditionType: string;
  conditionConfig: Record<string, unknown>;
  isActive: boolean;
  createdAt: string;
}

export interface UserAchievement {
  id: number;
  userId: number;
  achievementId: number;
  progressCurrent: number;
  progressTarget: number;
  unlockedAt?: string;
  notificationSent: boolean;
  createdAt: string;
  updatedAt: string;
  achievement: Achievement;
}

export const authService = {
  validateToken: (): Promise<ApiResponse<{ valid: boolean; user: User }>> => {
    return apiClient.get(apiEndpoints.auth.validate).then((res) => res.data);
  },

  register: (userData: {
    username: string;
    email: string;
    password: string;
    phone?: string;
    realName?: string;
  }): Promise<ApiResponse<{ token: string; refreshToken: string; user: User }>> => {
    return apiClient.post(apiEndpoints.auth.register, userData).then((res) => res.data);
  },

  login: (credentials: {
    username: string;
    password: string;
  }): Promise<ApiResponse<{ token: string; refreshToken: string; user: User }>> => {
    return apiClient.post(apiEndpoints.auth.login, credentials).then((res) => res.data);
  },

  refreshToken: (refreshToken: string): Promise<ApiResponse<{ token: string; refreshToken: string }>> => {
    return apiClient
      .post(apiEndpoints.auth.refresh, {}, {
        headers: {
          Authorization: `Bearer ${refreshToken}`,
        },
      })
      .then((res) => res.data);
  },
};

export const userService = {
  getCurrentUser: (): Promise<ApiResponse<User>> => {
    return apiClient.get(apiEndpoints.user.profile).then((res) => res.data);
  },

  updateProfile: (data: Partial<User>): Promise<ApiResponse<User>> => {
    return apiClient
      .put(apiEndpoints.user.profile, data)
      .then((res) => res.data);
  },

  uploadAvatar: (file: File): Promise<ApiResponse<{ url: string }>> => {
    const formData = new FormData();
    formData.append("avatar", file);
    return apiClient
      .post(`${apiEndpoints.user.profile}/avatar`, formData)
      .then((res) => res.data);
  },

  getStats: (): Promise<ApiResponse<UserStats>> => {
    return apiClient.get(apiEndpoints.user.stats).then((res) => res.data);
  },

  getUserById: (userId: number): Promise<ApiResponse<User>> => {
    return apiClient.get(`${apiEndpoints.user.base}/${userId}`).then((res) => res.data);
  },

  updateUser: (userId: number, userData: Partial<User>): Promise<ApiResponse<User>> => {
    return apiClient
      .put(`${apiEndpoints.user.base}/${userId}`, userData)
      .then((res) => res.data);
  },

  getUserProfile: (userId: number): Promise<ApiResponse<UserProfile>> => {
    return apiClient
      .get(`${apiEndpoints.user.base}/${userId}/profile`)
      .then((res) => res.data);
  },

  updateUserProfile: (userId: number, profileData: Partial<UserProfile>): Promise<ApiResponse<UserProfile>> => {
    return apiClient
      .put(`${apiEndpoints.user.base}/${userId}/profile`, profileData)
      .then((res) => res.data);
  },

  getAchievements: (): Promise<ApiResponse<Achievement[]>> => {
    return apiClient
      .get(apiEndpoints.user.achievements)
      .then((res) => res.data);
  },

  getUserAchievements: (userId: number): Promise<ApiResponse<UserAchievement[]>> => {
    return apiClient
      .get(`${apiEndpoints.user.base}/${userId}/achievements`)
      .then((res) => res.data);
  },

  getLearningHistory: (params?: {
    page?: number;
    size?: number;
    type?: string;
  }): Promise<ApiResponse<{ items: LearningHistory[]; total: number }>> => {
    return apiClient
      .get(apiEndpoints.user.history, { params })
      .then((res) => res.data);
  },

  getUserLearningHistory: (userId: number, params?: {
    startDate?: string;
    endDate?: string;
    page?: number;
    size?: number;
  }): Promise<ApiResponse<{ items: LearningHistory[]; total: number; page: number; size: number }>> => {
    return apiClient
      .get(`${apiEndpoints.user.base}/${userId}/learning-history`, { params })
      .then((res) => res.data);
  },

  getUsers: (params?: {
    page?: number;
    size?: number;
    search?: string;
    role?: string;
    isActive?: number;
  }): Promise<ApiResponse<{ items: User[]; total: number }>> => {
    return apiClient
      .get(apiEndpoints.user.base, { params })
      .then((res) => res.data);
  },

  createUser: (data: Partial<User>): Promise<ApiResponse<User>> => {
    return apiClient.post(apiEndpoints.user.base, data).then((res) => res.data);
  },

  deleteUser: (id: number): Promise<ApiResponse<void>> => {
    return apiClient
      .delete(`${apiEndpoints.user.base}/${id}`)
      .then((res) => res.data);
  },

  batchDeleteUsers: (ids: number[]): Promise<ApiResponse<void>> => {
    return apiClient
      .post(`${apiEndpoints.user.base}/batch-delete`, { ids })
      .then((res) => res.data);
  },

  changePassword: (
    oldPassword: string,
    newPassword: string
  ): Promise<ApiResponse<void>> => {
    return apiClient
      .post(apiEndpoints.auth.changePassword, { oldPassword, newPassword })
      .then((res) => res.data);
  },

  resetPassword: (id: number, newPassword: string): Promise<ApiResponse<void>> => {
    return apiClient
      .post(`${apiEndpoints.user.base}/${id}/reset-password`, { newPassword })
      .then((res) => res.data);
  },
};

export default userService;

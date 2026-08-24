import apiClient from './api';

// Admin用户管理接口（基于zhixue_user数据库）
export interface AdminUser {
  id: number;
  username: string;
  email: string;
  passwordHash?: string;
  realName?: string;
  avatarUrl?: string;
  phone?: string;
  school?: string;
  major?: string;
  grade?: string;
  learningLevel?: number;
  experiencePoints?: number;
  coins?: number;
  achievementScore?: number;
  dailyStreak?: number;
  lastActiveDate?: string;
  isActive: number;
  emailVerfied?: number;
  phoneVerfied?: number;
  createdAt: string;
  updatedAt?: string;
  deletedAt?: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  realName?: string;
  phone?: string;
  school?: string;
  major?: string;
  grade?: string;
  role?: string;
  isActive?: number;
}

export interface UpdateUserRequest {
  username?: string;
  email?: string;
  realName?: string;
  phone?: string;
  school?: string;
  major?: string;
  grade?: string;
  role?: string;
  isActive?: number;
}

// Admin系统日志接口
export interface SystemLog {
  id: number;
  userId: number;
  username: string;
  action: string;
  resource: string;
  status: 'success' | 'failed';
  ipAddress: string;
  userAgent: string;
  createdAt: string;
  details?: string;
}

// Admin系统统计接口
export interface SystemStats {
  totalUsers: number;
  activeUsers: number;
  systemHealth: 'healthy' | 'warning' | 'critical';
}

// 系统监控指标接口
export interface SystemMetrics {
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  onlineUsers: number;
  systemLoad: number;
  uptime: string;
  lastCheckTime: string;
}

// 角色权限接口
export interface Role {
  id: number;
  name: string;
  description: string;
  permissions: string[];
  createdAt: string;
  updatedAt?: string;
}

export interface CreateRoleRequest {
  name: string;
  description: string;
  permissions: string[];
}

export interface BackupRecord {
  id: number;
  fileName: string;
  fileSize: number;
  backupType: 'full' | 'incremental' | 'manual';
  status: 'pending' | 'running' | 'completed' | 'failed';
  startTime: string;
  endTime?: string;
  createdBy: string;
  downloadUrl?: string;
}

// 分页响应接口
export interface PaginationResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

// 统一的API响应处理函数
async function handleApiResponse<T>(promise: Promise<any>): Promise<T> {
  const response = await promise;
  if (response.data?.code === 200 || response.data?.code === 201) {
    return response.data.data;
  } else {
    throw new Error(response.data?.message || response.data?.msg || 'API调用失败');
  }
}

// Admin服务类
export class AdminService {
  // 用户管理接口
  static async getUsers(params?: {
    page?: number;
    size?: number;
    search?: string;
    isActive?: number;
    role?: string;
  }): Promise<PaginationResponse<AdminUser>> {
    return handleApiResponse<PaginationResponse<AdminUser>>(
      apiClient.get('/admin/users', { params })
    );
  }

  static async createUser(userData: CreateUserRequest): Promise<AdminUser> {
    return handleApiResponse<AdminUser>(
      apiClient.post('/admin/users', userData)
    );
  }

  static async updateUser(id: number, userData: UpdateUserRequest): Promise<AdminUser> {
    return handleApiResponse<AdminUser>(
      apiClient.put(`/admin/users/${id}`, userData)
    );
  }

  static async deleteUser(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/users/${id}`)
    );
  }

  static async batchDeleteUsers(ids: number[]): Promise<void> {
    return handleApiResponse<void>(
      apiClient.post('/admin/users/batch-delete', ids)
    );
  }

  static async updatePassword(id: number, newPassword: string): Promise<void> {
    return handleApiResponse<void>(
      apiClient.put(`/admin/users/${id}/password`, { newPassword })
    );
  }

  // 系统日志接口
  static async getSystemLogs(params?: {
    page?: number;
    size?: number;
    search?: string;
    startDate?: string;
    endDate?: string;
    status?: 'success' | 'failed';
    action?: string;
  }): Promise<PaginationResponse<SystemLog>> {
    return handleApiResponse<PaginationResponse<SystemLog>>(
      apiClient.get('/admin/logs', { params })
    );
  }

  static async getSystemStats(): Promise<SystemStats> {
    return handleApiResponse<SystemStats>(
      apiClient.get('/admin/stats')
    );
  }

  // 系统监控接口
  static async getSystemMetrics(): Promise<SystemMetrics> {
    return handleApiResponse<SystemMetrics>(
      apiClient.get('/admin/monitoring/metrics')
    );
  }

  static async getSystemEvents(page?: number, size?: number): Promise<PaginationResponse<any>> {
    return handleApiResponse<PaginationResponse<any>>(
      apiClient.get('/admin/monitoring/events', { params: { page, size } })
    );
  }

  static async getBackups(params?: {
    page?: number;
    size?: number;
    type?: 'full' | 'incremental' | 'manual';
    status?: 'pending' | 'running' | 'completed' | 'failed';
  }): Promise<PaginationResponse<BackupRecord>> {
    return handleApiResponse<PaginationResponse<BackupRecord>>(
      apiClient.get('/admin/backups', { params })
    );
  }

  static async createBackup(type: 'full' | 'incremental' | 'manual'): Promise<BackupRecord> {
    return handleApiResponse<BackupRecord>(
      apiClient.post('/admin/backups', { type })
    );
  }

  static async deleteBackup(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/backups/${id}`)
    );
  }

  static async downloadBackup(id: number): Promise<Blob> {
    const response = await apiClient.get(`/admin/backups/${id}/download`, {
      responseType: 'blob'
    });
    // downloadBackup 返回的是 Blob，不需要通过 handleApiResponse 处理
    return response.data;
  }

  static async getRoles(params?: {
    page?: number;
    size?: number;
    search?: string;
  }): Promise<PaginationResponse<Role>> {
    return handleApiResponse<PaginationResponse<Role>>(
      apiClient.get('/admin/roles', { params })
    );
  }

  static async createRole(role: CreateRoleRequest): Promise<Role> {
    return handleApiResponse<Role>(
      apiClient.post('/admin/roles', role)
    );
  }

  static async updateRole(id: number, role: Partial<CreateRoleRequest>): Promise<Role> {
    return handleApiResponse<Role>(
      apiClient.put(`/admin/roles/${id}`, role)
    );
  }

  static async deleteRole(id: number): Promise<void> {
    return handleApiResponse<void>(
      apiClient.delete(`/admin/roles/${id}`)
    );
  }
}

// 导出默认实例
export const adminService = new AdminService();

import api from './api';
import { ApiResponse, AuthResponse, LoginCredentials, RegisterData } from '@/types';

export const authService = {
  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    const response = await api.post<ApiResponse<AuthResponse>>('/auth/login', credentials);
    return response.data.data;
  },

  async register(data: RegisterData): Promise<AuthResponse> {
    const response = await api.post<ApiResponse<AuthResponse>>('/auth/register', data);
    return response.data.data;
  },

  async logout(): Promise<void> {
    await api.post('/auth/logout');
  },

  async validateToken(): Promise<AuthResponse> {
    const response = await api.get<ApiResponse<AuthResponse>>('/auth/validate');
    return response.data.data;
  },

  async refreshToken(refreshToken: string): Promise<{ token: string; refreshToken: string }> {
    const response = await api.post<ApiResponse<{ token: string; refreshToken: string }>>(
      '/auth/refresh',
      {},
      { headers: { Authorization: `Bearer ${refreshToken}` } }
    );
    return response.data.data;
  },
};

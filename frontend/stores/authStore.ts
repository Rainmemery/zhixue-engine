import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { User, AuthResponse } from '@/types';
import { setToken, clearToken, getToken, getRefreshToken } from '@/lib/auth';

interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setAuth: (auth: AuthResponse) => void;
  clearAuth: () => void;
  logout: () => void;
  setUser: (user: User) => void;
  setLoading: (loading: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: true,
      setAuth: (auth: AuthResponse) => {
        // 存储到localStorage使用统一的键名
        setToken(auth.token, auth.refreshToken);
        set({
          user: auth.user,
          token: auth.token,
          refreshToken: auth.refreshToken,
          isAuthenticated: true,
          isLoading: false,
        });
      },
      clearAuth: () => {
        clearToken();
        set({
          user: null,
          token: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        });
      },
      logout: () => {
        clearToken();
        set({
          user: null,
          token: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        });
      },
      setUser: (user: User) => set({ user }),
      setLoading: (loading: boolean) => set({ isLoading: loading }),
    }),
    {
      name: 'auth-storage',
    }
  )
);

// 初始化时从localStorage读取token
export const initializeAuth = () => {
  const token = getToken();
  const refreshToken = getRefreshToken();
  if (token) {
    useAuthStore.setState({ token, refreshToken, isAuthenticated: true, isLoading: false });
  } else {
    useAuthStore.setState({ isLoading: false });
  }
};

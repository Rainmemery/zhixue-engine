// Token存储管理工具
// 与旧项目保持一致的token存储键名

const ACCESS_TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refresh_token';

// JWT Payload 类型定义
interface JwtPayload {
  userId?: number;
  sub?: number;
  username?: string;
  role?: string;
  exp?: number;
  iat?: number;
  [key: string]: unknown;
}

/**
 * 存储访问令牌
 * @param accessToken 访问令牌
 * @param refreshToken 刷新令牌（可选）
 */
export const setToken = (accessToken: string, refreshToken?: string): void => {
  if (typeof window !== 'undefined') {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    }
  }
};

/**
 * 获取访问令牌
 * @returns 访问令牌或null
 */
export const getToken = (): string | null => {
  if (typeof window !== 'undefined') {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }
  return null;
};

/**
 * 获取刷新令牌
 * @returns 刷新令牌或null
 */
export const getRefreshToken = (): string | null => {
  if (typeof window !== 'undefined') {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }
  return null;
};

/**
 * 清除令牌
 */
export const clearToken = (): void => {
  if (typeof window !== 'undefined') {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
};

/**
 * 检查是否已认证
 * @returns 是否已认证
 */
export const isAuthenticated = (): boolean => {
  const token = getToken();
  if (!token) {
    return false;
  }

  // 检查令牌是否过期
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const currentTime = Math.floor(Date.now() / 1000);
    return payload.exp > currentTime;
  } catch (error) {
    return false;
  }
};

/**
 * 解码JWT令牌
 * @param token JWT令牌
 * @returns 解码后的payload或null
 */
export const decodeToken = (token: string): JwtPayload | null => {
  try {
    const payload = token.split('.')[1];
    const decodedPayload = atob(payload);
    return JSON.parse(decodedPayload) as JwtPayload;
  } catch (error) {
    console.error('解析令牌失败:', error);
    return null;
  }
};

export const parseJwtPayload = decodeToken;

/**
 * 获取用户ID
 * @returns 用户ID或null
 */
export const getUserId = (): number | null => {
  const token = getToken();
  if (!token) {
    return null;
  }

  const decoded = decodeToken(token);
  if (!decoded) return null;
  return decoded.userId ?? decoded.sub ?? null;
};

/**
 * 获取用户名
 * @returns 用户名或null
 */
export const getUsername = (): string | null => {
  const token = getToken();
  if (!token) {
    return null;
  }

  const decoded = decodeToken(token);
  if (!decoded) return null;
  return decoded.username ?? null;
};

/**
 * 获取用户角色
 * @returns 用户角色或null
 */
export const getRole = (): string | null => {
  const token = getToken();
  if (!token) {
    return null;
  }

  const decoded = decodeToken(token);
  if (!decoded) return null;
  return decoded.role ?? null;
};

/**
 * 角色常量定义
 */

export const UserRole = {
  ADMIN: "admin",
  TEACHER: "teacher",
  STUDENT: "student",
} as const;

export type UserRoleType = (typeof UserRole)[keyof typeof UserRole];

export const RoleLabels: Record<UserRoleType, string> = {
  [UserRole.ADMIN]: "管理员",
  [UserRole.TEACHER]: "教师",
  [UserRole.STUDENT]: "学生",
};

export const RoleColors: Record<UserRoleType, string> = {
  [UserRole.ADMIN]: "red",
  [UserRole.TEACHER]: "blue",
  [UserRole.STUDENT]: "green",
};

/**
 * 标准化角色字符串（转为小写）
 * @param role 角色字符串
 * @returns 小写角色字符串
 */
export function normalizeRole(role: string | undefined): string {
  if (!role) return "";
  return role.toLowerCase().trim();
}

/**
 * 权限检查函数 - 支持大小写不敏感匹配
 * @param userRole 用户角色
 * @param requiredRole 需要的角色
 * @returns 是否有权限
 */
export function hasRole(
  userRole: string | undefined,
  requiredRole: UserRoleType | UserRoleType[]
): boolean {
  if (!userRole) return false;
  
  const normalizedUserRole = normalizeRole(userRole);
  
  if (Array.isArray(requiredRole)) {
    return requiredRole.some(role => normalizeRole(role) === normalizedUserRole);
  }
  return normalizeRole(requiredRole) === normalizedUserRole;
}

/**
 * 检查是否为管理员 - 支持大小写不敏感匹配
 * @param userRole 用户角色
 * @returns 是否为管理员
 */
export function isAdmin(userRole: string | undefined): boolean {
  return normalizeRole(userRole) === UserRole.ADMIN;
}

/**
 * 检查是否为教师 - 支持大小写不敏感匹配
 * @param userRole 用户角色
 * @returns 是否为教师
 */
export function isTeacher(userRole: string | undefined): boolean {
  return normalizeRole(userRole) === UserRole.TEACHER;
}

/**
 * 检查是否为学生 - 支持大小写不敏感匹配
 * @param userRole 用户角色
 * @returns 是否为学生
 */
export function isStudent(userRole: string | undefined): boolean {
  return normalizeRole(userRole) === UserRole.STUDENT;
}

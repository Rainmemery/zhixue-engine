/**
 * HTTP状态码常量
 */

export const HttpStatus = {
  OK: 200,
  CREATED: 201,
  ACCEPTED: 202,
  NO_CONTENT: 204,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  UNPROCESSABLE_ENTITY: 422,
  TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500,
  BAD_GATEWAY: 502,
  SERVICE_UNAVAILABLE: 503,
  GATEWAY_TIMEOUT: 504,
} as const;

/**
 * 业务状态码常量
 */
export const BusinessCode = {
  SUCCESS: 200,
  CREATED: 201,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_ERROR: 500,
} as const;

/**
 * 用户状态
 */
export const UserStatus = {
  ACTIVE: 1,
  INACTIVE: 0,
  SUSPENDED: 2,
} as const;

export type UserStatusType = (typeof UserStatus)[keyof typeof UserStatus];

export const UserStatusLabels: Record<UserStatusType, string> = {
  [UserStatus.ACTIVE]: "活跃",
  [UserStatus.INACTIVE]: "禁用",
  [UserStatus.SUSPENDED]: "暂停",
};

export const UserStatusColors: Record<UserStatusType, string> = {
  [UserStatus.ACTIVE]: "success",
  [UserStatus.INACTIVE]: "error",
  [UserStatus.SUSPENDED]: "warning",
};

/**
 * 题目难度
 */
export const ProblemDifficulty = {
  EASY: "easy",
  MEDIUM: "medium",
  HARD: "hard",
} as const;

export type ProblemDifficultyType =
  (typeof ProblemDifficulty)[keyof typeof ProblemDifficulty];

export const DifficultyLabels: Record<ProblemDifficultyType, string> = {
  [ProblemDifficulty.EASY]: "简单",
  [ProblemDifficulty.MEDIUM]: "中等",
  [ProblemDifficulty.HARD]: "困难",
};

export const DifficultyColors: Record<ProblemDifficultyType, string> = {
  [ProblemDifficulty.EASY]: "green",
  [ProblemDifficulty.MEDIUM]: "yellow",
  [ProblemDifficulty.HARD]: "red",
};

/**
 * 提交状态
 */
export const SubmissionStatus = {
  PENDING: "pending",
  COMPILING: "compiling",
  RUNNING: "running",
  ACCEPTED: "accepted",
  PARTIAL_ACCEPTED: "partial_accepted",
  WRONG_ANSWER: "wrong_answer",
  TIME_LIMIT_EXCEEDED: "time_limit_exceeded",
  MEMORY_LIMIT_EXCEEDED: "memory_limit_exceeded",
  RUNTIME_ERROR: "runtime_error",
  COMPILE_ERROR: "compile_error",
  SYSTEM_ERROR: "system_error",
} as const;

export type SubmissionStatusType =
  (typeof SubmissionStatus)[keyof typeof SubmissionStatus];

export const SubmissionStatusLabels: Record<SubmissionStatusType, string> = {
  [SubmissionStatus.PENDING]: "等待中",
  [SubmissionStatus.COMPILING]: "编译中",
  [SubmissionStatus.RUNNING]: "运行中",
  [SubmissionStatus.ACCEPTED]: "通过",
  [SubmissionStatus.PARTIAL_ACCEPTED]: "部分通过",
  [SubmissionStatus.WRONG_ANSWER]: "答案错误",
  [SubmissionStatus.TIME_LIMIT_EXCEEDED]: "超时",
  [SubmissionStatus.MEMORY_LIMIT_EXCEEDED]: "内存超限",
  [SubmissionStatus.RUNTIME_ERROR]: "运行错误",
  [SubmissionStatus.COMPILE_ERROR]: "编译错误",
  [SubmissionStatus.SYSTEM_ERROR]: "系统错误",
};

export const SubmissionStatusColors: Record<SubmissionStatusType, string> = {
  [SubmissionStatus.PENDING]: "gray",
  [SubmissionStatus.COMPILING]: "blue",
  [SubmissionStatus.RUNNING]: "indigo",
  [SubmissionStatus.ACCEPTED]: "green",
  [SubmissionStatus.PARTIAL_ACCEPTED]: "sky",
  [SubmissionStatus.WRONG_ANSWER]: "red",
  [SubmissionStatus.TIME_LIMIT_EXCEEDED]: "orange",
  [SubmissionStatus.MEMORY_LIMIT_EXCEEDED]: "purple",
  [SubmissionStatus.RUNTIME_ERROR]: "pink",
  [SubmissionStatus.COMPILE_ERROR]: "yellow",
  [SubmissionStatus.SYSTEM_ERROR]: "red",
};

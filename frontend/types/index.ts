// User Types
export interface User {
  id: number
  username: string
  email: string
  realName?: string
  avatarUrl?: string
  phone?: string
  school?: string
  major?: string
  grade?: string
  role: string
  learningLevel: number
  experiencePoints: number
  coins?: number
  achievementScore?: number
  dailyStreak?: number
  solvedProblems: number
  learningPaths: number
  dailyGoalMinutes: number
  isActive: number | boolean
  createdAt: string
  updatedAt: string
}

export interface UserProfile {
  userId: number
  programmingLanguage: string
  difficultyPreference: 'adaptive' | 'easy' | 'medium' | 'hard'
  dailyGoalMinutes: number
  learningStyle: 'visual' | 'auditory' | 'read_write' | 'kinesthetic'
  weeklyLearningDays: number
  createdAt: string
  updatedAt: string
}

// Auth Types
export interface LoginCredentials {
  username: string
  password: string
}

export interface RegisterData {
  username: string
  email: string
  password: string
  phone?: string
  realName?: string
}

export interface AuthResponse {
  token: string
  refreshToken: string
  user: User
}

// Problem Types
export interface Problem {
  id: number
  title: string
  titleEn?: string
  description: string
  difficulty: 'easy' | 'medium' | 'hard' | string
  categoryId?: number
  category?: string
  tags: string[]
  initialCode?: string
  solutionCode?: string
  testCases?: TestCase[]
  timeLimitMs?: number
  memoryLimitMb?: number
  acceptanceRate?: number
  submitCount?: number
  acceptedCount?: number
  likes?: number
  dislikes?: number
  isActive?: boolean
  createdAt: string
  updatedAt: string
}

export interface TestCase {
  input: string
  output: string
  expected: string
}

export interface ProblemSubmission {
  submissionId: string
  problemId: number
  userId: number
  status: 'accepted' | 'partial_accepted' | 'wrong_answer' | 'runtime_error' | 'time_limit_exceeded' | 'memory_limit_exceeded' | 'compile_error'
  score: number
  executionTimeMs: number
  memoryUsedKb: number
  testCaseResults: TestCaseResult[]
  feedback: string
  createdAt: string
}

export interface TestCaseResult {
  testCaseId: number
  status: 'passed' | 'failed' | 'error'
  executionTimeMs: number
  memoryUsedKb: number
}

// Learning Types
export interface LearningRecord {
  id: number
  userId: number
  problemId: number
  submissionId: string
  status: 'solved' | 'attempted' | 'unsolved'
  score: number
  timeSpentMs: number
  debugCount: number
  attempts: number
  firstSolvedAt?: string
  lastAttemptAt?: string
  createdAt: string
  updatedAt: string
}

export interface LearningPath {
  id: number | string
  userId: number
  title: string
  description: string
  goal: string
  targetDays: number
  dailyMinutes: number
  focusAreas: string[]
  difficulty: 'beginner' | 'intermediate' | 'advanced' | 'expert'
  steps: LearningStep[]
  currentStep: number
  progress: number
  status: 'active' | 'completed' | 'paused' | 'cancelled'
  category?: string
  completedCount?: number
  problemCount?: number
  estimatedHours?: number
  createdAt: string
  updatedAt: string
}

export interface LearningStep {
  stepNumber: number
  title: string
  description: string
  resources: LearningResource[]
  estimatedTimeMinutes: number
  prerequisites: number[]
  status: 'pending' | 'completed' | 'skipped'
  completedAt?: string
  score?: number
  timeSpentMinutes?: number
}

export interface LearningResource {
  type: 'article' | 'problem' | 'video'
  title: string
  url?: string
  problemId?: number
}

// AI Types
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp: string
}

export interface StreamChunk {
  type: 'text' | 'code' | 'thinking' | 'complete' | 'error'
  content: string
  delta?: string
}

export interface CodeSelection {
  startLine: number
  endLine: number
  selectedText: string
  language: string
}

export interface AiChatRequest {
  messages: ChatMessage[]
  role?: 'explainer' | 'collaborator' | 'reviewer' | 'questioner'
  temperature?: number
  maxTokens?: number
}

// Achievement Types
export interface Achievement {
  id: number
  code: string
  name: string
  description: string
  iconUrl: string
  category: string
  rarity: 'common' | 'rare' | 'epic' | 'legendary'
  pointsAwarded: number
  coinsAwarded: number
  badgeUrl?: string
  conditionType: string
  conditionConfig: Record<string, unknown>
  isActive: boolean
  createdAt: string
}

export interface UserAchievement {
  id: number
  userId: number
  achievementId: number
  progressCurrent: number
  progressTarget: number
  unlockedAt?: string
  notificationSent: boolean
  createdAt: string
  updatedAt: string
  achievement: Achievement
}

// Admin Types
export interface AdminUser {
  id: number
  username: string
  email: string
  role: string
  status: 'active' | 'inactive'
  createdAt: string
  lastLogin?: string
}

export interface Role {
  id: number
  name: string
  description: string
  permissions: string[]
  createdAt: string
  updatedAt: string
}

export interface SystemLog {
  id: number
  userId?: number
  username?: string
  action: string
  resource: string
  status: 'success' | 'failed'
  ipAddress?: string
  userAgent?: string
  details?: string
  createdAt: string
}

export interface SystemMetrics {
  cpuUsage: number
  memoryUsage: number
  diskUsage: number
  databaseConnections: number
  onlineUsers: number
  systemLoad: number
  uptime: string
  lastCheckTime: string
}

// API Response Types
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
  requestId?: string
}

export interface PaginatedResponse<T> {
  items: T[]
  pagination: {
    page: number
    size: number
    total: number
    totalPages: number
  }
}

// UI Types
export interface NavItem {
  title: string
  href: string
  icon: string
  children?: NavItem[]
}

export interface Theme {
  mode: 'light' | 'dark' | 'system'
}

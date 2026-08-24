"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import {
  HelpCircle,
  Send,
  Square,
  Database,
  Lightbulb,
  RefreshCw,
  Loader2,
  ChevronDown,
  User,
  Sparkles,
  Copy,
  Bot,
  CheckCircle2,
  XCircle,
  Wrench,
  Menu,
  ExternalLink,
  Bell,
} from "lucide-react";
import { aiService, AgentTaskCreateResult, AgentTaskProgress, RecoveryResult, RecoveryStatusResult } from "@/services/aiService";
import { appConfig } from "@/config/app.config";
import { ChatMessage } from "@/types";
import { useToast } from "@/hooks/use-toast";
import { slideInLeft, slideInRight, useReducedMotion } from "@/lib/animations";
import { MarkdownRenderer } from "@/components/chat/markdown-renderer";
import { useSmartScroll } from "@/hooks/useSmartScroll";
import { processStreamDelta } from "@/lib/textProcessor";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { useAgentNotification, useMessageAck } from "@/hooks/useAgentNotification";
import { AgentNotification } from "@/services/websocketService";
import { webSocketService } from "@/services/websocketService";
import { getToolDisplayName, getToolDescription } from "@/lib/tool-mappings";

// 对话状态
enum ConversationStep {
  IDLE = 0,
  INITIAL_INPUT = 1,
  INITIAL_RESPONSE = 2,
  CONVERSATION_ACTIVE = 3,
  FOLLOWUP_RESPONSE = 4,
}

export default function SmartQuestionerPage() {
  const { toast } = useToast();
  const reducedMotion = useReducedMotion();

  // 状态管理
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [initialInput, setInitialInput] = useState("");
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [conversationStep, setConversationStep] = useState<ConversationStep>(
    ConversationStep.IDLE
  );
  const [isConversationActive, setIsConversationActive] = useState(false);
  const [userResponded, setUserResponded] = useState(false);
  const [ragEnabled, setRagEnabled] = useState(false);
  const [initialized, setInitialized] = useState(false);
  const [retrying, setRetrying] = useState(false);

  interface ToolCallInfo {
    id: string;
    callId?: string;
    toolName: string;
    toolArgs: Record<string, unknown>;
    status: 'pending' | 'running' | 'completed' | 'failed';
    result?: Record<string, unknown>;
    cached?: boolean;
    saveFailed?: boolean;
    startTime: number;
    endTime?: number;
    errorMessage?: string;
    resultSummary?: string;
  }

  interface GeneratedProblem {
    problemId?: number;
    title: string;
    difficulty: string;
    saveFailed?: boolean;
    saveError?: string;
    acceptanceRate?: number;
    problemType?: string;
    isRecommended?: boolean;
    status?: 'generating' | 'success' | 'failed';
  }

  const [activeToolCalls, setActiveToolCalls] = useState<ToolCallInfo[]>([]);
  const [generatedProblems, setGeneratedProblems] = useState<GeneratedProblem[]>([]);
  const [problemGenerateError, setProblemGenerateError] = useState<string | null>(null);
  const [pollingTaskId, setPollingTaskId] = useState<string | null>(null);
  const pollingTimerRef = useRef<NodeJS.Timeout | null>(null);
  const pollingStartRef = useRef<number>(0);
  const lastSegmentIndexRef = useRef(0);
  const lastAcknowledgedSeqRef = useRef<number>(0);
  const [wsConnected, setWsConnected] = useState(false);
  const [isRecovering, setIsRecovering] = useState(false);
  const [recoveryAvailable, setRecoveryAvailable] = useState(false);
  const [recoveryError, setRecoveryError] = useState<string | null>(null);
  const recoveryHandlerRef = useRef<ReturnType<typeof aiService.createRecoveryHandler> | null>(null);

  // Refs
  const conversationTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const isRequestPendingRef = useRef(false);

  // 使用智能滚动 hook - 和AI助手页面相同的实现
  const { containerRef, showScrollButton, resumeAutoScroll } = useSmartScroll({
    autoScrollEnabled: true,
    storageKey: "smart-questioner-scroll",
  });

  // 消息确认hook
  const { sendImmediateAck } = useMessageAck();

  const getResultSummary = (toolName: string, result: Record<string, unknown> | undefined): string | undefined => {
    if (!result) return undefined;
    switch (toolName) {
      case 'problem_generate':
        return result.title ? `已创建: ${result.title}` : '题目创建成功';
      case 'problem_recommend':
        if (Array.isArray(result.problems) && result.problems.length > 0) {
          return `推荐了 ${result.problems.length} 道题目`;
        }
        return '推荐完成';
      case 'user_profile':
        return '用户画像更新成功';
      case 'difficulty_adapt':
        return '难度适配完成';
      case 'wrong_question':
        return '错题分析完成';
      case 'problem_validate':
        return result.isValid !== undefined ? (result.isValid ? '题目验证通过' : '题目验证未通过') : '题目验证完成';
      case 'test_data_generate':
        if (result.testCases && Array.isArray(result.testCases)) {
          return `已生成 ${result.testCases.length} 组测试数据`;
        }
        return '测试数据生成完成';
      default:
        return '处理完成';
    }
  };

  // 通知处理
  const handleTaskCompleted = useCallback((notification: AgentNotification) => {
    if (notification.taskId === pollingTaskId) {
      setStreaming(false);
      setLoading(false);
    }
  }, [pollingTaskId]);

  const handleProblemGenerated = useCallback((notification: AgentNotification) => {
    if (notification.problem) {
      setGeneratedProblems(prev => {
        const existing = prev.find(p => p.problemId === notification.problem?.problemId);
        if (existing) {
          return prev.map(p => 
            p.problemId === notification.problem?.problemId 
              ? { ...p, status: 'success' as const }
              : p
          );
        }
        return [...prev, {
          problemId: notification.problem!.problemId,
          title: notification.problem!.title,
          difficulty: notification.problem!.difficulty,
          isRecommended: notification.problem!.isRecommended,
          status: 'success' as const,
          acceptanceRate: notification.problem!.acceptanceRate,
          problemType: notification.problem!.problemType,
        }];
      });
    }
  }, []);

  const handleProblemRecommended = useCallback((notification: AgentNotification) => {
    console.info('[ProblemRecommended] Received notification:', JSON.stringify(notification, null, 2));
    
    if (!notification) {
      console.error('[ProblemRecommended] Notification is null or undefined');
      return;
    }
    
    const problems = notification.problems;
    if (!problems) {
      console.warn('[ProblemRecommended] No problems field in notification');
      return;
    }
    
    if (!Array.isArray(problems)) {
      console.error('[ProblemRecommended] problems is not an array:', typeof problems, problems);
      return;
    }
    
    if (problems.length === 0) {
      console.warn('[ProblemRecommended] Empty problems array in notification');
      return;
    }
    
    console.info(`[ProblemRecommended] Processing ${problems.length} problems:`, 
      problems.map(p => ({ id: p.problemId, title: p.title, difficulty: p.difficulty })));
    
    setGeneratedProblems(prev => {
      const newProblems: GeneratedProblem[] = problems.map(p => ({
        problemId: p.problemId,
        title: p.title || '未知题目',
        difficulty: p.difficulty || 'medium',
        isRecommended: p.isRecommended ?? true,
        status: 'success' as const,
        acceptanceRate: p.acceptanceRate,
        problemType: p.problemType,
      }));
      
      console.info('[ProblemRecommended] New problems to add:', newProblems);
      
      const existingIds = new Set(prev.map(p => p.problemId));
      const filteredNew = newProblems.filter(p => !existingIds.has(p.problemId));
      
      if (filteredNew.length === 0) {
        console.info('[ProblemRecommended] All problems already exist, skipping');
        return prev;
      }
      
      console.info('[ProblemRecommended] Adding', filteredNew.length, 'new problems');
      return [...prev, ...filteredNew];
    });
  }, []);

  const handleTaskFailed = useCallback((notification: AgentNotification) => {
    if (notification.taskId === pollingTaskId) {
      setStreaming(false);
      setLoading(false);
      toast({
        title: "任务失败",
        description: notification.error || "任务执行失败",
        variant: "destructive",
      });
    }
  }, [pollingTaskId, toast]);

  const attemptRecovery = useCallback(async (taskId: string, aiMessageId: string, fromSeq: number) => {
    if (isRecovering) {
      console.warn('[恢复] 已有恢复任务在进行中');
      return;
    }

    setIsRecovering(true);
    setRecoveryError(null);
    console.info(`[恢复] 开始尝试恢复: taskId=${taskId}, fromSeq=${fromSeq}`);

    try {
      const result: RecoveryResult = await aiService.recoverSegments(taskId, fromSeq);
      
      if (result.success && result.segments.length > 0) {
        console.info(`[恢复成功] 恢复了 ${result.recoveredCount} 个消息段`);
        
        const ackSegments: Array<{ sequenceNumber: number; checksum: string }> = [];
        
        for (const segment of result.segments) {
          if (segment.sequenceNumber !== undefined && segment.checksum) {
            ackSegments.push({
              sequenceNumber: segment.sequenceNumber,
              checksum: segment.checksum,
            });
          }
          
          if (segment.type === "text" && segment.content) {
            const processedContent = processStreamDelta(segment.content);
            setMessages(prev => prev.map(msg =>
              msg.id === aiMessageId
                ? { ...msg, content: (msg.content || "") + processedContent }
                : msg
            ));
          } else if (segment.type === "tool_call") {
              const toolName = segment.toolName || 'unknown';
              const now = Date.now();
              setActiveToolCalls(prev => {
                if (prev.some(tc => tc.toolName === toolName && tc.status === 'running')) {
                  return prev;
                }
                return [...prev, {
                  id: `call_${now}_${toolName}`,
                  callId: `call_${now}`,
                  toolName: toolName,
                  toolArgs: {},
                  status: 'running',
                  startTime: now,
                }];
              });
              if (toolName === 'problem_generate') {
                setProblemGenerateError(null);
              }
            } else if (segment.type === "tool_result") {
              const toolName = segment.toolName;
              const now = Date.now();
              setActiveToolCalls(prev => prev.map(tc => {
                if (tc.toolName === toolName && tc.status === 'running') {
                  const errorData = segment.data as { error?: string; message?: string } | undefined;
                  const errorMsg = errorData?.error || errorData?.message;
                  return {
                    ...tc,
                    status: segment.success ? 'completed' : 'failed',
                    result: segment.data,
                    cached: segment.data?.cached === true,
                    endTime: now,
                    errorMessage: segment.success ? undefined : errorMsg,
                    resultSummary: segment.success ? getResultSummary(toolName, segment.data) : undefined,
                  };
                }
                return tc;
              }));
              if (toolName === 'problem_generate') {
                if (!segment.success) {
                  const errorData = segment.data as { error?: string; message?: string } | undefined;
                  const errorMsg = errorData?.error || errorData?.message || '题目生成失败，请稍后重试';
                  setProblemGenerateError(errorMsg);
                }
              }
            }
          }
        
        if (ackSegments.length > 0) {
          sendImmediateAck(taskId, ackSegments);
        }
        
        lastAcknowledgedSeqRef.current = fromSeq + result.recoveredCount;
        lastSegmentIndexRef.current = result.totalSegments;
        
        toast({
          title: "恢复成功",
          description: `已恢复 ${result.recoveredCount} 个消息段`,
        });
      } else if (result.success) {
        console.info('[恢复成功] 无需恢复的消息段');
      } else {
        setRecoveryError(result.error || '恢复失败');
        toast({
          title: "恢复失败",
          description: result.error || "无法恢复消息段",
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error('[恢复失败] 异常:', error);
      setRecoveryError(error instanceof Error ? error.message : '恢复失败');
      toast({
        title: "恢复失败",
        description: error instanceof Error ? error.message : "恢复过程中发生错误",
        variant: "destructive",
      });
    } finally {
      setIsRecovering(false);
    }
  }, [isRecovering, sendImmediateAck, toast]);

  const checkRecoveryAvailability = useCallback(async (taskId: string) => {
    try {
      const status: RecoveryStatusResult = await aiService.getRecoveryStatus(taskId);
      setRecoveryAvailable(status.canRecover && status.unacknowledgedCount > 0);
      lastAcknowledgedSeqRef.current = status.acknowledgedIndex;
      return status;
    } catch (error) {
      console.error('[恢复状态检查失败]:', error);
      return null;
    }
  }, []);

  // Agent通知hook
  const { notificationHistory, unreadCount, markAllAsRead } = useAgentNotification({
    showToast: true,
    onTaskCompleted: handleTaskCompleted,
    onTaskFailed: handleTaskFailed,
    onProblemGenerated: handleProblemGenerated,
    onProblemRecommended: handleProblemRecommended,
  });

  // 获取消息动画变体
  const getMessageVariants = (role: string) =>
    role === "user" ? slideInRight : slideInLeft;

  // WebSocket连接状态监控
  // 注意：WebSocket连接已由 main-layout.tsx 统一管理，此处仅监控连接状态用于UI显示
  useEffect(() => {
    const checkConnection = setInterval(() => {
      const connected = webSocketService.isConnected;
      if (wsConnected !== connected) {
        setWsConnected(connected);
        if (!connected) {
          console.warn('[WebSocket] Connection lost, notifications may be delayed');
        }
      }
    }, 5000);

    return () => {
      clearInterval(checkConnection);
    };
  }, [wsConnected]);

  // 初始化
  useEffect(() => {
    const initTimer = setTimeout(() => {
      setInitialized(true);
    }, 100);
    return () => clearTimeout(initTimer);
  }, []);

  // 加载RAG配置
  useEffect(() => {
    loadRagConfig();
  }, []);

  // 清理超时定时器
  useEffect(() => {
    return () => {
      if (conversationTimeoutRef.current) {
        clearTimeout(conversationTimeoutRef.current);
      }
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
      if (pollingTimerRef.current) {
        clearTimeout(pollingTimerRef.current);
      }
    };
  }, []);

  // 自动滚动到底部 - 和AI助手页面相同的实现
  useEffect(() => {
    if (!showScrollButton && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({
        behavior: reducedMotion ? "auto" : "smooth",
      });
    }
  }, [messages, streaming, showScrollButton, reducedMotion]);

  // 加载RAG模块配置
  const loadRagConfig = async () => {
    try {
      const response = await fetch(
        `${appConfig.api.baseURL}/rag/config/modules/intelligent_question`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem(
              appConfig.storage.tokenKey
            )}`,
          },
        }
      );
      if (response.ok) {
        const result = await response.json();
        if (result.data?.enabled) {
          setRagEnabled(true);
        }
      }
    } catch (error) {
      console.debug("加载模块配置失败，RAG功能可能未启用");
    }
  };

  // 设置对话超时
  const retrySyncRequest = async <T,>(
    fn: () => Promise<T>,
    maxRetries: number = 2
  ): Promise<T> => {
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await fn();
      } catch (error) {
        if (attempt < maxRetries) {
          const delay = Math.min(1000 * Math.pow(2, attempt), 4000);
          setRetrying(true);
          toast({ title: `请求失败，${delay / 1000}秒后重试 (${attempt + 1}/${maxRetries})...` });
          await new Promise(resolve => setTimeout(resolve, delay));
        } else {
          setRetrying(false);
          throw error;
        }
      }
    }
    throw new Error("Max retries exceeded");
  };

  const setupConversationTimeout = useCallback(
    (timeout: number) => {
      if (conversationTimeoutRef.current) {
        clearTimeout(conversationTimeoutRef.current);
      }

      conversationTimeoutRef.current = setTimeout(() => {
        if (!userResponded && isConversationActive) {
          handleAutoEndConversation();
        }
      }, timeout);
    },
    [userResponded, isConversationActive]
  );

  const startPolling = useCallback((taskId: string, aiMessageId: string) => {
    setPollingTaskId(taskId);
    lastSegmentIndexRef.current = 0;
    lastAcknowledgedSeqRef.current = 0;
    pollingStartRef.current = Date.now();
    setRecoveryAvailable(false);
    setRecoveryError(null);

    recoveryHandlerRef.current = aiService.createRecoveryHandler(taskId, {
      maxRetries: 3,
      retryDelay: 1000,
      onRecoveryStart: () => {
        setIsRecovering(true);
        console.info('[恢复处理器] 开始恢复');
      },
      onRecoverySuccess: (count) => {
        setIsRecovering(false);
        console.info(`[恢复处理器] 恢复成功: ${count} 个消息段`);
      },
      onRecoveryFailed: (error) => {
        setIsRecovering(false);
        setRecoveryError(error.message);
        console.error('[恢复处理器] 恢复失败:', error);
      },
    });

    let consecutiveErrors = 0;
    const maxConsecutiveErrors = 3;

    const poll = async () => {
      try {
        const progress: AgentTaskProgress = await aiService.pollAgentProgress(
          taskId,
          lastSegmentIndexRef.current
        );

        consecutiveErrors = 0;

        if (progress.newSegments && progress.newSegments.length > 0) {
          const ackSegments: Array<{ sequenceNumber: number; checksum: string }> = [];
          
          for (const segment of progress.newSegments) {
            if (segment.sequenceNumber !== undefined && segment.checksum) {
              ackSegments.push({
                sequenceNumber: segment.sequenceNumber,
                checksum: segment.checksum,
              });
              if (segment.sequenceNumber > lastAcknowledgedSeqRef.current) {
                lastAcknowledgedSeqRef.current = segment.sequenceNumber;
              }
            }
            
            if (segment.type === "text" && segment.content) {
              const processedContent = processStreamDelta(segment.content);
              setMessages(prev => prev.map(msg =>
                msg.id === aiMessageId
                  ? { ...msg, content: (msg.content || "") + processedContent }
                  : msg
              ));
            } else if (segment.type === "tool_call") {
              const toolName = segment.toolName || 'unknown';
              const now = Date.now();
              setActiveToolCalls(prev => {
                if (prev.some(tc => tc.toolName === toolName && tc.status === 'running')) {
                  return prev;
                }
                return [...prev, {
                  id: `call_${now}_${toolName}`,
                  callId: `call_${now}`,
                  toolName: toolName,
                  toolArgs: {},
                  status: 'running',
                  startTime: now,
                }];
              });
              if (toolName === 'problem_generate') {
                setProblemGenerateError(null);
              }
            } else if (segment.type === "tool_result") {
              const toolName = segment.toolName;
              const now = Date.now();
              setActiveToolCalls(prev => prev.map(tc => {
                if (tc.toolName === toolName && tc.status === 'running') {
                  const errorData = segment.data as { error?: string; message?: string } | undefined;
                  const errorMsg = errorData?.error || errorData?.message;
                  return {
                    ...tc,
                    status: segment.success ? 'completed' : 'failed',
                    result: segment.data,
                    cached: segment.data?.cached === true,
                    endTime: now,
                    errorMessage: segment.success ? undefined : errorMsg,
                    resultSummary: segment.success ? getResultSummary(toolName, segment.data) : undefined,
                  };
                }
                return tc;
              }));
              if (toolName === 'problem_generate') {
                if (!segment.success) {
                  const errorData = segment.data as { error?: string; message?: string } | undefined;
                  const errorMsg = errorData?.error || errorData?.message || '题目生成失败，请稍后重试';
                  setProblemGenerateError(errorMsg);
                }
              }
            }
          }
          
          if (ackSegments.length > 0) {
            sendImmediateAck(taskId, ackSegments);
          }
          
          lastSegmentIndexRef.current = progress.totalSegments;
        }

        if (progress.allProblems && progress.allProblems.length > 0) {
          console.info('[Polling] allProblems received:', progress.allProblems);
          setGeneratedProblems(prev => {
            const seenIds = new Set<number>();
            const newProblems = progress.allProblems.filter(p => {
              if (p.problemId != null && seenIds.has(p.problemId)) {
                return false;
              }
              if (p.problemId != null) {
                seenIds.add(p.problemId);
              }
              return true;
            }).map(p => {
              const existing = prev.find(ep => ep.problemId === p.problemId);
              return {
                problemId: p.problemId,
                title: p.title,
                difficulty: p.difficulty,
                saveFailed: p.saveFailed,
                saveError: p.saveError,
                acceptanceRate: p.acceptanceRate,
                problemType: p.problemType,
                isRecommended: existing?.isRecommended ?? p.isRecommended ?? false,
              };
            });
            return newProblems;
          });
        }

        if (progress.completed) {
          setPollingTaskId(null);
          setRetrying(false);
          setStreaming(false);
          setLoading(false);
          isRequestPendingRef.current = false;

          setMessages(prev => {
            const aiMsg = prev.find(msg => msg.id === aiMessageId);
            if (aiMsg && !aiMsg.content?.trim()) {
              return prev.map(msg =>
                msg.id === aiMessageId
                  ? { ...msg, content: progress.error || "处理完成，但未生成有效回复" }
                  : msg
              );
            }
            return prev;
          });

          if (progress.error) {
            toast({ title: "AI处理出错", description: progress.error, variant: "destructive" });
          }

          setConversationStep(ConversationStep.CONVERSATION_ACTIVE);
          setupConversationTimeout(appConfig.conversation.followUpTimeout);
          return;
        }

        const elapsed = Date.now() - pollingStartRef.current;
        if (elapsed > 5 * 60 * 1000) {
          setPollingTaskId(null);
          setRetrying(false);
          setStreaming(false);
          setLoading(false);
          isRequestPendingRef.current = false;
          toast({ title: "处理超时", description: "AI处理时间过长，请稍后重试", variant: "destructive" });
          return;
        }

        pollingTimerRef.current = setTimeout(poll, 1500);
      } catch (error) {
        console.error("轮询进度失败:", error);
        consecutiveErrors++;
        
        if (consecutiveErrors >= maxConsecutiveErrors) {
          console.warn(`[轮询] 连续失败 ${consecutiveErrors} 次，检查恢复状态`);
          consecutiveErrors = 0;
          
          checkRecoveryAvailability(taskId).then(status => {
            if (status && status.canRecover) {
              console.info('[轮询] 检测到可恢复的消息段，尝试自动恢复');
              attemptRecovery(taskId, aiMessageId, status.acknowledgedIndex);
            }
          });
        }
        
        pollingTimerRef.current = setTimeout(poll, 3000);
      }
    };

    pollingTimerRef.current = setTimeout(poll, 800);
  }, [setupConversationTimeout, toast, sendImmediateAck, checkRecoveryAvailability, attemptRecovery]);

  const handleInitialInput = async () => {
    if (!initialInput.trim() || loading || isRequestPendingRef.current) return;

    isRequestPendingRef.current = true;

    const userMessage: ChatMessage = {
      id: `msg_${Date.now()}`,
      role: "user",
      content: initialInput.trim(),
      timestamp: new Date().toISOString(),
    };

    setMessages([userMessage]);
    setInitialInput("");
    setLoading(true);
    setStreaming(true);
    setIsConversationActive(true);
    setConversationStep(ConversationStep.INITIAL_RESPONSE);
    setUserResponded(false);
    setActiveToolCalls([]);
    setGeneratedProblems([]);
    setProblemGenerateError(null);

    const aiMessageId = `msg_${Date.now()}_ai`;
    setMessages((prev) => [
      ...prev,
      {
        id: aiMessageId,
        role: "assistant",
        content: "",
        timestamp: new Date().toISOString(),
      },
    ]);

    try {
      const ragOptions = {
        moduleCode: "intelligent_question",
        enableRag: true,
      };

      const taskResult: AgentTaskCreateResult = await retrySyncRequest(() =>
        aiService.createAgentTask([userMessage], undefined, ragOptions)
      );

      if (!taskResult.taskId || taskResult.taskId === "invalid" || taskResult.taskId === "error") {
        toast({ title: "创建任务失败", description: "无法启动AI处理", variant: "destructive" });
        setStreaming(false);
        setLoading(false);
        isRequestPendingRef.current = false;
        return;
      }

      startPolling(taskResult.taskId, aiMessageId);
    } catch (error) {
      console.error("AI请求失败:", error);
      const errorMessage: ChatMessage = {
        id: `msg_${Date.now()}_error`,
        role: "assistant",
        content: "抱歉，暂时无法响应，请稍后重试。",
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, errorMessage]);
      setIsConversationActive(false);
      setConversationStep(ConversationStep.IDLE);
      toast({
        title: "请求失败",
        description: "无法连接到AI服务，请检查网络连接",
        variant: "destructive",
      });
      setStreaming(false);
      setLoading(false);
      isRequestPendingRef.current = false;
    }
  };

  const handleUserResponse = async (message: string) => {
    if (!isConversationActive || !message.trim() || loading || isRequestPendingRef.current) return;

    isRequestPendingRef.current = true;

    const userMessage: ChatMessage = {
      id: `msg_${Date.now()}`,
      role: "user",
      content: message.trim(),
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setLoading(true);
    setStreaming(true);
    setUserResponded(true);
    setConversationStep(ConversationStep.FOLLOWUP_RESPONSE);
    setActiveToolCalls([]);
    setGeneratedProblems([]);
    setProblemGenerateError(null);

    const aiMessageId = `msg_${Date.now()}_ai`;
    setMessages((prev) => [
      ...prev,
      {
        id: aiMessageId,
        role: "assistant",
        content: "",
        timestamp: new Date().toISOString(),
      },
    ]);

    try {
      const allMessages = [...messages, userMessage];

      const ragOptions = {
        moduleCode: "intelligent_question",
        enableRag: true,
      };

      const taskResult: AgentTaskCreateResult = await retrySyncRequest(() =>
        aiService.createAgentTask(allMessages, undefined, ragOptions)
      );

      if (!taskResult.taskId || taskResult.taskId === "invalid" || taskResult.taskId === "error") {
        toast({ title: "创建任务失败", description: "无法启动AI处理", variant: "destructive" });
        setStreaming(false);
        setLoading(false);
        isRequestPendingRef.current = false;
        return;
      }

      startPolling(taskResult.taskId, aiMessageId);
    } catch (error) {
      console.error("AI请求失败:", error);
      const errorMessage: ChatMessage = {
        id: `msg_${Date.now()}_error`,
        role: "assistant",
        content: "抱歉，暂时无法响应，请稍后重试。",
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, errorMessage]);
      toast({
        title: "请求失败",
        description: "无法发送消息，请检查网络连接",
        variant: "destructive",
      });
      setStreaming(false);
      setLoading(false);
      isRequestPendingRef.current = false;
    }
  };

  // 停止对话
  const handleStopConversation = () => {
    setIsConversationActive(false);
    setConversationStep(ConversationStep.IDLE);
    setUserResponded(false);
    setLoading(false);
    setStreaming(false);
    isRequestPendingRef.current = false;

    if (conversationTimeoutRef.current) {
      clearTimeout(conversationTimeoutRef.current);
    }

    if (pollingTimerRef.current) {
      clearTimeout(pollingTimerRef.current);
      pollingTimerRef.current = null;
    }
    setPollingTaskId(null);

    const endMessage: ChatMessage = {
      id: `msg_${Date.now()}_end`,
      role: "assistant",
      content:
        "对话已结束。感谢您的参与！如果您想继续学习，随时可以开始新的对话。",
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, endMessage]);
    toast({
      title: "对话已结束",
      description: "您随时可以开始新的对话",
    });
  };

  // 自动结束对话
  const handleAutoEndConversation = () => {
    setIsConversationActive(false);
    setConversationStep(ConversationStep.IDLE);
    setUserResponded(false);
    setLoading(false);
    setStreaming(false);
    isRequestPendingRef.current = false;

    if (pollingTimerRef.current) {
      clearTimeout(pollingTimerRef.current);
      pollingTimerRef.current = null;
    }
    setPollingTaskId(null);

    const endMessage: ChatMessage = {
      id: `msg_${Date.now()}_timeout`,
      role: "assistant",
      content:
        "检测到长时间未回应，对话已自动结束。如果您还想继续，请重新开始对话。",
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, endMessage]);
    toast({
      title: "对话超时",
      description: "长时间未操作，对话已自动结束",
    });
  };

  // 重新开始对话
  const handleRestartConversation = () => {
    setMessages([]);
    setIsConversationActive(false);
    setConversationStep(ConversationStep.IDLE);
    setUserResponded(false);
    setInitialInput("");
    setInput("");
    setActiveToolCalls([]);
    setGeneratedProblems([]);
    setPollingTaskId(null);
    setProblemGenerateError(null);
    setLoading(false);
    setStreaming(false);
    isRequestPendingRef.current = false;
    if (pollingTimerRef.current) {
      clearTimeout(pollingTimerRef.current);
      pollingTimerRef.current = null;
    }

    if (conversationTimeoutRef.current) {
      clearTimeout(conversationTimeoutRef.current);
    }

    toast({
      title: "对话已重置",
      description: "可以开始新的对话了",
    });
  };

  // 处理初始输入回车
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleInitialInput();
    }
  };

  // 处理聊天输入回车
  const handleChatKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleUserResponse(input);
    }
  };

  // 复制消息内容
  const copyMessage = (content: string) => {
    navigator.clipboard.writeText(content);
    toast({
      title: "已复制",
      description: "消息内容已复制到剪贴板",
    });
  };

  // 格式化时间
  const formatTime = (timestamp: string) => {
    return new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  // 键盘导航：跳转到最新消息 - 和AI助手页面相同的实现
  const handleJumpToLatest = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: reducedMotion ? "auto" : "smooth" });
    resumeAutoScroll();
    textareaRef.current?.focus();
  }, [reducedMotion, resumeAutoScroll]);

  return (
    <motion.div
      initial={reducedMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={reducedMotion ? { duration: 0 } : { duration: 0.4, ease: [0.43, 0.13, 0.23, 0.96] }}
      className="h-[calc(100vh-120px)] flex flex-col"
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4 flex-shrink-0">
        <div className="flex items-center gap-4">
          <Sheet>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="md:hidden flex-shrink-0">
                <Menu className="w-5 h-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-80 p-0 overflow-y-auto">
              <div className="p-6 space-y-4">
                <h2 className="text-lg font-semibold">对话面板</h2>
                {/* Session Context Card */}
                <Card className="border-0 shadow-md">
                  <CardHeader>
                    <CardTitle>本次对话</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {activeToolCalls.length === 0 && generatedProblems.length === 0 ? (
                      <p className="text-sm text-gray-400">对话进行中...</p>
                    ) : (
                      <>
                        {activeToolCalls.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-gray-500 mb-1">工具调用</p>
                            <div className="space-y-1.5">
                              {activeToolCalls.map(tc => {
                                const toolDisplayName = getToolDisplayName(tc.toolName);
                                return (
                                  <div key={tc.id} className="flex items-center gap-1.5 text-xs">
                                    {tc.status === 'pending' ? <Wrench className="h-3 w-3 text-slate-400" /> :
                                     tc.status === 'running' ? <Loader2 className="h-3 w-3 animate-spin text-amber-500" /> :
                                     tc.status === 'completed' ? <CheckCircle2 className="h-3 w-3 text-green-500" /> :
                                     <XCircle className="h-3 w-3 text-red-500" />}
                                    <span className={`${
                                      tc.status === 'failed' ? 'text-red-500' : 'text-gray-600 dark:text-gray-400'
                                    }`}>
                                      {toolDisplayName}
                                    </span>
                                    {tc.status === 'pending' && (
                                      <span className="text-[10px] text-slate-400">准备中</span>
                                    )}
                                    {tc.status === 'running' && (
                                      <span className="text-[10px] text-amber-500">执行中</span>
                                    )}
                                    {tc.status === 'completed' && tc.resultSummary && (
                                      <span className="text-[10px] text-green-500 truncate max-w-[100px]">{tc.resultSummary}</span>
                                    )}
                                    {tc.status === 'failed' && tc.errorMessage && (
                                      <span className="text-[10px] text-red-400 truncate max-w-[80px]">{tc.errorMessage}</span>
                                    )}
                                    {tc.cached && (
                                      <span className="flex items-center gap-0.5 text-[10px] text-gray-400">
                                        <Database className="h-2.5 w-2.5" />
                                        缓存
                                      </span>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        )}
                        {generatedProblems.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-gray-500 mb-1">已生成题目</p>
                            <div className="space-y-1">
                              {generatedProblems.map((p, idx) => {
                                const content = (
                                  <>
                                    <Badge variant="outline" className={`text-[10px] px-1 py-0 ${
                                      p.difficulty === 'easy' ? 'border-green-300 text-green-600' :
                                      p.difficulty === 'medium' ? 'border-yellow-300 text-yellow-600' :
                                      'border-red-300 text-red-600'
                                    }`}>
                                      {p.difficulty === 'easy' ? '简' : p.difficulty === 'medium' ? '中' : '难'}
                                    </Badge>
                                    {p.isRecommended && (
                                      <span className="text-[10px] text-green-500">推荐</span>
                                    )}
                                    <span className={`truncate ${p.saveFailed ? 'text-orange-500' : 'text-gray-600 dark:text-gray-400'}`}>{p.title}</span>
                                    {p.saveFailed && (
                                      <span className="text-[10px] text-red-500">保存失败</span>
                                    )}
                                  </>
                                );
                                return !p.saveFailed && p.problemId ? (
                                  <Link key={p.problemId || `sheet_problem_${idx}`} href={`/problems/${p.problemId}`} className="flex items-center gap-1.5 text-xs hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
                                    {content}
                                  </Link>
                                ) : (
                                  <div key={p.problemId || `sheet_problem_${idx}`} className="flex items-center gap-1.5 text-xs">
                                    {content}
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        )}
                      </>
                    )}
                  </CardContent>
                </Card>

                {/* Control Card */}
                {messages.length > 0 && (
                  <Card className="border-0 shadow-md">
                    <CardHeader>
                      <CardTitle>对话控制</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-2">
                      <Button
                        variant="outline"
                        className="w-full flex items-center gap-2"
                        onClick={handleStopConversation}
                        disabled={!isConversationActive}
                      >
                        <Square className="w-4 h-4" />
                        结束当前对话
                      </Button>
                      <Button
                        variant="outline"
                        className="w-full flex items-center gap-2"
                        onClick={handleRestartConversation}
                      >
                        <RefreshCw className="w-4 h-4" />
                        重新开始
                      </Button>
                      {pollingTaskId && recoveryAvailable && (
                        <Button
                          variant="outline"
                          className="w-full flex items-center gap-2 border-amber-300 text-amber-700 hover:bg-amber-50 dark:border-amber-700 dark:text-amber-400 dark:hover:bg-amber-950/30"
                          onClick={() => {
                            const lastAiMessage = [...messages].reverse().find(m => m.role === 'assistant');
                            if (lastAiMessage) {
                              attemptRecovery(pollingTaskId, lastAiMessage.id, lastAcknowledgedSeqRef.current);
                            }
                          }}
                          disabled={isRecovering || loading}
                        >
                          {isRecovering ? (
                            <>
                              <Loader2 className="w-4 h-4 animate-spin" />
                              恢复中...
                            </>
                          ) : (
                            <>
                              <RefreshCw className="w-4 h-4" />
                              恢复消息
                            </>
                          )}
                        </Button>
                      )}
                      {recoveryError && (
                        <div className="text-xs text-red-500 p-2 bg-red-50 dark:bg-red-950/30 rounded">
                          {recoveryError}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                )}

                {/* Tips Card */}
                <Card className="border-0 shadow-md">
                  <CardHeader>
                    <CardTitle>使用建议</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <ul className="list-disc list-inside space-y-2 text-sm text-gray-600 dark:text-gray-400">
                      <li>清晰描述您的学习目标</li>
                      <li>积极参与AI提出的每个问题</li>
                      <li>思考后再回答，质量比速度重要</li>
                      <li>如需知识库增强，请联系管理员配置</li>
                    </ul>
                  </CardContent>
                </Card>

                {/* Notification History Card */}
                {notificationHistory.length > 0 && (
                  <Card className="border-0 shadow-md">
                    <CardHeader className="flex flex-row items-center justify-between py-3">
                      <CardTitle className="text-sm">通知历史</CardTitle>
                      {unreadCount > 0 && (
                        <Badge className="bg-red-500 text-white text-[10px] px-1.5 py-0.5">
                          {unreadCount}
                        </Badge>
                      )}
                    </CardHeader>
                    <CardContent className="max-h-48 overflow-y-auto">
                      <div className="space-y-2">
                        {notificationHistory.slice(0, 10).map((item) => (
                          <div 
                            key={item.id} 
                            className={`text-xs p-2 rounded ${
                              item.read 
                                ? 'bg-gray-50 dark:bg-gray-800' 
                                : 'bg-blue-50 dark:bg-blue-900/20'
                            }`}
                          >
                            <div className="flex items-center gap-1.5">
                              {item.eventType === 'TASK_COMPLETED' && (
                                <CheckCircle2 className="h-3 w-3 text-green-500" />
                              )}
                              {item.eventType === 'TASK_FAILED' && (
                                <XCircle className="h-3 w-3 text-red-500" />
                              )}
                              {item.eventType === 'PROBLEM_GENERATED' && (
                                <Lightbulb className="h-3 w-3 text-blue-500" />
                              )}
                              {item.eventType === 'PROBLEM_RECOMMENDED' && (
                                <Sparkles className="h-3 w-3 text-purple-500" />
                              )}
                              {!['TASK_COMPLETED', 'TASK_FAILED', 'PROBLEM_GENERATED', 'PROBLEM_RECOMMENDED'].includes(item.eventType) && (
                                <Bell className="h-3 w-3 text-gray-400" />
                              )}
                              <span className="text-gray-700 dark:text-gray-300">
                                {item.displayMessage}
                              </span>
                            </div>
                            <div className="text-[10px] text-gray-400 mt-1">
                              {new Date(item.timestamp).toLocaleTimeString('zh-CN', {
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </div>
                          </div>
                        ))}
                      </div>
                      {unreadCount > 0 && (
                        <Button 
                          variant="ghost" 
                          size="sm" 
                          className="w-full mt-2 text-xs"
                          onClick={markAllAsRead}
                        >
                          全部标记为已读
                        </Button>
                      )}
                    </CardContent>
                  </Card>
                )}
              </div>
            </SheetContent>
          </Sheet>
          <motion.div
            whileHover={{ scale: 1.05, rotate: 5 }}
            transition={{ type: "spring", stiffness: 400 }}
            className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-gradient-to-br from-purple-500 via-violet-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-purple-500/30 cursor-pointer"
          >
            <HelpCircle className="w-5 h-5 sm:w-6 sm:h-6 text-white" />
          </motion.div>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-gray-900 dark:text-white">
              智能提问者
            </h1>
            <div className="flex items-center gap-2 mt-1">
              <Badge className="bg-gradient-to-r from-purple-600 to-indigo-600 text-white hover:from-purple-700 hover:to-indigo-700 border-0 shadow-sm">
                智能引导
              </Badge>
              {ragEnabled && (
                <Badge variant="outline" className="border-green-400 text-green-600 dark:border-green-600 dark:text-green-400 flex items-center gap-1 bg-green-50 dark:bg-green-900/30">
                  <Database className="w-3 h-3" />
                  知识库增强
                </Badge>
              )}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {isConversationActive && (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.9 }}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Button
                variant="destructive"
                size="lg"
                onClick={handleStopConversation}
                className="flex items-center gap-2 shadow-lg shadow-red-500/20"
              >
                <Square className="w-4 h-4" />
                <span className="hidden sm:inline">停止对话</span>
              </Button>
            </motion.div>
          )}
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col lg:flex-row gap-6 overflow-hidden min-h-0">
        {/* Chat Area */}
        <div className="flex-1 overflow-hidden min-h-0">
          <AnimatePresence mode="wait">
            {!initialized ? (
              <motion.div
                key="loading"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="h-full"
              >
                <Card className="h-full flex items-center justify-center border-0 shadow-lg">
                  <div className="text-center space-y-4">
                    <Skeleton className="h-12 w-12 rounded-full mx-auto" />
                    <Skeleton className="h-4 w-48 mx-auto" />
                    <div className="text-gray-500">
                      智能提问者正在准备中...
                    </div>
                  </div>
                </Card>
              </motion.div>
            ) : isConversationActive ? (
              <motion.div
                key="chat"
                initial={reducedMotion ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.98 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={reducedMotion ? { opacity: 0, scale: 1 } : { opacity: 0, scale: 0.98 }}
                transition={reducedMotion ? { duration: 0 } : { duration: 0.3 }}
                className="h-full"
              >
                <Card className="flex-1 border-0 shadow-lg overflow-hidden flex flex-col relative h-full">
                  {/* Messages */}
                  <CardContent
                    ref={containerRef}
                    className="flex-1 overflow-y-auto p-6 space-y-4 relative"
                  >
                    <AnimatePresence>
                      {messages.map((message, index) => (
                        <motion.div
                          key={message.id}
                          variants={getMessageVariants(message.role)}
                          initial="hidden"
                          animate="visible"
                          exit="exit"
                          custom={index}
                          className={`flex gap-4 ${
                            message.role === "user" ? "flex-row-reverse" : ""
                          }`}
                        >
                          {/* Avatar */}
                          <motion.div
                            whileHover={{ scale: 1.05 }}
                            className={`w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 ring-2 ring-white dark:ring-gray-800 shadow-lg ${
                              message.role === "assistant"
                                ? "bg-gradient-to-br from-purple-500 via-violet-500 to-indigo-600 shadow-purple-500/30"
                                : "bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-700 dark:to-gray-600"
                            }`}
                          >
                            {message.role === "assistant" ? (
                              <Sparkles className="w-5 h-5 text-white" />
                            ) : (
                              <User className="w-5 h-5 text-gray-600 dark:text-gray-300" />
                            )}
                          </motion.div>

                          {/* Message Content */}
                          <div
                            className={`max-w-[95%] sm:max-w-[85%] md:max-w-[80%] lg:max-w-[70%] ${
                              message.role === "user" ? "items-end" : "items-start"
                            }`}
                          >
                            <div
                              className={`relative p-4 shadow-lg ${
                                message.role === "assistant"
                                  ? "bg-white dark:bg-gray-800 text-gray-900 dark:text-white border border-gray-100 dark:border-gray-700 rounded-2xl rounded-tl-none"
                                  : "bg-gradient-to-br from-purple-600 via-violet-600 to-indigo-600 text-white rounded-2xl rounded-tr-none shadow-purple-500/20"
                              }`}
                            >
                              {message.role === "user" && (
                                <div className="absolute inset-0 rounded-2xl rounded-tr-none bg-gradient-to-br from-white/10 to-transparent" />
                              )}
                              {/* Content with Markdown Support */}
                              {message.role === "assistant" ? (
                                <div className="prose dark:prose-invert max-w-none text-sm">
                                  <MarkdownRenderer
                                    content={message.content || "思考中..."}
                                    isStreaming={streaming && message.role === "assistant"}
                                  />
                                </div>
                              ) : (
                                <p className="relative whitespace-pre-wrap leading-relaxed text-sm sm:text-base">
                                  {message.content}
                                </p>
                              )}
                            </div>
                            <div className="flex items-center gap-2 mt-2 px-1">
                              <span className="text-xs text-gray-400 dark:text-gray-500">
                                {formatTime(message.timestamp)}
                              </span>
                              {message.role === "assistant" &&
                                message.content && (
                                  <motion.button
                                    whileHover={{ scale: 1.05 }}
                                    whileTap={{ scale: 0.95 }}
                                    onClick={() => copyMessage(message.content)}
                                    className="text-xs text-gray-400 hover:text-purple-600 dark:hover:text-purple-400 flex items-center gap-1 transition-colors"
                                  >
                                    <Copy className="w-3 h-3" />
                                    复制
                                  </motion.button>
                                )}
                            </div>
                          </div>
                        </motion.div>
                      ))}
                    </AnimatePresence>

                    {/* Tool Call Status Cards */}
                    {activeToolCalls.length > 0 && (
                      <div className="space-y-3 mb-4">
                        {activeToolCalls.map((tc, idx) => {
                          const toolDisplayName = getToolDisplayName(tc.toolName);
                          const toolDescription = getToolDescription(tc.toolName);
                          const elapsedTime = tc.endTime 
                            ? Math.round((tc.endTime - tc.startTime) / 1000) 
                            : tc.status === 'running' 
                              ? Math.round((Date.now() - tc.startTime) / 1000) 
                              : 0;
                          
                          return (
                            <motion.div
                              key={tc.id}
                              initial={{ opacity: 0, y: 10, scale: 0.95 }}
                              animate={{ opacity: 1, y: 0, scale: 1 }}
                              exit={{ opacity: 0, y: -10, scale: 0.95 }}
                              transition={{ duration: 0.3, delay: idx * 0.05 }}
                              className={`relative overflow-hidden rounded-xl border shadow-sm ${
                                tc.status === 'pending' 
                                  ? 'bg-gradient-to-r from-slate-50 via-gray-50 to-slate-50 dark:from-slate-950/40 dark:via-gray-950/40 dark:to-slate-950/40 border-slate-300 dark:border-slate-700' 
                                  : tc.status === 'running' 
                                    ? 'bg-gradient-to-r from-amber-50 via-orange-50 to-amber-50 dark:from-amber-950/40 dark:via-orange-950/40 dark:to-amber-950/40 border-amber-300 dark:border-amber-700' 
                                    : tc.status === 'completed' 
                                      ? 'bg-gradient-to-r from-emerald-50 via-green-50 to-teal-50 dark:from-emerald-950/40 dark:via-green-950/40 dark:to-teal-950/40 border-emerald-300 dark:border-emerald-700' 
                                      : 'bg-gradient-to-r from-red-50 via-rose-50 to-pink-50 dark:from-red-950/40 dark:via-rose-950/40 dark:to-pink-950/40 border-red-300 dark:border-red-700'
                              }`}
                            >
                              {tc.status === 'running' && (
                                <motion.div 
                                  className="absolute inset-0 bg-gradient-to-r from-transparent via-white/30 to-transparent"
                                  animate={{ x: ['-100%', '100%'] }}
                                  transition={{ duration: 1.5, repeat: Infinity, ease: 'linear' }}
                                />
                              )}
                              <div className="relative flex items-start gap-3 px-4 py-3">
                                <motion.div
                                  animate={tc.status === 'running' ? { scale: [1, 1.1, 1] } : {}}
                                  transition={{ duration: 1.5, repeat: Infinity }}
                                  className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${
                                    tc.status === 'pending' 
                                      ? 'bg-gradient-to-br from-slate-400 to-gray-500' 
                                      : tc.status === 'running' 
                                        ? 'bg-gradient-to-br from-amber-400 to-orange-500 shadow-lg shadow-amber-500/30' 
                                        : tc.status === 'completed' 
                                          ? 'bg-gradient-to-br from-emerald-400 to-teal-500 shadow-lg shadow-emerald-500/30' 
                                          : 'bg-gradient-to-br from-red-400 to-rose-500 shadow-lg shadow-red-500/30'
                                  }`}
                                >
                                  {tc.status === 'pending' ? (
                                    <Wrench className="h-5 w-5 text-white" />
                                  ) : tc.status === 'running' ? (
                                    <Loader2 className="h-5 w-5 text-white animate-spin" />
                                  ) : tc.status === 'completed' ? (
                                    <CheckCircle2 className="h-5 w-5 text-white" />
                                  ) : (
                                    <XCircle className="h-5 w-5 text-white" />
                                  )}
                                </motion.div>
                                <div className="flex-1 min-w-0">
                                  <div className="flex items-center gap-2 flex-wrap mb-1">
                                    <span className="text-sm font-semibold text-gray-800 dark:text-gray-200">
                                      {toolDisplayName}
                                    </span>
                                    {tc.status === 'pending' && (
                                      <span className="text-xs font-medium text-slate-600 dark:text-slate-400 bg-slate-100/80 dark:bg-slate-800/50 px-2 py-0.5 rounded-full">
                                        准备中...
                                      </span>
                                    )}
                                    {tc.status === 'running' && (
                                      <motion.span 
                                        animate={{ opacity: [0.5, 1, 0.5] }}
                                        transition={{ duration: 1.5, repeat: Infinity }}
                                        className="text-xs font-medium text-amber-600 dark:text-amber-400 bg-amber-100/80 dark:bg-amber-900/50 px-2 py-0.5 rounded-full"
                                      >
                                        执行中...
                                      </motion.span>
                                    )}
                                    {tc.status === 'completed' && (
                                      <span className="text-xs font-medium text-emerald-600 dark:text-emerald-400 bg-emerald-100/80 dark:bg-emerald-900/50 px-2 py-0.5 rounded-full">
                                        完成
                                      </span>
                                    )}
                                    {tc.status === 'failed' && (
                                      <span className="text-xs font-medium text-red-600 dark:text-red-400 bg-red-100/80 dark:bg-red-900/50 px-2 py-0.5 rounded-full">
                                        失败
                                      </span>
                                    )}
                                    {tc.cached && (
                                      <span className="flex items-center gap-1 text-[10px] font-medium text-blue-600 dark:text-blue-400 bg-blue-100/80 dark:bg-blue-900/50 px-2 py-0.5 rounded-full">
                                        <Database className="h-3 w-3" />
                                        缓存
                                      </span>
                                    )}
                                    {elapsedTime > 0 && (
                                      <span className="text-[10px] text-gray-400">
                                        {elapsedTime}s
                                      </span>
                                    )}
                                  </div>
                                  {tc.status === 'running' && (
                                    <p className="text-xs text-gray-500 dark:text-gray-400">
                                      {toolDescription}
                                    </p>
                                  )}
                                  {tc.status === 'completed' && tc.resultSummary && (
                                    <p className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">
                                      {tc.resultSummary}
                                    </p>
                                  )}
                                  {tc.status === 'failed' && tc.errorMessage && (
                                    <div className="mt-1">
                                      <p className="text-xs text-red-600 dark:text-red-400">
                                        {tc.errorMessage}
                                      </p>
                                      <p className="text-[10px] text-gray-400 mt-1">
                                        请尝试重新描述您的需求或稍后重试
                                      </p>
                                    </div>
                                  )}
                                </div>
                              </div>
                            </motion.div>
                          );
                        })}
                      </div>
                    )}

                    {/* Generated Problem Cards */}
                    {generatedProblems.length > 0 && (
                      <div className="space-y-4 mb-4">
                        {generatedProblems.map((p, idx) => {
                          const hasLink = !p.saveFailed && p.problemId;
                          const cardContent = (
                            <motion.div
                              initial={{ opacity: 0, y: 15, scale: 0.97 }}
                              animate={{ opacity: 1, y: 0, scale: 1 }}
                              transition={{ duration: 0.4, delay: idx * 0.08, ease: [0.43, 0.13, 0.23, 0.96] }}
                              whileHover={hasLink ? { y: -3, transition: { duration: 0.2 } } : {}}
                              className={`relative overflow-hidden rounded-2xl transition-all duration-300 ${
                                p.isRecommended
                                  ? 'bg-gradient-to-br from-emerald-50 via-green-50 to-teal-50 dark:from-emerald-950/50 dark:via-green-950/50 dark:to-teal-950/50'
                                  : p.saveFailed
                                    ? 'bg-gradient-to-br from-orange-50 via-amber-50 to-yellow-50 dark:from-orange-950/50 dark:via-amber-950/50 dark:to-yellow-950/50'
                                    : 'bg-gradient-to-br from-blue-50 via-indigo-50 to-violet-50 dark:from-blue-950/50 dark:via-indigo-950/50 dark:to-violet-950/50'
                              } ${hasLink ? 'cursor-pointer group' : ''}`}
                            >
                              <div className={`absolute inset-0 rounded-2xl p-[1.5px] ${
                                p.isRecommended
                                  ? 'bg-gradient-to-br from-emerald-400 via-green-400 to-teal-400'
                                  : p.saveFailed
                                    ? 'bg-gradient-to-br from-orange-400 via-amber-400 to-yellow-400'
                                    : 'bg-gradient-to-br from-blue-400 via-indigo-400 to-violet-400'
                              }`}>
                                <div className={`w-full h-full rounded-xl ${
                                  p.isRecommended
                                    ? 'bg-gradient-to-br from-emerald-50 via-green-50 to-teal-50 dark:from-emerald-950/95 dark:via-green-950/95 dark:to-teal-950/95'
                                    : p.saveFailed
                                      ? 'bg-gradient-to-br from-orange-50 via-amber-50 to-yellow-50 dark:from-orange-950/95 dark:via-amber-950/95 dark:to-yellow-950/95'
                                      : 'bg-gradient-to-br from-blue-50 via-indigo-50 to-violet-50 dark:from-blue-950/95 dark:via-indigo-950/95 dark:to-violet-950/95'
                                }`} />
                              </div>
                              <div className="relative p-5">
                                <div className="flex items-center justify-between mb-3">
                                  <div className="flex items-center gap-2.5">
                                    <motion.div
                                      animate={{ rotate: p.isRecommended ? [0, 10, -10, 0] : 0 }}
                                      transition={{ duration: 0.5, delay: idx * 0.1 }}
                                      className={`w-10 h-10 rounded-xl flex items-center justify-center shadow-lg ${
                                        p.isRecommended
                                          ? 'bg-gradient-to-br from-emerald-400 to-teal-500 shadow-emerald-500/30'
                                          : p.saveFailed
                                            ? 'bg-gradient-to-br from-orange-400 to-amber-500 shadow-orange-500/30'
                                            : 'bg-gradient-to-br from-blue-400 to-indigo-500 shadow-blue-500/30'
                                      }`}
                                    >
                                      {p.isRecommended ? (
                                        <Lightbulb className="h-5 w-5 text-white" />
                                      ) : p.saveFailed ? (
                                        <XCircle className="h-5 w-5 text-white" />
                                      ) : (
                                        <CheckCircle2 className="h-5 w-5 text-white" />
                                      )}
                                    </motion.div>
                                    <span className={`text-sm font-bold ${
                                      p.isRecommended 
                                        ? 'text-emerald-700 dark:text-emerald-300' 
                                        : p.saveFailed 
                                          ? 'text-orange-700 dark:text-orange-300' 
                                          : 'text-blue-700 dark:text-blue-300'
                                    }`}>
                                      {p.isRecommended ? '推荐题目' : p.saveFailed ? '题目生成失败' : '题目创建成功'}
                                    </span>
                                  </div>
                                  <div className="flex items-center gap-2">
                                    {p.isRecommended && (
                                      <Badge className="text-[10px] font-semibold bg-gradient-to-r from-emerald-100 to-teal-100 text-emerald-700 dark:from-emerald-900/60 dark:to-teal-900/60 dark:text-emerald-300 border-0 shadow-sm">
                                        推荐
                                      </Badge>
                                    )}
                                    {p.saveFailed && (
                                      <Badge className="text-[10px] font-semibold bg-gradient-to-r from-red-100 to-rose-100 text-red-700 dark:from-red-900/60 dark:to-rose-900/60 dark:text-red-300 border-0 shadow-sm">
                                        保存失败
                                      </Badge>
                                    )}
                                    <Badge className={`text-[10px] font-semibold border-0 shadow-sm ${
                                      p.difficulty === 'easy' 
                                        ? 'bg-gradient-to-r from-green-100 to-emerald-100 text-green-700 dark:from-green-900/60 dark:to-emerald-900/60 dark:text-green-300' 
                                        : p.difficulty === 'medium' 
                                          ? 'bg-gradient-to-r from-yellow-100 to-amber-100 text-yellow-700 dark:from-yellow-900/60 dark:to-amber-900/60 dark:text-yellow-300' 
                                          : 'bg-gradient-to-r from-red-100 to-rose-100 text-red-700 dark:from-red-900/60 dark:to-rose-900/60 dark:text-red-300'
                                    }`}>
                                      {p.difficulty === 'easy' ? '简单' : p.difficulty === 'medium' ? '中等' : '困难'}
                                    </Badge>
                                  </div>
                                </div>
                                <p className="text-base font-bold text-gray-800 dark:text-gray-100 mb-2 line-clamp-2">{p.title}</p>
                                {p.saveFailed && p.saveError && (
                                  <div className="text-xs text-red-600 dark:text-red-400 mb-3 bg-red-100/80 dark:bg-red-900/40 px-3 py-2 rounded-lg border border-red-200 dark:border-red-800">
                                    错误: {p.saveError}
                                  </div>
                                )}
                                <div className="flex items-center justify-between pt-2 border-t border-gray-200/50 dark:border-gray-700/50">
                                  <div className="flex items-center gap-4">
                                    <span className="text-xs text-gray-500 dark:text-gray-400 font-medium">
                                      ID: {p.problemId || '未保存'}
                                    </span>
                                    {p.isRecommended && p.acceptanceRate != null && (
                                      <span className="text-xs text-gray-500 dark:text-gray-400 font-medium">
                                        通过率: {typeof p.acceptanceRate === 'number' ? (p.acceptanceRate > 1 ? p.acceptanceRate.toFixed(0) : (p.acceptanceRate * 100).toFixed(0)) : 'N/A'}%
                                      </span>
                                    )}
                                  </div>
                                  {hasLink && (
                                    <motion.span 
                                      className="inline-flex items-center gap-1.5 text-xs font-bold text-blue-500 dark:text-blue-400 group-hover:text-blue-600 dark:group-hover:text-blue-300"
                                      whileHover={{ x: 3 }}
                                      transition={{ duration: 0.2 }}
                                    >
                                      查看详情
                                      <ExternalLink className="w-3.5 h-3.5" />
                                    </motion.span>
                                  )}
                                </div>
                              </div>
                            </motion.div>
                          );

                          if (hasLink) {
                            return (
                              <Link key={p.problemId || `problem_${idx}`} href={`/problems/${p.problemId}`}>
                                {cardContent}
                              </Link>
                            );
                          }
                          return <div key={p.problemId || `problem_${idx}`}>{cardContent}</div>;
                        })}
                      </div>
                    )}

                    {/* Loading Indicator */}
                    {loading && !retrying && (
                      <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3 }}
                        className="flex gap-4"
                      >
                        <motion.div
                          animate={{ scale: [1, 1.05, 1] }}
                          transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
                          className="w-10 h-10 rounded-full bg-gradient-to-br from-purple-500 via-violet-500 to-indigo-600 flex items-center justify-center ring-2 ring-white dark:ring-gray-800 shadow-lg shadow-purple-500/30"
                        >
                          <Bot className="w-5 h-5 text-white" />
                        </motion.div>
                        <div className="relative overflow-hidden bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 p-4 rounded-2xl rounded-tl-none shadow-md flex items-center gap-3">
                          <div className="absolute inset-0 bg-gradient-to-r from-purple-500/5 via-violet-500/5 to-indigo-500/5" />
                          <motion.div
                            animate={{ rotate: 360 }}
                            transition={{ duration: 1.5, repeat: Infinity, ease: "linear" }}
                            className="w-5 h-5 rounded-full border-2 border-purple-200 dark:border-purple-800 border-t-purple-600 dark:border-t-purple-400"
                          />
                          <span className="relative text-gray-600 dark:text-gray-300 font-medium">AI正在思考...</span>
                        </div>
                      </motion.div>
                    )}
                    {retrying && (
                      <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3 }}
                        className="flex gap-4"
                      >
                        <motion.div
                          animate={{ scale: [1, 1.1, 1] }}
                          transition={{ duration: 1, repeat: Infinity }}
                          className="w-10 h-10 rounded-full bg-gradient-to-br from-amber-500 via-orange-500 to-red-500 flex items-center justify-center ring-2 ring-white dark:ring-gray-800 shadow-lg shadow-amber-500/30"
                        >
                          <RefreshCw className="w-5 h-5 text-white" />
                        </motion.div>
                        <div className="relative overflow-hidden bg-gradient-to-r from-amber-50 via-orange-50 to-yellow-50 dark:from-amber-950/50 dark:via-orange-950/50 dark:to-yellow-950/50 border border-amber-200 dark:border-amber-800 p-4 rounded-2xl rounded-tl-none shadow-md flex items-center gap-3">
                          <motion.div
                            animate={{ rotate: 360 }}
                            transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                            className="w-5 h-5 rounded-full border-2 border-amber-300 dark:border-amber-700 border-t-amber-600 dark:border-t-amber-400"
                          />
                          <span className="text-amber-700 dark:text-amber-300 font-medium">重新连接中...</span>
                        </div>
                      </motion.div>
                    )}
                    {problemGenerateError && !activeToolCalls.some(tc => tc.toolName === 'problem_generate' && tc.status === 'running') && (
                      <motion.div
                        initial={{ opacity: 0, y: 10, x: -5 }}
                        animate={{ opacity: 1, y: 0, x: 0 }}
                        transition={{ duration: 0.3, type: "spring", stiffness: 300 }}
                        className="flex gap-4"
                      >
                        <motion.div
                          animate={{ scale: [1, 1.1, 1] }}
                          transition={{ duration: 0.5 }}
                          className="w-10 h-10 rounded-full bg-gradient-to-br from-red-500 via-rose-500 to-pink-600 flex items-center justify-center ring-2 ring-white dark:ring-gray-800 shadow-lg shadow-red-500/30"
                        >
                          <XCircle className="w-5 h-5 text-white" />
                        </motion.div>
                        <div className="relative overflow-hidden bg-gradient-to-r from-red-50 via-rose-50 to-pink-50 dark:from-red-950/50 dark:via-rose-950/50 dark:to-pink-950/50 border border-red-200 dark:border-red-800 p-4 rounded-2xl rounded-tl-none shadow-md flex items-center gap-3">
                          <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-red-400 via-rose-400 to-pink-400" />
                          <XCircle className="w-5 h-5 text-red-600 dark:text-red-400 flex-shrink-0" />
                          <div className="flex flex-col">
                            <span className="text-red-700 dark:text-red-300 font-semibold">题目生成失败</span>
                            <span className="text-red-600 dark:text-red-400 text-xs">{problemGenerateError}</span>
                            <span className="text-red-500 dark:text-red-500 text-[10px] mt-1">请尝试重新描述您的需求或稍后重试</span>
                          </div>
                        </div>
                      </motion.div>
                    )}
                    <div ref={messagesEndRef} tabIndex={-1} aria-hidden="true" />
                  </CardContent>

                  {/* 返回底部浮动按钮 */}
                  <AnimatePresence>
                    {showScrollButton && (
                      <motion.div
                        initial={reducedMotion ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.8 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={reducedMotion ? { opacity: 0, scale: 0.8 } : { opacity: 0, scale: 0.8 }}
                        transition={reducedMotion ? { duration: 0 } : { duration: 0.2 }}
                        className="absolute bottom-24 left-1/2 -translate-x-1/2 z-10"
                      >
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={handleJumpToLatest}
                          className="shadow-lg rounded-full px-4 py-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700"
                        >
                          <ChevronDown className="w-4 h-4 mr-1" />
                          返回底部
                        </Button>
                      </motion.div>
                    )}
                  </AnimatePresence>

                  {/* Input Area */}
                  <div className="p-4 sm:p-5 border-t border-gray-100 dark:border-gray-800 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm flex-shrink-0">
                    <div className="flex gap-3 max-w-3xl mx-auto">
                      <div className="flex-1 relative group">
                        <div className="absolute -inset-0.5 bg-gradient-to-r from-purple-400 via-violet-400 to-indigo-400 rounded-xl opacity-0 group-focus-within:opacity-40 blur transition-all duration-300" />
                        <textarea
                          ref={textareaRef}
                          value={input}
                          onChange={(e) => setInput(e.target.value)}
                          onKeyDown={handleChatKeyDown}
                          placeholder="请输入您的回答..."
                          rows={1}
                          disabled={loading}
                          className="relative w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500 resize-none disabled:opacity-50 leading-relaxed text-sm sm:text-base shadow-sm transition-all duration-300"
                          style={{ minHeight: "48px", maxHeight: "120px" }}
                        />
                      </div>
                      <motion.div
                        whileHover={{ scale: 1.02 }}
                        whileTap={{ scale: 0.98 }}
                      >
                        <Button
                          onClick={() => handleUserResponse(input)}
                          disabled={!input.trim() || loading}
                          className="min-w-[48px] min-h-[48px] bg-gradient-to-r from-purple-600 via-violet-600 to-indigo-600 hover:from-purple-700 hover:via-violet-700 hover:to-indigo-700 shadow-lg shadow-purple-500/30 hover:shadow-xl hover:shadow-purple-500/40 transition-all duration-300 rounded-xl"
                        >
                          {loading ? (
                            <Loader2 className="w-5 h-5 animate-spin" />
                          ) : (
                            <Send className="w-5 h-5" />
                          )}
                        </Button>
                      </motion.div>
                    </div>
                    <p className="text-xs text-gray-400 dark:text-gray-500 mt-2 text-center">
                      按 Enter 发送，Shift + Enter 换行
                    </p>
                  </div>
                </Card>
              </motion.div>
            ) : (
              <motion.div
                key="input"
                initial={reducedMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={reducedMotion ? { opacity: 0, y: 0 } : { opacity: 0, y: -20 }}
                transition={reducedMotion ? { duration: 0 } : { duration: 0.3 }}
                className="h-full"
              >
                <Card className="h-full border-0 shadow-lg bg-gradient-to-br from-purple-50 via-violet-50 to-indigo-50 dark:from-purple-950/40 dark:via-violet-950/40 dark:to-indigo-950/40 overflow-hidden">
                  <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-purple-200/30 via-transparent to-transparent dark:from-purple-800/20" />
                  <div className="absolute bottom-0 left-0 right-0 h-1/2 bg-[radial-gradient(ellipse_at_bottom_left,_var(--tw-gradient-stops))] from-indigo-200/30 via-transparent to-transparent dark:from-indigo-800/20" />
                  <CardContent className="relative max-w-2xl mx-auto h-full flex flex-col justify-center py-8 px-4 sm:px-6">
                    <div className="text-center mb-8">
                      <motion.div
                        animate={reducedMotion ? {} : { scale: [1, 1.05, 1], rotate: [0, 2, -2, 0] }}
                        transition={reducedMotion ? {} : { duration: 4, repeat: Infinity, ease: "easeInOut" }}
                        className="relative w-20 h-20 sm:w-24 sm:h-24 rounded-2xl bg-gradient-to-br from-purple-500 via-violet-500 to-indigo-600 flex items-center justify-center mx-auto mb-6 shadow-2xl shadow-purple-500/40"
                      >
                        <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-white/20 to-transparent" />
                        <Lightbulb className="text-3xl sm:text-4xl text-white relative z-10" />
                        <motion.div
                          animate={{ scale: [1, 1.2, 1], opacity: [0.5, 0.8, 0.5] }}
                          transition={{ duration: 2, repeat: Infinity }}
                          className="absolute inset-0 rounded-2xl bg-gradient-to-br from-purple-400 to-indigo-500 blur-xl -z-10"
                        />
                      </motion.div>
                      <h2 className="text-2xl sm:text-3xl lg:text-4xl font-bold bg-gradient-to-r from-purple-600 via-violet-600 to-indigo-600 bg-clip-text text-transparent mb-3">
                        开始智能对话
                      </h2>
                      <p className="text-sm sm:text-base text-gray-500 dark:text-gray-400 max-w-md mx-auto">
                        AI将引导您深入学习，让学习更高效、更有趣
                      </p>
                    </div>

                    <div className="w-full space-y-4">
                      <div className="relative group">
                        <div className="absolute -inset-1 bg-gradient-to-r from-purple-400 via-violet-400 to-indigo-400 rounded-2xl opacity-30 group-hover:opacity-50 blur transition-all duration-300" />
                        <textarea
                          rows={5}
                          placeholder="例如：我想学习数据结构中的树相关算法 / 我正在准备算法面试 / 我想深入了解JavaScript异步编程..."
                          value={initialInput}
                          onChange={(e) => setInitialInput(e.target.value)}
                          onKeyDown={handleKeyPress}
                          disabled={loading}
                          className="relative w-full resize-none text-sm sm:text-base rounded-xl border-2 border-purple-200 dark:border-purple-700 focus:border-purple-500 dark:focus:border-purple-400 bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm text-gray-900 dark:text-white px-4 py-3 focus:outline-none transition-all duration-300 disabled:opacity-50 shadow-lg"
                        />
                      </div>
                      <div className="flex justify-end">
                        <motion.div
                          whileHover={reducedMotion ? {} : { scale: 1.02 }}
                          whileTap={reducedMotion ? {} : { scale: 0.98 }}
                          className="inline-block"
                        >
                          <Button
                            variant="gradient"
                            size="lg"
                            onClick={handleInitialInput}
                            disabled={!initialInput.trim() || loading}
                            className="flex items-center gap-2 bg-gradient-to-r from-purple-600 via-violet-600 to-indigo-600 hover:from-purple-700 hover:via-violet-700 hover:to-indigo-700 shadow-lg shadow-purple-500/30 hover:shadow-xl hover:shadow-purple-500/40 transition-all duration-300"
                          >
                            {loading ? (
                              <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                              <Send className="w-4 h-4" />
                            )}
                            开始对话
                          </Button>
                        </motion.div>
                      </div>
                    </div>

                    {/* Quick Topics */}
                    <div className="mt-8">
                      <p className="text-gray-400 text-sm block mb-3 text-center">
                        热门话题
                      </p>
                      <div className="flex flex-wrap justify-center gap-2">
                        {[
                          "算法入门",
                          "数据结构",
                          "面试准备",
                          "JavaScript",
                          "Python",
                          "系统设计",
                        ].map((topic, idx) => (
                          <motion.div
                            key={topic}
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.1 + idx * 0.05 }}
                            whileHover={reducedMotion ? {} : { scale: 1.05, y: -2 }}
                            whileTap={reducedMotion ? {} : { scale: 0.95 }}
                            className="inline-block"
                          >
                            <Button
                              variant="outline"
                              onClick={() =>
                                setInitialInput(`我想学习${topic}...`)
                              }
                              className="rounded-full bg-white/50 dark:bg-gray-800/50 backdrop-blur-sm border-purple-200 dark:border-purple-700 hover:bg-purple-50 hover:border-purple-300 dark:hover:bg-purple-950/50 dark:hover:border-purple-600 transition-all duration-300 shadow-sm hover:shadow-md"
                            >
                              {topic}
                            </Button>
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Sidebar */}
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ delay: 0.2 }}
          className="w-full lg:w-80 flex-shrink-0 overflow-y-auto hidden md:block"
        >
          {/* Session Context Card */}
          <Card className="mb-4 border-0 shadow-md">
            <CardHeader>
              <CardTitle>本次对话</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {activeToolCalls.length === 0 && generatedProblems.length === 0 ? (
                <p className="text-sm text-gray-400">对话进行中...</p>
              ) : (
                <>
                  {activeToolCalls.length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-gray-500 mb-1">工具调用</p>
                      <div className="space-y-1.5">
                        {activeToolCalls.map(tc => {
                          const toolDisplayName = getToolDisplayName(tc.toolName);
                          return (
                            <div key={tc.id} className="flex items-center gap-1.5 text-xs">
                              {tc.status === 'pending' ? <Wrench className="h-3 w-3 text-slate-400" /> :
                               tc.status === 'running' ? <Loader2 className="h-3 w-3 animate-spin text-amber-500" /> :
                               tc.status === 'completed' ? <CheckCircle2 className="h-3 w-3 text-green-500" /> :
                               <XCircle className="h-3 w-3 text-red-500" />}
                              <span className={`${
                                tc.status === 'failed' ? 'text-red-500' : 'text-gray-600 dark:text-gray-400'
                              }`}>
                                {toolDisplayName}
                              </span>
                              {tc.status === 'pending' && (
                                <span className="text-[10px] text-slate-400">准备中</span>
                              )}
                              {tc.status === 'running' && (
                                <span className="text-[10px] text-amber-500">执行中</span>
                              )}
                              {tc.status === 'completed' && tc.resultSummary && (
                                <span className="text-[10px] text-green-500 truncate max-w-[100px]">{tc.resultSummary}</span>
                              )}
                              {tc.status === 'failed' && tc.errorMessage && (
                                <span className="text-[10px] text-red-400 truncate max-w-[80px]">{tc.errorMessage}</span>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                  {generatedProblems.length > 0 && (
                    <div>
                      <p className="text-xs font-medium text-gray-500 mb-1">已生成题目</p>
                      <div className="space-y-1">
                        {generatedProblems.map((p, idx) => {
                          const content = (
                            <>
                              <Badge variant="outline" className={`text-[10px] px-1 py-0 ${
                                p.difficulty === 'easy' ? 'border-green-300 text-green-600' :
                                p.difficulty === 'medium' ? 'border-yellow-300 text-yellow-600' :
                                'border-red-300 text-red-600'
                              }`}>
                                {p.difficulty === 'easy' ? '简' : p.difficulty === 'medium' ? '中' : '难'}
                              </Badge>
                              {p.isRecommended && (
                                <span className="text-[10px] text-green-500">推荐</span>
                              )}
                              <span className={`truncate ${p.saveFailed ? 'text-orange-500' : 'text-gray-600 dark:text-gray-400'}`}>{p.title}</span>
                              {p.saveFailed && (
                                <span className="text-[10px] text-red-500">保存失败</span>
                              )}
                            </>
                          );
                          return !p.saveFailed && p.problemId ? (
                            <Link key={p.problemId || `sidebar_problem_${idx}`} href={`/problems/${p.problemId}`} className="flex items-center gap-1.5 text-xs hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
                              {content}
                            </Link>
                          ) : (
                            <div key={p.problemId || `sidebar_problem_${idx}`} className="flex items-center gap-1.5 text-xs">
                              {content}
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          {/* Control Card */}
          {messages.length > 0 && (
            <Card className="mb-4 border-0 shadow-md">
              <CardHeader>
                <CardTitle>对话控制</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <Button
                  variant="outline"
                  className="w-full flex items-center gap-2"
                  onClick={handleStopConversation}
                  disabled={!isConversationActive}
                >
                  <Square className="w-4 h-4" />
                  结束当前对话
                </Button>
                <Button
                  variant="outline"
                  className="w-full flex items-center gap-2"
                  onClick={handleRestartConversation}
                >
                  <RefreshCw className="w-4 h-4" />
                  重新开始
                </Button>
                {pollingTaskId && recoveryAvailable && (
                  <Button
                    variant="outline"
                    className="w-full flex items-center gap-2 border-amber-300 text-amber-700 hover:bg-amber-50 dark:border-amber-700 dark:text-amber-400 dark:hover:bg-amber-950/30"
                    onClick={() => {
                      const lastAiMessage = [...messages].reverse().find(m => m.role === 'assistant');
                      if (lastAiMessage) {
                        attemptRecovery(pollingTaskId, lastAiMessage.id, lastAcknowledgedSeqRef.current);
                      }
                    }}
                    disabled={isRecovering || loading}
                  >
                    {isRecovering ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        恢复中...
                      </>
                    ) : (
                      <>
                        <RefreshCw className="w-4 h-4" />
                        恢复消息
                      </>
                    )}
                  </Button>
                )}
                {recoveryError && (
                  <div className="text-xs text-red-500 p-2 bg-red-50 dark:bg-red-950/30 rounded">
                    {recoveryError}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Tips Card */}
          <Card className="border-0 shadow-md">
            <CardHeader>
              <CardTitle>使用建议</CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="list-disc list-inside space-y-2 text-sm text-gray-600 dark:text-gray-400">
                <li>清晰描述您的学习目标</li>
                <li>积极参与AI提出的每个问题</li>
                <li>思考后再回答，质量比速度重要</li>
                <li>如需知识库增强，请联系管理员配置</li>
              </ul>
            </CardContent>
          </Card>

          {/* Notification History Card - Desktop */}
          {notificationHistory.length > 0 && (
            <Card className="border-0 shadow-md">
              <CardHeader className="flex flex-row items-center justify-between py-3">
                <CardTitle className="text-sm flex items-center gap-2">
                  <Bell className="h-4 w-4" />
                  通知历史
                </CardTitle>
                <div className="flex items-center gap-2">
                  {unreadCount > 0 && (
                    <Badge className="bg-red-500 text-white text-[10px] px-1.5 py-0.5">
                      {unreadCount} 未读
                    </Badge>
                  )}
                  {wsConnected && (
                    <Badge variant="outline" className="text-[10px] px-1.5 py-0.5 border-green-500 text-green-600">
                      已连接
                    </Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="max-h-64 overflow-y-auto">
                <div className="space-y-2">
                  {notificationHistory.slice(0, 15).map((item) => (
                    <div 
                      key={item.id} 
                      className={`text-xs p-2 rounded transition-colors ${
                        item.read 
                          ? 'bg-gray-50 dark:bg-gray-800' 
                          : 'bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800'
                      }`}
                    >
                      <div className="flex items-center gap-1.5">
                        {item.eventType === 'TASK_COMPLETED' && (
                          <CheckCircle2 className="h-3 w-3 text-green-500 flex-shrink-0" />
                        )}
                        {item.eventType === 'TASK_FAILED' && (
                          <XCircle className="h-3 w-3 text-red-500 flex-shrink-0" />
                        )}
                        {item.eventType === 'PROBLEM_GENERATED' && (
                          <Lightbulb className="h-3 w-3 text-blue-500 flex-shrink-0" />
                        )}
                        {item.eventType === 'PROBLEM_RECOMMENDED' && (
                          <Sparkles className="h-3 w-3 text-purple-500 flex-shrink-0" />
                        )}
                        {!['TASK_COMPLETED', 'TASK_FAILED', 'PROBLEM_GENERATED', 'PROBLEM_RECOMMENDED'].includes(item.eventType) && (
                          <Bell className="h-3 w-3 text-gray-400 flex-shrink-0" />
                        )}
                        <span className="text-gray-700 dark:text-gray-300 line-clamp-2">
                          {item.displayMessage}
                        </span>
                      </div>
                      <div className="text-[10px] text-gray-400 mt-1">
                        {new Date(item.timestamp).toLocaleTimeString('zh-CN', {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </div>
                    </div>
                  ))}
                </div>
                {unreadCount > 0 && (
                  <Button 
                    variant="ghost" 
                    size="sm" 
                    className="w-full mt-2 text-xs"
                    onClick={markAllAsRead}
                  >
                    全部标记为已读
                  </Button>
                )}
              </CardContent>
            </Card>
          )}
        </motion.div>
      </div>
    </motion.div>
  );
}

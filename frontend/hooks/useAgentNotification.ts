import { useEffect, useCallback, useState, useRef } from "react";
import { 
  webSocketService, 
  AgentNotification, 
  NotificationHistoryItem 
} from "@/services/websocketService";
import { useToast } from "@/hooks/use-toast";
import { getToolDisplayName } from '@/lib/tool-mappings';

export interface UseAgentNotificationOptions {
  showToast?: boolean;
  onTaskCompleted?: (notification: AgentNotification) => void;
  onTaskFailed?: (notification: AgentNotification) => void;
  onProblemGenerated?: (notification: AgentNotification) => void;
  onProblemRecommended?: (notification: AgentNotification) => void;
  onToolExecuted?: (notification: AgentNotification) => void;
}

export function useAgentNotification(options: UseAgentNotificationOptions = {}) {
  const { showToast = true } = options;
  const { toast } = useToast();
  const [notificationHistory, setNotificationHistory] = useState<NotificationHistoryItem[]>(
    webSocketService.getNotificationHistory()
  );
  const [unreadCount, setUnreadCount] = useState<number>(0);

  const optionsRef = useRef(options);
  optionsRef.current = options;

  const handleNotification = useCallback(
    (notification: AgentNotification) => {
      const opts = optionsRef.current;
      if (showToast) {
        showNotificationToast(notification, toast);
      }

      switch (notification.eventType) {
        case "TASK_COMPLETED":
          opts.onTaskCompleted?.(notification);
          break;
        case "TASK_FAILED":
          opts.onTaskFailed?.(notification);
          break;
        case "PROBLEM_GENERATED":
          opts.onProblemGenerated?.(notification);
          break;
        case "PROBLEM_RECOMMENDED":
          opts.onProblemRecommended?.(notification);
          break;
        case "TOOL_EXECUTED":
          opts.onToolExecuted?.(notification);
          break;
      }

      setNotificationHistory(webSocketService.getNotificationHistory());
      setUnreadCount(webSocketService.getUnreadCount());
    },
    [showToast, toast]
  );

  useEffect(() => {
    const unsubscribe = webSocketService.onAgentNotification(handleNotification);
    
    setNotificationHistory(webSocketService.getNotificationHistory());
    setUnreadCount(webSocketService.getUnreadCount());

    return unsubscribe;
  }, [handleNotification]);

  const markAsRead = useCallback((notificationId: string) => {
    webSocketService.markNotificationRead(notificationId);
    setNotificationHistory(webSocketService.getNotificationHistory());
    setUnreadCount(webSocketService.getUnreadCount());
  }, []);

  const markAllAsRead = useCallback(() => {
    webSocketService.markAllNotificationsRead();
    setNotificationHistory(webSocketService.getNotificationHistory());
    setUnreadCount(0);
  }, []);

  const clearHistory = useCallback(() => {
    webSocketService.clearNotificationHistory();
    setNotificationHistory([]);
    setUnreadCount(0);
  }, []);

  return {
    notificationHistory,
    unreadCount,
    markAsRead,
    markAllAsRead,
    clearHistory,
  };
}

function showNotificationToast(
  notification: AgentNotification, 
  toast: ReturnType<typeof useToast>["toast"]
) {
  const getToastConfig = () => {
    switch (notification.eventType) {
      case "TASK_COMPLETED":
        return {
          title: "任务完成",
          description: notification.message || "任务执行成功",
          variant: "default" as const,
        };
      case "TASK_FAILED":
        return {
          title: "任务失败",
          description: notification.error || notification.message || "任务执行失败",
          variant: "destructive" as const,
        };
      case "PROBLEM_GENERATED":
        return {
          title: "题目生成成功",
          description: notification.problem?.title 
            ? `「${notification.problem.title}」已生成` 
            : "新题目已生成",
          variant: "default" as const,
        };
      case "PROBLEM_RECOMMENDED":
        return {
          title: "题目推荐",
          description: notification.problems?.length 
            ? `为您推荐了 ${notification.problems.length} 道题目`
            : "题目推荐完成",
          variant: "default" as const,
        };
      case "TOOL_EXECUTED":
        const toolName = getToolDisplayName(notification.toolName);
        return notification.status === "success"
          ? {
              title: `${toolName}完成`,
              description: "工具执行成功",
              variant: "default" as const,
            }
          : {
              title: `${toolName}失败`,
              description: "工具执行失败",
              variant: "destructive" as const,
            };
      default:
        return {
          title: "新通知",
          description: notification.message || "收到新通知",
          variant: "default" as const,
        };
    }
  };

  const config = getToastConfig();
  toast(config);
}


export function useMessageAck() {
  const addAck = useCallback((taskId: string, sequenceNumber: number, checksum: string) => {
    webSocketService.addPendingAck(taskId, {
      sequenceNumber,
      checksum,
      status: "received",
      receivedAt: Date.now(),
    });
  }, []);

  const sendImmediateAck = useCallback(
    async (taskId: string, segments: Array<{ sequenceNumber: number; checksum: string }>) => {
      await webSocketService.sendAckImmediately(
        taskId,
        segments.map((s) => ({
          ...s,
          status: "received",
          receivedAt: Date.now(),
        }))
      );
    },
    []
  );

  const getStats = useCallback(async (taskId: string) => {
    return webSocketService.getAckStats(taskId);
  }, []);

  return {
    addAck,
    sendImmediateAck,
    getStats,
  };
}

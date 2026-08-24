import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client/dist/sockjs";
import { appConfig } from "@/config/app.config";

const getWSBaseURL = (): string => {
  const baseURL = appConfig.api.baseURL;
  try {
    const url = new URL(baseURL);
    return url.origin;
  } catch {
    return "http://localhost:8080";
  }
};

export interface DataChangeEvent {
  eventType: string;
  entityType: string;
  entityId: string;
  data: unknown;
  operation: string;
  timestamp: number;
  source: string;
}

export type DataChangeHandler = (event: DataChangeEvent) => void;

export interface AgentNotification {
  eventType: string;
  taskId: string;
  userId: number;
  status: string;
  timestamp: number;
  problem?: ProblemNotificationData;
  problems?: ProblemNotificationData[];
  toolName?: string;
  message?: string;
  error?: string;
}

export interface ProblemNotificationData {
  problemId: number;
  title: string;
  difficulty: string;
  isRecommended: boolean;
  status: string;
  problemType?: string;
  acceptanceRate?: number;
}

export type AgentNotificationHandler = (notification: AgentNotification) => void;

export interface NotificationHistoryItem extends AgentNotification {
  id: string;
  read: boolean;
  displayMessage: string;
}

export interface MessageSegmentAck {
  sequenceNumber: number;
  checksum: string;
  status: string;
  receivedAt: number;
}

class WebSocketService {
  private stompClient: Client | null = null;
  private handlers: Map<string, Set<DataChangeHandler>> = new Map();
  private agentHandlers: Set<AgentNotificationHandler> = new Set();
  private userId: string | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private notificationHistory: NotificationHistoryItem[] = [];
  private maxHistorySize = 50;
  private pendingAcks: Map<string, MessageSegmentAck[]> = new Map();
  private ackTimer: NodeJS.Timeout | null = null;

  connect(userId: string, token?: string) {
    this.userId = userId;

    if (this.stompClient?.active) {
      return;
    }

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(`${getWSBaseURL()}/ws?token=${token || ""}&userId=${userId}`),
      reconnectDelay: this.reconnectDelay,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.reconnectAttempts = 0;
        this.subscribeToTopics();
      },
      onDisconnect: () => {
      },
      onStompError: (frame) => {
        console.error("[WS] STOMP error:", frame.headers["message"]);
      },
      onWebSocketClose: () => {
        this.reconnectAttempts++;
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
          console.warn("[WS] Max reconnect attempts reached, stopping reconnection");
          this.stompClient?.deactivate();
        }
      },
    });

    this.stompClient.activate();
    this.startAckTimer();
  }

  disconnect() {
    if (this.stompClient?.active) {
      this.stompClient.deactivate();
    }
    this.userId = null;
    this.reconnectAttempts = 0;
    this.stopAckTimer();
  }

  reset() {
    this.disconnect();
    this.handlers.clear();
    this.agentHandlers.clear();
    this.notificationHistory = [];
    this.pendingAcks.clear();
  }

  private subscribeToTopics() {
    if (!this.stompClient?.active) return;

    this.stompClient.subscribe("/topic/data/submission", (message: IMessage) => {
      try {
        const event: DataChangeEvent = JSON.parse(message.body);
        this.notifyHandlers("submission", event);
      } catch (e) {
        console.error("[WS] Failed to parse submission event:", e);
      }
    });

    this.stompClient.subscribe("/topic/data/all", (message: IMessage) => {
      try {
        const event: DataChangeEvent = JSON.parse(message.body);
        this.notifyHandlers(event.entityType, event);
      } catch (e) {
        console.error("[WS] Failed to parse all event:", e);
      }
    });

    if (this.userId) {
      this.stompClient.subscribe(`/user/${this.userId}/queue/data`, (message: IMessage) => {
        try {
          const event: DataChangeEvent = JSON.parse(message.body);
          this.notifyHandlers(event.entityType, event);
        } catch (e) {
          console.error("[WS] Failed to parse user event:", e);
        }
      });

      this.stompClient.subscribe(`/user/${this.userId}/queue/notifications`, (message: IMessage) => {
        try {
          const notification: AgentNotification = JSON.parse(message.body);
          this.handleAgentNotification(notification);
        } catch (e) {
          console.error("[WS] Failed to parse agent notification:", e);
        }
      });
    }
  }

  private handleAgentNotification(notification: AgentNotification) {
    const historyItem: NotificationHistoryItem = {
      ...notification,
      id: `notif_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      read: false,
      displayMessage: this.generateDisplayMessage(notification),
    };

    this.notificationHistory.unshift(historyItem);
    if (this.notificationHistory.length > this.maxHistorySize) {
      this.notificationHistory = this.notificationHistory.slice(0, this.maxHistorySize);
    }

    this.agentHandlers.forEach((handler) => {
      try {
        handler(notification);
      } catch (e) {
        console.error("[WS] Agent notification handler error:", e);
      }
    });
  }

  private generateDisplayMessage(notification: AgentNotification): string {
    switch (notification.eventType) {
      case "TASK_COMPLETED":
        return notification.message || "任务执行完成";
      case "TASK_FAILED":
        return notification.error || notification.message || "任务执行失败";
      case "PROBLEM_GENERATED":
        return notification.problem?.title 
          ? `题目「${notification.problem.title}」生成成功` 
          : "题目生成完成";
      case "PROBLEM_RECOMMENDED":
        return notification.problems?.length 
          ? `为您推荐了 ${notification.problems.length} 道题目`
          : "题目推荐完成";
      case "TOOL_EXECUTED":
        return notification.status === "success" 
          ? `${notification.toolName || "工具"}执行成功`
          : `${notification.toolName || "工具"}执行失败`;
      default:
        return notification.message || "收到新通知";
    }
  }

  onEntityType(entityType: string, handler: DataChangeHandler): () => void {
    if (!this.handlers.has(entityType)) {
      this.handlers.set(entityType, new Set());
    }
    this.handlers.get(entityType)!.add(handler);

    return () => {
      this.handlers.get(entityType)?.delete(handler);
    };
  }

  onAgentNotification(handler: AgentNotificationHandler): () => void {
    this.agentHandlers.add(handler);
    return () => {
      this.agentHandlers.delete(handler);
    };
  }

  private notifyHandlers(entityType: string, event: DataChangeEvent) {
    const entityHandlers = this.handlers.get(entityType);
    if (entityHandlers) {
      entityHandlers.forEach((handler) => {
        try {
          handler(event);
        } catch (e) {
          console.error("[WS] Handler error:", e);
        }
      });
    }

    const allHandlers = this.handlers.get("*");
    if (allHandlers) {
      allHandlers.forEach((handler) => {
        try {
          handler(event);
        } catch (e) {
          console.error("[WS] Handler error:", e);
        }
      });
    }
  }

  get isConnected(): boolean {
    return this.stompClient?.active ?? false;
  }

  getNotificationHistory(): NotificationHistoryItem[] {
    return [...this.notificationHistory];
  }

  getUnreadCount(): number {
    return this.notificationHistory.filter((item) => !item.read).length;
  }

  markNotificationRead(notificationId: string) {
    const item = this.notificationHistory.find((n) => n.id === notificationId);
    if (item) {
      item.read = true;
    }
  }

  markAllNotificationsRead() {
    this.notificationHistory.forEach((item) => {
      item.read = true;
    });
  }

  clearNotificationHistory() {
    this.notificationHistory = [];
  }

  addPendingAck(taskId: string, segment: MessageSegmentAck) {
    if (!this.pendingAcks.has(taskId)) {
      this.pendingAcks.set(taskId, []);
    }
    this.pendingAcks.get(taskId)!.push(segment);
  }

  private startAckTimer() {
    if (this.ackTimer) return;
    
    this.ackTimer = setInterval(() => {
      this.flushPendingAcks();
    }, 2000);
  }

  private stopAckTimer() {
    if (this.ackTimer) {
      clearInterval(this.ackTimer);
      this.ackTimer = null;
    }
  }

  private async flushPendingAcks() {
    if (this.pendingAcks.size === 0) return;

    const acksToSend = new Map(this.pendingAcks);
    this.pendingAcks.clear();

    for (const [taskId, segments] of acksToSend) {
      if (segments.length === 0) continue;

      try {
        const token = localStorage.getItem(appConfig.storage.tokenKey);
        const response = await fetch(`${getWSBaseURL()}/api/v1/ai/agent/ack`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({
            taskId,
            segments,
          }),
        });

        if (!response.ok) {
          console.error("[Ack] Failed to send acknowledgment:", response.status);
        }
      } catch (error) {
        console.error("[Ack] Error sending acknowledgment:", error);
      }
    }
  }

  async sendAckImmediately(taskId: string, segments: MessageSegmentAck[]) {
    if (segments.length === 0) return;

    try {
      const token = localStorage.getItem(appConfig.storage.tokenKey);
      const response = await fetch(`${getWSBaseURL()}/api/v1/ai/agent/ack`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          taskId,
          segments,
        }),
      });

      if (!response.ok) {
        console.error("[Ack] Failed to send immediate acknowledgment:", response.status);
      }
    } catch (error) {
      console.error("[Ack] Error sending immediate acknowledgment:", error);
    }
  }

  async getAckStats(taskId: string): Promise<{
    totalSegments: number;
    acknowledged: number;
    sent: number;
    pending: number;
    failed: number;
    retrying: number;
  } | null> {
    try {
      const token = localStorage.getItem(appConfig.storage.tokenKey);
      const response = await fetch(`${getWSBaseURL()}/api/v1/ai/agent/ack/stats/${taskId}`, {
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });

      if (!response.ok) {
        return null;
      }

      return response.json();
    } catch (error) {
      console.error("[Ack] Error getting ack stats:", error);
      return null;
    }
  }
}

export const webSocketService = new WebSocketService();

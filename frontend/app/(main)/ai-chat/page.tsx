"use client";

import { useState, useRef, useEffect, useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { motion, AnimatePresence } from "framer-motion";
import {
  Send,
  Bot,
  User,
  Copy,
  Trash2,
  Sparkles,
  Loader2,
  ChevronDown,
  MessageSquare,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ChatMessage } from "@/types";
import { aiService, parseSSEStream } from "@/services/aiService";
import { useToast } from "@/hooks/use-toast";
import { MarkdownRenderer } from "@/components/chat/markdown-renderer";
import { useSmartScroll } from "@/hooks/useSmartScroll";
import { slideInLeft, slideInRight, useReducedMotion } from "@/lib/animations";
import { processStreamDelta } from "@/lib/textProcessor";

export default function AiChatPage() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const reducedMotion = useReducedMotion();
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      content: "你好！我是智学引擎AI助手，我可以帮助你学习编程、解答算法问题、解释代码等。有什么我可以帮你的吗？",
      timestamp: new Date().toISOString(),
    },
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 使用智能滚动hook
  const { containerRef, showScrollButton, resumeAutoScroll } = useSmartScroll({
    autoScrollEnabled: true,
    storageKey: "ai-chat-scroll",
  });

  // 使用useMemo缓存欢迎消息
  const welcomeMessage = useMemo(() => ({
    id: "welcome",
    role: "assistant" as const,
    content: "你好！我是智学引擎AI助手，我可以帮助你学习编程、解答算法问题、解释代码等。有什么我可以帮你的吗？",
    timestamp: new Date().toISOString(),
  }), []);

  // 获取消息动画变体 - 使用useCallback缓存
  const getMessageVariants = useCallback((role: string) =>
    role === "user" ? slideInRight : slideInLeft,
    []
  );

  // 使用useCallback缓存发送处理函数
  const handleSend = useCallback(async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      role: "user",
      content: input,
      timestamp: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsLoading(true);

    try {
      const aiMessageId = (Date.now() + 1).toString();
      setMessages((prev) => [
        ...prev,
        {
          id: aiMessageId,
          role: "assistant",
          content: "",
          timestamp: new Date().toISOString(),
        },
      ]);

      const reader = await aiService.streamChat(
        messages.concat(userMessage).map((m) => ({
          role: m.role,
          content: m.content,
        }))
      );

      let fullContent = "";

      await parseSSEStream(reader, {
        onText: (delta) => {
          const processedDelta = processStreamDelta(delta);
          fullContent += processedDelta;
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMessageId ? { ...msg, content: fullContent } : msg
            )
          );
        },
        onComplete: (content) => {
          if (content) {
            const processedContent = processStreamDelta(content);
            fullContent += processedContent;
            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === aiMessageId ? { ...msg, content: fullContent } : msg
              )
            );
          }
          setIsLoading(false);
        },
        onError: (error) => {
          console.error("AI响应错误:", error);
          setIsLoading(false);
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === aiMessageId
                ? { ...msg, content: "抱歉，AI服务暂时不可用，请稍后重试。" }
                : msg
            )
          );
          toast({
            title: "AI响应错误",
            description: "获取AI响应时出现问题，请稍后重试",
            variant: "destructive",
          });
        },
      });
    } catch (error) {
      console.error("AI请求失败:", error);
      setIsLoading(false);
      const errorMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: "assistant",
        content: "抱歉，暂时无法响应，请稍后重试。",
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, errorMessage]);
      toast({
        title: "请求失败",
        description: "无法连接到AI服务，请检查网络连接",
        variant: "destructive",
      });
    }
  }, [input, isLoading, messages, toast]);

  // 使用useCallback缓存键盘事件处理
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      handleSend();
    } else if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }, [handleSend]);

  // 使用useCallback缓存复制消息函数
  const copyMessage = useCallback((content: string) => {
    navigator.clipboard.writeText(content);
    toast({
      title: "已复制",
      description: "消息内容已复制到剪贴板",
    });
  }, [toast]);

  // 使用useCallback缓存清空对话函数
  const clearChat = useCallback(() => {
    setMessages([welcomeMessage]);
    toast({
      title: "对话已清空",
      description: "可以开始新的对话了",
    });
  }, [welcomeMessage, toast]);

  // 使用useCallback缓存时间格式化函数
  const formatTime = useCallback((timestamp: string) => {
    return new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  }, []);

  // 键盘导航：跳转到最新消息
  const handleJumpToLatest = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: reducedMotion ? "auto" : "smooth" });
    resumeAutoScroll();
    // 焦点管理：发送后聚焦到输入框
    textareaRef.current?.focus();
  }, [reducedMotion, resumeAutoScroll]);

  return (
    <motion.div
      initial={reducedMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={reducedMotion ? { duration: 0 } : undefined}
      className="h-[calc(100vh-120px)] flex flex-col"
      role="main"
      aria-label={t("ai.chat.title")}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
            {t("ai.chat.title")}
          </h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">
            与AI助手对话，获取编程学习的个性化指导
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={clearChat}
          aria-label={t("ai.chat.clearChat")}
          className="focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
        >
          <Trash2 className="w-4 h-4 mr-2" aria-hidden="true" />
          {t("ai.chat.clear")}
        </Button>
      </div>

      {/* Chat Container */}
      <Card className="flex-1 border-0 shadow-lg overflow-hidden flex flex-col relative">
        {/* Messages */}
        <CardContent
          ref={containerRef}
          className="flex-1 overflow-y-auto p-4 md:p-6 space-y-4 relative"
          role="log"
          aria-live="polite"
          aria-label={t("ai.chat.messages")}
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
                className={`flex gap-3 md:gap-4 ${
                  message.role === "user" ? "flex-row-reverse" : ""
                }`}
                role="listitem"
                aria-label={message.role === "user" ? t("ai.chat.userMessage") : t("ai.chat.assistantMessage")}
              >
                {/* Avatar */}
                <div
                  className={`w-9 h-9 md:w-10 md:h-10 rounded-full flex items-center justify-center flex-shrink-0 ring-2 ring-white dark:ring-gray-800 ${
                    message.role === "assistant"
                      ? "bg-gradient-to-br from-indigo-500 to-purple-500 shadow-lg shadow-indigo-500/30"
                      : "bg-gray-200 dark:bg-gray-700"
                  }`}
                  aria-hidden="true"
                >
                  {message.role === "assistant" ? (
                    <Sparkles className="w-4 h-4 md:w-5 md:h-5 text-white" aria-hidden="true" />
                  ) : (
                    <User className="w-4 h-4 md:w-5 md:h-5 text-gray-600 dark:text-gray-300" aria-hidden="true" />
                  )}
                </div>

                {/* Message Content */}
                <div
                  className={`max-w-[85%] md:max-w-[70%] ${
                    message.role === "user" ? "items-end" : "items-start"
                  }`}
                >
                  <div
                    className={`p-3 md:p-4 rounded-2xl shadow-md ${
                      message.role === "assistant"
                        ? "bg-white dark:bg-gray-800 text-gray-900 dark:text-white rounded-tl-none border border-gray-200 dark:border-gray-700"
                        : "bg-gradient-to-r from-indigo-600 via-purple-600 to-cyan-600 text-white rounded-tr-none shadow-lg shadow-indigo-500/20"
                    }`}
                  >
                    {/* Content with Markdown Support */}
                    {message.role === "assistant" ? (
                      <div className="prose dark:prose-invert max-w-none text-sm">
                        <MarkdownRenderer content={message.content || "思考中..."} isStreaming={isLoading && message.role === "assistant" && message.id !== "welcome"} />
                      </div>
                    ) : (
                      <p className="whitespace-pre-wrap leading-relaxed">
                        {message.content}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-2">
                    <span className="text-xs text-gray-400" aria-label={t("ai.chat.sentAt", { time: formatTime(message.timestamp) })}>
                      {formatTime(message.timestamp)}
                    </span>
                    {message.role === "assistant" && message.content && (
                      <button
                        onClick={() => copyMessage(message.content)}
                        className="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 flex items-center gap-1 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 rounded px-1 transition-colors"
                        aria-label={t("ai.chat.copyMessage")}
                      >
                        <Copy className="w-3 h-3" aria-hidden="true" />
                        {t("ai.chat.copy")}
                      </button>
                    )}
                  </div>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>

          {/* Loading Indicator */}
          {isLoading && (
            <motion.div
              initial={reducedMotion ? { opacity: 1 } : { opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={reducedMotion ? { duration: 0 } : undefined}
              className="flex gap-3 md:gap-4"
              role="status"
              aria-live="polite"
              aria-label={t("ai.chat.thinking")}
            >
              <div className="w-9 h-9 md:w-10 md:h-10 rounded-full bg-gradient-to-br from-indigo-500 to-purple-500 flex items-center justify-center ring-2 ring-white dark:ring-gray-800 shadow-lg shadow-indigo-500/30" aria-hidden="true">
                <Bot className="w-4 h-4 md:w-5 md:h-5 text-white" aria-hidden="true" />
              </div>
              <div className="bg-white dark:bg-gray-800 p-3 md:p-4 rounded-2xl rounded-tl-none flex items-center gap-2 border border-gray-200 dark:border-gray-700 shadow-md">
                <Loader2 className="w-4 h-4 animate-spin text-indigo-600" aria-hidden="true" />
                <span className="text-gray-500 dark:text-gray-400">{t("ai.chat.thinking")}</span>
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
              <motion.div
                whileHover={{ scale: 1.05 }}
                transition={{ duration: 0.2 }}
              >
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={handleJumpToLatest}
                  className="shadow-lg rounded-full px-4 py-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800 animate-pulse"
                  aria-label={t("ai.chat.scrollToBottom")}
                >
                  <ChevronDown className="w-4 h-4 mr-1" aria-hidden="true" />
                  {t("ai.chat.scrollToBottom")}
                </Button>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Input Area */}
        <div className="p-3 md:p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900">
          <div className="flex gap-2">
            <div className="flex-1 relative">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={t("ai.chat.placeholder")}
                rows={1}
                className="w-full px-3 md:px-4 py-2.5 md:py-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 transition-all duration-200 resize-none"
                style={{ minHeight: "44px", maxHeight: "120px" }}
                aria-label={t("ai.chat.inputLabel")}
                aria-describedby="input-hint"
              />
            </div>
            <motion.div
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              transition={{ duration: 0.2 }}
            >
              <Button
                onClick={handleSend}
                disabled={!input.trim() || isLoading}
                className="px-4 md:px-6 h-full bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
                aria-label={isLoading ? t("ai.chat.sending") : t("ai.chat.send")}
              >
                {isLoading ? (
                  <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
                ) : (
                  <Send className="w-4 h-4" aria-hidden="true" />
                )}
              </Button>
            </motion.div>
          </div>
          <p id="input-hint" className="text-xs text-gray-400 mt-2 text-center">
            {t("ai.chat.inputHint")}
          </p>
        </div>
      </Card>
    </motion.div>
  );
}

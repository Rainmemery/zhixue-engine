"use client";

import React from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Bot, User, Copy, Check } from "lucide-react";
import { useState, useMemo, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { MarkdownRenderer } from "./markdown-renderer";
import {
  fadeIn,
  slideInLeft,
  slideInRight,
  useReducedMotion,
  cardHover,
  fastTransition,
} from "@/lib/animations";

interface MessageBubbleProps {
  role: "user" | "assistant";
  content: string;
  timestamp?: string;
  isStreaming?: boolean;
}

export const MessageBubble = React.memo(function MessageBubble({
  role,
  content,
  timestamp,
  isStreaming,
}: MessageBubbleProps) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const isUser = role === "user";
  const reducedMotion = useReducedMotion();

  // 根据消息类型选择进入动画 - 使用useMemo缓存
  const variants = useMemo(() =>
    isUser ? slideInRight : slideInLeft,
    [isUser]
  );

  // 使用useCallback缓存复制处理函数
  const handleCopy = useCallback(async () => {
    await navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [content]);

  // 使用useMemo缓存格式化时间
  const formattedTime = useMemo(() => {
    if (!timestamp) return null;
    return new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  }, [timestamp]);

  return (
    <motion.div
      variants={reducedMotion ? fadeIn : variants}
      initial="hidden"
      animate="visible"
      className={cn(
        "flex gap-3 mb-4",
        isUser ? "flex-row-reverse" : "flex-row"
      )}
      role="listitem"
      aria-label={isUser ? t("chat.userMessage") : t("chat.assistantMessage")}
    >
      {/* Avatar */}
      <motion.div
        initial={reducedMotion ? { scale: 1, opacity: 1 } : { scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={reducedMotion ? { duration: 0 } : { ...fastTransition, delay: 0.1 }}
        className={cn(
          "w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0",
          isUser
            ? "bg-gradient-to-br from-indigo-500 to-purple-600"
            : "bg-gradient-to-br from-emerald-500 to-teal-600"
        )}
        aria-hidden="true"
      >
        {isUser ? (
          <User className="w-4 h-4 text-white" aria-hidden="true" />
        ) : (
          <Bot className="w-4 h-4 text-white" aria-hidden="true" />
        )}
      </motion.div>

      {/* Message Content */}
      <div
        className={cn(
          "flex flex-col max-w-[80%]",
          isUser ? "items-end" : "items-start"
        )}
      >
        <motion.div
          whileHover={reducedMotion ? undefined : { y: -2, transition: { duration: 0.2 } }}
          className={cn(
            "relative group px-4 py-3 rounded-2xl cursor-default",
            isUser
              ? "bg-gradient-to-r from-indigo-600 to-purple-600 text-white"
              : "bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100"
          )}
        >
          {/* Copy Button */}
          <AnimatePresence>
            <motion.button
              onClick={handleCopy}
              initial={reducedMotion ? { opacity: 1, scale: 1 } : { opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              whileHover={reducedMotion ? undefined : { scale: 1.1 }}
              whileTap={reducedMotion ? undefined : { scale: 0.9 }}
              transition={fastTransition}
              className={cn(
                "absolute top-2 opacity-0 group-hover:opacity-100 transition-opacity p-1.5 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:opacity-100",
                isUser ? "left-2" : "right-2",
                "bg-white/20 hover:bg-white/30 backdrop-blur-sm"
              )}
              aria-label={copied ? t("chat.copied") : t("chat.copy")}
              aria-live="polite"
            >
              <AnimatePresence mode="wait">
                {copied ? (
                  <motion.div
                    key="check"
                    initial={reducedMotion ? { scale: 1, rotate: 0 } : { scale: 0, rotate: -180 }}
                    animate={{ scale: 1, rotate: 0 }}
                    exit={reducedMotion ? { scale: 0, rotate: 0 } : { scale: 0, rotate: 180 }}
                    transition={fastTransition}
                  >
                    <Check className="w-3.5 h-3.5 text-green-400" aria-hidden="true" />
                  </motion.div>
                ) : (
                  <motion.div
                    key="copy"
                    initial={reducedMotion ? { scale: 1 } : { scale: 0 }}
                    animate={{ scale: 1 }}
                    exit={reducedMotion ? { scale: 0 } : { scale: 0 }}
                    transition={fastTransition}
                  >
                    <Copy className="w-3.5 h-3.5" aria-hidden="true" />
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.button>
          </AnimatePresence>

          {/* Content */}
          <div className="leading-relaxed text-sm">
            {isUser ? (
              <div className="whitespace-pre-wrap">{content}</div>
            ) : (
              <MarkdownRenderer
                content={content}
                className="prose dark:prose-invert max-w-none"
              />
            )}
            {isStreaming && (
              <motion.span
                className={cn(
                  "inline-block w-1.5 h-4 ml-1 rounded-sm",
                  isUser ? "bg-white/80" : "bg-current"
                )}
                animate={{ opacity: [1, 0.3, 1] }}
                transition={{
                  duration: 0.8,
                  repeat: Infinity,
                  ease: "easeInOut",
                }}
              />
            )}
          </div>
        </motion.div>

        {/* Timestamp */}
        <AnimatePresence>
          {formattedTime && (
            <motion.span
              initial={reducedMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: -5 }}
              animate={{ opacity: 1, y: 0 }}
              transition={reducedMotion ? { duration: 0 } : { delay: 0.2, duration: 0.2 }}
              className="text-xs text-gray-400 mt-1"
              aria-label={t("chat.sentAt", { time: formattedTime })}
            >
              {formattedTime}
            </motion.span>
          )}
        </AnimatePresence>
      </div>
    </motion.div>
  );
});

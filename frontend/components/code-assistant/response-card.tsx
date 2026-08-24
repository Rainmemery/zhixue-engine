"use client";

import React from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
  Trash2,
  Lightbulb,
  Code2,
  Sparkles
} from "lucide-react";
import { useState, useRef, useEffect, useMemo, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { MarkdownRenderer } from "@/components/chat/markdown-renderer";
import { useReducedMotion } from "@/lib/animations";

const HEIGHT_THRESHOLD = 400; // 高度阈值
const ANIMATION_DURATION = 0.3; // 动画时长 300ms

interface ResponseCardProps {
  id: string;
  type: 'explain' | 'review' | 'refactor';
  content: string;
  isStreaming?: boolean;
  timestamp: string;
  isExpanded?: boolean; // 控制展开状态
  showActions?: boolean; // 控制是否显示操作按钮
  onDelete?: (id: string) => void;
}

const typeConfig = {
  explain: {
    icon: Lightbulb,
    label: '代码解释',
    gradient: 'from-amber-500 to-orange-600',
    bgColor: 'bg-amber-50 dark:bg-amber-900/20',
    borderColor: 'border-amber-200 dark:border-amber-800',
  },
  review: {
    icon: Code2,
    label: '代码审查',
    gradient: 'from-blue-500 to-cyan-600',
    bgColor: 'bg-blue-50 dark:bg-blue-900/20',
    borderColor: 'border-blue-200 dark:border-blue-800',
  },
  refactor: {
    icon: Sparkles,
    label: '代码重构',
    gradient: 'from-emerald-500 to-teal-600',
    bgColor: 'bg-emerald-50 dark:bg-emerald-900/20',
    borderColor: 'border-emerald-200 dark:border-emerald-800',
  },
};

export const ResponseCard = React.memo(function ResponseCard({
  id,
  type,
  content,
  isStreaming = false,
  timestamp,
  isExpanded: propIsExpanded,
  showActions = true,
  onDelete,
}: ResponseCardProps) {
  const { t } = useTranslation();
  const reducedMotion = useReducedMotion();
  const [copied, setCopied] = useState(false);
  const [isExpandedInternal, setIsExpandedInternal] = useState(false);
  const [needsExpand, setNeedsExpand] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);
  const [contentHeight, setContentHeight] = useState(0);

  // 优先使用外部传入的展开状态，否则使用内部状态
  const isExpanded = propIsExpanded !== undefined ? propIsExpanded : isExpandedInternal;
  const setIsExpanded = propIsExpanded !== undefined ? () => {} : setIsExpandedInternal;

  // 使用useMemo缓存配置对象
  const config = useMemo(() => typeConfig[type], [type]);
  const Icon = config.icon;

  // 检测内容高度
  useEffect(() => {
    if (contentRef.current) {
      const height = contentRef.current.scrollHeight;
      setContentHeight(height);
      setNeedsExpand(height > HEIGHT_THRESHOLD);
    }
  }, [content]);

  // 使用useCallback缓存复制功能
  const handleCopy = useCallback(async () => {
    await navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, [content]);

  // 使用useCallback缓存删除功能
  const handleDelete = useCallback(() => {
    onDelete?.(id);
  }, [onDelete, id]);

  // 使用useCallback缓存展开/收起切换
  const toggleExpand = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);

  // 使用useMemo缓存格式化时间戳
  const formattedTime = useMemo(() =>
    new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    }),
    [timestamp]
  );

  return (
    <motion.div
      initial={reducedMotion ? { opacity: 1, y: 0, scale: 1 } : { opacity: 0, y: 20, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -20, scale: 0.95 }}
      transition={reducedMotion ? { duration: 0 } : { duration: 0.3, ease: "easeOut" }}
      className={cn(
        "relative rounded-xl border overflow-hidden",
        "bg-white dark:bg-gray-900",
        config.borderColor,
        "shadow-sm hover:shadow-md transition-shadow duration-300"
      )}
      role="article"
      aria-label={t("responseCard.typeLabel", { type: config.label })}
    >
      {/* 头部区域 */}
      <div className={cn(
        "flex items-center justify-between px-4 py-3",
        config.bgColor,
        "border-b",
        config.borderColor
      )}>
        <div className="flex items-center gap-2">
          {/* 类型图标 */}
          <div className={cn(
            "w-8 h-8 rounded-lg flex items-center justify-center",
            "bg-gradient-to-br",
            config.gradient,
            "shadow-sm"
          )}>
            <Icon className="w-4 h-4 text-white" aria-hidden="true" />
          </div>

          {/* 类型标签 */}
          <span className="font-medium text-gray-900 dark:text-gray-100">
            {config.label}
          </span>

          {/* 流式状态指示器 */}
          {isStreaming && (
            <motion.span
              className="flex items-center gap-1 text-xs text-gray-500"
              initial={reducedMotion ? { opacity: 1 } : { opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={reducedMotion ? { duration: 0 } : undefined}
              aria-live="polite"
              aria-label={t("responseCard.generating")}
            >
              <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" aria-hidden="true" />
              {t("responseCard.generating")}
            </motion.span>
          )}
        </div>

        {/* 右侧操作按钮 */}
        {showActions && (
          <div className="flex items-center gap-1">
            {/* 复制按钮 */}
            <motion.button
              whileHover={reducedMotion ? undefined : { scale: 1.05 }}
              whileTap={reducedMotion ? undefined : { scale: 0.95 }}
              onClick={handleCopy}
              className={cn(
                "p-2 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 dark:focus:ring-offset-gray-900",
                "text-gray-600 dark:text-gray-400",
                "hover:bg-gray-200 dark:hover:bg-gray-700"
              )}
              aria-label={copied ? t("responseCard.copied") : t("responseCard.copy")}
              aria-live="polite"
            >
              <AnimatePresence mode="wait">
                {copied ? (
                  <motion.div
                    key="check"
                    initial={reducedMotion ? { scale: 1 } : { scale: 0 }}
                    animate={{ scale: 1 }}
                    exit={reducedMotion ? { scale: 0 } : { scale: 0 }}
                  >
                    <Check className="w-4 h-4 text-green-600 dark:text-green-400" aria-hidden="true" />
                  </motion.div>
                ) : (
                  <motion.div
                    key="copy"
                    initial={reducedMotion ? { scale: 1 } : { scale: 0 }}
                    animate={{ scale: 1 }}
                    exit={reducedMotion ? { scale: 0 } : { scale: 0 }}
                  >
                    <Copy className="w-4 h-4" aria-hidden="true" />
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.button>

            {/* 删除按钮 */}
            {onDelete && (
              <motion.button
                whileHover={reducedMotion ? undefined : { scale: 1.05 }}
                whileTap={reducedMotion ? undefined : { scale: 0.95 }}
                onClick={handleDelete}
                className={cn(
                  "p-2 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-1 dark:focus:ring-offset-gray-900",
                  "text-gray-600 dark:text-gray-400",
                  "hover:bg-red-100 dark:hover:bg-red-900/30",
                  "hover:text-red-600 dark:hover:text-red-400"
                )}
                aria-label={t("responseCard.delete")}
              >
                <Trash2 className="w-4 h-4" aria-hidden="true" />
              </motion.button>
            )}
          </div>
        )}
      </div>

      {/* 内容区域 */}
      <div className="relative">
        <motion.div
          id={`content-${id}`}
          animate={{
            height: isExpanded ? contentHeight : needsExpand ? HEIGHT_THRESHOLD : "auto",
          }}
          transition={reducedMotion ? { duration: 0 } : {
            duration: ANIMATION_DURATION,
            ease: [0.4, 0, 0.2, 1],
          }}
          className={cn(
            "overflow-hidden",
            needsExpand && !isExpanded && "mask-gradient"
          )}
        >
          <div
            ref={contentRef}
            className="px-4 py-4"
          >
            <MarkdownRenderer
              content={content}
              className="prose dark:prose-invert max-w-none prose-sm"
              isStreaming={isStreaming}
            />
            
            {/* 流式输出光标 */}
            {isStreaming && (
              <motion.span
                className="inline-block w-2 h-5 ml-1 bg-blue-500 rounded-sm"
                animate={{
                  opacity: [1, 0, 1],
                }}
                transition={{
                  duration: 0.8,
                  repeat: Infinity,
                  ease: "easeInOut",
                }}
              />
            )}
          </div>
        </motion.div>

        {/* 渐变遮罩（收起状态时） */}
        {needsExpand && !isExpanded && (
          <div className="absolute bottom-0 left-0 right-0 h-20 bg-gradient-to-t from-white dark:from-gray-900 to-transparent pointer-events-none" />
        )}
      </div>

      {/* 展开/收起按钮 - 仅在没有外部控制展开状态时显示 */}
      <AnimatePresence>
        {needsExpand && propIsExpanded === undefined && (
          <motion.div
            initial={reducedMotion ? { opacity: 1, height: "auto" } : { opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={reducedMotion ? { opacity: 0, height: 0 } : { opacity: 0, height: 0 }}
            transition={reducedMotion ? { duration: 0 } : undefined}
            className={cn(
              "border-t",
              config.borderColor,
              config.bgColor
            )}
          >
            <motion.button
              whileHover={reducedMotion ? undefined : { backgroundColor: "rgba(0,0,0,0.03)" }}
              whileTap={reducedMotion ? undefined : { scale: 0.98 }}
              onClick={toggleExpand}
              className={cn(
                "w-full flex items-center justify-center gap-2 py-2",
                "text-sm text-gray-600 dark:text-gray-400",
                "transition-colors focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
              )}
              aria-expanded={isExpanded}
              aria-controls={`content-${id}`}
              aria-label={isExpanded ? t("responseCard.collapse") : t("responseCard.expand")}
            >
              <AnimatePresence mode="wait">
                {isExpanded ? (
                  <motion.span
                    key="collapse"
                    initial={reducedMotion ? { y: 0, opacity: 1 } : { y: 10, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    exit={reducedMotion ? { y: 0, opacity: 0 } : { y: -10, opacity: 0 }}
                    transition={reducedMotion ? { duration: 0 } : undefined}
                    className="flex items-center gap-2"
                  >
                    <ChevronUp className="w-4 h-4" aria-hidden="true" />
                    {t("responseCard.collapse")}
                  </motion.span>
                ) : (
                  <motion.span
                    key="expand"
                    initial={reducedMotion ? { y: 0, opacity: 1 } : { y: -10, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    exit={reducedMotion ? { y: 0, opacity: 0 } : { y: 10, opacity: 0 }}
                    transition={reducedMotion ? { duration: 0 } : undefined}
                    className="flex items-center gap-2"
                  >
                    <ChevronDown className="w-4 h-4" aria-hidden="true" />
                    {t("responseCard.expand")}
                  </motion.span>
                )}
              </AnimatePresence>
            </motion.button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 底部时间戳 */}
      <div className="px-4 py-2 border-t border-gray-100 dark:border-gray-800 flex justify-end">
        <span className="text-xs text-gray-400" aria-label={t("responseCard.generatedAt", { time: formattedTime })}>
          {formattedTime}
        </span>
      </div>
    </motion.div>
  );
});

// 流式光标组件（单独导出以便复用）
export function StreamingCursor({ className }: { className?: string }) {
  const reducedMotion = useReducedMotion();

  if (reducedMotion) {
    return (
      <span
        className={cn(
          "inline-block w-2 h-5 ml-1 rounded-sm",
          className || "bg-blue-500"
        )}
        aria-hidden="true"
      />
    );
  }

  return (
    <motion.span
      className={cn(
        "inline-block w-2 h-5 ml-1 rounded-sm",
        className || "bg-blue-500"
      )}
      animate={{
        opacity: [1, 0.3, 1],
      }}
      transition={{
        duration: 0.8,
        repeat: Infinity,
        ease: "easeInOut",
      }}
      aria-hidden="true"
    />
  );
}

export default ResponseCard;

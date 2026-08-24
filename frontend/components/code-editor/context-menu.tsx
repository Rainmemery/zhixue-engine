"use client";

import { useEffect, useRef, useCallback, useState, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Copy,
  Scissors,
  Wand2,
  MessageSquare,
  Sparkles,
  FileCode,
  Check,
} from "lucide-react";
import { cn } from "@/lib/utils";

export interface CodeSelection {
  startLine: number;
  endLine: number;
  startColumn: number;
  endColumn: number;
  selectedText: string;
  language: string;
  contextBefore?: string;
  contextAfter?: string;
  fullCode?: string;
  totalLines?: number;
}

interface ContextMenuProps {
  visible: boolean;
  position: { x: number; y: number };
  codeSelection: CodeSelection | null;
  onAction: (action: ContextMenuAction, selection: CodeSelection | null) => void;
  onClose: () => void;
}

export type ContextMenuAction =
  | "copy"
  | "cut"
  | "format"
  | "explain"
  | "review"
  | "refactor";

export type AIContextMenuAction = "explain" | "review" | "refactor";

interface MenuItem {
  action: ContextMenuAction;
  label: string;
  icon: React.ElementType;
  shortcut?: string;
  divider?: boolean;
  requiresSelection?: boolean;
  isBasicAction?: boolean;
}

const menuItems: MenuItem[] = [
  { action: "copy", label: "复制", icon: Copy, shortcut: "Ctrl+C", isBasicAction: true },
  { action: "cut", label: "剪切", icon: Scissors, shortcut: "Ctrl+X", isBasicAction: true },
  { action: "format", label: "格式化代码", icon: FileCode, shortcut: "Shift+Alt+F", divider: true, isBasicAction: true },
  { action: "explain", label: "AI解释", icon: MessageSquare, requiresSelection: true },
  { action: "review", label: "AI评审", icon: Sparkles, requiresSelection: true },
  { action: "refactor", label: "智能重构", icon: Wand2, divider: true, requiresSelection: true },
];

// 计算菜单位置，确保在视口内
function calculateAdjustedPosition(position: { x: number; y: number }): { x: number; y: number } {
  if (typeof window === "undefined") return position;
  
  const menuWidth = 220;
  const menuHeight = 350;
  
  let newX = position.x;
  let newY = position.y;
  
  // 防止超出右边界
  if (position.x + menuWidth > window.innerWidth) {
    newX = window.innerWidth - menuWidth - 10;
  }
  
  // 防止超出下边界
  if (position.y + menuHeight > window.innerHeight) {
    newY = window.innerHeight - menuHeight - 10;
  }
  
  // 防止超出左边界
  if (newX < 0) {
    newX = 10;
  }
  
  // 防止超出上边界
  if (newY < 0) {
    newY = 10;
  }
  
  return { x: newX, y: newY };
}

export function ContextMenu({
  visible,
  position,
  codeSelection,
  onAction,
  onClose,
}: ContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  // 使用 useMemo 计算调整后的位置，避免级联渲染
  const adjustedPosition = useMemo(() => {
    if (!visible) return { x: 0, y: 0 };
    return calculateAdjustedPosition(position);
  }, [visible, position]);

  // Close menu when clicking outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    if (visible) {
      document.addEventListener("mousedown", handleClickOutside);
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [visible, onClose]);

  // Close on escape key
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    if (visible) {
      document.addEventListener("keydown", handleEscape);
    }

    return () => {
      document.removeEventListener("keydown", handleEscape);
    };
  }, [visible, onClose]);

  // 显示操作反馈
  const showFeedback = useCallback((message: string) => {
    setActionFeedback(message);
    setTimeout(() => setActionFeedback(null), 1000);
  }, []);

  const handleAction = useCallback((action: ContextMenuAction) => {
    const item = menuItems.find(m => m.action === action);
    
    // 处理基本操作（复制、剪切、格式化）
    if (item?.isBasicAction) {
      onAction(action, codeSelection);
      
      // 显示反馈
      const feedbackMessages: Record<string, string> = {
        copy: "已复制",
        cut: "已剪切",
        format: "已格式化",
      };
      showFeedback(feedbackMessages[action]);
      
      onClose();
      return;
    }
    
    // 处理 AI 相关操作
    const aiActions: AIContextMenuAction[] = ["explain", "review", "refactor"];
    if (codeSelection && aiActions.includes(action as AIContextMenuAction)) {
      onAction(action, codeSelection);
      onClose();
    }
  }, [codeSelection, onAction, onClose, showFeedback]);

  // 检查菜单项是否应该禁用
  const isItemDisabled = useCallback((item: MenuItem): boolean => {
    // 复制和剪切需要选中文本
    if ((item.action === "copy" || item.action === "cut") && item.requiresSelection !== false) {
      return !codeSelection?.selectedText;
    }
    
    // AI 功能需要选中文本
    if (item.requiresSelection) {
      return !codeSelection?.selectedText;
    }
    
    return false;
  }, [codeSelection]);

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          ref={menuRef}
          initial={{ opacity: 0, scale: 0.95, y: -5 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: -5 }}
          transition={{ duration: 0.15, ease: [0.4, 0, 0.2, 1] }}
          style={{
            position: "fixed",
            left: adjustedPosition.x,
            top: adjustedPosition.y,
            zIndex: 9999,
          }}
          className={cn(
            "w-56 py-1.5 rounded-lg shadow-2xl",
            "bg-white/95 dark:bg-gray-800/95",
            "border border-gray-200/80 dark:border-gray-700/80",
            "backdrop-blur-sm"
          )}
        >
          {/* 操作反馈提示 */}
          <AnimatePresence>
            {actionFeedback && (
              <motion.div
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="absolute -top-10 left-1/2 -translate-x-1/2 px-3 py-1.5 bg-green-500 text-white text-xs rounded-full flex items-center gap-1 shadow-lg"
              >
                <Check className="w-3 h-3" />
                {actionFeedback}
              </motion.div>
            )}
          </AnimatePresence>

          {menuItems.map((item, index) => {
            const disabled = isItemDisabled(item);
            const Icon = item.icon;
            
            return (
              <div key={item.action}>
                {item.divider && index > 0 && (
                  <div className="my-1.5 mx-2 border-t border-gray-200 dark:border-gray-700" />
                )}
                <button
                  onClick={() => !disabled && handleAction(item.action)}
                  disabled={disabled}
                  className={cn(
                    "w-full px-3 py-2 mx-1 flex items-center gap-2.5 text-sm rounded-md",
                    "transition-all duration-150 ease-out",
                    disabled 
                      ? "opacity-40 cursor-not-allowed text-gray-400 dark:text-gray-500" 
                      : "hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-200 cursor-pointer"
                  )}
                  style={{ width: "calc(100% - 8px)" }}
                >
                  <Icon className={cn(
                    "w-4 h-4 flex-shrink-0",
                    !disabled && item.action === "format" && "text-blue-500 dark:text-blue-400",
                    !disabled && (item.action === "copy" || item.action === "cut") && "text-gray-500 dark:text-gray-400"
                  )} />
                  <span className="flex-1 text-left font-medium">{item.label}</span>
                  {item.shortcut && (
                    <kbd className="text-[10px] px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 rounded border border-gray-200 dark:border-gray-600 font-mono">
                      {item.shortcut}
                    </kbd>
                  )}
                </button>
              </div>
            );
          })}

          {/* 选中代码预览 */}
          {codeSelection?.selectedText && (
            <>
              <div className="my-1.5 mx-2 border-t border-gray-200 dark:border-gray-700" />
              <div className="px-3 py-2">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-[11px] uppercase tracking-wider text-gray-400 dark:text-gray-500 font-semibold">
                    已选中
                  </span>
                  <span className="text-[10px] text-gray-400 dark:text-gray-500">
                    {codeSelection.selectedText.length} 字符
                  </span>
                </div>
                <div className="bg-gray-50 dark:bg-gray-900/50 rounded-md p-2 border border-gray-100 dark:border-gray-700/50">
                  <code className="text-xs text-gray-700 dark:text-gray-300 font-mono line-clamp-2 break-all">
                    {codeSelection.selectedText}
                  </code>
                </div>
                <p className="text-[10px] text-gray-400 dark:text-gray-500 mt-1.5">
                  第 {codeSelection.startLine}-{codeSelection.endLine} 行, 
                  列 {codeSelection.startColumn}-{codeSelection.endColumn}
                </p>
              </div>
            </>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

export default ContextMenu;

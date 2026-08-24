"use client";

import { useState, useCallback, useMemo, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Code2,
  Sparkles,
  Trash2,
  MessageSquare,
  X,
  Clock,
  Lightbulb,
  GripVertical,
} from "lucide-react";
import { CodeEditor } from "@/components/code-editor/code-editor";
import { CodeSelection, AIContextMenuAction } from "@/components/code-editor/context-menu";
import { aiService, parseSSEStream } from "@/services/aiService";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { ResponseCard } from "@/components/code-assistant/response-card";
import { useReducedMotion } from "@/lib/animations";
import { processStreamDelta, stripMarkdownCodeBlock, cleanJsonString, isJsonObject } from "@/lib/textProcessor";

function isTrivialLine(line: string): boolean {
  if (!line) return true;
  const trimmed = line.trim();
  if (!trimmed) return true;
  return trimmed === "{" || trimmed === "}" || trimmed === "};" || trimmed.length <= 1;
}

function trimContextOverflow(
  refactoredCode: string,
  contextBefore?: string,
  contextAfter?: string
): string {
  if (!refactoredCode) return refactoredCode;
  if (!contextBefore && !contextAfter) return refactoredCode;
  
  const codeLines = refactoredCode.split("\n");
  let startIdx = 0;
  let endIdx = codeLines.length;
  
  if (contextBefore) {
    const beforeLines = contextBefore.trim().split("\n").map(l => l.trim());
    let consecutiveMatches = 0;
    for (let i = 0; i < Math.min(beforeLines.length, codeLines.length); i++) {
      const codeLine = codeLines[i].trim();
      if (isTrivialLine(codeLine)) break;
      if (beforeLines.includes(codeLine)) {
        consecutiveMatches++;
      } else {
        break;
      }
    }
    if (consecutiveMatches >= 2) {
      startIdx = consecutiveMatches;
    }
  }
  
  if (contextAfter) {
    const afterLines = contextAfter.trim().split("\n").map(l => l.trim());
    let consecutiveMatches = 0;
    for (let i = 0; i < Math.min(afterLines.length, codeLines.length - startIdx); i++) {
      const codeLine = codeLines[codeLines.length - 1 - i].trim();
      if (isTrivialLine(codeLine)) break;
      if (afterLines.includes(codeLine)) {
        consecutiveMatches++;
      } else {
        break;
      }
    }
    if (consecutiveMatches >= 2) {
      endIdx = codeLines.length - consecutiveMatches;
    }
  }
  
  if (startIdx > 0 || endIdx < codeLines.length) {
    if (startIdx >= endIdx) {
      console.warn("[Refactor] trimContextOverflow: 截取后代码为空，返回原始代码");
      return refactoredCode;
    }
    const trimmed = codeLines.slice(startIdx, endIdx).join("\n").trim();
    console.log(`[Refactor] trimContextOverflow: 从${codeLines.length}行截取为${endIdx - startIdx}行`);
    return trimmed;
  }
  
  return refactoredCode;
}

// AI功能类型
enum AIActionType {
  EXPLAIN = "explain",
  REVIEW = "review",
  REFACTOR = "refactor",
}

interface AIResponse {
  id: string;
  type: AIActionType;
  content: string;
  isStreaming: boolean;
  timestamp: string;
}

// 类型图标映射
const typeIcons: Record<AIActionType, React.ElementType> = {
  [AIActionType.EXPLAIN]: Lightbulb,
  [AIActionType.REVIEW]: Code2,
  [AIActionType.REFACTOR]: Sparkles,
};

// 类型背景色映射
const typeBgColors: Record<AIActionType, string> = {
  [AIActionType.EXPLAIN]: "bg-amber-50 dark:bg-amber-950/30 border-amber-200 dark:border-amber-800",
  [AIActionType.REVIEW]: "bg-blue-50 dark:bg-blue-950/30 border-blue-200 dark:border-blue-800",
  [AIActionType.REFACTOR]: "bg-emerald-50 dark:bg-emerald-950/30 border-emerald-200 dark:border-emerald-800",
};

export default function CodeAssistantPage() {
  const { toast } = useToast();
  const reducedMotion = useReducedMotion();

  // 状态管理
  const [code, setCode] = useState("");
  const [language, setLanguage] = useState("javascript");
  const [responses, setResponses] = useState<AIResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [selectedResponseId, setSelectedResponseId] = useState<string | null>(null);

  // 拖拽调整分栏宽度
  const [leftWidth, setLeftWidth] = useState(55);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // 当前选中代码
  const [currentSelection, setCurrentSelection] = useState<CodeSelection | null>(null);

  // 使用 useMemo 缓存选中的响应数据
  const selectedResponse = useMemo(() =>
    responses.find(r => r.id === selectedResponseId) || null,
    [responses, selectedResponseId]
  );

  // 获取最新响应（最后一个）
  const latestResponse = useMemo(() =>
    responses.length > 0 ? responses[responses.length - 1] : null,
    [responses]
  );

  // 获取历史响应（除最新外的所有）
  const historyResponses = useMemo(() =>
    responses.length > 1 ? responses.slice(0, -1) : [],
    [responses]
  );

  // 处理拖拽
  const handleMouseDown = useCallback(() => {
    setIsDragging(true);
  }, []);

  useEffect(() => {
    if (!isDragging) return;

    const handleMouseMove = (e: MouseEvent) => {
      if (!containerRef.current) return;
      const rect = containerRef.current.getBoundingClientRect();
      const newWidth = ((e.clientX - rect.left) / rect.width) * 100;
      setLeftWidth(Math.min(Math.max(newWidth, 30), 70));
    };

    const handleMouseUp = () => {
      setIsDragging(false);
    };

    document.addEventListener("mousemove", handleMouseMove);
    document.addEventListener("mouseup", handleMouseUp);
    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
    };
  }, [isDragging]);

  // 处理语言变化
  const handleLanguageChange = useCallback((lang: string) => {
    setLanguage(lang);
  }, []);

  // 处理AI操作
  const handleAIAction = useCallback(
    async (action: AIActionType, selection: CodeSelection) => {
      const codeToSend = (selection.selectedText || code).trim();
      if (!codeToSend) {
        toast({
          title: "提示",
          description: "请先编写或选中代码",
        });
        return;
      }

      setCurrentSelection(selection);
      setLoading(true);
      setStreaming(true);

      const responseId = `resp_${Date.now()}`;
      const newResponse: AIResponse = {
        id: responseId,
        type: action,
        content: "",
        isStreaming: true,
        timestamp: new Date().toISOString(),
      };

      setResponses((prev) => [...prev, newResponse]);

      try {
        if (action === AIActionType.REFACTOR) {
          const languageToSend = selection.language || language;
          const result = await aiService.refactorCode(codeToSend, languageToSend, selection);
          
          if (result.data && result.data.refactoredCode) {
            let refactoredCode = result.data.refactoredCode;
            
            refactoredCode = cleanJsonString(refactoredCode);
            
            refactoredCode = stripMarkdownCodeBlock(refactoredCode);
            
            refactoredCode = trimContextOverflow(
              refactoredCode,
              selection.contextBefore,
              selection.contextAfter
            );
            
            const codeLines = refactoredCode.split("\n");
            if (codeLines.length > 80) {
              refactoredCode = codeLines.slice(0, 80).join("\n");
              console.warn(`[Refactor] 重构代码超过80行，已截断`);
            }
            
            refactoredCode = refactoredCode.trim();
            
            const isValidCode = refactoredCode && 
                                refactoredCode.length > 0 && 
                                !isJsonObject(refactoredCode);
            
            const content = `**重构后的代码：**\n\n\`\`\`${language}\n${refactoredCode}\n\`\`\`\n\n**重构说明：**\n${result.data.explanation || '重构完成'}`;
            
            setResponses((prev) =>
              prev.map((resp) =>
                resp.id === responseId
                  ? { ...resp, content, isStreaming: false }
                  : resp
              )
            );
            
            if (selection && isValidCode && selection.selectedText && selection.selectedText.trim()) {
              const replaceEvent = new CustomEvent('codeReplace', {
                detail: {
                  newCode: refactoredCode,
                  selection: selection
                }
              });
              window.dispatchEvent(replaceEvent);
              
              toast({ title: "重构成功", description: "代码已重构并替换，可使用 Ctrl+Z 撤销" });
            } else if (!isValidCode) {
              toast({ title: "重构提示", description: "重构结果格式异常，请查看右侧卡片中的代码", variant: "destructive" });
            }
          } else {
            const content = result.data
              ? `**重构后的代码：**\n\n\`\`\`${language}\n${result.data.refactoredCode || '无'}\n\`\`\`\n\n**重构说明：**\n${result.data.explanation || '重构完成'}`
              : "重构完成，但未返回有效代码";

            setResponses((prev) =>
              prev.map((resp) =>
                resp.id === responseId
                  ? { ...resp, content, isStreaming: false }
                  : resp
              )
            );
            
            toast({ title: "重构提示", description: "未获取到有效的重构代码", variant: "destructive" });
          }
          
          setStreaming(false);
          setLoading(false);
          return;
        }

        let reader: ReadableStreamDefaultReader<Uint8Array>;
        const languageToSend = selection.language || language;

        switch (action) {
          case AIActionType.EXPLAIN:
            reader = await aiService.streamExplainCode(
              codeToSend,
              languageToSend,
              selection
            );
            break;
          case AIActionType.REVIEW:
            reader = await aiService.streamReviewCode(
              codeToSend,
              languageToSend,
              selection
            );
            break;
          default:
            throw new Error("未知的AI操作类型");
        }

        let fullContent = "";

        await parseSSEStream(reader, {
          onText: (delta) => {
            fullContent += processStreamDelta(delta);
            setResponses((prev) =>
              prev.map((resp) =>
                resp.id === responseId ? { ...resp, content: fullContent } : resp
              )
            );
          },
          onComplete: (content) => {
            if (content) {
              fullContent += processStreamDelta(content);
              setResponses((prev) =>
                prev.map((resp) =>
                  resp.id === responseId ? { ...resp, content: fullContent } : resp
                )
              );
            }
            setStreaming(false);
            setLoading(false);
            setResponses((prev) =>
              prev.map((resp) =>
                resp.id === responseId ? { ...resp, isStreaming: false } : resp
              )
            );
          },
          onError: (error) => {
            console.error("AI响应错误:", error);
            setStreaming(false);
            setLoading(false);
            setResponses((prev) =>
              prev.map((resp) =>
                resp.id === responseId
                  ? { ...resp, content: "抱歉，AI服务暂时不可用，请稍后重试。", isStreaming: false }
                  : resp
              )
            );
          },
        });
      } catch (error) {
        console.error("AI请求失败:", error);
        setStreaming(false);
        setLoading(false);
        const errorMessage = error instanceof Error ? error.message : "未知错误";
        setResponses((prev) =>
          prev.map((resp) =>
            resp.id === responseId
              ? { ...resp, content: `请求失败：${errorMessage}。请检查网络连接后重试。`, isStreaming: false }
              : resp
          )
        );
      }
    },
    [code, language, toast]
  );

  // 处理右键菜单AI操作
  const handleEditorAIAction = useCallback(
    (action: AIContextMenuAction, selection: CodeSelection) => {
      const actionMap: Record<AIContextMenuAction, AIActionType> = {
        explain: AIActionType.EXPLAIN,
        review: AIActionType.REVIEW,
        refactor: AIActionType.REFACTOR,
      };

      const aiAction = actionMap[action];
      if (aiAction) {
        handleAIAction(aiAction, selection);
      }
    },
    [handleAIAction]
  );

  // 清空响应
  const handleClearResponses = useCallback(() => {
    setResponses([]);
    toast({
      title: "已清空",
      description: "所有AI响应已清除",
    });
  }, [toast]);

  // 删除单个响应
  const handleDeleteResponse = useCallback((id: string) => {
    setResponses((prev) => prev.filter((resp) => resp.id !== id));
  }, []);

  // 使用 useCallback 缓存打开详情弹窗
  const handleOpenDetail = useCallback((response: AIResponse) => {
    setSelectedResponseId(response.id);
  }, []);

  // 使用 useCallback 缓存关闭详情弹窗
  const handleCloseDetail = useCallback(() => {
    setSelectedResponseId(null);
  }, []);

  // 格式化时间
  const formatTime = (timestamp: string) => {
    return new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  // 获取类型标签
  const getTypeLabel = (type: AIActionType) => {
    switch (type) {
      case AIActionType.EXPLAIN:
        return "代码解释";
      case AIActionType.REVIEW:
        return "代码审查";
      case AIActionType.REFACTOR:
        return "智能重构";
      default:
        return "AI分析";
    }
  };

  // 获取类型颜色（Badge 用）
  const getTypeColor = (type: AIActionType) => {
    switch (type) {
      case AIActionType.EXPLAIN:
        return "bg-amber-500/10 text-amber-600 border-amber-500/20";
      case AIActionType.REVIEW:
        return "bg-blue-500/10 text-blue-600 border-blue-500/20";
      case AIActionType.REFACTOR:
        return "bg-emerald-500/10 text-emerald-600 border-emerald-500/20";
      default:
        return "bg-gray-500/10 text-gray-600 border-gray-500/20";
    }
  };

  return (
    <div className="h-[calc(100vh-64px)] flex flex-col bg-gray-50 dark:bg-gray-950">
      {/* Header */}
      <motion.header
        initial={reducedMotion ? { opacity: 1 } : { opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={reducedMotion ? { duration: 0 } : { duration: 0.3 }}
        className="flex items-center justify-between px-4 py-2 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 flex-shrink-0 shadow-sm z-10 relative overflow-hidden"
      >
        {/* 背景装饰 */}
        <div className="absolute right-8 top-1/2 -translate-y-1/2 opacity-5 pointer-events-none">
          <Code2 className="w-24 h-24" />
        </div>

        <div className="flex items-center gap-3 relative z-10">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center shadow-sm">
            <Code2 className="w-4 h-4 text-white" />
          </div>
          <div>
            <h1 className="text-base font-semibold bg-gradient-to-r from-cyan-600 to-blue-600 bg-clip-text text-transparent">
              代码助手
            </h1>
            <p className="text-xs text-gray-500">AI驱动的智能编程助手</p>
          </div>
        </div>

        <div className="flex items-center gap-2 relative z-10">
          {responses.length > 0 && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleClearResponses}
              className="text-gray-600 hover:text-red-600 h-8"
            >
              <Trash2 className="w-4 h-4 mr-1" />
              清空
            </Button>
          )}
        </div>
      </motion.header>

      {/* Main Content - 响应式布局 */}
      <div
        ref={containerRef}
        className="flex-1 flex overflow-hidden lg:flex-row flex-col"
      >
        {/* Left Side - Code Editor */}
        <motion.div
          initial={reducedMotion ? { opacity: 1 } : { opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={reducedMotion ? { duration: 0 } : { duration: 0.4 }}
          className="lg:flex-[55] flex flex-col border-b lg:border-b-0 lg:border-r border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-inner"
          style={{ flex: `0 0 ${leftWidth}%` }}
        >
          <div className="flex-1 min-h-0">
            <CodeEditor
              initialValue={code}
              initialLanguage={language}
              onChange={setCode}
              onLanguageChange={handleLanguageChange}
              onAIAction={handleEditorAIAction}
              className="h-full"
            />
          </div>
        </motion.div>

        {/* 拖拽条 - 仅在桌面端显示 */}
        <div
          className="hidden lg:flex w-1 bg-gray-200 dark:bg-gray-800 hover:bg-cyan-400 dark:hover:bg-cyan-600 cursor-col-resize items-center justify-center transition-colors z-10"
          onMouseDown={handleMouseDown}
          style={{ cursor: isDragging ? "col-resize" : undefined }}
        >
          <GripVertical className="w-3 h-3 text-gray-400" />
        </div>

        {/* Right Side - AI Analysis Results */}
        <motion.div
          initial={reducedMotion ? { opacity: 1 } : { opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={reducedMotion ? { duration: 0 } : { duration: 0.4 }}
          className="lg:flex-[45] flex flex-col bg-gray-50/50 dark:bg-gray-950/50"
          style={{ flex: `1 1 ${100 - leftWidth}%` }}
        >
          {/* Section Header */}
          <div className="flex items-center justify-between px-4 py-2 bg-white dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 flex-shrink-0">
            <div className="flex items-center gap-2">
              <MessageSquare className="w-4 h-4 text-cyan-600" />
              <span className="text-sm font-medium text-gray-900 dark:text-white">AI 分析结果</span>
              {responses.length > 0 && (
                <Badge variant="secondary" className="text-xs">
                  {responses.length}
                </Badge>
              )}
              {streaming && (
                <span className="flex items-center gap-1 text-xs text-green-600">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                  生成中...
                </span>
              )}
            </div>
          </div>

          {/* Responses List */}
          <ScrollArea className="flex-1 p-4">
            {responses.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-center">
                <Card className="p-8 max-w-sm w-full bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-800 dark:to-gray-700 border-0 shadow-none">
                  <motion.div
                    initial={reducedMotion ? { scale: 1, opacity: 1 } : { scale: 0.9, opacity: 0 }}
                    animate={{ scale: 1, opacity: 1 }}
                    transition={reducedMotion ? { duration: 0 } : { duration: 0.4 }}
                    className="flex flex-col items-center"
                  >
                    <div className="w-16 h-16 rounded-full bg-white dark:bg-gray-900 flex items-center justify-center mb-4 shadow-sm">
                      <Sparkles className="w-8 h-8 text-cyan-500 animate-pulse" />
                    </div>
                    <p className="text-sm text-gray-900 dark:text-gray-100 mb-1 font-medium">
                      暂无AI响应
                    </p>
                    <p className="text-xs text-gray-600 dark:text-gray-400 text-center leading-relaxed">
                      在代码编辑器中选中代码，右键选择 AI 功能开始体验
                    </p>
                    <div className="mt-4 flex flex-wrap justify-center gap-2">
                      {["代码解释", "代码审查", "智能重构"].map((feature) => (
                        <span
                          key={feature}
                          className="text-xs px-2 py-1 rounded-full bg-white/60 dark:bg-gray-900/60 text-gray-600 dark:text-gray-400"
                        >
                          {feature}
                        </span>
                      ))}
                    </div>
                  </motion.div>
                </Card>
              </div>
            ) : (
              <div className="space-y-3">
                {/* Latest Response - Always Expanded */}
                {latestResponse && (
                  <motion.div
                    initial={reducedMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={reducedMotion ? { duration: 0 } : { duration: 0.3 }}
                    className={cn(
                      "rounded-xl border overflow-hidden shadow-md hover:shadow-lg transition-shadow",
                      typeBgColors[latestResponse.type]
                    )}
                  >
                    <ResponseCard
                      id={latestResponse.id}
                      type={latestResponse.type}
                      content={latestResponse.content}
                      isStreaming={latestResponse.isStreaming}
                      timestamp={latestResponse.timestamp}
                      isExpanded={true}
                      showActions={true}
                      onDelete={handleDeleteResponse}
                    />
                  </motion.div>
                )}

                {/* History Responses - Collapsed Cards */}
                {historyResponses.length > 0 && (
                  <div className="border-t border-gray-200 dark:border-gray-800 pt-3">
                    <p className="text-xs text-gray-500 mb-2 px-1 flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      历史记录
                    </p>
                    <div className="space-y-2">
                      <AnimatePresence mode="popLayout">
                        {historyResponses.map((response, index) => {
                          const TypeIcon = typeIcons[response.type];
                          return (
                            <motion.div
                              key={response.id}
                              initial={reducedMotion ? { opacity: 1, y: 0 } : { opacity: 0, y: 20 }}
                              animate={{ opacity: 1, y: 0 }}
                              exit={reducedMotion ? { opacity: 0 } : { opacity: 0, y: -20, scale: 0.95 }}
                              transition={reducedMotion ? { duration: 0 } : { delay: index * 0.05, duration: 0.3 }}
                              layout
                              className="group"
                            >
                              <div
                                className={cn(
                                  "bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 p-3 cursor-pointer",
                                  "hover:border-cyan-500/50 hover:shadow-md transition-all",
                                  "shadow-sm"
                                )}
                                onClick={() => handleOpenDetail(response)}
                              >
                                <div className="flex items-start justify-between mb-1">
                                  <div className="flex items-center gap-2">
                                    <Badge className={cn("text-xs flex items-center gap-1", getTypeColor(response.type))}>
                                      <TypeIcon className="w-3 h-3" />
                                      {getTypeLabel(response.type)}
                                    </Badge>
                                    {response.isStreaming && (
                                      <span className="flex items-center gap-1 text-xs text-green-600">
                                        <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                                        生成中
                                      </span>
                                    )}
                                  </div>
                                  <div className="flex items-center gap-1 text-xs text-gray-500">
                                    <Clock className="w-3 h-3" />
                                    {formatTime(response.timestamp)}
                                  </div>
                                </div>
                                <p className="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">
                                  {response.content || "等待AI响应..."}
                                </p>
                                <div className="flex items-center justify-between mt-2">
                                  <span className="text-xs text-cyan-600">点击查看详情</span>
                                  <motion.div
                                    whileHover={reducedMotion ? undefined : { scale: 1.1 }}
                                    whileTap={reducedMotion ? undefined : { scale: 0.9 }}
                                  >
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        handleDeleteResponse(response.id);
                                      }}
                                      className="h-6 text-xs text-gray-500 hover:text-red-600 opacity-0 group-hover:opacity-100 transition-opacity px-2"
                                    >
                                      <Trash2 className="w-3 h-3 mr-1" />
                                      删除
                                    </Button>
                                  </motion.div>
                                </div>
                              </div>
                            </motion.div>
                          );
                        })}
                      </AnimatePresence>
                    </div>
                  </div>
                )}
              </div>
            )}
          </ScrollArea>
        </motion.div>
      </div>

      {/* Detail View Dialog */}
      <Dialog open={!!selectedResponseId} onOpenChange={handleCloseDetail}>
        <DialogContent showCloseButton={false} className="max-w-3xl max-h-[90vh] p-0 overflow-hidden flex flex-col">
          <DialogTitle className="sr-only">AI分析详情</DialogTitle>
          {selectedResponse && (
            <>
              <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 flex-shrink-0">
                <div className="flex items-center gap-2">
                  <Badge className={cn(getTypeColor(selectedResponse.type))}>
                    {getTypeLabel(selectedResponse.type)}
                  </Badge>
                  <span className="text-sm text-gray-500">
                    {formatTime(selectedResponse.timestamp)}
                  </span>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleCloseDetail}
                  className="h-8 w-8 p-0"
                >
                  <X className="w-4 h-4" />
                </Button>
              </div>
              <div className="flex-1 overflow-y-auto p-4 bg-gray-50 dark:bg-gray-950">
                <ResponseCard
                  id={selectedResponse.id}
                  type={selectedResponse.type}
                  content={selectedResponse.content}
                  isStreaming={selectedResponse.isStreaming}
                  timestamp={selectedResponse.timestamp}
                  isExpanded={true}
                  showActions={false}
                  onDelete={() => {
                    handleDeleteResponse(selectedResponse.id);
                    handleCloseDetail();
                  }}
                />
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

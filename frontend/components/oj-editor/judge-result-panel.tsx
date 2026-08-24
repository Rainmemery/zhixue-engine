"use client";

import { motion } from "framer-motion";
import { Loader2, CheckCircle2, XCircle, Clock, MemoryStick, AlertTriangle, Hourglass, ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";

export interface TestCaseResult {
  status: string;
  input: string;
  expectedOutput: string;
  actualOutput?: string;
  executionTime?: number;
  memory?: number;
  errorMessage?: string;
}

export interface SubmitResult {
  status: string;
  totalTime?: number;
  maxMemory?: number;
  passedCount?: number;
  totalCount?: number;
  testCases?: TestCaseResult[];
  errorMessage?: string;
}

interface JudgeResultPanelProps {
  result: any;
  isRunning: boolean;
  isSubmitting: boolean;
  isCustomMode?: boolean;
}

const statusConfig: Record<string, { label: string; color: string; bgColor: string; icon: React.ReactNode }> = {
  pending: {
    label: "等待判题",
    color: "text-blue-600 dark:text-blue-400",
    bgColor: "bg-blue-50 dark:bg-blue-950/30 border-blue-200 dark:border-blue-800",
    icon: <Hourglass className="w-5 h-5" />,
  },
  judging: {
    label: "判题中",
    color: "text-blue-600 dark:text-blue-400",
    bgColor: "bg-blue-50 dark:bg-blue-950/30 border-blue-200 dark:border-blue-800",
    icon: <Loader2 className="w-5 h-5 animate-spin" />,
  },
  accepted: {
    label: "通过",
    color: "text-emerald-600 dark:text-emerald-400",
    bgColor: "bg-emerald-50 dark:bg-emerald-950/30 border-emerald-200 dark:border-emerald-800",
    icon: <CheckCircle2 className="w-5 h-5" />,
  },
  partial_accepted: {
    label: "部分通过",
    color: "text-sky-600 dark:text-sky-400",
    bgColor: "bg-sky-50 dark:bg-sky-950/30 border-sky-200 dark:border-sky-800",
    icon: <CheckCircle2 className="w-5 h-5" />,
  },
  passed: {
    label: "通过",
    color: "text-emerald-600 dark:text-emerald-400",
    bgColor: "bg-emerald-50 dark:bg-emerald-950/30 border-emerald-200 dark:border-emerald-800",
    icon: <CheckCircle2 className="w-5 h-5" />,
  },
  wrong_answer: {
    label: "答案错误",
    color: "text-red-600 dark:text-red-400",
    bgColor: "bg-red-50 dark:bg-red-950/30 border-red-200 dark:border-red-800",
    icon: <XCircle className="w-5 h-5" />,
  },
  time_limit_exceeded: {
    label: "时间超限",
    color: "text-amber-600 dark:text-amber-400",
    bgColor: "bg-amber-50 dark:bg-amber-950/30 border-amber-200 dark:border-amber-800",
    icon: <Clock className="w-5 h-5" />,
  },
  memory_limit_exceeded: {
    label: "内存超限",
    color: "text-orange-600 dark:text-orange-400",
    bgColor: "bg-orange-50 dark:bg-orange-950/30 border-orange-200 dark:border-orange-800",
    icon: <MemoryStick className="w-5 h-5" />,
  },
  runtime_error: {
    label: "运行错误",
    color: "text-purple-600 dark:text-purple-400",
    bgColor: "bg-purple-50 dark:bg-purple-950/30 border-purple-200 dark:border-purple-800",
    icon: <AlertTriangle className="w-5 h-5" />,
  },
  compilation_error: {
    label: "编译错误",
    color: "text-yellow-800 dark:text-yellow-400",
    bgColor: "bg-yellow-50 dark:bg-yellow-950/30 border-yellow-200 dark:border-yellow-800",
    icon: <AlertTriangle className="w-5 h-5" />,
  },
  system_error: {
    label: "系统错误",
    color: "text-gray-600 dark:text-gray-400",
    bgColor: "bg-gray-50 dark:bg-gray-950/30 border-gray-200 dark:border-gray-800",
    icon: <AlertTriangle className="w-5 h-5" />,
  },
  output_limit_exceeded: {
    label: "输出超限",
    color: "text-pink-600 dark:text-pink-400",
    bgColor: "bg-pink-50 dark:bg-pink-950/30 border-pink-200 dark:border-pink-800",
    icon: <AlertTriangle className="w-5 h-5" />,
  },
};

function getStatusConfig(status: string) {
  return statusConfig[status] || statusConfig.system_error;
}

function formatTime(ms?: number) {
  if (ms === undefined || ms === null) return "-";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function formatMemory(kb?: number) {
  if (kb === undefined || kb === null) return "-";
  if (kb < 1024) return `${kb}KB`;
  return `${(kb / 1024).toFixed(1)}MB`;
}

function DiffView({ expected, actual }: { expected: string; actual: string }) {
  const expectedLines = expected.split("\n");
  const actualLines = actual.split("\n");
  const maxLines = Math.max(expectedLines.length, actualLines.length);

  return (
    <div className="mt-2 rounded-md border border-gray-200 dark:border-gray-700 overflow-hidden">
      <div className="grid grid-cols-[auto_1fr_auto_1fr] text-xs font-semibold text-muted-foreground bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="px-2 py-1 border-r border-gray-200 dark:border-gray-700 w-8 text-center"></div>
        <div className="px-2 py-1 border-r border-gray-200 dark:border-gray-700">期望输出</div>
        <div className="px-2 py-1 border-r border-gray-200 dark:border-gray-700 w-8 text-center"></div>
        <div className="px-2 py-1">实际输出</div>
      </div>
      <div className="max-h-40 overflow-y-auto">
        {Array.from({ length: maxLines }).map((_, i) => {
          const expLine = expectedLines[i];
          const actLine = actualLines[i];
          const expExists = expLine !== undefined;
          const actExists = actLine !== undefined;
          const isDeletion = expExists && !actExists;
          const isAddition = !expExists && actExists;
          const isDiff = expExists && actExists && expLine !== actLine;

          return (
            <div key={i} className="grid grid-cols-[auto_1fr_auto_1fr] text-xs font-mono">
              <div className={cn(
                "px-2 py-0.5 border-r border-gray-200 dark:border-gray-700 w-8 text-right text-muted-foreground/50 select-none",
                (isDeletion || isDiff) && "bg-red-50 dark:bg-red-950/20"
              )}>
                {expExists ? i + 1 : ""}
              </div>
              <div className={cn(
                "px-2 py-0.5 border-r border-gray-200 dark:border-gray-700",
                isDeletion && "bg-red-100 dark:bg-red-950/30 text-red-700 dark:text-red-400",
                isDiff && "bg-red-50 dark:bg-red-950/20 text-red-700 dark:text-red-400"
              )}>
                {expExists ? expLine || "\u00A0" : "\u00A0"}
              </div>
              <div className={cn(
                "px-2 py-0.5 border-r border-gray-200 dark:border-gray-700 w-8 text-right text-muted-foreground/50 select-none",
                (isAddition || isDiff) && "bg-green-50 dark:bg-green-950/20"
              )}>
                {actExists ? i + 1 : ""}
              </div>
              <div className={cn(
                "px-2 py-0.5",
                isAddition && "bg-green-100 dark:bg-green-950/30 text-green-700 dark:text-green-400",
                isDiff && "bg-green-50 dark:bg-green-950/20 text-green-700 dark:text-green-400"
              )}>
                {actExists ? actLine || "\u00A0" : "\u00A0"}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function TestCaseCard({ result, index, isCustom }: { result: TestCaseResult; index: number; isCustom?: boolean }) {
  const config = getStatusConfig(result.status);
  const isAccepted = result.status === "accepted" || result.status === "passed";
  const hasDiff = !isAccepted && result.actualOutput !== undefined && result.expectedOutput;

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, delay: index * 0.05 }}
      className={cn(
        "rounded-lg border p-3 text-sm",
        isAccepted
          ? "border-emerald-200 dark:border-emerald-800 bg-emerald-50/50 dark:bg-emerald-950/20"
          : "border-red-200 dark:border-red-800 bg-red-50/50 dark:bg-red-950/20"
      )}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="font-medium text-xs text-muted-foreground">
          {isCustom ? "自定义测试" : `测试样例 #${index + 1}`}
        </span>
        <span className={cn("flex items-center gap-1 text-xs font-semibold", config.color)}>
          {isAccepted ? <CheckCircle2 className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
          {config.label}
        </span>
      </div>
      <div className="space-y-2">
        <div>
          <span className="text-xs font-semibold text-muted-foreground">输入:</span>
          <pre className="mt-0.5 p-2 rounded bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 text-xs font-mono overflow-x-auto whitespace-pre-wrap">
            {result.input || "(空)"}
          </pre>
        </div>

        {hasDiff ? (
          <DiffView expected={result.expectedOutput} actual={result.actualOutput || ""} />
        ) : (
          <>
            {result.expectedOutput && (
              <div>
                <span className="text-xs font-semibold text-muted-foreground">期望输出:</span>
                <pre className="mt-0.5 p-2 rounded bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 text-xs font-mono overflow-x-auto whitespace-pre-wrap">
                  {result.expectedOutput}
                </pre>
              </div>
            )}
            {result.actualOutput !== undefined && (
              <div>
                <span className={cn("text-xs font-semibold", isAccepted ? "text-muted-foreground" : "text-red-600 dark:text-red-400")}>
                  实际输出:
                </span>
                <pre className={cn(
                  "mt-0.5 p-2 rounded text-xs font-mono overflow-x-auto whitespace-pre-wrap",
                  isAccepted
                    ? "bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700"
                    : "bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800"
                )}>
                  {result.actualOutput || "(空)"}
                </pre>
              </div>
            )}
          </>
        )}

        {(result.status === "runtime_error" || result.status === "compilation_error") && result.errorMessage && (
          <div className="p-2 rounded bg-orange-50 dark:bg-orange-950/30 border border-orange-200 dark:border-orange-800">
            <div className="flex items-center gap-1 mb-1">
              <AlertTriangle className="w-3 h-3 text-orange-600 dark:text-orange-400" />
              <span className="text-xs font-semibold text-orange-600 dark:text-orange-400">
                {result.status === "compilation_error" ? "编译错误" : "运行错误"}
              </span>
            </div>
            <pre className="text-xs font-mono text-orange-700 dark:text-orange-300 whitespace-pre-wrap overflow-x-auto">
              {result.errorMessage}
            </pre>
          </div>
        )}

        {!(result.status === "runtime_error" || result.status === "compilation_error") && result.errorMessage && (
          <div className="p-2 rounded bg-red-50 dark:bg-red-950/30 border border-red-200 dark:border-red-800">
            <pre className="text-xs font-mono text-red-600 dark:text-red-400 whitespace-pre-wrap overflow-x-auto">
              {result.errorMessage}
            </pre>
          </div>
        )}

        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          {result.executionTime !== undefined && (
            <span className="flex items-center gap-1">
              <Clock className="w-3 h-3" />
              {formatTime(result.executionTime)}
            </span>
          )}
          {result.memory !== undefined && result.memory !== null && (
            <span className="flex items-center gap-1">
              <MemoryStick className="w-3 h-3" />
              {formatMemory(result.memory)}
            </span>
          )}
        </div>
      </div>
    </motion.div>
  );
}

export function JudgeResultPanel({ result, isRunning, isSubmitting, isCustomMode }: JudgeResultPanelProps) {
  if (isRunning || isSubmitting) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-3">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
        <span className="text-sm text-muted-foreground">{isRunning ? "运行测试中..." : "提交判题中..."}</span>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <p className="text-sm">运行测试或提交代码查看结果</p>
      </div>
    );
  }

  if (result.status === "pending" || result.status === "judging") {
    const isPending = result.status === "pending";
    const config = getStatusConfig(result.status);

    return (
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className={cn("rounded-lg border p-4", config.bgColor)}
      >
        <div className="flex flex-col items-center justify-center py-6 gap-3">
          <span className={config.color}>
            {isPending ? (
              <Hourglass className="w-8 h-8" />
            ) : (
              <Loader2 className="w-8 h-8 animate-spin" />
            )}
          </span>
          <span className={cn("text-sm font-semibold", config.color)}>
            {config.label}
          </span>
          {isPending ? (
            <span className="text-xs text-muted-foreground">排队中，请稍候...</span>
          ) : (
            <div className="w-48 space-y-1.5">
              <div className="h-1.5 rounded-full bg-blue-100 dark:bg-blue-900/50 overflow-hidden">
                <motion.div
                  className="h-full rounded-full bg-blue-500 dark:bg-blue-400"
                  initial={{ width: "0%" }}
                  animate={{ width: "100%" }}
                  transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
                />
              </div>
            </div>
          )}
        </div>
      </motion.div>
    );
  }

  if (result.results && Array.isArray(result.results)) {
    const passedCount = result.passedCount || 0;
    const totalCount = result.totalCount || result.results.length;
    const allPassed = passedCount === totalCount && totalCount > 0;

    return (
      <div className="space-y-3">
        <div className={cn(
          "flex items-center justify-between p-2.5 rounded-lg border",
          allPassed
            ? "bg-emerald-50 dark:bg-emerald-950/20 border-emerald-200 dark:border-emerald-800"
            : "bg-red-50 dark:bg-red-950/20 border-red-200 dark:border-red-800"
        )}>
          <div className="flex items-center gap-2">
            {allPassed ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
            ) : (
              <XCircle className="w-4 h-4 text-red-600 dark:text-red-400" />
            )}
            <span className={cn(
              "text-sm font-semibold",
              allPassed ? "text-emerald-600 dark:text-emerald-400" : "text-red-600 dark:text-red-400"
            )}>
              {allPassed ? "全部通过" : "未全部通过"}
            </span>
          </div>
          <span className={cn(
            "text-xs font-medium",
            allPassed ? "text-emerald-600 dark:text-emerald-400" : "text-red-600 dark:text-red-400"
          )}>
            通过 {passedCount}/{totalCount}
          </span>
        </div>
        {result.results.map((r: any, i: number) => (
          <TestCaseCard
            key={i}
            result={{
              status: r.status,
              input: r.input || "",
              expectedOutput: r.expectedOutput || "",
              actualOutput: r.actualOutput ?? r.output ?? r.stdout,
              executionTime: r.executionTimeMs,
              memory: r.memoryUsedKb,
              errorMessage: r.errorMessage,
            }}
            index={i}
            isCustom={isCustomMode}
          />
        ))}
      </div>
    );
  }

  const status = result.status || "system_error";
  const config = getStatusConfig(status);

  return (
    <div className="space-y-3">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className={cn("rounded-lg border p-4", config.bgColor)}
      >
        <div className="flex items-center gap-3">
          <span className={config.color}>{config.icon}</span>
          <div className="flex-1">
            <div className={cn("text-lg font-bold", config.color)}>
              {config.label}
            </div>
            <div className="flex items-center gap-4 mt-1 text-xs text-muted-foreground">
              {result.passedCount !== undefined && result.totalCount !== undefined && (
                <span>
                  通过: {result.passedCount}/{result.totalCount}
                </span>
              )}
              {result.totalTimeMs !== undefined && (
                <span className="flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  {formatTime(result.totalTimeMs)}
                </span>
              )}
              {result.maxMemoryKb !== undefined && result.maxMemoryKb !== null && (
                <span className="flex items-center gap-1">
                  <MemoryStick className="w-3 h-3" />
                  {formatMemory(result.maxMemoryKb)}
                </span>
              )}
            </div>
          </div>
        </div>
      </motion.div>

      {result.errorMessage && (
        <div className="rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-950/20 p-3">
          <pre className="text-xs font-mono text-red-600 dark:text-red-400 whitespace-pre-wrap overflow-x-auto">
            {result.errorMessage}
          </pre>
        </div>
      )}

      {result.testcaseResults && result.testcaseResults.length > 0 && (
        <div className="space-y-2">
          <span className="text-xs font-medium text-muted-foreground">测试点详情</span>
          {result.testcaseResults.map((tc: any, i: number) => (
            <TestCaseCard
              key={i}
              result={{
                status: tc.status,
                input: tc.input || "",
                expectedOutput: tc.expectedOutput || "",
                actualOutput: tc.actualOutput ?? tc.output ?? tc.stdout,
                executionTime: tc.executionTimeMs,
                memory: tc.memoryUsedKb,
              }}
              index={i}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default JudgeResultPanel;

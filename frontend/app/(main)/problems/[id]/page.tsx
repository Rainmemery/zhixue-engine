"use client";

import { useState, useEffect, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import {
  ArrowLeft, CheckCircle2, XCircle, Clock, Copy, Check,
  ChevronDown, ChevronUp, ChevronLeft, ChevronRight, Timer, HardDrive, Tag, Lightbulb,
  FileText, Code2, Terminal, MemoryStick, AlertTriangle, Hourglass, Loader2
} from "lucide-react";
import { problemService, Problem, ProblemSample, Submission } from "@/services/problemService";
import { MarkdownRenderer } from "@/components/chat/markdown-renderer";
import { OJCodeEditor } from "@/components/oj-editor/oj-code-editor";
import { useAuthStore } from "@/stores/authStore";
import { useSubmissionStatus } from "@/hooks/useSubmissionStatus";
import { cn } from "@/lib/utils";

const difficultyConfig: Record<string, { label: string; color: string; bgColor: string; borderColor: string }> = {
  easy: {
    label: "简单",
    color: "text-emerald-600 dark:text-emerald-400",
    bgColor: "bg-emerald-50 dark:bg-emerald-950/30",
    borderColor: "border-emerald-200 dark:border-emerald-800",
  },
  medium: {
    label: "中等",
    color: "text-amber-600 dark:text-amber-400",
    bgColor: "bg-amber-50 dark:bg-amber-950/30",
    borderColor: "border-amber-200 dark:border-amber-800",
  },
  hard: {
    label: "困难",
    color: "text-red-600 dark:text-red-400",
    bgColor: "bg-red-50 dark:bg-red-950/30",
    borderColor: "border-red-200 dark:border-red-800",
  },
};

function SampleCard({ sample, index }: { sample: ProblemSample; index: number }) {
  const [copied, setCopied] = useState<"input" | "output" | null>(null);
  const [expanded, setExpanded] = useState(true);

  const handleCopy = useCallback(async (text: string, type: "input" | "output") => {
    await navigator.clipboard.writeText(text);
    setCopied(type);
    setTimeout(() => setCopied(null), 2000);
  }, []);

  return (
    <div className="rounded-md border border-gray-200 dark:border-gray-700/80 overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between px-2.5 py-1 bg-gray-50/80 dark:bg-gray-800/60 text-[11px] font-medium text-muted-foreground hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
      >
        <span className="flex items-center gap-1">
          <FileText className="w-3 h-3" />
          样例 {index + 1}
        </span>
        {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
      </button>
      {expanded && (
        <div className="p-2 space-y-1.5">
          <div>
            <div className="flex items-center justify-between mb-0.5">
              <span className="text-[10px] font-semibold text-muted-foreground/70 uppercase tracking-wider">输入</span>
              <button
                onClick={() => handleCopy(sample.input, "input")}
                className="text-[10px] text-muted-foreground/60 hover:text-foreground flex items-center gap-0.5 transition-colors"
              >
                {copied === "input" ? <Check className="w-2.5 h-2.5 text-emerald-500" /> : <Copy className="w-2.5 h-2.5" />}
                {copied === "input" ? "已复制" : "复制"}
              </button>
            </div>
            <pre className="p-1.5 rounded bg-gray-50 dark:bg-gray-900/80 border border-gray-100 dark:border-gray-800 text-[11px] font-mono overflow-x-auto whitespace-pre-wrap leading-relaxed max-h-24 overflow-y-auto">
              {sample.input}
            </pre>
          </div>
          <div>
            <div className="flex items-center justify-between mb-0.5">
              <span className="text-[10px] font-semibold text-muted-foreground/70 uppercase tracking-wider">输出</span>
              <button
                onClick={() => handleCopy(sample.output, "output")}
                className="text-[10px] text-muted-foreground/60 hover:text-foreground flex items-center gap-0.5 transition-colors"
              >
                {copied === "output" ? <Check className="w-2.5 h-2.5 text-emerald-500" /> : <Copy className="w-2.5 h-2.5" />}
                {copied === "output" ? "已复制" : "复制"}
              </button>
            </div>
            <pre className="p-1.5 rounded bg-gray-50 dark:bg-gray-900/80 border border-gray-100 dark:border-gray-800 text-[11px] font-mono overflow-x-auto whitespace-pre-wrap leading-relaxed max-h-24 overflow-y-auto">
              {sample.output}
            </pre>
          </div>
          {sample.explanation && (
            <div className="p-1.5 rounded bg-blue-50/40 dark:bg-blue-950/20 border border-blue-100/80 dark:border-blue-900/40 text-[11px] text-muted-foreground leading-relaxed">
              <MarkdownRenderer content={sample.explanation} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const submissionStatusConfig: Record<string, { label: string; color: string; bgColor: string; borderColor: string; icon: React.ReactNode }> = {
  accepted: { label: "通过", color: "text-emerald-600 dark:text-emerald-400", bgColor: "bg-emerald-50 dark:bg-emerald-950/30", borderColor: "border-emerald-200 dark:border-emerald-800", icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
  partial_accepted: { label: "部分通过", color: "text-sky-600 dark:text-sky-400", bgColor: "bg-sky-50 dark:bg-sky-950/30", borderColor: "border-sky-200 dark:border-sky-800", icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
  passed: { label: "通过", color: "text-emerald-600 dark:text-emerald-400", bgColor: "bg-emerald-50 dark:bg-emerald-950/30", borderColor: "border-emerald-200 dark:border-emerald-800", icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
  wrong_answer: { label: "答案错误", color: "text-red-600 dark:text-red-400", bgColor: "bg-red-50 dark:bg-red-950/30", borderColor: "border-red-200 dark:border-red-800", icon: <XCircle className="w-3.5 h-3.5" /> },
  time_limit_exceeded: { label: "时间超限", color: "text-amber-600 dark:text-amber-400", bgColor: "bg-amber-50 dark:bg-amber-950/30", borderColor: "border-amber-200 dark:border-amber-800", icon: <Clock className="w-3.5 h-3.5" /> },
  memory_limit_exceeded: { label: "内存超限", color: "text-orange-600 dark:text-orange-400", bgColor: "bg-orange-50 dark:bg-orange-950/30", borderColor: "border-orange-200 dark:border-orange-800", icon: <MemoryStick className="w-3.5 h-3.5" /> },
  runtime_error: { label: "运行错误", color: "text-purple-600 dark:text-purple-400", bgColor: "bg-purple-50 dark:bg-purple-950/30", borderColor: "border-purple-200 dark:border-purple-800", icon: <AlertTriangle className="w-3.5 h-3.5" /> },
  compilation_error: { label: "编译错误", color: "text-yellow-800 dark:text-yellow-400", bgColor: "bg-yellow-50 dark:bg-yellow-950/30", borderColor: "border-yellow-200 dark:border-yellow-800", icon: <AlertTriangle className="w-3.5 h-3.5" /> },
  pending: { label: "等待中", color: "text-blue-600 dark:text-blue-400", bgColor: "bg-blue-50 dark:bg-blue-950/30", borderColor: "border-blue-200 dark:border-blue-800", icon: <Hourglass className="w-3.5 h-3.5" /> },
  judging: { label: "判题中", color: "text-blue-600 dark:text-blue-400", bgColor: "bg-blue-50 dark:bg-blue-950/30", borderColor: "border-blue-200 dark:border-blue-800", icon: <Loader2 className="w-3.5 h-3.5 animate-spin" /> },
  system_error: { label: "系统错误", color: "text-gray-600 dark:text-gray-400", bgColor: "bg-gray-50 dark:bg-gray-950/30", borderColor: "border-gray-200 dark:border-gray-800", icon: <AlertTriangle className="w-3.5 h-3.5" /> },
};

function getSubmissionStatusConfig(status: string) {
  return submissionStatusConfig[status] || submissionStatusConfig.system_error;
}

function formatTime(ms?: number) {
  if (ms === undefined || ms === null) return "-";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function formatMemory(kb?: number) {
  if (kb === undefined || kb === null) return "-";
  return `${(kb / 1024).toFixed(2)}MB`;
}

function formatLanguage(lang: string) {
  const map: Record<string, string> = { java: "Java", cpp: "C++", c: "C", python: "Python", javascript: "JavaScript" };
  return map[lang] || lang;
}

function formatRelativeTime(dateStr: string) {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  if (days > 7) return date.toLocaleDateString("zh-CN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  if (days > 0) return `${days}天前`;
  if (hours > 0) return `${hours}小时前`;
  if (minutes > 0) return `${minutes}分钟前`;
  return "刚刚";
}

export default function ProblemDetailPage() {
  const params = useParams();
  const router = useRouter();
  const { user } = useAuthStore();
  const problemId = Number(params.id);

  const [problem, setProblem] = useState<Problem | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"description" | "submissions">("description");
  const [mobileView, setMobileView] = useState<"problem" | "editor">("problem");
  const [hintExpanded, setHintExpanded] = useState(false);
  const [trackingSubmissionId, setTrackingSubmissionId] = useState<number | null>(null);
  const { submission: trackedSubmission, isLoading: isSubmissionTracking } = useSubmissionStatus(trackingSubmissionId);

  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [submissionsPage, setSubmissionsPage] = useState(1);
  const [submissionsTotal, setSubmissionsTotal] = useState(0);
  const [submissionsLoading, setSubmissionsLoading] = useState(false);
  const [expandedSubmissionId, setExpandedSubmissionId] = useState<number | null>(null);

  const fetchSubmissions = useCallback(async () => {
    if (!user?.id) return;
    setSubmissionsLoading(true);
    try {
      const res = await problemService.getProblemSubmissions(problemId, user.id, submissionsPage, 10);
      if (res.data) {
        setSubmissions(res.data.items || []);
        setSubmissionsTotal(res.data.total || 0);
      }
    } catch (err) {
      console.error("Failed to fetch submissions:", err);
    } finally {
      setSubmissionsLoading(false);
    }
  }, [problemId, user?.id, submissionsPage]);

  useEffect(() => {
    if (activeTab === "submissions") {
      fetchSubmissions();
    }
  }, [activeTab, fetchSubmissions]);

  useEffect(() => {
    const fetchProblem = async () => {
      try {
        const res = await problemService.getProblemDetail(problemId, user?.id);
        if (res.data) {
          setProblem(res.data);
        }
      } catch (err) {
        console.error("Failed to fetch problem:", err);
      } finally {
        setLoading(false);
      }
    };
    if (problemId) fetchProblem();
  }, [problemId, user?.id]);

  const handleRunTest = useCallback(async (code: string, language: string, customInput?: string, customExpectedOutput?: string) => {
    setTrackingSubmissionId(null);
    const res = await problemService.runCode(problemId, language, code, customInput, customExpectedOutput);
    return res.data;
  }, [problemId]);

  const handleSubmitCode = useCallback(async (code: string, language: string) => {
    const res = await problemService.submitCode(problemId, language, code);
    if (res.data?.submissionId) {
      setTrackingSubmissionId(res.data.submissionId);
    }
    return res.data;
  }, [problemId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="flex flex-col items-center gap-3">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
          <span className="text-sm text-muted-foreground">加载题目中...</span>
        </div>
      </div>
    );
  }

  if (!problem) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <div className="w-16 h-16 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
          <XCircle className="w-8 h-8 text-muted-foreground" />
        </div>
        <p className="text-muted-foreground font-medium">题目不存在</p>
        <button onClick={() => router.push("/problems")} className="text-primary hover:underline text-sm">返回题目列表</button>
      </div>
    );
  }

  const diffConfig = difficultyConfig[problem.difficulty] || difficultyConfig.medium;
  const samples = problem.samples || problem.examples || [];

  const problemHeader = (
    <div className="shrink-0 px-3 py-2 border-b border-gray-200 dark:border-gray-700/80 bg-white dark:bg-gray-950">
      <div className="flex items-center gap-2 mb-1.5">
        <button
          onClick={() => router.push("/problems")}
          className="flex items-center gap-0.5 text-[11px] text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-3 h-3" />
          返回
        </button>
        <div className="flex-1" />
        <div className="flex items-center gap-3 text-[11px] text-muted-foreground">
          <span className="flex items-center gap-0.5" title="通过率">
            <CheckCircle2 className="w-3 h-3" />
            {problem.acceptanceRate != null ? `${problem.acceptanceRate}%` : "-"}
          </span>
          <span className="flex items-center gap-0.5" title="提交次数">
            <Code2 className="w-3 h-3" />
            {problem.submitCount || 0}
          </span>
          {problem.timeLimitMs && (
            <span className="flex items-center gap-0.5" title="时间限制">
              <Timer className="w-2.5 h-2.5" />
              {problem.timeLimitMs}ms
            </span>
          )}
          {problem.memoryLimitMb && (
            <span className="flex items-center gap-0.5" title="内存限制">
              <HardDrive className="w-2.5 h-2.5" />
              {problem.memoryLimitMb}MB
            </span>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 min-w-0">
        <h1 className="text-base font-bold truncate">{problem.title}</h1>
        <span className={cn(
          "shrink-0 px-1.5 py-px rounded text-[10px] font-semibold border",
          diffConfig.bgColor, diffConfig.borderColor, diffConfig.color
        )}>
          {diffConfig.label}
        </span>
        {problem.tags?.slice(0, 3).map((tag) => (
          <span key={tag} className="hidden sm:inline-flex items-center gap-0.5 px-1.5 py-px rounded text-[10px] bg-gray-100 dark:bg-gray-800 text-muted-foreground border border-gray-200 dark:border-gray-700 shrink-0">
            <Tag className="w-2.5 h-2.5" />
            {tag}
          </span>
        ))}
        {problem.tags && problem.tags.length > 3 && (
          <span className="hidden sm:inline text-[10px] text-muted-foreground">+{problem.tags.length - 3}</span>
        )}
      </div>
    </div>
  );

  const tabNav = (
    <div className="shrink-0 flex items-center gap-0.5 px-3 py-1.5 border-b border-gray-200 dark:border-gray-700/80 bg-gray-50/50 dark:bg-gray-900/30">
      {(["description", "submissions"] as const).map((tab) => (
        <button
          key={tab}
          onClick={() => setActiveTab(tab)}
          className={cn(
            "px-3 py-1 rounded-md text-[11px] font-medium transition-all",
            activeTab === tab
              ? "bg-white dark:bg-gray-800 text-foreground shadow-sm border border-gray-200 dark:border-gray-700"
              : "text-muted-foreground hover:text-foreground border border-transparent"
          )}
        >
          {tab === "description" ? "题面" : "提交记录"}
        </button>
      ))}
    </div>
  );

  const contentArea = (
    <div className="flex-1 overflow-y-auto px-3 py-2.5 space-y-3">
      {activeTab === "description" ? (
        <>
          <section className="space-y-0.5">
            <h2 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1">
              <FileText className="w-3 h-3 text-primary" />
              题目描述
            </h2>
            <div className="text-[13px] leading-relaxed">
              <MarkdownRenderer content={problem.description || ""} />
            </div>
          </section>

          {problem.inputDescription && (
            <section className="space-y-0.5">
              <h2 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1">
                <Terminal className="w-3 h-3 text-blue-500" />
                输入描述
              </h2>
              <div className="text-xs text-muted-foreground leading-relaxed">
                <MarkdownRenderer content={problem.inputDescription} />
              </div>
            </section>
          )}

          {problem.outputDescription && (
            <section className="space-y-0.5">
              <h2 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1">
                <Terminal className="w-3 h-3 text-emerald-500" />
                输出描述
              </h2>
              <div className="text-xs text-muted-foreground leading-relaxed">
                <MarkdownRenderer content={problem.outputDescription} />
              </div>
            </section>
          )}

          {samples.length > 0 && (
            <section className="space-y-1.5">
              <h2 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1">
                <Code2 className="w-3 h-3 text-violet-500" />
                样例
              </h2>
              <div className="space-y-1.5">
                {samples.map((sample, i) => (
                  <SampleCard key={sample.id || i} sample={sample} index={i} />
                ))}
              </div>
            </section>
          )}

          {problem.hint && (
            <section>
              <button
                onClick={() => setHintExpanded(!hintExpanded)}
                className="flex items-center gap-1 text-[11px] font-semibold text-amber-600 dark:text-amber-400 hover:underline"
              >
                <Lightbulb className="w-3 h-3" />
                提示
                {hintExpanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
              </button>
              {hintExpanded && (
                <div className="mt-1 p-2 rounded-md bg-amber-50/60 dark:bg-amber-950/20 border border-amber-200/80 dark:border-amber-800/60 text-xs leading-relaxed">
                  <MarkdownRenderer content={problem.hint} />
                </div>
              )}
            </section>
          )}

          {problem.source && (
            <section className="pt-2 border-t border-gray-100 dark:border-gray-800">
              <span className="text-[10px] text-muted-foreground/70">来源: {problem.source}</span>
            </section>
          )}
        </>
      ) : (
        <div className="space-y-1.5">
          {submissionsLoading ? (
            <div className="space-y-1.5">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="rounded-md border border-gray-200 dark:border-gray-700 p-2.5 animate-pulse">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2.5">
                      <div className="h-4 w-14 rounded bg-gray-200 dark:bg-gray-700" />
                      <div className="h-3.5 w-9 rounded bg-gray-200 dark:bg-gray-700" />
                      <div className="h-3.5 w-14 rounded bg-gray-200 dark:bg-gray-700" />
                    </div>
                    <div className="flex items-center gap-2.5">
                      <div className="h-3.5 w-10 rounded bg-gray-200 dark:bg-gray-700" />
                      <div className="h-3.5 w-12 rounded bg-gray-200 dark:bg-gray-700" />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : submissions.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-muted-foreground gap-2">
              <Code2 className="w-7 h-7 text-gray-300 dark:text-gray-600" />
              <p className="text-xs">暂无提交记录</p>
              <p className="text-[10px] text-muted-foreground/60">提交代码后，记录将在此显示</p>
            </div>
          ) : (
            <>
              {submissions.map((sub) => {
                const config = getSubmissionStatusConfig(sub.status);
                const isExpanded = expandedSubmissionId === sub.id;
                return (
                  <div
                    key={sub.id}
                    className="rounded-md border border-gray-200 dark:border-gray-700/80 overflow-hidden"
                  >
                    <button
                      onClick={() => setExpandedSubmissionId(isExpanded ? null : sub.id)}
                      className="w-full flex items-center justify-between px-2.5 py-1.5 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors text-left"
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <span className={cn(
                          "inline-flex items-center gap-0.5 px-1.5 py-px rounded text-[10px] font-semibold border",
                          config.bgColor, config.borderColor, config.color
                        )}>
                          {config.icon}
                          {config.label}
                        </span>
                        <span className="px-1 py-px rounded text-[10px] bg-gray-100 dark:bg-gray-800 text-muted-foreground border border-gray-200 dark:border-gray-700 shrink-0">
                          {formatLanguage(sub.language)}
                        </span>
                        <span className="flex items-center gap-0.5 text-[10px] text-muted-foreground shrink-0">
                          <Timer className="w-2.5 h-2.5" />
                          {formatTime(sub.totalTimeMs)}
                        </span>
                        <span className="flex items-center gap-0.5 text-[10px] text-muted-foreground shrink-0">
                          <HardDrive className="w-2.5 h-2.5" />
                          {formatMemory(sub.maxMemoryKb)}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        {sub.passedCount !== undefined && sub.totalCount !== undefined && (
                          <span className={cn(
                            "text-[10px] font-medium",
                            sub.passedCount === sub.totalCount
                              ? "text-emerald-600 dark:text-emerald-400"
                              : "text-red-600 dark:text-red-400"
                          )}>
                            {sub.passedCount}/{sub.totalCount}
                          </span>
                        )}
                        <span className="text-[10px] text-muted-foreground/60" title={new Date(sub.createdAt).toLocaleString()}>
                          {formatRelativeTime(sub.createdAt)}
                        </span>
                        {isExpanded ? <ChevronUp className="w-3 h-3 text-muted-foreground" /> : <ChevronDown className="w-3 h-3 text-muted-foreground" />}
                      </div>
                    </button>
                    {isExpanded && (
                      <div className="border-t border-gray-200 dark:border-gray-700/80 p-2.5 space-y-1.5">
                        {sub.code && (
                          <div>
                            <span className="text-[10px] font-semibold text-muted-foreground/70 uppercase tracking-wider">提交代码</span>
                            <pre className="mt-0.5 p-2 rounded-md bg-gray-50 dark:bg-gray-900/80 border border-gray-100 dark:border-gray-800 text-[11px] font-mono overflow-x-auto whitespace-pre-wrap max-h-48 overflow-y-auto leading-relaxed">
                              {sub.code}
                            </pre>
                          </div>
                        )}
                        {sub.errorMessage && (
                          <div>
                            <span className="text-[10px] font-semibold text-red-600 dark:text-red-400 uppercase tracking-wider">错误信息</span>
                            <div className="mt-0.5 p-2 rounded-md bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-800">
                              <pre className="text-[11px] font-mono text-red-600 dark:text-red-400 whitespace-pre-wrap leading-relaxed">
                                {sub.errorMessage}
                              </pre>
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
              {(() => {
                const totalPages = Math.ceil(submissionsTotal / 10);
                if (totalPages <= 1) return null;
                return (
                  <div className="flex items-center justify-center gap-1.5 pt-1.5">
                    <button
                      onClick={() => setSubmissionsPage(p => Math.max(1, p - 1))}
                      disabled={submissionsPage <= 1}
                      className="inline-flex items-center justify-center h-6 w-6 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 text-muted-foreground hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                    >
                      <ChevronLeft className="w-3 h-3" />
                    </button>
                    <span className="text-[10px] text-muted-foreground tabular-nums">
                      {submissionsPage} / {totalPages}
                    </span>
                    <button
                      onClick={() => setSubmissionsPage(p => Math.min(totalPages, p + 1))}
                      disabled={submissionsPage >= totalPages}
                      className="inline-flex items-center justify-center h-6 w-6 rounded border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 text-muted-foreground hover:bg-gray-50 dark:hover:bg-gray-800 disabled:opacity-40 disabled:pointer-events-none transition-colors"
                    >
                      <ChevronRight className="w-3 h-3" />
                    </button>
                  </div>
                );
              })()}
            </>
          )}
        </div>
      )}
    </div>
  );

  const editorPanel = (
    <OJCodeEditor
      problemId={problemId}
      templateCode={problem.templateCode}
      onRunTest={handleRunTest}
      onSubmitCode={handleSubmitCode}
      submissionResult={trackedSubmission}
      isSubmissionTracking={isSubmissionTracking}
    />
  );

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="flex flex-col lg:flex-row w-full h-[calc(100vh-4rem)] overflow-hidden"
    >
      <div className="flex items-center gap-1 px-3 py-1.5 border-b border-gray-200 dark:border-gray-700/80 bg-white dark:bg-gray-950 lg:hidden">
        <button
          onClick={() => router.push("/problems")}
          className="flex items-center gap-0.5 text-[11px] text-muted-foreground hover:text-foreground transition-colors mr-2"
        >
          <ArrowLeft className="w-3 h-3" />
          返回
        </button>
        <div className="flex-1 min-w-0">
          <span className="text-sm font-bold truncate block">{problem.title}</span>
        </div>
        <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-gray-100 dark:bg-gray-800">
          <button
            onClick={() => setMobileView("problem")}
            className={cn(
              "px-3 py-1 rounded-md text-[11px] font-medium transition-all",
              mobileView === "problem"
                ? "bg-white dark:bg-gray-700 text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            题目
          </button>
          <button
            onClick={() => setMobileView("editor")}
            className={cn(
              "px-3 py-1 rounded-md text-[11px] font-medium transition-all",
              mobileView === "editor"
                ? "bg-white dark:bg-gray-700 text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            )}
          >
            代码
          </button>
        </div>
      </div>

      <div className={cn(
        "flex-col border-r border-gray-200 dark:border-gray-700/80 bg-white dark:bg-gray-950",
        "lg:w-[45%] lg:min-w-[400px] lg:flex",
        mobileView === "problem" ? "flex flex-1" : "hidden"
      )}>
        {problemHeader}
        {tabNav}
        {contentArea}
      </div>

      <div className={cn(
        "min-w-0",
        "lg:flex-1 lg:flex",
        mobileView === "editor" ? "flex flex-1" : "hidden lg:flex"
      )}>
        {editorPanel}
      </div>
    </motion.div>
  );
}

"use client";

import { useState, useEffect, useCallback } from "react";
import { motion } from "framer-motion";
import { CheckCircle2, XCircle, Clock, MemoryStick, AlertTriangle, Hourglass, Loader2, ChevronDown, ChevronUp } from "lucide-react";
import { problemService, Submission } from "@/services/problemService";
import { useAuthStore } from "@/stores/authStore";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const statusConfig: Record<string, { label: string; color: string; bgColor: string; icon: React.ReactNode }> = {
  accepted: { label: "通过", color: "text-emerald-600 dark:text-emerald-400", bgColor: "bg-emerald-50 dark:bg-emerald-950/30", icon: <CheckCircle2 className="w-4 h-4" /> },
  partial_accepted: { label: "部分通过", color: "text-sky-600 dark:text-sky-400", bgColor: "bg-sky-50 dark:bg-sky-950/30", icon: <CheckCircle2 className="w-4 h-4" /> },
  wrong_answer: { label: "答案错误", color: "text-red-600 dark:text-red-400", bgColor: "bg-red-50 dark:bg-red-950/30", icon: <XCircle className="w-4 h-4" /> },
  time_limit_exceeded: { label: "时间超限", color: "text-amber-600 dark:text-amber-400", bgColor: "bg-amber-50 dark:bg-amber-950/30", icon: <Clock className="w-4 h-4" /> },
  memory_limit_exceeded: { label: "内存超限", color: "text-orange-600 dark:text-orange-400", bgColor: "bg-orange-50 dark:bg-orange-950/30", icon: <MemoryStick className="w-4 h-4" /> },
  runtime_error: { label: "运行错误", color: "text-purple-600 dark:text-purple-400", bgColor: "bg-purple-50 dark:bg-purple-950/30", icon: <AlertTriangle className="w-4 h-4" /> },
  compilation_error: { label: "编译错误", color: "text-yellow-800 dark:text-yellow-400", bgColor: "bg-yellow-50 dark:bg-yellow-950/30", icon: <AlertTriangle className="w-4 h-4" /> },
  system_error: { label: "系统错误", color: "text-gray-600 dark:text-gray-400", bgColor: "bg-gray-50 dark:bg-gray-950/30", icon: <AlertTriangle className="w-4 h-4" /> },
  pending: { label: "等待中", color: "text-blue-600 dark:text-blue-400", bgColor: "bg-blue-50 dark:bg-blue-950/30", icon: <Hourglass className="w-4 h-4" /> },
  judging: { label: "判题中", color: "text-blue-600 dark:text-blue-400", bgColor: "bg-blue-50 dark:bg-blue-950/30", icon: <Loader2 className="w-4 h-4 animate-spin" /> },
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
  return `${(kb / 1024).toFixed(2)}MB`;
}

function formatLanguage(lang: string) {
  const map: Record<string, string> = { java: "Java", cpp: "C++", c: "C" };
  return map[lang] || lang;
}

export default function SubmissionsPage() {
  const { user } = useAuthStore();
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [languageFilter, setLanguageFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const fetchSubmissions = useCallback(async () => {
    setLoading(true);
    try {
      const params: any = { page, size: pageSize };
      if (user?.id) params.userId = user.id;
      if (languageFilter !== "all") params.language = languageFilter;
      if (statusFilter !== "all") params.status = statusFilter;
      const res = await problemService.getSubmissionList(params);
      if (res.data) {
        setSubmissions(res.data.items || []);
        setTotal(res.data.total || 0);
      }
    } catch (err) {
      console.error("Failed to fetch submissions:", err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, languageFilter, statusFilter, user?.id]);

  useEffect(() => {
    fetchSubmissions();
  }, [fetchSubmissions]);

  const totalPages = Math.ceil(total / pageSize);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="p-6 space-y-4"
    >
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">提交记录</h1>
        <div className="flex items-center gap-3">
          <Select value={languageFilter} onValueChange={(v) => { setLanguageFilter(v); setPage(1); }}>
            <SelectTrigger className="w-28 h-8 text-xs">
              <SelectValue placeholder="语言" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部语言</SelectItem>
              <SelectItem value="java">Java</SelectItem>
              <SelectItem value="cpp">C++</SelectItem>
              <SelectItem value="c">C</SelectItem>
            </SelectContent>
          </Select>
          <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); setPage(1); }}>
            <SelectTrigger className="w-28 h-8 text-xs">
              <SelectValue placeholder="状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              <SelectItem value="accepted">通过</SelectItem>
              <SelectItem value="wrong_answer">答案错误</SelectItem>
              <SelectItem value="time_limit_exceeded">时间超限</SelectItem>
              <SelectItem value="memory_limit_exceeded">内存超限</SelectItem>
              <SelectItem value="runtime_error">运行错误</SelectItem>
              <SelectItem value="compilation_error">编译错误</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
        </div>
      ) : submissions.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">暂无提交记录</div>
      ) : (
        <div className="space-y-2">
          {submissions.map((sub) => {
            const config = getStatusConfig(sub.status);
            const isExpanded = expandedId === sub.id;
            return (
              <div
                key={sub.id}
                className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden"
              >
                <button
                  onClick={() => setExpandedId(isExpanded ? null : sub.id)}
                  className="w-full flex items-center justify-between px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors"
                >
                  <div className="flex items-center gap-4">
                    <span className={cn("flex items-center gap-1.5 text-sm font-medium", config.color)}>
                      {config.icon}
                      {config.label}
                    </span>
                    <span className="text-sm text-muted-foreground">
                      {sub.problemTitle || `题目 #${sub.problemId}`}
                    </span>
                    <span className="px-1.5 py-0.5 rounded text-xs bg-gray-100 dark:bg-gray-800 text-muted-foreground">
                      {formatLanguage(sub.language)}
                    </span>
                  </div>
                  <div className="flex items-center gap-4 text-xs text-muted-foreground">
                    {sub.totalTimeMs !== undefined && (
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        {formatTime(sub.totalTimeMs)}
                      </span>
                    )}
                    {sub.maxMemoryKb !== undefined && (
                      <span className="flex items-center gap-1">
                        <MemoryStick className="w-3 h-3" />
                        {formatMemory(sub.maxMemoryKb)}
                      </span>
                    )}
                    <span>{new Date(sub.createdAt).toLocaleString()}</span>
                    {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  </div>
                </button>
                {isExpanded && sub.code && (
                  <div className="border-t border-gray-200 dark:border-gray-700 p-4">
                    <pre className="p-3 rounded-lg bg-gray-100 dark:bg-gray-800 text-xs font-mono overflow-x-auto whitespace-pre-wrap max-h-64 overflow-y-auto">
                      {sub.code}
                    </pre>
                    {sub.errorMessage && (
                      <div className="mt-2 p-3 rounded-lg bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-800">
                        <pre className="text-xs font-mono text-red-600 dark:text-red-400 whitespace-pre-wrap">
                          {sub.errorMessage}
                        </pre>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 pt-4">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 1}
            onClick={() => setPage(page - 1)}
          >
            上一页
          </Button>
          <span className="text-sm text-muted-foreground">
            {page} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages}
            onClick={() => setPage(page + 1)}
          >
            下一页
          </Button>
        </div>
      )}
    </motion.div>
  );
}

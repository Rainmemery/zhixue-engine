"use client";

import { useState, useEffect, useCallback } from "react";
import { motion } from "framer-motion";
import {
  Plus, Edit, Trash2, RefreshCw, Search, Tags, FolderOpen,
  FileText, Eye, EyeOff, ChevronDown
} from "lucide-react";
import { useRouter } from "next/navigation";
import { ProblemAdminService, ProblemCategory, CreateProblemRequest, UpdateProblemRequest } from "@/services/problemAdminService";
import { Problem } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

const DifficultyConfig: Record<string, { label: string; color: string; bgColor: string }> = {
  easy: { label: "简单", color: "text-emerald-600 dark:text-emerald-400", bgColor: "bg-emerald-50 dark:bg-emerald-950/30" },
  medium: { label: "中等", color: "text-amber-600 dark:text-amber-400", bgColor: "bg-amber-50 dark:bg-amber-950/30" },
  hard: { label: "困难", color: "text-red-600 dark:text-red-400", bgColor: "bg-red-50 dark:bg-red-950/30" },
};

export default function ProblemsManagementPage() {
  const router = useRouter();
  const [problems, setProblems] = useState<Problem[]>([]);
  const [categories, setCategories] = useState<ProblemCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [searchParams, setSearchParams] = useState({
    search: "",
    difficulty: "",
    categoryId: "",
  });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchProblems = useCallback(async () => {
    try {
      setLoading(true);
      const params: any = {
        page: pagination.current,
        size: pagination.pageSize,
      };
      if (searchParams.search) params.search = searchParams.search;
      if (searchParams.difficulty) params.difficulty = searchParams.difficulty;
      if (searchParams.categoryId) params.categoryId = searchParams.categoryId;

      const data = await ProblemAdminService.getProblems(params);
      if (data && data.items) {
        setProblems(data.items);
        setPagination((prev) => ({ ...prev, total: data.total || 0 }));
      } else {
        setProblems([]);
        setPagination((prev) => ({ ...prev, total: 0 }));
      }
    } catch (error) {
      console.error("获取题目列表失败:", error);
      setProblems([]);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchParams]);

  const fetchCategories = useCallback(async () => {
    try {
      const data = await ProblemAdminService.getCategories();
      if (data) setCategories(data);
    } catch (error) {
      console.error("获取分类列表失败:", error);
    }
  }, []);

  useEffect(() => {
    fetchProblems();
    fetchCategories();
  }, [fetchProblems, fetchCategories]);

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    fetchProblems();
  };

  const handleReset = () => {
    setSearchParams({ search: "", difficulty: "", categoryId: "" });
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleDelete = async (id: number) => {
    if (!confirm("确定要删除此题目吗？")) return;
    try {
      setLoading(true);
      await ProblemAdminService.deleteProblem(id);
      fetchProblems();
    } catch (error) {
      console.error("删除题目失败:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedIds.size === 0) return;
    if (!confirm(`确定要删除选中的 ${selectedIds.size} 个题目吗？`)) return;
    try {
      setLoading(true);
      await ProblemAdminService.batchDeleteProblems(Array.from(selectedIds));
      setSelectedIds(new Set());
      fetchProblems();
    } catch (error) {
      console.error("批量删除失败:", error);
    } finally {
      setLoading(false);
    }
  };

  const toggleSelect = (id: number) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === problems.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(problems.map(p => p.id)));
    }
  };

  const totalPages = Math.ceil(pagination.total / pagination.pageSize);

  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <FileText className="w-6 h-6 text-primary" />
            题目管理
          </h1>
          <p className="text-sm text-muted-foreground mt-1">管理编程题目，支持CRUD操作</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => router.push("/admin/problems/categories")}>
            <FolderOpen className="w-4 h-4 mr-1" />
            分类
          </Button>
          <Button variant="outline" size="sm" onClick={() => router.push("/admin/problems/tags")}>
            <Tags className="w-4 h-4 mr-1" />
            标签
          </Button>
          <Button variant="outline" size="sm" onClick={() => fetchProblems()}>
            <RefreshCw className={cn("w-4 h-4 mr-1", loading && "animate-spin")} />
            刷新
          </Button>
          <Button size="sm" onClick={() => router.push("/admin/problems/new")}>
            <Plus className="w-4 h-4 mr-1" />
            新建题目
          </Button>
        </div>
      </div>

      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950 overflow-hidden">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900/50">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[200px] max-w-[300px]">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="搜索题目..."
                value={searchParams.search}
                onChange={(e) => setSearchParams((prev) => ({ ...prev, search: e.target.value }))}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
                className="pl-8 h-9"
              />
            </div>
            <select
              value={searchParams.difficulty}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, difficulty: e.target.value }))}
              className="h-9 px-3 rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950 text-sm"
            >
              <option value="">全部难度</option>
              <option value="easy">简单</option>
              <option value="medium">中等</option>
              <option value="hard">困难</option>
            </select>
            <select
              value={searchParams.categoryId}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, categoryId: e.target.value }))}
              className="h-9 px-3 rounded-md border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-950 text-sm"
            >
              <option value="">全部分类</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </select>
            <Button size="sm" onClick={handleSearch}>
              <Search className="w-4 h-4 mr-1" />
              搜索
            </Button>
            <Button variant="outline" size="sm" onClick={handleReset}>
              重置
            </Button>
            {selectedIds.size > 0 && (
              <Button variant="destructive" size="sm" onClick={handleBatchDelete}>
                <Trash2 className="w-4 h-4 mr-1" />
                删除 ({selectedIds.size})
              </Button>
            )}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50">
                <th className="w-10 px-3 py-3 text-left">
                  <input
                    type="checkbox"
                    checked={selectedIds.size === problems.length && problems.length > 0}
                    onChange={toggleSelectAll}
                    className="rounded border-gray-300"
                  />
                </th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">ID</th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">标题</th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">难度</th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">分类</th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">标签</th>
                <th className="px-3 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">通过率</th>
                <th className="px-3 py-3 text-right text-xs font-semibold text-muted-foreground uppercase tracking-wider">提交数</th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-muted-foreground uppercase tracking-wider">状态</th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-muted-foreground uppercase tracking-wider">创建时间</th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-muted-foreground uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={11} className="px-3 py-12 text-center text-muted-foreground">
                    <div className="flex items-center justify-center gap-2">
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      加载中...
                    </div>
                  </td>
                </tr>
              ) : problems.length === 0 ? (
                <tr>
                  <td colSpan={11} className="px-3 py-12 text-center text-muted-foreground">
                    暂无题目数据
                  </td>
                </tr>
              ) : (
                problems.map((problem) => {
                  const diffConfig = DifficultyConfig[problem.difficulty] || DifficultyConfig.medium;
                  const tagsArray: string[] = Array.isArray(problem.tags)
                    ? problem.tags as string[]
                    : typeof problem.tags === "string"
                      ? (problem.tags as string).split(",").map((t) => t.trim()).filter(Boolean)
                      : [];
                  const isSelected = selectedIds.has(problem.id);

                  return (
                    <tr
                      key={problem.id}
                      className={cn(
                        "border-b border-gray-100 dark:border-gray-800 transition-colors hover:bg-gray-50 dark:hover:bg-gray-900/50",
                        isSelected && "bg-primary/5 dark:bg-primary/10"
                      )}
                    >
                      <td className="px-3 py-3">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleSelect(problem.id)}
                          className="rounded border-gray-300"
                        />
                      </td>
                      <td className="px-3 py-3 text-sm text-muted-foreground">{problem.id}</td>
                      <td className="px-3 py-3">
                        <button
                          onClick={() => router.push(`/admin/problems/${problem.id}`)}
                          className="text-sm font-medium hover:text-primary transition-colors text-left"
                        >
                          {problem.title}
                          {problem.titleEn && (
                            <span className="block text-xs text-muted-foreground font-normal">{problem.titleEn}</span>
                          )}
                        </button>
                      </td>
                      <td className="px-3 py-3">
                        <span className={cn(
                          "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium",
                          diffConfig.bgColor, diffConfig.color
                        )}>
                          {diffConfig.label}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-sm text-muted-foreground">{problem.category || "-"}</td>
                      <td className="px-3 py-3">
                        <div className="flex flex-wrap gap-1">
                          {tagsArray.length > 0
                            ? tagsArray.slice(0, 3).map((tag: string) => (
                                <span key={tag} className="px-1.5 py-0.5 rounded text-xs bg-gray-100 dark:bg-gray-800 text-muted-foreground">
                                  {tag}
                                </span>
                              ))
                            : <span className="text-xs text-muted-foreground">-</span>
                          }
                          {tagsArray.length > 3 && (
                            <span className="text-xs text-muted-foreground">+{tagsArray.length - 3}</span>
                          )}
                        </div>
                      </td>
                      <td className="px-3 py-3 text-right text-sm">
                        <span className={cn(
                          "font-medium",
                          (problem.acceptanceRate ?? 0) >= 50 ? "text-emerald-600 dark:text-emerald-400" : "text-amber-600 dark:text-amber-400"
                        )}>
                          {problem.acceptanceRate != null ? `${(problem.acceptanceRate ?? 0).toFixed(1)}%` : "-"}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-right text-sm text-muted-foreground">{problem.submitCount || 0}</td>
                      <td className="px-3 py-3 text-center">
                        <span className={cn(
                          "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium",
                          problem.isActive
                            ? "bg-emerald-50 dark:bg-emerald-950/30 text-emerald-600 dark:text-emerald-400"
                            : "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400"
                        )}>
                          {problem.isActive ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                          {problem.isActive ? "公开" : "隐藏"}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-sm text-muted-foreground">
                        {problem.createdAt ? new Date(problem.createdAt).toLocaleDateString("zh-CN") : "-"}
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex items-center justify-center gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => router.push(`/admin/problems/${problem.id}`)}
                            className="h-7 px-2"
                          >
                            <Edit className="w-3.5 h-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDelete(problem.id)}
                            className="h-7 px-2 text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/20"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {pagination.total > 0 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900/50">
            <span className="text-xs text-muted-foreground">
              共 {pagination.total} 条记录，第 {pagination.current}/{totalPages} 页
            </span>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="sm"
                disabled={pagination.current <= 1}
                onClick={() => setPagination((prev) => ({ ...prev, current: prev.current - 1 }))}
                className="h-7 px-2"
              >
                上一页
              </Button>
              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                let page: number;
                if (totalPages <= 5) {
                  page = i + 1;
                } else if (pagination.current <= 3) {
                  page = i + 1;
                } else if (pagination.current >= totalPages - 2) {
                  page = totalPages - 4 + i;
                } else {
                  page = pagination.current - 2 + i;
                }
                return (
                  <Button
                    key={page}
                    variant={page === pagination.current ? "default" : "outline"}
                    size="sm"
                    onClick={() => setPagination((prev) => ({ ...prev, current: page }))}
                    className="h-7 w-7 p-0"
                  >
                    {page}
                  </Button>
                );
              })}
              <Button
                variant="outline"
                size="sm"
                disabled={pagination.current >= totalPages}
                onClick={() => setPagination((prev) => ({ ...prev, current: prev.current + 1 }))}
                className="h-7 px-2"
              >
                下一页
              </Button>
            </div>
          </div>
        )}
      </div>
    </motion.div>
  );
}

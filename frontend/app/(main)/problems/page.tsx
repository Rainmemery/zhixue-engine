"use client";

import { useState, useEffect, useCallback, memo } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import { Search, Filter, Code2, CheckCircle, Clock, BookOpen, ArrowRight, Sparkles, Tag, ChevronLeft, ChevronRight } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { SkeletonList } from "@/components/ui/skeleton";
import { problemService, Problem, ProblemCategory, ProblemTag } from "@/services/problemService";
import { useToast } from "@/hooks/use-toast";
import Link from "next/link";

const difficulties = ["全部", "简单", "中等", "困难"];
const problemTypes = [
  { value: "全部", label: "全部类型" },
  { value: "traditional", label: "传统题" },
  { value: "interactive", label: "交互题" },
  { value: "special_judge", label: "特殊判断" },
];
const sortOptions = [
  { value: "latest", label: "最新" },
  { value: "most_submissions", label: "最多提交" },
  { value: "highest_acceptance", label: "最高通过率" },
];

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05, delayChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 15 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.4, ease: "easeOut" as const },
  },
};

const UserStatusIcon = memo(({ status }: { status?: string }) => {
  if (status === "accepted") {
    return (
      <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-emerald-500">
        <CheckCircle className="w-3.5 h-3.5 text-white" />
      </span>
    );
  }
  if (status === "attempted") {
    return (
      <span className="inline-flex items-center justify-center w-5 h-5 rounded-full bg-amber-400">
        <span className="block w-2 h-2 rounded-full bg-white" />
      </span>
    );
  }
  return null;
});
UserStatusIcon.displayName = "UserStatusIcon";

const ProblemCard = memo(({ problem, index }: { problem: Problem; index: number }) => {
  const getDifficultyColor = (difficulty: string) => {
    const diff = difficulty?.toLowerCase();
    if (diff === "easy" || diff === "简单") {
      return "bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800";
    }
    if (diff === "medium" || diff === "中等") {
      return "bg-amber-100 text-amber-700 border-amber-200 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-800";
    }
    if (diff === "hard" || diff === "困难") {
      return "bg-red-100 text-red-700 border-red-200 dark:bg-red-900/30 dark:text-red-400 dark:border-red-800";
    }
    return "bg-muted text-muted-foreground border-border";
  };

  const getDifficultyLabel = (difficulty: string) => {
    const diff = difficulty?.toLowerCase();
    if (diff === "easy") return "简单";
    if (diff === "medium") return "中等";
    if (diff === "hard") return "困难";
    return difficulty;
  };

  return (
    <motion.div
      variants={itemVariants}
      whileHover={{ y: -2, transition: { duration: 0.2 } }}
    >
      <Link href={`/problems/${problem.id}`}>
        <Card className="border-0 shadow-sm hover:shadow-lg transition-all duration-300 cursor-pointer group overflow-hidden">
          <div className={`absolute top-0 left-0 w-1 h-full ${
            problem.userStatus === "accepted"
              ? "bg-gradient-to-b from-emerald-500 to-emerald-600"
              : problem.userStatus === "attempted"
              ? "bg-gradient-to-b from-amber-400 to-amber-500"
              : problem.acceptedCount > 0
              ? "bg-gradient-to-b from-emerald-500 to-emerald-600"
              : "bg-gradient-to-b from-primary to-accent"
          }`} />
          <CardContent className="p-5 pl-6">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-4 min-w-0 flex-1">
                <div className="flex items-center gap-2 shrink-0">
                  <UserStatusIcon status={problem.userStatus} />
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                    problem.userStatus === "accepted"
                      ? "bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30"
                      : problem.userStatus === "attempted"
                      ? "bg-amber-100 text-amber-600 dark:bg-amber-900/30"
                      : problem.acceptedCount > 0
                      ? "bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30"
                      : "bg-primary/10 text-primary"
                  }`}>
                    {problem.userStatus === "accepted" || problem.acceptedCount > 0 ? (
                      <CheckCircle className="w-5 h-5" />
                    ) : (
                      <Code2 className="w-5 h-5" />
                    )}
                  </div>
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="font-semibold text-foreground group-hover:text-primary transition-colors truncate">
                    {problem.id}. {problem.title}
                  </h3>
                  <div className="flex items-center gap-3 mt-1.5 text-sm text-muted-foreground flex-wrap">
                    <span className="flex items-center gap-1">
                      <BookOpen className="w-3.5 h-3.5" />
                      {problem.categoryName || problem.category}
                    </span>
                    <span className="flex items-center gap-1">
                      <CheckCircle className="w-3.5 h-3.5" />
                      {Math.round(problem.acceptanceRate || 0)}%
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" />
                      {(problem.submitCount || 0).toLocaleString()} 次提交
                    </span>
                    {problem.problemType && problem.problemType !== "traditional" && (
                      <Badge variant="outline" className="text-xs py-0 px-1.5">
                        {problem.problemType === "interactive" ? "交互题" : problem.problemType === "special_sudge" ? "特判" : problem.problemType}
                      </Badge>
                    )}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-3 shrink-0">
                <Badge variant="outline" className={`${getDifficultyColor(problem.difficulty)} text-xs`}>
                  {getDifficultyLabel(problem.difficulty)}
                </Badge>
                <Button size="sm" variant="ghost" className="opacity-0 group-hover:opacity-100 transition-opacity">
                  <ArrowRight className="w-4 h-4" />
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </Link>
    </motion.div>
  );
});
ProblemCard.displayName = "ProblemCard";

export default function ProblemsPage() {
  const { t } = useTranslation();
  const { toast } = useToast();

  const [problems, setProblems] = useState<Problem[]>([]);
  const [categories, setCategories] = useState<ProblemCategory[]>([]);
  const [tags, setTags] = useState<ProblemTag[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("全部");
  const [selectedDifficulty, setSelectedDifficulty] = useState("全部");
  const [selectedTagId, setSelectedTagId] = useState<number | undefined>(undefined);
  const [selectedProblemType, setSelectedProblemType] = useState("全部");
  const [selectedSort, setSelectedSort] = useState("latest");
  const [pageJump, setPageJump] = useState("");
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchProblems = useCallback(async (
    page = 1,
    pageSize = 10,
    search = "",
    category?: string,
    difficulty?: string,
    tagId?: number,
    problemType?: string,
    sort?: string
  ) => {
    try {
      setLoading(true);
      const params: any = { page, size: pageSize };
      if (search) params.search = search;
      if (category && category !== "全部") params.category = category;
      if (difficulty && difficulty !== "全部") {
        params.difficulty = difficulty === "简单" ? "easy" : difficulty === "中等" ? "medium" : "hard";
      }
      if (tagId) params.tagId = tagId;
      if (problemType && problemType !== "全部") params.problemType = problemType;
      if (sort) params.sort = sort;

      const response = await problemService.getProblems(params);

      if (response.code === 200) {
        setProblems(response.data.items);
        setPagination({
          current: page,
          pageSize,
          total: response.data.total,
        });
      }
    } catch (error) {
      console.error("获取题目列表失败:", error);
      toast({
        title: "加载失败",
        description: "无法获取题目列表，请稍后重试",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  const fetchCategories = useCallback(async () => {
    try {
      const response = await problemService.getCategories();
      if (response.code === 200) {
        setCategories(response.data);
      }
    } catch (error) {
      console.error("获取分类失败:", error);
    }
  }, []);

  const fetchTags = useCallback(async () => {
    try {
      const response = await problemService.getTags();
      if (response.code === 200) {
        setTags(response.data);
      }
    } catch (error) {
      console.error("获取标签失败:", error);
    }
  }, []);

  useEffect(() => {
    fetchProblems();
    fetchCategories();
    fetchTags();
  }, [fetchProblems, fetchCategories, fetchTags]);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchProblems(
        1,
        pagination.pageSize,
        searchQuery,
        selectedCategory,
        selectedDifficulty,
        selectedTagId,
        selectedProblemType,
        selectedSort
      );
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery, selectedCategory, selectedDifficulty, selectedTagId, selectedProblemType, selectedSort, fetchProblems, pagination.pageSize]);

  const handlePageChange = (page: number, pageSize?: number) => {
    fetchProblems(
      page,
      pageSize || pagination.pageSize,
      searchQuery,
      selectedCategory,
      selectedDifficulty,
      selectedTagId,
      selectedProblemType,
      selectedSort
    );
  };

  const handlePageJump = () => {
    const page = parseInt(pageJump);
    if (!isNaN(page) && page >= 1 && page <= totalPages) {
      handlePageChange(page);
      setPageJump("");
    }
  };

  const solvedCount = problems.filter((p) => p.userStatus === "accepted" || p.acceptedCount > 0).length;
  const totalPages = Math.ceil(pagination.total / pagination.pageSize);

  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const current = pagination.current;

    if (totalPages <= 7) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
      pages.push(1);
      if (current > 3) pages.push("...");
      const start = Math.max(2, current - 1);
      const end = Math.min(totalPages - 1, current + 1);
      for (let i = start; i <= end; i++) pages.push(i);
      if (current < totalPages - 2) pages.push("...");
      pages.push(totalPages);
    }
    return pages;
  };

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className="space-y-6"
    >
      <motion.div variants={itemVariants} className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {t("problems.title") || "题目列表"}
          </h1>
          <p className="text-muted-foreground mt-1">
            选择题目开始练习，提升编程技能
          </p>
        </div>
        <div className="flex gap-3 text-sm">
          <div className="flex items-center gap-2 px-4 py-2.5 bg-emerald-50 dark:bg-emerald-950/30 rounded-xl border border-emerald-200 dark:border-emerald-800">
            <CheckCircle className="w-4 h-4 text-emerald-600" />
            <span className="text-emerald-700 dark:text-emerald-400 font-medium">已解决: {solvedCount}</span>
          </div>
          <div className="flex items-center gap-2 px-4 py-2.5 bg-primary/5 dark:bg-primary/10 rounded-xl border border-primary/20">
            <Sparkles className="w-4 h-4 text-primary" />
            <span className="text-primary font-medium">总题目: {pagination.total}</span>
          </div>
        </div>
      </motion.div>

      <motion.div variants={itemVariants}>
        <Card className="border-0 shadow-sm hover:shadow-md transition-shadow duration-300">
          <CardContent className="p-5">
            <div className="flex flex-col gap-4">
              <div className="flex flex-col md:flex-row gap-4">
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                  <Input
                    placeholder={t("problems.search") || "搜索题目..."}
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-10 h-11 rounded-xl"
                  />
                </div>
                <div className="flex gap-2 flex-wrap">
                  <div className="relative">
                    <select
                      value={selectedCategory}
                      onChange={(e) => setSelectedCategory(e.target.value)}
                      className="px-4 py-2.5 pr-10 rounded-xl border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer h-11"
                    >
                      <option value="全部">全部分类</option>
                      {categories.map((cat) => (
                        <option key={cat.id} value={cat.name}>
                          {cat.name}
                        </option>
                      ))}
                    </select>
                    <Filter className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                  </div>
                  <div className="relative">
                    <select
                      value={selectedDifficulty}
                      onChange={(e) => setSelectedDifficulty(e.target.value)}
                      className="px-4 py-2.5 pr-10 rounded-xl border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer h-11"
                    >
                      {difficulties.map((diff) => (
                        <option key={diff} value={diff}>
                          {diff}
                        </option>
                      ))}
                    </select>
                    <Filter className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                  </div>
                  <div className="relative">
                    <select
                      value={selectedTagId ?? ""}
                      onChange={(e) => setSelectedTagId(e.target.value ? Number(e.target.value) : undefined)}
                      className="px-4 py-2.5 pr-10 rounded-xl border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer h-11"
                    >
                      <option value="">全部标签</option>
                      {tags.map((tag) => (
                        <option key={tag.id} value={tag.id}>
                          {tag.name}
                        </option>
                      ))}
                    </select>
                    <Tag className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                  </div>
                  <div className="relative">
                    <select
                      value={selectedProblemType}
                      onChange={(e) => setSelectedProblemType(e.target.value)}
                      className="px-4 py-2.5 pr-10 rounded-xl border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer h-11"
                    >
                      {problemTypes.map((pt) => (
                        <option key={pt.value} value={pt.value}>
                          {pt.label}
                        </option>
                      ))}
                    </select>
                    <Filter className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                  </div>
                  <div className="relative">
                    <select
                      value={selectedSort}
                      onChange={(e) => setSelectedSort(e.target.value)}
                      className="px-4 py-2.5 pr-10 rounded-xl border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary appearance-none cursor-pointer h-11"
                    >
                      {sortOptions.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                    <Filter className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground pointer-events-none" />
                  </div>
                </div>
              </div>
              {selectedTagId && tags.length > 0 && (
                <div className="flex items-center gap-2 flex-wrap">
                  {tags.filter(tag => tag.id === selectedTagId).map((tag) => (
                    <Badge
                      key={tag.id}
                      variant="outline"
                      className="cursor-pointer gap-1"
                      style={tag.color ? { borderColor: tag.color, color: tag.color } : undefined}
                      onClick={() => setSelectedTagId(undefined)}
                    >
                      {tag.name}
                      <span className="text-xs opacity-60">✕</span>
                    </Badge>
                  ))}
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      </motion.div>

      {loading ? (
        <SkeletonList count={5} />
      ) : problems.length === 0 ? (
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="text-center py-20 text-muted-foreground"
        >
          <div className="w-20 h-20 rounded-full bg-muted flex items-center justify-center mx-auto mb-4">
            <Code2 className="w-10 h-10 opacity-50" />
          </div>
          <p className="font-medium text-lg">暂无符合条件的题目</p>
          <p className="text-sm mt-1">尝试调整筛选条件</p>
        </motion.div>
      ) : (
        <div className="grid gap-3">
          {problems.map((problem, index) => (
            <ProblemCard key={problem.id} problem={problem} index={index} />
          ))}
        </div>
      )}

      {!loading && problems.length > 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex items-center justify-between pt-4"
        >
          <div className="text-sm text-muted-foreground">
            共 {pagination.total} 条，第 {pagination.current}/{totalPages} 页
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={pagination.current === 1}
              onClick={() => handlePageChange(pagination.current - 1)}
              className="rounded-lg"
            >
              <ChevronLeft className="w-4 h-4" />
            </Button>
            <div className="flex items-center gap-1">
              {getPageNumbers().map((page, idx) =>
                typeof page === "string" ? (
                  <span key={`ellipsis-${idx}`} className="px-2 text-muted-foreground">
                    ...
                  </span>
                ) : (
                  <Button
                    key={page}
                    variant={pagination.current === page ? "default" : "ghost"}
                    size="sm"
                    onClick={() => handlePageChange(page)}
                    className="w-9 h-9 rounded-lg"
                  >
                    {page}
                  </Button>
                )
              )}
            </div>
            <Button
              variant="outline"
              size="sm"
              disabled={pagination.current >= totalPages}
              onClick={() => handlePageChange(pagination.current + 1)}
              className="rounded-lg"
            >
              <ChevronRight className="w-4 h-4" />
            </Button>
            <div className="flex items-center gap-2 ml-4">
              <span className="text-sm text-muted-foreground">跳至</span>
              <Input
                value={pageJump}
                onChange={(e) => setPageJump(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handlePageJump();
                }}
                className="w-16 h-9 text-center rounded-lg"
              />
              <span className="text-sm text-muted-foreground">页</span>
              <Button
                variant="outline"
                size="sm"
                onClick={handlePageJump}
                className="rounded-lg"
              >
                跳转
              </Button>
            </div>
          </div>
        </motion.div>
      )}
    </motion.div>
  );
}

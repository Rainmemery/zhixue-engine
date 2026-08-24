"use client";

import { useState, useEffect, memo } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import {
  Code2,
  Flame,
  Star,
  Trophy,
  TrendingUp,
  Clock,
  Target,
  Zap,
  ArrowRight,
  BookOpen,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { SkeletonStats, SkeletonCard } from "@/components/ui/skeleton";
import { useAuthStore } from "@/stores/authStore";
import { userService } from "@/services/userService";
import { problemService, Problem } from "@/services/problemService";
import { learningService } from "@/services/learningService";
import { LearningPath, LearningRecord } from "@/types";
import { useToast } from "@/hooks/use-toast";
import Link from "next/link";
import { StaggerContainer, StaggerItem, FadeIn } from "@/components/ui/page-transition";

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08,
      delayChildren: 0.1,
    },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, ease: [0.43, 0.13, 0.23, 0.96] as const },
  },
};

interface DashboardData {
  stats: {
    solvedProblems: number;
    learningStreak: number;
    experiencePoints: number;
    achievements: number;
  } | null;
  recentRecords: LearningRecord[];
  recommendedProblems: Problem[];
  learningPaths: LearningPath[];
  loading: boolean;
  errors: {
    stats?: string;
    records?: string;
    problems?: string;
    paths?: string;
  };
}

// Memoized stat card component
const StatCard = memo(({ stat, index }: { stat: any; index: number }) => (
  <motion.div
    variants={itemVariants}
    whileHover={{ y: -4, transition: { duration: 0.2 } }}
    className="group"
  >
    <Card className="border-0 shadow-sm hover:shadow-lg transition-all duration-300 overflow-hidden relative">
      <div className={`absolute top-0 left-0 w-full h-1 bg-gradient-to-r ${stat.color}`} />
      <CardContent className="p-6">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <p className="text-sm font-medium text-muted-foreground">
              {stat.title}
            </p>
            <h3 className="text-3xl font-bold tracking-tight">
              {stat.value}
            </h3>
            <p className="text-sm text-emerald-600 dark:text-emerald-400 flex items-center font-medium">
              <TrendingUp className="w-3.5 h-3.5 mr-1" />
              {stat.trend}
            </p>
          </div>
          <div className={`p-3 rounded-xl bg-gradient-to-br ${stat.color} shadow-lg shadow-primary/20 group-hover:scale-110 transition-transform duration-300`}>
            <stat.icon className="w-6 h-6 text-white" />
          </div>
        </div>
      </CardContent>
    </Card>
  </motion.div>
));
StatCard.displayName = "StatCard";

// Memoized activity item
const ActivityItem = memo(({ record, index }: { record: LearningRecord; index: number }) => {
  const getStatusIcon = (status: string) => {
    switch (status) {
      case "solved": return Code2;
      case "attempted": return Zap;
      default: return Target;
    }
  };

  const getStatusText = (record: LearningRecord) => {
    switch (record.status) {
      case "solved":
        return { action: "解决了题目", target: `题目 #${record.problemId}`, color: "text-emerald-600 dark:text-emerald-400" };
      case "attempted":
        return { action: "尝试了题目", target: `题目 #${record.problemId}`, color: "text-amber-600 dark:text-amber-400" };
      default:
        return { action: "学习中", target: `题目 #${record.problemId}`, color: "text-muted-foreground" };
    }
  };

  const formatTimeAgo = (date: string) => {
    const now = new Date();
    const past = new Date(date);
    const diffMs = now.getTime() - past.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 60) return `${diffMins}分钟前`;
    if (diffHours < 24) return `${diffHours}小时前`;
    if (diffDays < 30) return `${diffDays}天前`;
    return past.toLocaleDateString("zh-CN");
  };

  const StatusIcon = getStatusIcon(record.status);
  const { action, target, color } = getStatusText(record);

  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.1, duration: 0.4, ease: "easeOut" }}
      className="flex items-center gap-4 p-4 rounded-xl bg-muted/50 hover:bg-muted transition-colors group cursor-pointer"
    >
      <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center shadow-md shadow-primary/20 group-hover:scale-110 transition-transform duration-200">
        <StatusIcon className="w-5 h-5 text-white" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-medium text-foreground truncate">
          {action} <span className={color}>{target}</span>
        </p>
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <span>得分: {record.score}%</span>
          <span>用时: {Math.round(record.timeSpentMs / 1000)}秒</span>
          <span>{formatTimeAgo(record.createdAt)}</span>
        </div>
      </div>
    </motion.div>
  );
});
ActivityItem.displayName = "ActivityItem";

export default function DashboardPage() {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const { toast } = useToast();
  const [data, setData] = useState<DashboardData>({
    stats: null,
    recentRecords: [],
    recommendedProblems: [],
    learningPaths: [],
    loading: true,
    errors: {},
  });

  useEffect(() => {
    const fetchDashboardData = async () => {
      if (!user?.id) {
        setData((prev) => ({ ...prev, loading: false }));
        return;
      }

      try {
        setData((prev) => ({ ...prev, loading: true, errors: {} }));

        const [userRes, recordsRes, problemsRes, pathsRes] = await Promise.all([
          userService.getUserById(user.id).catch((err) => ({
            error: err.message || "获取用户信息失败",
          })),
          learningService
            .getLearningRecords({
              userId: user.id,
              page: 1,
              size: 5,
            })
            .catch((err) => ({ error: err.message || "获取学习记录失败" })),
          problemService
            .getProblems({ page: 1, size: 3 })
            .catch((err) => ({ error: err.message || "获取推荐题目失败" })),
          learningService
            .getUserLearningPaths(user.id)
            .catch((err) => ({ error: err.message || "获取学习路径失败" })),
        ]);

        const errors: DashboardData["errors"] = {};
        let stats: DashboardData["stats"] = null;
        let recentRecords: LearningRecord[] = [];
        let recommendedProblems: Problem[] = [];
        let learningPaths: LearningPath[] = [];

        if ("error" in userRes) {
          errors.stats = userRes.error;
        } else {
          const userData = (userRes.data as any)?.data;
          if (userData) {
            stats = {
              solvedProblems: userData.experiencePoints
                ? Math.floor(userData.experiencePoints / 10)
                : 0,
              learningStreak: userData.dailyStreak || 0,
              experiencePoints: userData.experiencePoints || 0,
              achievements: 0,
            };
          }
        }

        if ("error" in recordsRes) {
          errors.records = recordsRes.error;
        } else {
          const recordsData = (recordsRes.data as any)?.data;
          recentRecords = recordsData?.items || [];
          
          if (!stats && recentRecords.length > 0) {
            const solvedCount = recentRecords.filter(
              (r) => r.status === "solved"
            ).length;
            stats = {
              solvedProblems: solvedCount,
              learningStreak: 1,
              experiencePoints: recentRecords.reduce(
                (sum, r) => sum + (r.score || 0) * 10,
                0
              ),
              achievements: 0,
            };
          }
        }

        if ("error" in problemsRes) {
          errors.problems = problemsRes.error;
        } else {
          recommendedProblems = problemsRes.data.items?.slice(0, 3) || [];
        }

        if ("error" in pathsRes) {
          errors.paths = pathsRes.error;
        } else {
          learningPaths = pathsRes.data.items?.slice(0, 3) || [];
        }

        setData({
          stats,
          recentRecords,
          recommendedProblems,
          learningPaths,
          loading: false,
          errors,
        });

        if (Object.keys(errors).length > 0) {
          toast({
            title: "部分数据加载失败",
            description: Object.values(errors).join("；"),
            variant: "destructive",
          });
        }
      } catch (error) {
        console.error("获取仪表盘数据失败:", error);
        toast({
          title: "数据加载失败",
          description: "请稍后重试",
          variant: "destructive",
        });
        setData((prev) => ({ ...prev, loading: false }));
      }
    };

    fetchDashboardData();

    const refreshInterval = setInterval(fetchDashboardData, 30000);
    return () => clearInterval(refreshInterval);
  }, [user?.id, toast]);

  const stats = [
    {
      title: t("dashboard.stats.solvedProblems"),
      value: data.stats?.solvedProblems ?? user?.solvedProblems ?? 0,
      icon: Code2,
      color: "from-blue-500 to-cyan-500",
      trend: "+12%",
    },
    {
      title: t("dashboard.stats.learningStreak"),
      value: data.stats?.learningStreak ?? user?.dailyStreak ?? 0,
      icon: Flame,
      color: "from-orange-500 to-red-500",
      trend: "+3天",
    },
    {
      title: t("dashboard.stats.experiencePoints"),
      value: data.stats?.experiencePoints ?? user?.experiencePoints ?? 0,
      icon: Star,
      color: "from-yellow-500 to-amber-500",
      trend: "+250",
    },
    {
      title: t("dashboard.stats.achievements"),
      value: data.stats?.achievements ?? 0,
      icon: Trophy,
      color: "from-purple-500 to-pink-500",
      trend: "+2",
    },
  ];

  const getDifficultyColor = (difficulty: string) => {
    switch (difficulty?.toLowerCase()) {
      case "easy":
      case "简单":
        return "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800";
      case "medium":
      case "中等":
        return "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400 border-amber-200 dark:border-amber-800";
      case "hard":
      case "困难":
        return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400 border-red-200 dark:border-red-800";
      default:
        return "bg-muted text-muted-foreground border-border";
    }
  };

  const getDifficultyLabel = (difficulty: string) => {
    switch (difficulty?.toLowerCase()) {
      case "easy": return "简单";
      case "medium": return "中等";
      case "hard": return "困难";
      default: return difficulty;
    }
  };

  if (data.loading) {
    return (
      <div className="space-y-6">
        <div className="h-8 w-48 bg-muted rounded-lg animate-pulse" />
        <SkeletonStats count={4} />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2">
            <SkeletonCard />
          </div>
          <SkeletonCard />
        </div>
      </div>
    );
  }

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="visible"
      className="space-y-6"
    >
      {/* Header */}
      <motion.div variants={itemVariants} className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {t("dashboard.welcome")}，<span className="bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">{user?.username || "用户"}</span>
          </h1>
          <p className="text-muted-foreground mt-1">
            今天是学习编程的好日子，继续加油！
          </p>
        </div>
        <div className="flex gap-3">
          <Link href="/history">
            <Button variant="outline" className="shadow-sm">
              <Clock className="w-4 h-4 mr-2" />
              学习记录
            </Button>
          </Link>
          <Link href="/problems">
            <Button variant="gradient">
              <Zap className="w-4 h-4 mr-2" />
              开始学习
            </Button>
          </Link>
        </div>
      </motion.div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat, index) => (
          <StatCard key={index} stat={stat} index={index} />
        ))}
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Activity */}
        <motion.div variants={itemVariants} className="lg:col-span-2">
          <Card className="border-0 shadow-sm hover:shadow-md transition-shadow duration-300">
            <CardHeader className="pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="text-xl flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-primary" />
                  {t("dashboard.recentActivity")}
                </CardTitle>
                <Link href="/history">
                  <Button variant="ghost" size="sm" className="text-muted-foreground hover:text-foreground">
                    查看全部
                    <ArrowRight className="w-4 h-4 ml-1" />
                  </Button>
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {data.recentRecords.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  <div className="w-16 h-16 rounded-full bg-muted flex items-center justify-center mx-auto mb-4">
                    <Zap className="w-8 h-8 opacity-50" />
                  </div>
                  <p className="font-medium">暂无学习记录</p>
                  <p className="text-sm mt-1">开始你的编程之旅吧！</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {data.recentRecords.map((record, index) => (
                    <ActivityItem key={record.id} record={record} index={index} />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </motion.div>

        {/* Recommended Problems */}
        <motion.div variants={itemVariants}>
          <Card className="border-0 shadow-sm hover:shadow-md transition-shadow duration-300">
            <CardHeader className="pb-4">
              <CardTitle className="text-xl flex items-center gap-2">
                <Target className="w-5 h-5 text-primary" />
                {t("dashboard.recommendedProblems")}
              </CardTitle>
            </CardHeader>
            <CardContent>
              {data.recommendedProblems.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  <div className="w-16 h-16 rounded-full bg-muted flex items-center justify-center mx-auto mb-4">
                    <Code2 className="w-8 h-8 opacity-50" />
                  </div>
                  <p>暂无推荐题目</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {data.recommendedProblems.map((problem, index) => (
                    <motion.div
                      key={problem.id}
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: index * 0.1 }}
                    >
                      <Link href={`/problems/${problem.id}`}>
                        <div className="p-4 rounded-xl border border-border/50 hover:border-primary/30 hover:shadow-md transition-all duration-200 cursor-pointer group bg-card">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0 flex-1">
                              <h4 className="font-medium text-foreground truncate group-hover:text-primary transition-colors">
                                {problem.title}
                              </h4>
                              <p className="text-sm text-muted-foreground mt-1">{problem.category}</p>
                            </div>
                            <span className={`px-2.5 py-1 rounded-full text-xs font-medium border shrink-0 ${getDifficultyColor(problem.difficulty)}`}>
                              {getDifficultyLabel(problem.difficulty)}
                            </span>
                          </div>
                          <div className="mt-3 flex items-center gap-4 text-sm text-muted-foreground">
                            <span>通过率: {Math.round(problem.acceptanceRate || 0)}%</span>
                            <ArrowRight className="w-4 h-4 ml-auto opacity-0 group-hover:opacity-100 group-hover:translate-x-1 transition-all" />
                          </div>
                        </div>
                      </Link>
                    </motion.div>
                  ))}
                </div>
              )}
              <Link href="/problems">
                <Button variant="outline" className="w-full mt-4 shadow-sm hover:shadow-md transition-shadow">
                  查看更多题目
                  <ArrowRight className="w-4 h-4 ml-2" />
                </Button>
              </Link>
            </CardContent>
          </Card>
        </motion.div>
      </div>

      {/* Learning Progress */}
      <motion.div variants={itemVariants}>
        <Card className="border-0 shadow-sm hover:shadow-md transition-shadow duration-300">
          <CardHeader className="pb-4">
            <div className="flex items-center justify-between">
              <CardTitle className="text-xl flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-primary" />
                {t("dashboard.learningProgress")}
              </CardTitle>
              <Link href="/learning">
                <Button variant="ghost" size="sm" className="text-muted-foreground hover:text-foreground">
                  查看全部
                  <ArrowRight className="w-4 h-4 ml-1" />
                </Button>
              </Link>
            </div>
          </CardHeader>
          <CardContent>
            {data.learningPaths.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                <div className="w-16 h-16 rounded-full bg-muted flex items-center justify-center mx-auto mb-4">
                  <Target className="w-8 h-8 opacity-50" />
                </div>
                <p className="font-medium">暂无学习路径进度</p>
                <Link href="/learning">
                  <Button variant="outline" className="mt-4 shadow-sm">
                    探索学习路径
                  </Button>
                </Link>
              </div>
            ) : (
              <div className="space-y-6">
                {data.learningPaths.map((path, index) => (
                  <div key={path.id}>
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-medium text-foreground">
                        {path.title}
                      </span>
                      <span className="text-sm font-medium text-primary">
                        {Math.round(path.progress || 0)}%
                      </span>
                    </div>
                    <div className="h-2.5 bg-muted rounded-full overflow-hidden">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${path.progress || 0}%` }}
                        transition={{ duration: 1, delay: 0.5 + index * 0.1, ease: "easeOut" }}
                        className={`h-full rounded-full ${
                          index % 3 === 0
                            ? "bg-gradient-to-r from-primary to-accent"
                            : index % 3 === 1
                            ? "bg-gradient-to-r from-blue-500 to-cyan-500"
                            : "bg-gradient-to-r from-orange-500 to-red-500"
                        }`}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </motion.div>
    </motion.div>
  );
}

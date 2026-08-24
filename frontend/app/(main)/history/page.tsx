"use client";

import { useState, useEffect, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import {
  Clock,
  CheckCircle,
  XCircle,
  Code2,
  Calendar,
  TrendingUp,
  Award,
  Loader2,
  Zap,
  RefreshCw,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { userService, type LearningHistory, type UserStats } from "@/services/userService";
import { useToast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/authStore";

interface HistoryItem extends LearningHistory {
  displayType: "problem" | "learning" | "achievement";
  displayTitle: string;
  displayStatus?: string;
  formattedTime: string;
}

export default function HistoryPage() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const { user } = useAuthStore();

  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [stats, setStats] = useState<UserStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  });
  const [dateRange, setDateRange] = useState<{
    startDate: string;
    endDate: string;
  }>({ startDate: "", endDate: "" });

  const formatTimeAgo = (dateString: string): string => {
    const now = new Date();
    const past = new Date(dateString);
    const diffMs = now.getTime() - past.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return "刚刚";
    if (diffMins < 60) return `${diffMins}分钟前`;
    if (diffHours < 24) return `${diffHours}小时前`;
    if (diffDays < 30) return `${diffDays}天前`;
    return past.toLocaleDateString("zh-CN");
  };

  const transformHistoryItem = (item: LearningHistory): HistoryItem => {
    let displayType: "problem" | "learning" | "achievement" = "learning";
    let displayTitle = "";
    let displayStatus = item.status;

    switch (item.type) {
      case "problem_solved":
        displayType = "problem";
        displayTitle = `完成题目：${item.problemTitle || "未知题目"}`;
        displayStatus = "success";
        break;
      case "problem_attempted":
        displayType = "problem";
        displayTitle = `尝试题目：${item.problemTitle || "未知题目"}`;
        displayStatus = item.status === "accepted" ? "success" : "failed";
        break;
      case "learning_streak":
        displayType = "learning";
        displayTitle = `连续学习第${item.timeSpent || 1}天`;
        break;
      case "achievement_unlocked":
        displayType = "achievement";
        displayTitle = "获得新成就";
        break;
      case "path_completed":
        displayType = "learning";
        displayTitle = `完成学习路径：${item.problemTitle || "未知路径"}`;
        break;
      default:
        displayType = "learning";
        displayTitle = item.problemTitle || "学习活动";
    }

    return {
      ...item,
      displayType,
      displayTitle,
      displayStatus,
      formattedTime: formatTimeAgo(item.createdAt),
    };
  };

  const fetchHistory = useCallback(async (page = 1, pageSize = 20) => {
    if (!user?.id) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);

      const params: {
        page: number;
        size: number;
        startDate?: string;
        endDate?: string;
      } = { page, size: pageSize };

      if (dateRange.startDate) {
        params.startDate = dateRange.startDate;
      }
      if (dateRange.endDate) {
        params.endDate = dateRange.endDate;
      }

      const historyRes = await userService.getUserLearningHistory(user.id, params);
      const apiResponseData = historyRes as any;

      if (apiResponseData.code === 200 && apiResponseData.data && apiResponseData.data.items) {
        const records = apiResponseData.data.items;
        const transformedItems = records.map(transformHistoryItem);
        setHistory(transformedItems);
        setPagination({
          current: page,
          pageSize,
          total: apiResponseData.data.total || records.length,
        });

        const calculatedStats = calculateStatsFromRecords(records);
        setStats(calculatedStats);
      } else {
        setHistory([]);
        setPagination((prev) => ({ ...prev, total: 0 }));
      }
    } catch (error) {
      console.error("获取学习历史失败:", error);
      toast({
        title: "加载失败",
        description: "无法获取学习历史，请稍后重试",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [user?.id, dateRange.startDate, dateRange.endDate, toast]);

  const calculateStatsFromRecords = (records: LearningHistory[]): UserStats => {
    const totalSubmissions = records.length;
    const solvedProblems = records.filter(
      (r) => r.status === "solved" || r.status === "accepted" || r.type === "problem_solved"
    ).length;
    const totalStudyTime = records.reduce(
      (sum, record) => sum + (record.timeSpent || 0),
      0
    );
    const experiencePoints = records.reduce(
      (sum, record) => sum + Math.round((record.score || 0) * 10),
      0
    );

    return {
      solvedProblems,
      totalSubmissions,
      acceptanceRate:
        totalSubmissions > 0 ? Math.round((solvedProblems / totalSubmissions) * 100) : 0,
      learningStreak: Math.max(...records.map(() => 1), 0),
      experiencePoints,
      coins: 0,
      achievements: 0,
    };
  };

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const handlePageChange = (page: number) => {
    fetchHistory(page, pagination.pageSize);
  };

  const handleDateChange = (field: "startDate" | "endDate", value: string) => {
    setDateRange((prev) => ({ ...prev, [field]: value }));
  };

  const handleRefresh = () => {
    fetchHistory(pagination.current, pagination.pageSize);
  };

  const handleClearDateFilter = () => {
    setDateRange({ startDate: "", endDate: "" });
  };

  const getIcon = (type: string, status?: string) => {
    if (type === "problem") {
      return status === "success" ? CheckCircle : XCircle;
    } else if (type === "learning") {
      return Clock;
    } else if (type === "achievement") {
      return Award;
    }
    return Code2;
  };

  const getIconColor = (type: string, status?: string) => {
    if (type === "problem") {
      return status === "success"
        ? "bg-green-100 text-green-600"
        : "bg-red-100 text-red-600";
    } else if (type === "learning") {
      return "bg-blue-100 text-blue-600";
    } else if (type === "achievement") {
      return "bg-yellow-100 text-yellow-600";
    }
    return "bg-gray-100 text-gray-600";
  };

  const successRate = stats
    ? stats.totalSubmissions > 0
      ? Math.round((stats.solvedProblems / stats.totalSubmissions) * 100)
      : 0
    : 0;

  const statItems = [
    { label: "总解题数", value: stats?.solvedProblems || 0, icon: Code2 },
    { label: "总提交数", value: stats?.totalSubmissions || 0, icon: TrendingUp },
    { label: "成功率", value: `${successRate}%`, icon: CheckCircle },
    { label: "连续天数", value: stats?.learningStreak || 0, icon: Calendar },
  ];

  if (!user) {
    return (
      <div className="flex items-center justify-center h-[calc(100vh-200px)]">
        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">请先登录</h2>
          <p className="text-gray-500 dark:text-gray-400">登录后查看您的学习历史</p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[calc(100vh-200px)]">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-600" />
        <span className="ml-2 text-gray-600">加载中...</span>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-6"
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
            {t("nav.history")}
          </h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">
            查看你的学习历程和进步轨迹
          </p>
        </div>
      </div>

      {/* Filters */}
      <Card className="border-0 shadow-md">
        <CardContent className="p-4">
          <div className="flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-gray-500" />
              <span className="text-sm text-gray-600 dark:text-gray-400">日期筛选:</span>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="date"
                value={dateRange.startDate}
                onChange={(e) => handleDateChange("startDate", e.target.value)}
                className="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="开始日期"
              />
              <span className="text-gray-400">至</span>
              <input
                type="date"
                value={dateRange.endDate}
                onChange={(e) => handleDateChange("endDate", e.target.value)}
                className="px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="结束日期"
              />
            </div>
            {(dateRange.startDate || dateRange.endDate) && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleClearDateFilter}
                className="text-gray-600"
              >
                清除筛选
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={handleRefresh}
              className="ml-auto"
            >
              <RefreshCw className="w-4 h-4 mr-2" />
              刷新
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {statItems.map((stat, index) => (
          <motion.div
            key={index}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.1 }}
          >
            <Card className="border-0 shadow-md">
              <CardContent className="p-4 flex items-center gap-4">
                <div className="w-12 h-12 rounded-xl bg-indigo-100 dark:bg-indigo-950/30 flex items-center justify-center">
                  <stat.icon className="w-6 h-6 text-indigo-600" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-gray-900 dark:text-white">
                    {stat.value}
                  </p>
                  <p className="text-sm text-gray-500">{stat.label}</p>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        ))}
      </div>

      {/* History Timeline */}
      <Card className="border-0 shadow-lg">
        <CardHeader>
          <CardTitle className="text-xl">学习记录</CardTitle>
        </CardHeader>
        <CardContent className="p-6">
          {history.length === 0 ? (
            <div className="text-center py-12 text-gray-500">
              <Zap className="w-16 h-16 mx-auto mb-4 opacity-50" />
              <p>暂无学习记录</p>
              <p className="text-sm mt-2">开始做题来记录你的学习历程吧！</p>
            </div>
          ) : (
            <>
              <div className="space-y-4">
                {history.map((item, index) => {
                  const Icon = getIcon(item.displayType, item.displayStatus);
                  const date = new Date(item.createdAt).toLocaleDateString("zh-CN");
                  return (
                    <motion.div
                      key={item.id}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: index * 0.05 }}
                      className="flex items-center gap-4 p-4 rounded-xl bg-gray-50 dark:bg-gray-800/50 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                    >
                      <div
                        className={`w-10 h-10 rounded-lg flex items-center justify-center ${getIconColor(
                          item.displayType,
                          item.displayStatus
                        )}`}
                      >
                        <Icon className="w-5 h-5" />
                      </div>
                      <div className="flex-1">
                        <p className="font-medium text-gray-900 dark:text-white">
                          {item.displayTitle}
                        </p>
                        <p className="text-sm text-gray-500">
                          {date} · {item.formattedTime}
                        </p>
                      </div>
                      {item.score !== undefined && item.score > 0 && (
                        <div
                          className={`text-sm font-medium ${
                            item.displayStatus === "success"
                              ? "text-green-600"
                              : "text-red-600"
                          }`}
                        >
                          {item.displayStatus === "success" ? "通过" : "未通过"} · {item.score}分
                        </div>
                      )}
                    </motion.div>
                  );
                })}
              </div>

              {/* Pagination */}
              {pagination.total > pagination.pageSize && (
                <div className="flex items-center justify-center gap-2 pt-6 mt-4 border-t border-gray-200 dark:border-gray-700">
                  <button
                    className="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                    disabled={pagination.current === 1}
                    onClick={() => handlePageChange(pagination.current - 1)}
                  >
                    上一页
                  </button>
                  <span className="text-sm text-gray-600 px-4">
                    第 {pagination.current} 页，共 {Math.ceil(pagination.total / pagination.pageSize)} 页
                  </span>
                  <button
                    className="px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-700 disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                    disabled={pagination.current >= Math.ceil(pagination.total / pagination.pageSize)}
                    onClick={() => handlePageChange(pagination.current + 1)}
                  >
                    下一页
                  </button>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}

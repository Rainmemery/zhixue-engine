"use client";

import { useState, useEffect, useCallback, memo } from "react";
import { useTranslation } from "react-i18next";
import { motion, AnimatePresence } from "framer-motion";
import {
  BookOpen,
  CheckCircle,
  Circle,
  Lock,
  Clock,
  Target,
  ChevronRight,
  Play,
  Pause,
  Plus,
  Sparkles,
  Route,
  ArrowRight,
  TrendingUp,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Slider } from "@/components/ui/slider";
import { SkeletonCard } from "@/components/ui/skeleton";
import { learningService, LearningProgress } from "@/services/learningService";
import { LearningPath, LearningStep } from "@/types";
import { useToast } from "@/hooks/use-toast";
import { useAuthStore } from "@/stores/authStore";

const getDifficultyColor = (difficulty: string) => {
  const colors: Record<string, string> = {
    beginner: "from-blue-400 to-cyan-500",
    intermediate: "from-emerald-400 to-teal-500",
    advanced: "from-amber-400 to-orange-500",
    expert: "from-red-400 to-rose-500",
  };
  return colors[difficulty] || "from-primary to-accent";
};

const getDifficultyLabel = (difficulty: string) => {
  const labels: Record<string, string> = {
    beginner: "初级",
    intermediate: "中级",
    advanced: "高级",
    expert: "专家级",
  };
  return labels[difficulty] || "未知";
};

const getStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    active: "from-primary to-accent",
    completed: "from-emerald-400 to-emerald-600",
    paused: "from-amber-400 to-amber-600",
    cancelled: "from-gray-400 to-gray-600",
  };
  return colors[status] || "from-gray-400 to-gray-600";
};

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    active: "进行中",
    completed: "已完成",
    paused: "已暂停",
    cancelled: "已取消",
  };
  return labels[status] || "未知";
};

const getStepStatusIcon = (status: string) => {
  switch (status) {
    case "completed":
      return <CheckCircle className="w-5 h-5" />;
    case "pending":
      return <Play className="w-4 h-4" />;
    default:
      return <Lock className="w-4 h-4" />;
  }
};

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.06, delayChildren: 0.1 },
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

// Memoized components
const PathCard = memo(({ path, onStart }: { path: LearningPath; onStart: (path: LearningPath) => void }) => (
  <motion.div variants={itemVariants} whileHover={{ y: -4, transition: { duration: 0.2 } }}>
    <Card className="border-0 shadow-sm hover:shadow-lg transition-all duration-300 overflow-hidden group">
      <div className={`h-28 bg-gradient-to-br ${getDifficultyColor(path.difficulty || "beginner")} flex items-center justify-center relative`}>
        <div className="absolute inset-0 bg-black/10" />
        <BookOpen className="w-12 h-12 text-white relative z-10 group-hover:scale-110 transition-transform duration-300" />
      </div>
      <CardContent className="p-5">
        <div className="flex items-center gap-2 mb-2">
          <span className={`px-2 py-0.5 rounded-full text-xs font-medium text-white bg-gradient-to-r ${getDifficultyColor(path.difficulty || "beginner")}`}>
            {getDifficultyLabel(path.difficulty || "beginner")}
          </span>
          <span className="text-xs text-muted-foreground flex items-center gap-1">
            <Clock className="w-3 h-3" />
            {path.targetDays || 30}天
          </span>
        </div>
        <h3 className="font-semibold text-foreground mb-2 line-clamp-1 group-hover:text-primary transition-colors">
          {path.goal}
        </h3>
        <p className="text-sm text-muted-foreground mb-4 line-clamp-2">
          {path.description || `专注领域: ${path.focusAreas?.join(", ") || "通用学习"}`}
        </p>
        <Button className="w-full" variant="outline" onClick={() => onStart(path)}>
          <Play className="w-4 h-4 mr-2" />
          开始学习
        </Button>
      </CardContent>
    </Card>
  </motion.div>
));
PathCard.displayName = "PathCard";

const StepItem = memo(({ step, index }: { step: LearningStep; index: number }) => (
  <motion.div
    initial={{ opacity: 0, x: -20 }}
    animate={{ opacity: 1, x: 0 }}
    transition={{ delay: index * 0.05, duration: 0.4, ease: "easeOut" }}
    className={`flex items-center gap-4 p-4 rounded-xl border transition-all duration-200 ${
      step.status === "completed"
        ? "bg-emerald-50/50 dark:bg-emerald-950/10 border-emerald-200 dark:border-emerald-800/50"
        : step.status === "pending"
        ? "bg-primary/5 dark:bg-primary/5 border-primary/20 dark:border-primary/20"
        : "bg-muted/30 border-border/50"
    }`}
  >
    <div
      className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${
        step.status === "completed"
          ? "bg-emerald-500 text-white shadow-lg shadow-emerald-500/30"
          : step.status === "pending"
          ? "bg-primary text-white shadow-lg shadow-primary/30"
          : "bg-muted text-muted-foreground"
      }`}
    >
      {getStepStatusIcon(step.status)}
    </div>
    <div className="flex-1 min-w-0">
      <p className={`font-medium ${step.status === "pending" ? "text-muted-foreground" : "text-foreground"}`}>
        {index + 1}. {step.title}
      </p>
      {step.description && (
        <p className="text-sm text-muted-foreground mt-0.5 line-clamp-1">
          {step.description}
        </p>
      )}
    </div>
    <div className="flex items-center gap-2 text-sm text-muted-foreground shrink-0">
      <Clock className="w-4 h-4" />
      <span>{step.estimatedTimeMinutes}分钟</span>
      {step.status !== "pending" && (
        <ChevronRight className="w-4 h-4" />
      )}
    </div>
  </motion.div>
));
StepItem.displayName = "StepItem";

export default function LearningPage() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const { user, isAuthenticated } = useAuthStore();

  const [learningPaths, setLearningPaths] = useState<LearningPath[]>([]);
  const [recommendedPaths, setRecommendedPaths] = useState<LearningPath[]>([]);
  const [progress, setProgress] = useState<LearningProgress[]>([]);
  const [loading, setLoading] = useState(true);
  const [activePathId, setActivePathId] = useState<number | string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    goal: "",
    focusAreas: "",
    targetDays: 30,
    dailyMinutes: 60,
  });

  const fetchData = useCallback(async () => {
    if (!isAuthenticated || !user) {
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const [pathsRes, recommendedRes] = await Promise.all([
        learningService.getUserLearningPaths(user.id),
        learningService.getRecommendedPaths(),
      ]);

      if (pathsRes.code === 200) {
        setLearningPaths(pathsRes.data?.items || []);
        if (pathsRes.data?.items?.length > 0 && !activePathId) {
          setActivePathId(pathsRes.data.items[0].id);
        }
      } else {
        setLearningPaths([]);
      }

      if (recommendedRes.code === 200) {
        setRecommendedPaths(recommendedRes.data?.items || []);
      } else {
        setRecommendedPaths([]);
      }
    } catch (error) {
      console.error("获取学习路径失败:", error);
      toast({
        title: "加载失败",
        description: "无法获取学习路径数据",
        variant: "destructive",
      });
      setLearningPaths([]);
      setRecommendedPaths([]);
    } finally {
      setLoading(false);
    }
  }, [toast, user, isAuthenticated, activePathId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const getActivePath = () => {
    return learningPaths.find((p) => p.id === activePathId) || learningPaths[0];
  };

  const getPathProgress = (pathId: number | string) => {
    return progress.find((p) => p.pathId === pathId);
  };

  const activePath = getActivePath();
  const activeProgress = activePath ? getPathProgress(activePath.id) : null;

  const handleStartPath = async (path: LearningPath) => {
    try {
      const response = await learningService.startLearningPath(path.id);
      if (response.code === 200) {
        toast({ title: "开始学习", description: `开始学习路径: ${path.goal}` });
        fetchData();
      } else {
        throw new Error(response.message || "启动学习路径失败");
      }
    } catch (error: any) {
      console.error("启动学习路径失败:", error);
      toast({ title: "操作失败", description: error.message || "启动学习路径失败", variant: "destructive" });
    }
  };

  const handleContinuePath = async (path: LearningPath) => {
    try {
      const response = await learningService.continueLearningPath(String(path.id));
      if (response.code === 200) {
        toast({ title: "继续学习", description: `继续学习路径: ${path.goal}` });
        fetchData();
      } else {
        throw new Error(response.message || "继续学习路径失败");
      }
    } catch (error: any) {
      console.error("继续学习路径失败:", error);
      toast({ title: "操作失败", description: error.message || "继续学习路径失败", variant: "destructive" });
    }
  };

  const handlePausePath = async (path: LearningPath) => {
    try {
      const response = await learningService.pauseLearningPath(String(path.id));
      if (response.code === 200) {
        setLearningPaths((prev) => prev.map((p) => (p.id === path.id ? { ...p, status: "paused" } : p)));
        toast({ title: "已暂停", description: `学习路径已暂停: ${path.goal}` });
      } else {
        throw new Error(response.message || "暂停学习路径失败");
      }
    } catch (error: any) {
      console.error("暂停学习路径失败:", error);
      toast({ title: "操作失败", description: error.message || "暂停学习路径失败", variant: "destructive" });
    }
  };

  const handleCompletePath = async (path: LearningPath) => {
    try {
      const response = await learningService.completeLearningPath(String(path.id));
      if (response.code === 200) {
        setLearningPaths((prev) => prev.map((p) => (p.id === path.id ? { ...p, status: "completed" } : p)));
        toast({ title: "已完成", description: `学习路径已完成: ${path.goal}` });
      } else {
        throw new Error(response.message || "完成学习路径失败");
      }
    } catch (error: any) {
      console.error("完成学习路径失败:", error);
      toast({ title: "操作失败", description: error.message || "完成学习路径失败", variant: "destructive" });
    }
  };

  const handleCreatePath = async () => {
    if (!user) {
      toast({ title: "请先登录", description: "您需要登录后才能创建学习路径", variant: "destructive" });
      return;
    }

    if (!createForm.goal || !createForm.focusAreas) {
      toast({ title: "信息不完整", description: "请填写学习目标和专注领域", variant: "destructive" });
      return;
    }

    try {
      const pathData = {
        userId: user.id,
        goal: createForm.goal,
        targetDays: createForm.targetDays,
        dailyMinutes: createForm.dailyMinutes,
        focusAreas: createForm.focusAreas.split(",").map((item) => item.trim()).join(","),
      };

      const response = await learningService.generateLearningPath(pathData);
      if (response.code === 200) {
        const createdPath = response.data;
        setLearningPaths((prev) => [...prev, createdPath]);
        setCreateModalOpen(false);
        setCreateForm({ goal: "", focusAreas: "", targetDays: 30, dailyMinutes: 60 });
        toast({ title: "创建成功", description: "学习路径创建成功！" });
      } else {
        throw new Error(response.message || "创建失败");
      }
    } catch (error: any) {
      console.error("创建学习路径失败:", error);
      toast({ title: "创建失败", description: error.message || "创建学习路径失败", variant: "destructive" });
    }
  };

  const handleStartRecommendedPath = async (path: LearningPath) => {
    if (!user) {
      toast({ title: "请先登录", description: "您需要登录后才能开始学习", variant: "destructive" });
      return;
    }

    try {
      const response = await learningService.startLearningPath(path.id);
      if (response.code === 200) {
        setLearningPaths((prev) => [...prev, response.data]);
        toast({ title: "开始学习", description: `开始学习路径: ${path.goal}` });
      } else {
        throw new Error("启动学习路径失败");
      }
    } catch (error: any) {
      console.error("启动推荐学习路径失败:", error);
      toast({ title: "操作失败", description: error.message || "启动学习路径失败", variant: "destructive" });
    }
  };

  if (!isAuthenticated) {
    return (
      <motion.div variants={containerVariants} initial="hidden" animate="visible" className="space-y-6">
        <motion.div variants={itemVariants}>
          <h1 className="text-3xl font-bold tracking-tight">{t("nav.learning") || "学习路径"}</h1>
          <p className="text-muted-foreground mt-1">按照精心设计的路线系统学习编程知识</p>
        </motion.div>
        <Card className="border-0 shadow-sm">
          <CardContent className="p-12 text-center">
            <div className="w-20 h-20 rounded-full bg-muted flex items-center justify-center mx-auto mb-4">
              <BookOpen className="w-10 h-10 text-muted-foreground" />
            </div>
            <h3 className="text-lg font-medium mb-2">请先登录</h3>
            <p className="text-muted-foreground">登录后即可查看和管理您的学习路径</p>
          </CardContent>
        </Card>
      </motion.div>
    );
  }

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="h-8 w-48 bg-muted rounded-lg animate-pulse" />
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      </div>
    );
  }

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="visible" className="space-y-6">
      {/* Header */}
      <motion.div variants={itemVariants} className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t("nav.learning") || "学习路径"}</h1>
          <p className="text-muted-foreground mt-1">按照精心设计的路线系统学习编程知识</p>
        </div>
        <Button variant="gradient" onClick={() => setCreateModalOpen(true)}>
          <Plus className="w-4 h-4 mr-2" />
          创建新路径
        </Button>
      </motion.div>

      {/* Recommended Paths */}
      {recommendedPaths.length > 0 && (
        <motion.div variants={itemVariants} className="space-y-4">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-amber-500" />
            <h2 className="text-xl font-semibold">推荐学习路径</h2>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {recommendedPaths.map((path) => (
              <PathCard key={path.id} path={path} onStart={handleStartRecommendedPath} />
            ))}
          </div>
        </motion.div>
      )}

      {/* My Learning Paths */}
      {learningPaths.length === 0 ? (
        <motion.div variants={itemVariants}>
          <Card className="border-0 shadow-sm">
            <CardContent className="p-12 text-center">
              <div className="w-20 h-20 rounded-full bg-muted flex items-center justify-center mx-auto mb-4">
                <Route className="w-10 h-10 text-muted-foreground" />
              </div>
              <h3 className="text-lg font-medium mb-2">暂无学习路径</h3>
              <p className="text-muted-foreground mb-4">点击上方按钮创建一个新的学习路径</p>
              <Button onClick={() => setCreateModalOpen(true)}>
                <Plus className="w-4 h-4 mr-2" />
                创建学习路径
              </Button>
            </CardContent>
          </Card>
        </motion.div>
      ) : (
        <>
          {/* Path Tabs */}
          {learningPaths.length > 1 && (
            <motion.div variants={itemVariants} className="flex gap-2 overflow-x-auto pb-2">
              {learningPaths.map((path) => (
                <Button
                  key={path.id}
                  variant={activePathId === path.id ? "default" : "outline"}
                  size="sm"
                  onClick={() => setActivePathId(path.id)}
                  className="rounded-lg whitespace-nowrap"
                >
                  {path.title || path.goal}
                  <span className="ml-2 text-xs opacity-70">{Math.round(path.progress || 0)}%</span>
                </Button>
              ))}
            </motion.div>
          )}

          {/* Active Path Detail */}
          <AnimatePresence mode="wait">
            {activePath && (
              <motion.div
                key={activePath.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.3 }}
              >
                <Card className="border-0 shadow-sm hover:shadow-md transition-shadow duration-300">
                  <CardContent className="p-6">
                    <div className="flex flex-col lg:flex-row gap-6">
                      {/* Left: Path Info */}
                      <div className="lg:w-1/3 space-y-4">
                        <div className="flex items-center gap-3">
                          <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${getStatusColor(activePath.status)} flex items-center justify-center shadow-lg`}>
                            {activePath.status === "completed" ? (
                              <CheckCircle className="w-6 h-6 text-white" />
                            ) : activePath.status === "paused" ? (
                              <Pause className="w-6 h-6 text-white" />
                            ) : (
                              <BookOpen className="w-6 h-6 text-white" />
                            )}
                          </div>
                          <div>
                            <h2 className="text-xl font-bold">{activePath.title || activePath.goal}</h2>
                            <p className="text-sm text-muted-foreground">{getStatusLabel(activePath.status)}</p>
                          </div>
                        </div>

                        <p className="text-muted-foreground">{activePath.description}</p>

                        {/* Progress */}
                        <div className="space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-sm text-muted-foreground">学习进度</span>
                            <span className="text-sm font-medium text-primary">{Math.round(activePath.progress || 0)}%</span>
                          </div>
                          <div className="h-2.5 bg-muted rounded-full overflow-hidden">
                            <motion.div
                              initial={{ width: 0 }}
                              animate={{ width: `${activePath.progress || 0}%` }}
                              transition={{ duration: 0.8, ease: "easeOut" }}
                              className={`h-full rounded-full bg-gradient-to-r ${getStatusColor(activePath.status)}`}
                            />
                          </div>
                        </div>

                        {/* Stats */}
                        <div className="grid grid-cols-2 gap-3">
                          <div className="p-3 bg-muted/50 rounded-xl">
                            <p className="text-xs text-muted-foreground">已完成</p>
                            <p className="text-lg font-bold">{activePath.completedCount || 0}/{activePath.problemCount || activePath.steps?.length || 0} 题</p>
                          </div>
                          <div className="p-3 bg-muted/50 rounded-xl">
                            <p className="text-xs text-muted-foreground">预计时间</p>
                            <p className="text-lg font-bold">{activePath.estimatedHours || activePath.targetDays || 0}小时</p>
                          </div>
                        </div>

                        {/* Actions */}
                        <div className="space-y-2">
                          {activePath.status === "active" && (
                            <div className="flex gap-2">
                              <Button className="flex-1" variant="gradient" onClick={() => handleContinuePath(activePath)}>
                                <Play className="w-4 h-4 mr-2" />
                                继续学习
                              </Button>
                              <Button variant="outline" onClick={() => handlePausePath(activePath)}>
                                <Pause className="w-4 h-4 mr-2" />
                                暂停
                              </Button>
                            </div>
                          )}
                          {activePath.status === "paused" && (
                            <div className="flex gap-2">
                              <Button className="flex-1" variant="gradient" onClick={() => handleContinuePath(activePath)}>
                                <Play className="w-4 h-4 mr-2" />
                                继续学习
                              </Button>
                              <Button variant="outline" onClick={() => handleCompletePath(activePath)}>
                                <CheckCircle className="w-4 h-4 mr-2" />
                                完成
                              </Button>
                            </div>
                          )}
                          {activePath.status === "completed" && (
                            <Button className="w-full" variant="outline" onClick={() => handleStartPath(activePath)}>
                              <Play className="w-4 h-4 mr-2" />
                              重新开始
                            </Button>
                          )}
                        </div>
                      </div>

                      {/* Right: Steps */}
                      <div className="lg:w-2/3">
                        <h3 className="font-semibold mb-4 flex items-center gap-2">
                          <Target className="w-5 h-5 text-primary" />
                          学习阶段
                        </h3>
                        <div className="space-y-3">
                          {activePath.steps?.map((step, index) => (
                            <StepItem key={index} step={step} index={index} />
                          ))}
                          {(!activePath.steps || activePath.steps.length === 0) && (
                            <div className="text-center py-8 text-muted-foreground">
                              <div className="w-16 h-16 rounded-full bg-muted flex items-center justify-center mx-auto mb-3">
                                <Target className="w-8 h-8 opacity-50" />
                              </div>
                              <p>暂无学习阶段</p>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </motion.div>
            )}
          </AnimatePresence>
        </>
      )}

      {/* Create Path Dialog */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="sm:max-w-[500px]">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Plus className="w-5 h-5 text-primary" />
              创建新的学习路径
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="goal">学习目标</Label>
              <Input
                id="goal"
                placeholder="例如：掌握JavaScript基础"
                value={createForm.goal}
                onChange={(e) => setCreateForm({ ...createForm, goal: e.target.value })}
                className="rounded-xl"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="focusAreas">专注领域</Label>
              <Textarea
                id="focusAreas"
                placeholder="例如：JavaScript, ES6+, DOM操作"
                value={createForm.focusAreas}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setCreateForm({ ...createForm, focusAreas: e.target.value })}
                rows={3}
                className="rounded-xl"
              />
              <p className="text-xs text-muted-foreground">多个领域用逗号分隔</p>
            </div>

            <div className="space-y-2">
              <Label>目标天数: {createForm.targetDays}天</Label>
              <Slider
                value={[createForm.targetDays]}
                onValueChange={(value: number[]) => setCreateForm({ ...createForm, targetDays: value[0] })}
                min={7}
                max={180}
                step={1}
              />
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>1周</span>
                <span>1月</span>
                <span>3月</span>
                <span>6月</span>
              </div>
            </div>

            <div className="space-y-2">
              <Label>每日学习时间: {createForm.dailyMinutes}分钟</Label>
              <Slider
                value={[createForm.dailyMinutes]}
                onValueChange={(value: number[]) => setCreateForm({ ...createForm, dailyMinutes: value[0] })}
                min={15}
                max={180}
                step={15}
              />
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>15分钟</span>
                <span>1小时</span>
                <span>2小时</span>
                <span>3小时</span>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateModalOpen(false)}>取消</Button>
            <Button onClick={handleCreatePath}>创建路径</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </motion.div>
  );
}

"use client";

import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import {
  Trophy,
  Star,
  Zap,
  Target,
  Clock,
  Flame,
  Code2,
  BookOpen,
  Loader2,
  Award,
  Crown,
  Gift,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { userService, UserAchievement } from "@/services/userService";
import { useAuthStore } from "@/stores/authStore";
import { useToast } from "@/hooks/use-toast";

const achievementIcons: Record<string, React.ElementType> = {
  "first-login": Star,
  "first-problem": Code2,
  "streak-7": Flame,
  "streak-30": Flame,
  "streak-100": Flame,
  "easy-10": BookOpen,
  "easy-50": BookOpen,
  "medium-10": Target,
  "medium-50": Target,
  "hard-10": Zap,
  "hard-50": Zap,
  "night-owl": Clock,
  "early-bird": Clock,
  "perfect-score": Trophy,
  default: Award,
};

const rarityConfig: Record<string, { color: string; bgColor: string; borderColor: string; icon: React.ElementType }> = {
  legendary: {
    color: "text-yellow-600",
    bgColor: "bg-yellow-100 dark:bg-yellow-950/30",
    borderColor: "border-yellow-300 dark:border-yellow-700",
    icon: Crown,
  },
  epic: {
    color: "text-purple-600",
    bgColor: "bg-purple-100 dark:bg-purple-950/30",
    borderColor: "border-purple-300 dark:border-purple-700",
    icon: Star,
  },
  rare: {
    color: "text-blue-600",
    bgColor: "bg-blue-100 dark:bg-blue-950/30",
    borderColor: "border-blue-300 dark:border-blue-700",
    icon: Trophy,
  },
  uncommon: {
    color: "text-green-600",
    bgColor: "bg-green-100 dark:bg-green-950/30",
    borderColor: "border-green-300 dark:border-green-700",
    icon: Target,
  },
  common: {
    color: "text-gray-600",
    bgColor: "bg-gray-100 dark:bg-gray-800",
    borderColor: "border-gray-300 dark:border-gray-600",
    icon: Gift,
  },
};

const categories = [
  { key: "all", label: "全部" },
  { key: "unlocked", label: "已解锁" },
  { key: "locked", label: "未解锁" },
  { key: "learning", label: "学习成就" },
  { key: "coding", label: "编码成就" },
  { key: "persistence", label: "坚持成就" },
  { key: "mastery", label: "精通成就" },
  { key: "challenge", label: "挑战成就" },
];

export default function AchievementsPage() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const { user } = useAuthStore();

  const [achievements, setAchievements] = useState<UserAchievement[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState("all");

  useEffect(() => {
    const fetchAchievements = async () => {
      if (!user?.id) {
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        const response = await userService.getUserAchievements(user.id);

        if (response.code === 200) {
          setAchievements(response.data || []);
        }
      } catch (error) {
        console.error("获取成就失败:", error);
        toast({
          title: "加载失败",
          description: "无法获取成就数据，请稍后重试",
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    };

    fetchAchievements();
  }, [user, toast]);

  const getAchievementIcon = (item: UserAchievement) => {
    const searchStr = (
      item.achievement.name +
      " " +
      item.achievement.iconUrl
    ).toLowerCase();
    const iconKey = Object.keys(achievementIcons).find((key) =>
      searchStr.includes(key)
    );
    return iconKey ? achievementIcons[iconKey] : achievementIcons.default;
  };

  const getRarityConfig = (rarity: string) => {
    return rarityConfig[rarity?.toLowerCase()] || rarityConfig.common;
  };

  const isUnlocked = (item: UserAchievement) => {
    return item.unlockedAt || item.progressCurrent >= item.progressTarget;
  };

  const getFilteredAchievements = () => {
    if (activeCategory === "all") return achievements;
    if (activeCategory === "unlocked")
      return achievements.filter((a) => isUnlocked(a));
    if (activeCategory === "locked")
      return achievements.filter((a) => !isUnlocked(a));
    return achievements.filter(
      (a) => a.achievement.category === activeCategory
    );
  };

  const filteredAchievements = getFilteredAchievements();

  const unlockedCount = achievements.filter((a) => isUnlocked(a)).length;
  const totalPoints = achievements
    .filter((a) => isUnlocked(a))
    .reduce((sum, a) => sum + (a.achievement.pointsAwarded || 0), 0);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[calc(100vh-200px)]">
        <Loader2 className="w-8 h-8 animate-spin text-indigo-600" />
        <span className="ml-2 text-gray-600 dark:text-gray-400">加载中...</span>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-6"
    >
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
            {t("nav.achievements")}
          </h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">
            收集成就，展示你的学习成果
          </p>
        </div>
        <div className="flex gap-4">
          <div className="text-center px-4 py-2 bg-indigo-50 dark:bg-indigo-950/30 rounded-lg">
            <p className="text-2xl font-bold text-indigo-600">{unlockedCount}</p>
            <p className="text-sm text-indigo-600/70">已解锁</p>
          </div>
          <div className="text-center px-4 py-2 bg-yellow-50 dark:bg-yellow-950/30 rounded-lg">
            <p className="text-2xl font-bold text-yellow-600">{totalPoints}</p>
            <p className="text-sm text-yellow-600/70">总积分</p>
          </div>
        </div>
      </div>

      <Card className="border-0 shadow-lg">
        <CardContent className="p-4">
          <div className="flex flex-wrap gap-2">
            {categories.map((cat) => (
              <Button
                key={cat.key}
                variant={activeCategory === cat.key ? "default" : "outline"}
                size="sm"
                onClick={() => setActiveCategory(cat.key)}
                className={
                  activeCategory === cat.key
                    ? "bg-indigo-600 hover:bg-indigo-700"
                    : ""
                }
              >
                {cat.label}
              </Button>
            ))}
          </div>
        </CardContent>
      </Card>

      {filteredAchievements.length === 0 ? (
        <Card className="border-0 shadow-lg">
          <CardContent className="p-12 text-center">
            <Trophy className="w-16 h-16 mx-auto mb-4 text-gray-300" />
            <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
              暂无成就
            </h3>
            <p className="text-gray-500">开始学习和做题来解锁成就吧！</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filteredAchievements.map((item, index) => {
            const Icon = getAchievementIcon(item);
            const rarity = item.achievement.rarity?.toLowerCase() || "common";
            const config = getRarityConfig(rarity);
            const unlocked = isUnlocked(item);
            const progressPercent = Math.min(
              (item.progressCurrent / item.progressTarget) * 100,
              100
            );

            return (
              <motion.div
                key={item.id}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
              >
                <Card
                  className={`overflow-hidden transition-all hover:shadow-lg ${
                    unlocked
                      ? `border-2 ${config.borderColor}`
                      : "border border-gray-200 dark:border-gray-700 opacity-60"
                  }`}
                >
                  <div
                    className={`h-28 flex items-center justify-center relative ${
                      unlocked ? config.bgColor : "bg-gray-100 dark:bg-gray-800"
                    }`}
                  >
                    <div
                      className={`text-5xl ${unlocked ? config.color : "text-gray-400"}`}
                    >
                      <Icon className="w-12 h-12" />
                    </div>
                    {!unlocked && (
                      <div className="absolute inset-0 bg-white/70 dark:bg-gray-900/70 flex items-center justify-center">
                        <span className="text-4xl text-gray-400 font-bold">?</span>
                      </div>
                    )}
                  </div>

                  <CardContent className="p-4">
                    <div className="flex items-center justify-between mb-2">
                      <h3 className="font-bold text-gray-900 dark:text-white truncate flex-1">
                        {item.achievement.name}
                      </h3>
                      {unlocked && (
                        <Badge
                          variant="outline"
                          className="ml-2 bg-green-50 text-green-600 border-green-200 dark:bg-green-950/30 dark:text-green-400 dark:border-green-800"
                        >
                          已解锁
                        </Badge>
                      )}
                    </div>

                    <p className="text-sm text-gray-500 dark:text-gray-400 line-clamp-2 mb-3">
                      {item.achievement.description}
                    </p>

                    <div className="flex items-center justify-between text-xs">
                      {unlocked ? (
                        <span className="text-gray-500 dark:text-gray-400">
                          {item.unlockedAt
                            ? new Date(item.unlockedAt).toLocaleDateString("zh-CN")
                            : "刚刚解锁"}
                        </span>
                      ) : (
                        <span className="text-gray-500 dark:text-gray-400">
                          进度: {item.progressCurrent}/{item.progressTarget}
                        </span>
                      )}

                      <span className="text-yellow-600 dark:text-yellow-400 font-medium">
                        +{item.achievement.pointsAwarded}分
                      </span>
                    </div>

                    {item.achievement.coinsAwarded > 0 && (
                      <div className="mt-2 text-xs text-amber-600 dark:text-amber-400">
                        +{item.achievement.coinsAwarded} 积分
                      </div>
                    )}

                    {!unlocked && item.progressCurrent > 0 && (
                      <div className="mt-3">
                        <div className="h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-indigo-500 rounded-full transition-all"
                            style={{ width: `${progressPercent}%` }}
                          />
                        </div>
                      </div>
                    )}
                  </CardContent>
                </Card>
              </motion.div>
            );
          })}
        </div>
      )}
    </motion.div>
  );
}

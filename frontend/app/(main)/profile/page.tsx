"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import {
  User as UserIcon,
  Mail,
  Phone,
  GraduationCap,
  BookOpen,
  Edit2,
  Save,
  Camera,
  Loader2,
  Trophy,
  Target,
  X,
  Flame,
  Star,
  Zap,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Slider } from "@/components/ui/slider";
import { Progress } from "@/components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useAuthStore } from "@/stores/authStore";
import {
  userService,
  type User,
  type UserStats,
  type UserProfile,
} from "@/services/userService";
import { useToast } from "@/hooks/use-toast";

interface ApiResponse<T> {
  code: number;
  message?: string;
  msg?: string;
  data: T;
  timestamp: number;
}

export default function ProfilePage() {
  const { t } = useTranslation();
  const { user: authUser, setUser } = useAuthStore();
  const { toast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [user, setUserState] = useState<User | null>(null);
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null);
  const [stats, setStats] = useState<UserStats | null>(null);
  const [formData, setFormData] = useState({
    username: "",
    email: "",
    phone: "",
    realName: "",
    school: "",
    major: "",
    grade: "",
    programmingLanguage: "python",
    difficultyPreference: "adaptive",
    dailyGoalMinutes: 60,
    learningStyle: "visual",
    weeklyLearningDays: 5,
  });
  const [originalFormData, setOriginalFormData] = useState(formData);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      const userId = authUser?.id;
      if (!userId) {
        console.error("用户ID不存在");
        toast({
          title: "加载失败",
          description: "用户未登录",
          variant: "destructive",
        });
        return;
      }

      const [userResponse, userProfileResponse] = await Promise.all([
        userService.getUserById(userId),
        userService.getUserProfile(userId),
      ]);

      const userData = userResponse?.data;
      const userProfileData = userProfileResponse?.data;

      if (userData) {
        setUserState(userData);
        setFormData((prev) => ({
          ...prev,
          username: userData.username || "",
          email: userData.email || "",
          phone: userData.phone || "",
          realName: userData.realName || "",
          school: userData.school || "",
          major: userData.major || "",
          grade: userData.grade || "",
        }));
      }

      if (userProfileData) {
        setUserProfile(userProfileData);
        setFormData((prev) => ({
          ...prev,
          programmingLanguage: userProfileData.programmingLanguage || "python",
          difficultyPreference: userProfileData.difficultyPreference || "adaptive",
          dailyGoalMinutes: userProfileData.dailyGoalMinutes || 60,
          learningStyle: userProfileData.learningStyle || "visual",
          weeklyLearningDays: userProfileData.weeklyLearningDays || 5,
        }));
      }
    } catch (error) {
      console.error("获取用户信息失败:", error);
      toast({
        title: "加载失败",
        description: "无法获取用户信息",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [toast, authUser?.id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  useEffect(() => {
    if (!loading) {
      setOriginalFormData(formData);
    }
  }, [loading]);

  const handleSave = async () => {
    try {
      setSaving(true);

      const errors: string[] = [];

      if (formData.realName && formData.realName.length > 50) {
        errors.push("真实姓名不能超过50个字符");
      }

      if (formData.phone && !/^1[3-9]\d{9}$/.test(formData.phone)) {
        errors.push("请输入正确的手机号格式");
      }

      if (formData.school && formData.school.length > 100) {
        errors.push("学校名称不能超过100个字符");
      }

      if (formData.major && formData.major.length > 100) {
        errors.push("专业名称不能超过100个字符");
      }

      if (errors.length > 0) {
        toast({
          title: "表单验证失败",
          description: errors.join("; "),
          variant: "destructive",
        });
        return;
      }

      if (authUser?.id) {
        const userUpdateData = {
          realName: formData.realName,
          phone: formData.phone,
          school: formData.school,
          major: formData.major,
        };

        await userService.updateUser(authUser.id, userUpdateData);

        const profileUpdateData = {
          programmingLanguage: formData.programmingLanguage,
          difficultyPreference: formData.difficultyPreference as
            | "easy"
            | "medium"
            | "hard"
            | "adaptive",
          dailyGoalMinutes: formData.dailyGoalMinutes,
          learningStyle: formData.learningStyle as
            | "visual"
            | "auditory"
            | "reading"
            | "kinesthetic",
          weeklyLearningDays: formData.weeklyLearningDays,
        };

        const profileResponse = await userService.updateUserProfile(
          authUser.id,
          profileUpdateData
        );

        if (profileResponse?.data) {
          setUserProfile(profileResponse.data);
        }

        const refreshedUserResponse = await userService.getUserById(authUser.id);
        const refreshedUserData = refreshedUserResponse?.data;
        if (refreshedUserData) {
          setUserState(refreshedUserData);
          if (authUser) {
            setUser({ ...authUser, ...refreshedUserData });
          }
        }
      }

      toast({
        title: "保存成功",
        description: "个人信息已更新",
      });
      setIsEditing(false);
      setOriginalFormData(formData);
    } catch (error) {
      console.error("保存用户信息失败:", error);
      toast({
        title: "保存失败",
        description: "请稍后重试",
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setFormData(originalFormData);
    setIsEditing(false);
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const allowedTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];
    if (!allowedTypes.includes(file.type)) {
      toast({
        title: "文件格式错误",
        description: "请上传 JPG、PNG、GIF 或 WEBP 格式的图片",
        variant: "destructive",
      });
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      toast({
        title: "文件过大",
        description: "图片大小不能超过 5MB",
        variant: "destructive",
      });
      return;
    }

    try {
      setUploadingAvatar(true);
      const response = await userService.uploadAvatar(file);

      if (response?.code === 200 && response?.data?.url && user) {
        const updatedUser: User = { ...user, avatarUrl: response.data.url };
        setUserState(updatedUser);
        if (authUser) {
          setUser({ ...authUser, avatarUrl: response.data.url });
        }
        toast({
          title: "头像更新成功",
          description: "您的头像已更新",
        });
      } else if (response?.code !== 200) {
        toast({
          title: "上传失败",
          description: response?.msg || "头像更新失败",
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error("上传头像失败:", error);
      toast({
        title: "上传失败",
        description: "无法上传头像，请稍后重试",
        variant: "destructive",
      });
    } finally {
      setUploadingAvatar(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const userStats = [
    {
      label: "学习等级",
      value: user?.learningLevel || 1,
      icon: GraduationCap,
      color: "text-green-600",
    },
    {
      label: "已解决问题",
      value: stats?.solvedProblems || (user?.experiencePoints
        ? Math.floor(user.experiencePoints / 10)
        : 0),
      icon: BookOpen,
      color: "text-green-600",
    },
    {
      label: "连续学习",
      value: stats?.learningStreak || user?.dailyStreak || 0,
      icon: Flame,
      color: "text-orange-500",
    },
    {
      label: "经验值",
      value: stats?.experiencePoints || user?.experiencePoints || 0,
      icon: Zap,
      color: "text-blue-600",
    },
    {
      label: "成就数",
      value: stats?.achievements || 0,
      icon: Trophy,
      color: "text-yellow-500",
    },
    {
      label: "积分",
      value: user?.coins || 0,
      icon: Star,
      color: "text-purple-500",
    },
    {
      label: "成就分数",
      value: user?.achievementScore || 0,
      icon: Target,
      color: "text-indigo-500",
    },
  ];

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
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
            {t("nav.profile") || "个人中心"}
          </h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">
            管理您的个人信息和账户设置
          </p>
        </div>
        <div className="flex gap-2">
          {isEditing && (
            <Button variant="outline" onClick={handleCancel} disabled={saving}>
              <X className="w-4 h-4 mr-2" />
              取消
            </Button>
          )}
          <Button
            variant={isEditing ? "default" : "outline"}
            onClick={() => (isEditing ? handleSave() : setIsEditing(true))}
            disabled={saving}
          >
            {saving ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                保存中...
              </>
            ) : isEditing ? (
              <>
                <Save className="w-4 h-4 mr-2" />
                保存
              </>
            ) : (
              <>
                <Edit2 className="w-4 h-4 mr-2" />
                编辑
              </>
            )}
          </Button>
        </div>
      </div>

      <Tabs defaultValue="profile" className="space-y-6">
        <TabsList>
          <TabsTrigger value="profile">个人资料</TabsTrigger>
          <TabsTrigger value="preferences">学习偏好</TabsTrigger>
        </TabsList>

        <TabsContent value="profile" className="space-y-6">
          <Card className="border-0 shadow-lg">
            <CardContent className="p-6">
              <div className="flex flex-col md:flex-row gap-6">
                <div className="flex flex-col items-center">
                  <div className="relative">
                    <div className="w-32 h-32 rounded-full bg-gradient-to-br from-indigo-500 to-purple-500 flex items-center justify-center text-white text-4xl font-bold overflow-hidden">
                      {user?.avatarUrl ? (
                        <img
                          src={user.avatarUrl}
                          alt={user.username}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        user?.username?.[0]?.toUpperCase() || "U"
                      )}
                    </div>
                    <button
                      onClick={handleAvatarClick}
                      disabled={uploadingAvatar}
                      className="absolute bottom-0 right-0 w-10 h-10 bg-white dark:bg-gray-800 rounded-full shadow-lg flex items-center justify-center text-gray-600 hover:text-indigo-600 transition-colors disabled:opacity-50"
                    >
                      {uploadingAvatar ? (
                        <Loader2 className="w-5 h-5 animate-spin" />
                      ) : (
                        <Camera className="w-5 h-5" />
                      )}
                    </button>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      onChange={handleAvatarUpload}
                      className="hidden"
                    />
                  </div>
                  <h2 className="mt-4 text-xl font-bold text-gray-900 dark:text-white">
                    {user?.username}
                  </h2>
                  <p className="text-gray-500">{user?.email}</p>
                  <div className="mt-2 px-3 py-1 bg-indigo-100 dark:bg-indigo-950/50 text-indigo-700 dark:text-indigo-400 rounded-full text-sm">
                    等级 {user?.learningLevel || 1}
                  </div>
                </div>

                <div className="flex-1 grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label className="flex items-center gap-2">
                      <UserIcon className="w-4 h-4" />
                      用户名
                    </Label>
                    <Input value={formData.username} disabled={true} />
                  </div>

                  <div className="space-y-2">
                    <Label className="flex items-center gap-2">
                      <Mail className="w-4 h-4" />
                      邮箱
                    </Label>
                    <Input
                      type="email"
                      value={formData.email}
                      disabled={true}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label className="flex items-center gap-2">
                      <Phone className="w-4 h-4" />
                      手机号
                    </Label>
                    <Input
                      value={formData.phone}
                      onChange={(e) =>
                        setFormData({ ...formData, phone: e.target.value })
                      }
                      disabled={!isEditing}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label className="flex items-center gap-2">
                      <UserIcon className="w-4 h-4" />
                      真实姓名
                    </Label>
                    <Input
                      value={formData.realName}
                      onChange={(e) =>
                        setFormData({ ...formData, realName: e.target.value })
                      }
                      disabled={!isEditing}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label className="flex items-center gap-2">
                      <GraduationCap className="w-4 h-4" />
                      学校
                    </Label>
                    <Input
                      value={formData.school}
                      onChange={(e) =>
                        setFormData({ ...formData, school: e.target.value })
                      }
                      disabled={!isEditing}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label className="flex items-center gap-2">
                      <BookOpen className="w-4 h-4" />
                      专业
                    </Label>
                    <Input
                      value={formData.major}
                      onChange={(e) =>
                        setFormData({ ...formData, major: e.target.value })
                      }
                      disabled={!isEditing}
                    />
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            {userStats.map((stat, index) => (
              <motion.div
                key={index}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.1 }}
              >
                <Card className="border-0 shadow-md">
                  <CardContent className="p-4 text-center">
                    <stat.icon className={`w-8 h-8 mx-auto mb-2 ${stat.color}`} />
                    <p className="text-2xl font-bold text-gray-900 dark:text-white">
                      {stat.value}
                    </p>
                    <p className="text-sm text-gray-500">{stat.label}</p>
                  </CardContent>
                </Card>
              </motion.div>
            ))}
          </div>

          <Card className="border-0 shadow-lg">
            <CardHeader>
              <CardTitle className="text-lg">学习进度</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600 dark:text-gray-400">本周进度</span>
                  <span className="font-medium">65%</span>
                </div>
                <Progress value={65} className="h-2" />
              </div>
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600 dark:text-gray-400">本月进度</span>
                  <span className="font-medium">45%</span>
                </div>
                <Progress value={45} className="h-2" />
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="preferences" className="space-y-6">
          <Card className="border-0 shadow-lg">
            <CardHeader>
              <CardTitle className="text-lg">学习偏好设置</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-2">
                  <Label>首选编程语言</Label>
                  <Select
                    value={formData.programmingLanguage}
                    onValueChange={(value) =>
                      setFormData({ ...formData, programmingLanguage: value })
                    }
                    disabled={!isEditing}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="选择编程语言" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="python">Python</SelectItem>
                      <SelectItem value="javascript">JavaScript</SelectItem>
                      <SelectItem value="java">Java</SelectItem>
                      <SelectItem value="cpp">C++</SelectItem>
                      <SelectItem value="csharp">C#</SelectItem>
                      <SelectItem value="go">Go</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>难度偏好</Label>
                  <Select
                    value={formData.difficultyPreference}
                    onValueChange={(value) =>
                      setFormData({ ...formData, difficultyPreference: value })
                    }
                    disabled={!isEditing}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="选择难度偏好" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="easy">简单</SelectItem>
                      <SelectItem value="medium">中等</SelectItem>
                      <SelectItem value="hard">困难</SelectItem>
                      <SelectItem value="adaptive">自适应</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>学习风格</Label>
                  <Select
                    value={formData.learningStyle}
                    onValueChange={(value) =>
                      setFormData({ ...formData, learningStyle: value })
                    }
                    disabled={!isEditing}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="选择学习风格" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="visual">视觉型</SelectItem>
                      <SelectItem value="auditory">听觉型</SelectItem>
                      <SelectItem value="reading">阅读型</SelectItem>
                      <SelectItem value="kinesthetic">动手型</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label>每周学习天数: {formData.weeklyLearningDays} 天</Label>
                  <Slider
                    value={[formData.weeklyLearningDays]}
                    onValueChange={(value: number[]) =>
                      setFormData({ ...formData, weeklyLearningDays: value[0] })
                    }
                    min={1}
                    max={7}
                    step={1}
                    disabled={!isEditing}
                    className="mt-2"
                  />
                  <div className="flex justify-between text-xs text-gray-500">
                    <span>1天</span>
                    <span>7天</span>
                  </div>
                </div>
              </div>

              <div className="space-y-2">
                <Label>
                  每日学习目标: {formData.dailyGoalMinutes} 分钟
                </Label>
                <Slider
                  value={[formData.dailyGoalMinutes]}
                  onValueChange={(value: number[]) =>
                    setFormData({ ...formData, dailyGoalMinutes: value[0] })
                  }
                  min={15}
                  max={180}
                  step={15}
                  disabled={!isEditing}
                  className="mt-2"
                />
                <div className="flex justify-between text-xs text-gray-500">
                  <span>15分钟</span>
                  <span>3小时</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </motion.div>
  );
}

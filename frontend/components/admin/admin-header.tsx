"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { useAuthStore } from "@/stores/authStore";
import { cn } from "@/lib/utils";
import {
  BellOutlined,
  UserOutlined,
  LogoutOutlined,
  SettingOutlined,
  HomeOutlined,
} from "@ant-design/icons";
import {
  Dropdown,
  Avatar,
  Badge,
  Button,
  Tooltip,
} from "antd";

export function AdminHeader() {
  const router = useRouter();
  const { user, logout } = useAuthStore();
  const [notifications] = useState(3);

  const handleLogout = () => {
    logout();
    router.push("/login");
  };

  const userMenuItems = [
    {
      key: "profile",
      icon: <UserOutlined />,
      label: <Link href="/profile">个人资料</Link>,
    },
    {
      key: "settings",
      icon: <SettingOutlined />,
      label: <Link href="/settings">账号设置</Link>,
    },
    { type: "divider" as const },
    {
      key: "logout",
      icon: <LogoutOutlined />,
      label: "退出登录",
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <header className="h-16 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between px-6">
      {/* Left Side */}
      <div className="flex items-center gap-4">
        <Tooltip title="返回前台">
          <Link href="/dashboard">
            <Button type="text" icon={<HomeOutlined />}>
              前台
            </Button>
          </Link>
        </Tooltip>
        <span className="text-gray-300 dark:text-gray-600">|</span>
        <h1 className="text-lg font-medium text-gray-800 dark:text-gray-200">
          管理后台
        </h1>
      </div>

      {/* Right Side */}
      <div className="flex items-center gap-4">
        {/* Notifications */}
        <Tooltip title="通知">
          <Badge count={notifications} size="small">
            <Button type="text" icon={<BellOutlined />} />
          </Badge>
        </Tooltip>

        {/* User Menu */}
        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
          <Button type="text" className="flex items-center gap-2">
            <Avatar
              size="small"
              icon={<UserOutlined />}
              src={user?.avatarUrl}
              className="bg-indigo-500"
            />
            <span className="hidden md:inline text-sm text-gray-700 dark:text-gray-300">
              {user?.username || "管理员"}
            </span>
          </Button>
        </Dropdown>
      </div>
    </header>
  );
}

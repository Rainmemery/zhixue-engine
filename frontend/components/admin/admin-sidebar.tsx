"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import {
  DashboardOutlined,
  UserOutlined,
  FileTextOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BookOutlined,
  ClusterOutlined,
  MonitorOutlined,
  ApiOutlined,
} from "@ant-design/icons";

interface NavItem {
  key: string;
  label: string;
  icon: React.ElementType;
  href: string;
}

const navItems: NavItem[] = [
  { key: "dashboard", label: "仪表板", icon: DashboardOutlined, href: "/admin" },
  { key: "users", label: "用户管理", icon: UserOutlined, href: "/admin/users" },
  { key: "problems", label: "题目管理", icon: FileTextOutlined, href: "/admin/problems" },
  { key: "models", label: "模型组管理", icon: ClusterOutlined, href: "/admin/models" },
  { key: "model-instances", label: "模型实例管理", icon: ApiOutlined, href: "/admin/model-instances" },
  { key: "dispatcher-monitor", label: "调度监控", icon: MonitorOutlined, href: "/admin/dispatcher/monitor" },
  { key: "rag", label: "RAG管理", icon: BookOutlined, href: "/admin/rag" },
];

export function AdminSidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);

  return (
    <motion.aside
      initial={false}
      animate={{ width: collapsed ? 80 : 240 }}
      className={cn(
        "h-full bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700",
        "flex flex-col transition-all duration-300"
      )}
    >
      {/* Logo */}
      <div className="h-16 flex items-center justify-center border-b border-gray-200 dark:border-gray-700">
        {collapsed ? (
          <span className="text-xl font-bold text-indigo-600">管</span>
        ) : (
          <span className="text-lg font-bold text-indigo-600">管理后台</span>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 py-4 overflow-y-auto">
        <ul className="space-y-1 px-3">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);

            return (
              <li key={item.key}>
                <Link
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200",
                    "hover:bg-gray-100 dark:hover:bg-gray-700",
                    isActive
                      ? "bg-indigo-50 dark:bg-indigo-950/30 text-indigo-600 dark:text-indigo-400"
                      : "text-gray-600 dark:text-gray-400"
                  )}
                >
                  <Icon className={cn("text-lg", isActive && "text-indigo-600 dark:text-indigo-400")} />
                  {!collapsed && <span className="text-sm font-medium">{item.label}</span>}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-gray-200 dark:border-gray-700">
        <button
          onClick={() => setCollapsed(!collapsed)}
          className={cn(
            "flex items-center gap-2 px-3 py-2 rounded-lg",
            "text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700",
            "transition-all duration-200 w-full",
            collapsed && "justify-center"
          )}
        >
          {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          {!collapsed && <span className="text-sm">收起菜单</span>}
        </button>
      </div>
    </motion.aside>
  );
}

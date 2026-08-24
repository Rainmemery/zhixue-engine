"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion, AnimatePresence } from "framer-motion";
import { useTranslation } from "react-i18next";
import {
  LayoutDashboard,
  Code2,
  BookOpen,
  MessageSquare,
  Bot,
  HelpCircle,
  User,
  Trophy,
  History,
  LogOut,
  ChevronLeft,
  ChevronRight,
  GraduationCap,
  Moon,
  Sun,
} from "lucide-react";
import { useTheme } from "next-themes";
import { useAuthStore } from "@/stores/authStore";
import { useUIStore } from "@/stores/uiStore";
import { cn } from "@/lib/utils";
import { useReducedMotion } from "@/lib/animations";

const navItems = [
  { href: "/dashboard", icon: LayoutDashboard, label: "nav.dashboard" },
  { href: "/problems", icon: Code2, label: "nav.problems" },
  { href: "/learning", icon: BookOpen, label: "nav.learning" },
  { href: "/ai-chat", icon: MessageSquare, label: "nav.aiChat" },
  { href: "/code-assistant", icon: Bot, label: "nav.codeAssistant" },
  { href: "/smart-questioner", icon: HelpCircle, label: "nav.smartQuestioner" },
];

const userItems = [
  { href: "/profile", icon: User, label: "nav.profile" },
  { href: "/achievements", icon: Trophy, label: "nav.achievements" },
  { href: "/history", icon: History, label: "nav.history" },
];

interface SidebarProps {
  className?: string;
}

export function Sidebar({ className }: SidebarProps) {
  const { t } = useTranslation();
  const pathname = usePathname();
  const { sidebarCollapsed: collapsed, toggleSidebar, _isHydrated } = useUIStore();
  const { theme, setTheme } = useTheme();
  const { user, clearAuth } = useAuthStore();
  const reducedMotion = useReducedMotion();

  // 键盘快捷键支持 (Ctrl+B)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.key === 'b') {
        e.preventDefault();
        toggleSidebar();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [toggleSidebar]);

  const handleLogout = () => {
    clearAuth();
    window.location.href = "/login";
  };

  // 避免 SSR 不匹配，等待 hydration 完成
  if (!_isHydrated) {
    return (
      <aside
        className={cn(
          "fixed left-0 top-0 z-40 h-screen w-[260px] bg-card border-r border-border flex flex-col",
          className
        )}
      />
    );
  }

  return (
    <motion.aside
      initial={false}
      animate={{ width: collapsed ? 80 : 260 }}
      transition={reducedMotion ? { duration: 0 } : { duration: 0.3, ease: "easeInOut" }}
      className={cn(
        "fixed left-0 top-0 z-40 h-screen bg-card/95 backdrop-blur-xl border-r border-border flex flex-col",
        className
      )}
      role="complementary"
      aria-label={t("app.name") + " " + t("nav.sidebar")}
    >
      {/* Logo */}
      <div className="h-16 flex items-center px-4 border-b border-border">
        <Link href="/dashboard" className="flex items-center gap-3 overflow-hidden focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 dark:focus:ring-offset-background rounded-lg">
          <div className="w-10 h-10 bg-gradient-to-br from-primary to-accent rounded-xl flex items-center justify-center flex-shrink-0 shadow-lg shadow-primary/20">
            <GraduationCap className="w-6 h-6 text-primary-foreground" aria-hidden="true" />
          </div>
          <AnimatePresence>
            {!collapsed && (
              <motion.span
                initial={reducedMotion ? { opacity: 1, width: "auto" } : { opacity: 0, width: 0 }}
                animate={{ opacity: 1, width: "auto" }}
                exit={reducedMotion ? { opacity: 0, width: 0 } : { opacity: 0, width: 0 }}
                transition={reducedMotion ? { duration: 0 } : undefined}
                className="font-bold text-xl bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent whitespace-nowrap"
              >
                {t("app.name")}
              </motion.span>
            )}
          </AnimatePresence>
        </Link>
      </div>

      {/* Main Navigation */}
      <nav className="flex-1 py-4 px-3 space-y-1 overflow-y-auto" role="navigation" aria-label={t("nav.mainNavigation")}>
        {navItems.map((item, index) => {
          const Icon = item.icon;
          const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);

          return (
            <Link 
              key={item.href} 
              href={item.href} 
              aria-current={isActive ? "page" : undefined}
              className="block"
            >
              <motion.div
                initial={false}
                whileHover={reducedMotion ? undefined : { x: 3 }}
                whileTap={reducedMotion ? undefined : { scale: 0.97 }}
                className={cn(
                  "flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 relative",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-md shadow-primary/20"
                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                )}
              >
                {isActive && (
                  <motion.div
                    layoutId="activeNavIndicator"
                    className="absolute inset-0 bg-primary rounded-xl pointer-events-none"
                    transition={{ type: "spring", stiffness: 380, damping: 30 }}
                  />
                )}
                <Icon className={cn("w-5 h-5 flex-shrink-0 relative z-10", isActive && "text-primary-foreground")} aria-hidden="true" />
                <AnimatePresence>
                  {!collapsed && (
                    <motion.span
                      initial={reducedMotion ? { opacity: 1, width: "auto" } : { opacity: 0, width: 0 }}
                      animate={{ opacity: 1, width: "auto" }}
                      exit={reducedMotion ? { opacity: 0, width: 0 } : { opacity: 0, width: 0 }}
                      transition={reducedMotion ? { duration: 0 } : undefined}
                      className="whitespace-nowrap font-medium relative z-10"
                    >
                      {t(item.label)}
                    </motion.span>
                  )}
                </AnimatePresence>
              </motion.div>
            </Link>
          );
        })}

        {/* User Section */}
        <div className="pt-4 mt-4 border-t border-border" role="group" aria-label={t("nav.userSection")}>
          <AnimatePresence>
            {!collapsed && (
              <motion.p
                initial={reducedMotion ? { opacity: 1 } : { opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={reducedMotion ? { opacity: 0 } : { opacity: 0 }}
                transition={reducedMotion ? { duration: 0 } : undefined}
                className="px-3 mb-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider"
              >
                {t("nav.userSection")}
              </motion.p>
            )}
          </AnimatePresence>

          {userItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href;

            return (
              <Link 
                key={item.href} 
                href={item.href} 
                aria-current={isActive ? "page" : undefined}
                className="block"
              >
                <motion.div
                  whileHover={reducedMotion ? undefined : { x: 3 }}
                  whileTap={reducedMotion ? undefined : { scale: 0.97 }}
                  className={cn(
                    "flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 relative",
                    isActive
                      ? "bg-primary text-primary-foreground shadow-md shadow-primary/20"
                      : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  )}
                >
                  {isActive && (
                    <motion.div
                      layoutId="activeUserNavIndicator"
                      className="absolute inset-0 bg-primary rounded-xl pointer-events-none"
                      transition={{ type: "spring", stiffness: 380, damping: 30 }}
                    />
                  )}
                  <Icon className={cn("w-5 h-5 flex-shrink-0 relative z-10", isActive && "text-primary-foreground")} aria-hidden="true" />
                  <AnimatePresence>
                    {!collapsed && (
                      <motion.span
                        initial={reducedMotion ? { opacity: 1, width: "auto" } : { opacity: 0, width: 0 }}
                        animate={{ opacity: 1, width: "auto" }}
                        exit={reducedMotion ? { opacity: 0, width: 0 } : { opacity: 0, width: 0 }}
                        transition={reducedMotion ? { duration: 0 } : undefined}
                        className="whitespace-nowrap font-medium relative z-10"
                      >
                        {t(item.label)}
                      </motion.span>
                    )}
                  </AnimatePresence>
                </motion.div>
              </Link>
            );
          })}
        </div>
      </nav>

      {/* Bottom Section */}
      <div className="p-3 border-t border-border space-y-1">
        {/* Theme Toggle */}
        <motion.button
          whileHover={reducedMotion ? undefined : { x: 3 }}
          whileTap={reducedMotion ? undefined : { scale: 0.97 }}
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 dark:focus:ring-offset-background"
          aria-label={theme === "dark" ? t("theme.switchToLight") : t("theme.switchToDark")}
        >
          {theme === "dark" ? (
            <Sun className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
          ) : (
            <Moon className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
          )}
          <AnimatePresence>
            {!collapsed && (
              <motion.span
                initial={reducedMotion ? { opacity: 1, width: "auto" } : { opacity: 0, width: 0 }}
                animate={{ opacity: 1, width: "auto" }}
                exit={reducedMotion ? { opacity: 0, width: 0 } : { opacity: 0, width: 0 }}
                transition={reducedMotion ? { duration: 0 } : undefined}
                className="whitespace-nowrap font-medium"
              >
                {theme === "dark" ? t("theme.switchToLight") : t("theme.switchToDark")}
              </motion.span>
            )}
          </AnimatePresence>
        </motion.button>

        {/* Logout */}
        <motion.button
          whileHover={reducedMotion ? undefined : { x: 3 }}
          whileTap={reducedMotion ? undefined : { scale: 0.97 }}
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-destructive hover:bg-destructive/10 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-destructive focus:ring-offset-1 dark:focus:ring-offset-background"
          aria-label={t("nav.logout")}
        >
          <LogOut className="w-5 h-5 flex-shrink-0" aria-hidden="true" />
          <AnimatePresence>
            {!collapsed && (
              <motion.span
                initial={reducedMotion ? { opacity: 1, width: "auto" } : { opacity: 0, width: 0 }}
                animate={{ opacity: 1, width: "auto" }}
                exit={reducedMotion ? { opacity: 0, width: 0 } : { opacity: 0, width: 0 }}
                transition={reducedMotion ? { duration: 0 } : undefined}
                className="whitespace-nowrap font-medium"
              >
                {t("nav.logout")}
              </motion.span>
            )}
          </AnimatePresence>
        </motion.button>

        {/* Collapse Button */}
        <motion.button
          whileHover={reducedMotion ? undefined : { scale: 1.05 }}
          whileTap={reducedMotion ? undefined : { scale: 0.95 }}
          onClick={toggleSidebar}
          className="w-full flex items-center justify-center p-2 rounded-xl text-muted-foreground hover:text-foreground hover:bg-accent transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1 dark:focus:ring-offset-background"
          aria-label={collapsed ? t("sidebar.expand") : t("sidebar.collapse")}
          aria-expanded={!collapsed}
          title={t("sidebar.keyboardShortcut")}
        >
          {collapsed ? <ChevronRight className="w-5 h-5" aria-hidden="true" /> : <ChevronLeft className="w-5 h-5" aria-hidden="true" />}
        </motion.button>
      </div>
    </motion.aside>
  );
}

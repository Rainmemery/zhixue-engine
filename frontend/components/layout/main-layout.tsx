"use client";

import { useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { motion } from "framer-motion";
import { Sidebar } from "./sidebar";
import { useAuthStore } from "@/stores/authStore";
import { useUIStore } from "@/stores/uiStore";
import { SkeletonStats } from "@/components/ui/skeleton";
import { webSocketService } from "@/services/websocketService";

interface MainLayoutProps {
  children: React.ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, isLoading, user, token } = useAuthStore();
  const { sidebarCollapsed, _isHydrated } = useUIStore();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push("/login");
    }
  }, [isAuthenticated, isLoading, router]);

  useEffect(() => {
    if (isAuthenticated && user && !isLoading) {
      webSocketService.connect(String(user.id), token ?? undefined);
    }

    return () => {
      if (!isAuthenticated) {
        webSocketService.disconnect();
      }
    };
  }, [isAuthenticated, user, token, isLoading]);

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-indigo-50/80 via-background to-cyan-50/80 dark:from-gray-950 dark:via-gray-900 dark:to-indigo-950/50">
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.3 }}
          className="text-center"
        >
          <div className="relative w-16 h-16 mx-auto mb-6">
            <div className="absolute inset-0 rounded-full border-4 border-primary/20" />
            <div className="absolute inset-0 rounded-full border-4 border-primary border-t-transparent animate-spin" />
          </div>
          <h1 className="text-2xl font-bold bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">
            智学引擎
          </h1>
          <p className="text-muted-foreground mt-2 text-sm">加载中...</p>
        </motion.div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  const sidebarWidth = sidebarCollapsed ? 80 : 260;

  return (
    <div className="min-h-screen bg-background">
      <Sidebar />
      <main
        className="transition-all duration-300 ease-in-out min-h-screen"
        style={{ marginLeft: sidebarWidth }}
      >
        <motion.div
          key={pathname}
          initial={{ opacity: 0, y: 15 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: "easeOut" }}
          className="p-4 sm:p-6 lg:p-8 max-w-[1600px] mx-auto"
        >
          {children}
        </motion.div>
      </main>
    </div>
  );
}

"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore, initializeAuth } from "@/stores/authStore";
import { motion } from "framer-motion";

export default function Home() {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuthStore();
  const [timeoutReached, setTimeoutReached] = useState(false);

  useEffect(() => {
    initializeAuth();
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      setTimeoutReached(true);
    }, 2000);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (!isLoading || timeoutReached) {
      if (isAuthenticated) {
        router.push("/dashboard");
      } else {
        router.push("/login");
      }
    }
  }, [isAuthenticated, isLoading, timeoutReached, router]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-indigo-50 via-white to-purple-50 dark:from-gray-900 dark:via-gray-900 dark:to-indigo-950">
      <motion.div
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5 }}
        className="text-center"
      >
        <div className="w-16 h-16 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
        <h1 className="text-2xl font-bold text-gray-800 dark:text-white">
          智学引擎
        </h1>
        <p className="text-gray-600 dark:text-gray-400 mt-2">加载中...</p>
      </motion.div>
    </div>
  );
}

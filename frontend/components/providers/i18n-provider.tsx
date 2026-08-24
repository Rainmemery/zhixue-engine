"use client";

import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
// 静态导入i18n配置，确保在组件渲染前完成初始化
import "@/lib/i18n";

interface I18nProviderProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

function I18nInitializer({ children }: { children: React.ReactNode }) {
  const { i18n } = useTranslation();
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    // 确保i18n初始化完成
    if (i18n.isInitialized) {
      setInitialized(true);
    } else {
      const handleInitialized = () => setInitialized(true);
      i18n.on('initialized', handleInitialized);
      // 如果已经初始化，立即设置状态
      if (i18n.isInitialized) {
        setInitialized(true);
      }
      return () => {
        i18n.off('initialized', handleInitialized);
      };
    }
  }, [i18n]);

  // 等待i18n初始化完成，期间可以显示加载状态或空内容
  if (!initialized && !i18n.isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-pulse text-gray-400">加载中...</div>
      </div>
    );
  }

  return <>{children}</>;
}

export function I18nProvider({ children, fallback }: I18nProviderProps) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  // 服务端渲染时直接返回children，避免hydration mismatch
  if (!mounted) {
    return <>{fallback || children}</>;
  }

  return (
    <I18nInitializer>
      {children}
    </I18nInitializer>
  );
}

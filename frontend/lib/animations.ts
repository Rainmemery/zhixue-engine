"use client";

import { useEffect, useState } from "react";
import { Variants, Transition } from "framer-motion";

// ============================================
// 缓动曲线配置
// ============================================

export const easings = {
  // 标准缓动
  easeOut: [0, 0, 0.2, 1],
  easeIn: [0.4, 0, 1, 1],
  easeInOut: [0.4, 0, 0.2, 1],

  // 弹性缓动
  spring: { type: "spring", stiffness: 300, damping: 30 },
  bouncy: { type: "spring", stiffness: 400, damping: 10 },
  stiff: { type: "spring", stiffness: 500, damping: 50 },
  gentle: { type: "spring", stiffness: 120, damping: 14 },

  // 自定义贝塞尔曲线
  smooth: [0.43, 0.13, 0.23, 0.96],
  snap: [0.22, 1, 0.36, 1],
  bounce: [0.68, -0.55, 0.265, 1.55],
  anticipate: [0.36, 0, 0.66, -0.56],
  decelerate: [0, 0.55, 0.45, 1],
} as const;

// ============================================
// 通用过渡配置
// ============================================

export const defaultTransition: Transition = {
  duration: 0.3,
  ease: easings.easeInOut,
};

export const springTransition: Transition = {
  type: "spring",
  stiffness: 300,
  damping: 30,
};

export const bounceTransition: Transition = {
  type: "spring",
  stiffness: 400,
  damping: 10,
};

export const slowTransition: Transition = {
  duration: 0.5,
  ease: easings.smooth,
};

export const fastTransition: Transition = {
  duration: 0.15,
  ease: easings.easeOut,
};

// ============================================
// 动画变体
// ============================================

// 淡入动画
export const fadeIn: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: defaultTransition,
  },
  exit: { opacity: 0, transition: fastTransition },
};

// 从上方滑入
export const slideInTop: Variants = {
  hidden: { opacity: 0, y: -20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: defaultTransition,
  },
  exit: { opacity: 0, y: -20, transition: fastTransition },
};

// 从下方滑入
export const slideInBottom: Variants = {
  hidden: { opacity: 0, y: 20 },
  visible: {
    opacity: 1,
    y: 0,
    transition: defaultTransition,
  },
  exit: { opacity: 0, y: 20, transition: fastTransition },
};

// 从左侧滑入
export const slideInLeft: Variants = {
  hidden: { opacity: 0, x: -20 },
  visible: {
    opacity: 1,
    x: 0,
    transition: defaultTransition,
  },
  exit: { opacity: 0, x: -20, transition: fastTransition },
};

// 从右侧滑入
export const slideInRight: Variants = {
  hidden: { opacity: 0, x: 20 },
  visible: {
    opacity: 1,
    x: 0,
    transition: defaultTransition,
  },
  exit: { opacity: 0, x: 20, transition: fastTransition },
};

// 缩放动画
export const scale: Variants = {
  hidden: { opacity: 0, scale: 0.9 },
  visible: {
    opacity: 1,
    scale: 1,
    transition: springTransition,
  },
  exit: { opacity: 0, scale: 0.9, transition: fastTransition },
};

// 弹跳缩放
export const scaleBounce: Variants = {
  hidden: { opacity: 0, scale: 0 },
  visible: {
    opacity: 1,
    scale: 1,
    transition: bounceTransition,
  },
  exit: { opacity: 0, scale: 0, transition: fastTransition },
};

// 翻转动画
export const flip: Variants = {
  hidden: { opacity: 0, rotateX: -90 },
  visible: {
    opacity: 1,
    rotateX: 0,
    transition: { ...defaultTransition, duration: 0.5 },
  },
  exit: { opacity: 0, rotateX: 90, transition: fastTransition },
};

// 展开动画（用于手风琴、列表等）
export const expand: Variants = {
  hidden: {
    opacity: 0,
    height: 0,
    transition: { duration: 0.2 },
  },
  visible: {
    opacity: 1,
    height: "auto",
    transition: { duration: 0.3, ease: easings.easeOut },
  },
  exit: {
    opacity: 0,
    height: 0,
    transition: { duration: 0.2 },
  },
};

// 宽度展开动画
export const expandWidth: Variants = {
  hidden: {
    opacity: 0,
    width: 0,
  },
  visible: {
    opacity: 1,
    width: "auto",
    transition: { duration: 0.3, ease: easings.easeInOut },
  },
  exit: {
    opacity: 0,
    width: 0,
    transition: { duration: 0.2 },
  },
};

// 列表项交错动画
export const staggerContainer: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05,
      delayChildren: 0.1,
    },
  },
  exit: {
    opacity: 0,
    transition: {
      staggerChildren: 0.03,
      staggerDirection: -1,
    },
  },
};

// 列表项动画
export const staggerItem: Variants = {
  hidden: { opacity: 0, y: 10 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.2, ease: easings.easeOut },
  },
  exit: {
    opacity: 0,
    y: -10,
    transition: { duration: 0.15 },
  },
};

// 页面过渡动画 - 增强
export const pageTransition: Variants = {
  initial: { opacity: 0, y: 15 },
  animate: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.4, ease: easings.smooth },
  },
  exit: {
    opacity: 0,
    y: -15,
    transition: { duration: 0.2 },
  },
};

// 页面过渡 - 带缩放
export const pageTransitionScale: Variants = {
  initial: { opacity: 0, scale: 0.98 },
  animate: {
    opacity: 1,
    scale: 1,
    transition: { duration: 0.3, ease: easings.smooth },
  },
  exit: {
    opacity: 0,
    scale: 0.98,
    transition: { duration: 0.2 },
  },
};

// 模态框/弹窗动画
export const modal: Variants = {
  hidden: {
    opacity: 0,
    scale: 0.95,
    y: 10,
  },
  visible: {
    opacity: 1,
    scale: 1,
    y: 0,
    transition: { duration: 0.2, ease: easings.easeOut },
  },
  exit: {
    opacity: 0,
    scale: 0.95,
    y: 10,
    transition: { duration: 0.15 },
  },
};

// 遮罩层动画
export const overlay: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.2 } },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

// 脉冲动画
export const pulse: Variants = {
  initial: { scale: 1 },
  animate: {
    scale: [1, 1.05, 1],
    transition: {
      duration: 1.5,
      repeat: Infinity,
      ease: "easeInOut",
    },
  },
};

// 摇晃动画
export const shake: Variants = {
  initial: { x: 0 },
  animate: {
    x: [-5, 5, -5, 5, 0],
    transition: {
      duration: 0.4,
      ease: "easeInOut",
    },
  },
};

// 滚动触发动画
export const scrollReveal: Variants = {
  hidden: { opacity: 0, y: 30 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, ease: easings.smooth },
  },
};

// 卡片进入动画
export const cardEnter: Variants = {
  hidden: { opacity: 0, y: 20, scale: 0.95 },
  visible: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { duration: 0.4, ease: easings.easeOut },
  },
};

// 文字渐变动画
export const textGradient: Variants = {
  hidden: { backgroundPosition: "0% 50%" },
  visible: {
    backgroundPosition: ["0% 50%", "100% 50%", "0% 50%"],
    transition: {
      duration: 3,
      repeat: Infinity,
      ease: "linear",
    },
  },
};

// ============================================
// Hover 动画配置
// ============================================

export const buttonHover = {
  scale: 1.02,
  transition: fastTransition,
};

export const buttonTap = {
  scale: 0.96,
  transition: fastTransition,
};

export const cardHover = {
  y: -4,
  boxShadow: "0 12px 24px -8px rgba(0, 0, 0, 0.15)",
  transition: { duration: 0.2, ease: easings.easeOut },
};

export const cardTap = {
  scale: 0.98,
  transition: fastTransition,
};

export const linkHover = {
  x: 2,
  transition: fastTransition,
};

export const iconHover = {
  scale: 1.1,
  rotate: 5,
  transition: springTransition,
};

export const iconTap = {
  scale: 0.9,
  transition: fastTransition,
};

// ============================================
// 布局动画
// ============================================

export const layoutTransition = {
  type: "spring",
  stiffness: 500,
  damping: 35,
};

// ============================================
// Reduced Motion 支持
// ============================================

/**
 * 检测用户是否偏好减少动画
 * 遵循 prefers-reduced-motion 媒体查询
 */
export function useReducedMotion(): boolean {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    setPrefersReducedMotion(mediaQuery.matches);

    const handleChange = (event: MediaQueryListEvent) => {
      setPrefersReducedMotion(event.matches);
    };

    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, []);

  return prefersReducedMotion;
}

/**
 * 根据用户动画偏好返回相应的变体
 * 如果用户偏好减少动画，返回简化版变体
 */
export function useAccessibleVariants(variants: Variants): Variants {
  const reducedMotion = useReducedMotion();

  if (!reducedMotion) return variants;

  // 返回简化版变体，只保留透明度变化
  return {
    hidden: { opacity: 0 },
    visible: { opacity: 1, transition: { duration: 0.1 } },
    exit: { opacity: 0, transition: { duration: 0.1 } },
  };
}

/**
 * 获取无障碍过渡配置
 */
export function useAccessibleTransition(
  transition: Transition = defaultTransition
): Transition {
  const reducedMotion = useReducedMotion();

  if (!reducedMotion) return transition;

  return {
    duration: 0.1,
  };
}

// ============================================
// 动画工具函数
// ============================================

/**
 * 创建延迟动画变体
 */
export function createDelayedVariants(
  baseVariants: Variants,
  delay: number
): Variants {
  return {
    ...baseVariants,
    visible: {
      ...(baseVariants.visible as object),
      transition: {
        ...(baseVariants.visible as { transition?: object }).transition,
        delay,
      },
    },
  };
}

/**
 * 创建自定义过渡配置
 */
export function createTransition(
  duration: number,
  ease: readonly number[] | string = easings.easeInOut,
  delay: number = 0
): Transition {
  return {
    duration,
    ease: ease as Transition["ease"],
    delay,
  };
}

/**
 * 创建交错动画容器
 */
export function createStaggerContainer(
  staggerChildren: number = 0.05,
  delayChildren: number = 0.1
): Variants {
  return {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren,
        delayChildren,
      },
    },
    exit: {
      opacity: 0,
      transition: {
        staggerChildren: staggerChildren * 0.6,
        staggerDirection: -1,
      },
    },
  };
}

/**
 * 创建交错动画子项
 */
export function createStaggerItem(
  y: number = 10,
  duration: number = 0.2
): Variants {
  return {
    hidden: { opacity: 0, y },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration, ease: easings.easeOut },
    },
    exit: {
      opacity: 0,
      y: -y,
      transition: { duration: duration * 0.75 },
    },
  };
}

// ============================================
// 预设动画组合
// ============================================

// 侧边栏折叠动画
export const sidebarCollapse = {
  expanded: { width: 260, transition: { duration: 0.3, ease: easings.easeInOut } },
  collapsed: { width: 80, transition: { duration: 0.3, ease: easings.easeInOut } },
};

// 菜单项动画
export const menuItem = {
  rest: { x: 0 },
  hover: { x: 2, transition: fastTransition },
};

// 下拉菜单动画
export const dropdownMenu = {
  hidden: {
    opacity: 0,
    y: -10,
    scale: 0.95,
  },
  visible: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { duration: 0.15, ease: easings.easeOut },
  },
  exit: {
    opacity: 0,
    y: -10,
    scale: 0.95,
    transition: { duration: 0.1 },
  },
};

// 提示消息动画
export const toastAnimation = {
  initial: { opacity: 0, y: 50, scale: 0.9 },
  animate: {
    opacity: 1,
    y: 0,
    scale: 1,
    transition: { duration: 0.3, ease: easings.spring },
  },
  exit: {
    opacity: 0,
    y: 20,
    scale: 0.9,
    transition: { duration: 0.2 },
  },
};

// 骨架屏加载动画
export const skeletonPulse = {
  animate: {
    opacity: [0.4, 0.8, 0.4],
    transition: {
      duration: 1.5,
      repeat: Infinity,
      ease: "easeInOut",
    },
  },
};

// 进度条动画
export const progressBar = {
  initial: { width: "0%" },
  animate: (progress: number) => ({
    width: `${progress}%`,
    transition: { duration: 0.8, ease: easings.easeOut },
  }),
};

// 打字机效果
export const typewriter = {
  hidden: { width: "0%" },
  visible: {
    width: "100%",
    transition: { duration: 1, ease: "linear" },
  },
};

// 呼吸效果
export const breathe = {
  animate: {
    scale: [1, 1.02, 1],
    opacity: [0.8, 1, 0.8],
    transition: {
      duration: 2,
      repeat: Infinity,
      ease: "easeInOut",
    },
  },
};

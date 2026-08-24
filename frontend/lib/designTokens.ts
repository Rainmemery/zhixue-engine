/**
 * Design Tokens
 * 统一设计令牌 - TypeScript 常量定义
 * 与 Tailwind CSS v4 和 shadcn/ui 兼容
 */

// ========================================
// 1. 颜色系统
// ========================================

/** 主色阶 */
export const primaryColors = {
  50: '#eff6ff',
  100: '#dbeafe',
  200: '#bfdbfe',
  300: '#93c5fd',
  400: '#60a5fa',
  500: '#3b82f6',
  600: '#2563eb',
  700: '#1d4ed8',
  800: '#1e40af',
  900: '#1e3a8a',
  950: '#172554',
} as const;

/** 辅助色阶 */
export const secondaryColors = {
  50: '#f5f3ff',
  100: '#ede9fe',
  200: '#ddd6fe',
  300: '#c4b5fd',
  400: '#a78bfa',
  500: '#8b5cf6',
  600: '#7c3aed',
  700: '#6d28d9',
  800: '#5b21b6',
  900: '#4c1d95',
  950: '#2e1065',
} as const;

/** 成功色阶 */
export const successColors = {
  50: '#f0fdf4',
  100: '#dcfce7',
  200: '#bbf7d0',
  300: '#86efac',
  400: '#4ade80',
  500: '#22c55e',
  600: '#16a34a',
  700: '#15803d',
  800: '#166534',
  900: '#14532d',
  950: '#052e16',
} as const;

/** 警告色阶 */
export const warningColors = {
  50: '#fffbeb',
  100: '#fef3c7',
  200: '#fde68a',
  300: '#fcd34d',
  400: '#fbbf24',
  500: '#f59e0b',
  600: '#d97706',
  700: '#b45309',
  800: '#92400e',
  900: '#78350f',
  950: '#451a03',
} as const;

/** 错误色阶 */
export const errorColors = {
  50: '#fef2f2',
  100: '#fee2e2',
  200: '#fecaca',
  300: '#fca5a5',
  400: '#f87171',
  500: '#ef4444',
  600: '#dc2626',
  700: '#b91c1c',
  800: '#991b1b',
  900: '#7f1d1d',
  950: '#450a0a',
} as const;

/** 信息色阶 */
export const infoColors = {
  50: '#f0f9ff',
  100: '#e0f2fe',
  200: '#bae6fd',
  300: '#7dd3fc',
  400: '#38bdf8',
  500: '#0ea5e9',
  600: '#0284c7',
  700: '#0369a1',
  800: '#075985',
  900: '#0c4a6e',
  950: '#082f49',
} as const;

/** 中性灰色阶 */
export const grayColors = {
  50: '#f9fafb',
  100: '#f3f4f6',
  200: '#e5e7eb',
  300: '#d1d5db',
  400: '#9ca3af',
  500: '#6b7280',
  600: '#4b5563',
  700: '#374151',
  800: '#1f2937',
  900: '#111827',
  950: '#030712',
} as const;

/** 所有颜色汇总 */
export const colors = {
  primary: primaryColors,
  secondary: secondaryColors,
  success: successColors,
  warning: warningColors,
  error: errorColors,
  info: infoColors,
  gray: grayColors,
} as const;

/** 主题颜色（与 shadcn/ui 兼容） */
export const semanticColors = {
  background: 'hsl(var(--background))',
  foreground: 'hsl(var(--foreground))',
  card: 'hsl(var(--card))',
  'card-foreground': 'hsl(var(--card-foreground))',
  popover: 'hsl(var(--popover))',
  'popover-foreground': 'hsl(var(--popover-foreground))',
  primary: 'hsl(var(--primary))',
  'primary-foreground': 'hsl(var(--primary-foreground))',
  secondary: 'hsl(var(--secondary))',
  'secondary-foreground': 'hsl(var(--secondary-foreground))',
  muted: 'hsl(var(--muted))',
  'muted-foreground': 'hsl(var(--muted-foreground))',
  accent: 'hsl(var(--accent))',
  'accent-foreground': 'hsl(var(--accent-foreground))',
  destructive: 'hsl(var(--destructive))',
  'destructive-foreground': 'hsl(var(--destructive-foreground))',
  border: 'hsl(var(--border))',
  input: 'hsl(var(--input))',
  ring: 'hsl(var(--ring))',
} as const;

// ========================================
// 2. 间距系统
// ========================================

/** 间距令牌 (基于 4px) */
export const spacing = {
  0: '0',
  1: '0.25rem',      // 4px
  2: '0.5rem',       // 8px
  3: '0.75rem',      // 12px
  4: '1rem',         // 16px
  5: '1.25rem',      // 20px
  6: '1.5rem',       // 24px
  8: '2rem',         // 32px
  10: '2.5rem',      // 40px
  12: '3rem',        // 48px
  16: '4rem',        // 64px
  20: '5rem',        // 80px
  24: '6rem',        // 96px
  32: '8rem',        // 128px
  40: '10rem',       // 160px
  48: '12rem',       // 192px
  56: '14rem',       // 224px
  64: '16rem',       // 256px
} as const;

/** 间距数值（用于计算） */
export const spacingPx = {
  0: 0,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  10: 40,
  12: 48,
  16: 64,
  20: 80,
  24: 96,
  32: 128,
  40: 160,
  48: 192,
  56: 224,
  64: 256,
} as const;

// ========================================
// 3. 字体系统
// ========================================

/** 字体大小 */
export const fontSize = {
  xs: '0.75rem',      // 12px
  sm: '0.875rem',     // 14px
  base: '1rem',       // 16px
  lg: '1.125rem',     // 18px
  xl: '1.25rem',      // 20px
  '2xl': '1.5rem',    // 24px
  '3xl': '1.875rem',  // 30px
  '4xl': '2.25rem',   // 36px
  '5xl': '3rem',      // 48px
  '6xl': '3.75rem',   // 60px
} as const;

/** 字体大小数值（px） */
export const fontSizePx = {
  xs: 12,
  sm: 14,
  base: 16,
  lg: 18,
  xl: 20,
  '2xl': 24,
  '3xl': 30,
  '4xl': 36,
  '5xl': 48,
  '6xl': 60,
} as const;

/** 字重 */
export const fontWeight = {
  light: 300,
  normal: 400,
  medium: 500,
  semibold: 600,
  bold: 700,
} as const;

/** 行高 */
export const lineHeight = {
  none: 1,
  tight: 1.25,
  snug: 1.375,
  normal: 1.5,
  relaxed: 1.625,
  loose: 2,
} as const;

/** 字体族 */
export const fontFamily = {
  sans: ['var(--font-sans)', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
  mono: ['var(--font-mono)', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'Liberation Mono', 'Courier New', 'monospace'],
} as const;

/** 字体系统汇总 */
export const typography = {
  fontSize,
  fontSizePx,
  fontWeight,
  lineHeight,
  fontFamily,
} as const;

// ========================================
// 4. 圆角系统
// ========================================

/** 圆角令牌 */
export const borderRadius = {
  none: '0',
  sm: '0.125rem',    // 2px
  md: '0.25rem',     // 4px
  lg: '0.5rem',      // 8px
  xl: '0.75rem',     // 12px
  '2xl': '1rem',     // 16px
  '3xl': '1.5rem',   // 24px
  full: '9999px',
} as const;

/** 圆角数值（px） */
export const borderRadiusPx = {
  none: 0,
  sm: 2,
  md: 4,
  lg: 8,
  xl: 12,
  '2xl': 16,
  '3xl': 24,
  full: 9999,
} as const;

// ========================================
// 5. 阴影系统
// ========================================

/** 阴影令牌 */
export const boxShadow = {
  sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
  md: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)',
  lg: '0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)',
  xl: '0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)',
  '2xl': '0 25px 50px -12px rgb(0 0 0 / 0.25)',
  inner: 'inset 0 2px 4px 0 rgb(0 0 0 / 0.05)',
  none: '0 0 #0000',
} as const;

/** 暗色模式阴影 */
export const boxShadowDark = {
  sm: '0 1px 2px 0 rgb(0 0 0 / 0.3)',
  md: '0 4px 6px -1px rgb(0 0 0 / 0.4), 0 2px 4px -2px rgb(0 0 0 / 0.4)',
  lg: '0 10px 15px -3px rgb(0 0 0 / 0.4), 0 4px 6px -4px rgb(0 0 0 / 0.4)',
  xl: '0 20px 25px -5px rgb(0 0 0 / 0.4), 0 8px 10px -6px rgb(0 0 0 / 0.4)',
  '2xl': '0 25px 50px -12px rgb(0 0 0 / 0.5)',
} as const;

// ========================================
// 6. 过渡系统
// ========================================

/** 过渡时间 */
export const transitionDuration = {
  fast: '150ms',
  normal: '200ms',
  slow: '300ms',
} as const;

/** 过渡时间数值（ms） */
export const transitionDurationMs = {
  fast: 150,
  normal: 200,
  slow: 300,
} as const;

/** 缓动函数 */
export const transitionTiming = {
  linear: 'linear',
  in: 'cubic-bezier(0.4, 0, 1, 1)',
  out: 'cubic-bezier(0, 0, 0.2, 1)',
  'in-out': 'cubic-bezier(0.4, 0, 0.2, 1)',
  spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
} as const;

/** 过渡属性 */
export const transitionProperty = {
  all: 'all',
  colors: 'color, background-color, border-color, text-decoration-color, fill, stroke',
  opacity: 'opacity',
  shadow: 'box-shadow',
  transform: 'transform',
  none: 'none',
} as const;

/** 过渡系统汇总 */
export const transitions = {
  duration: transitionDuration,
  durationMs: transitionDurationMs,
  timing: transitionTiming,
  property: transitionProperty,
} as const;

// ========================================
// 7. Z-Index 系统
// ========================================

/** Z-Index 令牌 */
export const zIndex = {
  dropdown: 1000,
  sticky: 1020,
  fixed: 1030,
  modalBackdrop: 1040,
  modal: 1050,
  popover: 1060,
  tooltip: 1070,
  toast: 1080,
} as const;

// ========================================
// 8. 断点系统
// ========================================

/** 响应式断点 */
export const breakpoints = {
  sm: '640px',
  md: '768px',
  lg: '1024px',
  xl: '1280px',
  '2xl': '1536px',
} as const;

/** 断点数值（px） */
export const breakpointsPx = {
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536,
} as const;

/** 媒体查询 */
export const mediaQueries = {
  sm: `(min-width: ${breakpoints.sm})`,
  md: `(min-width: ${breakpoints.md})`,
  lg: `(min-width: ${breakpoints.lg})`,
  xl: `(min-width: ${breakpoints.xl})`,
  '2xl': `(min-width: ${breakpoints['2xl']})`,
  smDown: `(max-width: ${breakpointsPx.sm - 1}px)`,
  mdDown: `(max-width: ${breakpointsPx.md - 1}px)`,
  lgDown: `(max-width: ${breakpointsPx.lg - 1}px)`,
  xlDown: `(max-width: ${breakpointsPx.xl - 1}px)`,
} as const;

// ========================================
// 9. 动画系统
// ========================================

/** 动画持续时间 */
export const animationDuration = {
  fast: '0.15s',
  normal: '0.3s',
  slow: '0.5s',
} as const;

/** 动画类名 */
export const animationClasses = {
  fadeIn: 'ds-animate-fadeIn',
  fadeOut: 'ds-animate-fadeOut',
  fadeInUp: 'ds-animate-fadeInUp',
  fadeInDown: 'ds-animate-fadeInDown',
  fadeInLeft: 'ds-animate-fadeInLeft',
  fadeInRight: 'ds-animate-fadeInRight',
  slideUp: 'ds-animate-slideUp',
  slideDown: 'ds-animate-slideDown',
  slideLeft: 'ds-animate-slideLeft',
  slideRight: 'ds-animate-slideRight',
  scaleIn: 'ds-animate-scaleIn',
  scaleOut: 'ds-animate-scaleOut',
  spin: 'ds-animate-spin',
  pulse: 'ds-animate-pulse',
  bounce: 'ds-animate-bounce',
  shake: 'ds-animate-shake',
} as const;

/** 动画系统汇总 */
export const animations = {
  duration: animationDuration,
  classes: animationClasses,
} as const;

// ========================================
// 10. 组件特定令牌
// ========================================

/** 按钮尺寸 */
export const buttonSizes = {
  sm: {
    padding: `${spacing[1]} ${spacing[3]}`,
    fontSize: fontSize.xs,
    borderRadius: borderRadius.md,
    height: '32px',
  },
  md: {
    padding: `${spacing[2]} ${spacing[4]}`,
    fontSize: fontSize.sm,
    borderRadius: borderRadius.lg,
    height: '40px',
  },
  lg: {
    padding: `${spacing[3]} ${spacing[6]}`,
    fontSize: fontSize.base,
    borderRadius: borderRadius.xl,
    height: '48px',
  },
} as const;

/** 输入框尺寸 */
export const inputSizes = {
  sm: {
    padding: `${spacing[1]} ${spacing[2]}`,
    fontSize: fontSize.xs,
    height: '32px',
  },
  md: {
    padding: `${spacing[2]} ${spacing[3]}`,
    fontSize: fontSize.sm,
    height: '40px',
  },
  lg: {
    padding: `${spacing[3]} ${spacing[4]}`,
    fontSize: fontSize.base,
    height: '48px',
  },
} as const;

/** 卡片尺寸 */
export const cardSizes = {
  sm: {
    padding: spacing[4],
    borderRadius: borderRadius.lg,
  },
  md: {
    padding: spacing[6],
    borderRadius: borderRadius.xl,
  },
  lg: {
    padding: spacing[8],
    borderRadius: borderRadius['2xl'],
  },
} as const;

/** 组件令牌汇总 */
export const componentTokens = {
  button: buttonSizes,
  input: inputSizes,
  card: cardSizes,
} as const;

// ========================================
// 11. 辅助函数
// ========================================

/**
 * 将 rem 转换为 px
 * @param rem - rem 值（如 '1rem' 或 1）
 * @param baseFontSize - 基准字体大小，默认 16
 * @returns px 值
 */
export function remToPx(rem: string | number, baseFontSize = 16): number {
  const value = typeof rem === 'string' ? parseFloat(rem) : rem;
  return Math.round(value * baseFontSize);
}

/**
 * 将 px 转换为 rem
 * @param px - px 值
 * @param baseFontSize - 基准字体大小，默认 16
 * @returns rem 字符串
 */
export function pxToRem(px: number, baseFontSize = 16): string {
  return `${px / baseFontSize}rem`;
}

/**
 * 获取颜色值
 * @param color - 颜色名称
 * @param shade - 色阶
 * @returns 颜色 hex 值
 */
export function getColor(
  color: keyof typeof colors,
  shade: keyof (typeof colors)['primary']
): string {
  return colors[color][shade];
}

/**
 * 获取间距值
 * @param size - 间距大小
 * @returns 间距值
 */
export function getSpacing(size: keyof typeof spacing): string {
  return spacing[size];
}

/**
 * 获取字体大小
 * @param size - 字体大小
 * @returns 字体大小值
 */
export function getFontSize(size: keyof typeof fontSize): string {
  return fontSize[size];
}

/**
 * 获取阴影
 * @param size - 阴影大小
 * @param isDark - 是否为暗色模式
 * @returns 阴影值
 */
export function getShadow(
  size: keyof typeof boxShadow,
  isDark = false
): string {
  if (isDark && size in boxShadowDark) {
    return boxShadowDark[size as keyof typeof boxShadowDark];
  }
  return boxShadow[size];
}

// ========================================
// 12. 导出所有令牌
// ========================================

/** 完整设计令牌对象 */
export const designTokens = {
  colors,
  semanticColors,
  spacing,
  spacingPx,
  typography,
  borderRadius,
  borderRadiusPx,
  boxShadow,
  boxShadowDark,
  transitions,
  zIndex,
  breakpoints,
  breakpointsPx,
  mediaQueries,
  animations,
  componentTokens,
} as const;

/** 默认导出 */
export default designTokens;

// 类型导出
export type DesignTokens = typeof designTokens;
export type Colors = typeof colors;
export type Spacing = typeof spacing;
export type Typography = typeof typography;
export type BorderRadius = typeof borderRadius;
export type BoxShadow = typeof boxShadow;
export type Transitions = typeof transitions;
export type ZIndex = typeof zIndex;
export type Breakpoints = typeof breakpoints;

"use client"

import { useCallback, useEffect, useRef, useState } from "react"

interface UseSmartScrollOptions {
  /** 底部阈值（像素），小于此值视为在底部 */
  bottomThreshold?: number
  /** 手动滚动检测阈值（像素） */
  manualScrollThreshold?: number
  /** 是否启用自动滚动 */
  autoScrollEnabled?: boolean
  /** sessionStorage存储键名 */
  storageKey?: string
  /** 是否记住滚动位置 */
  rememberPosition?: boolean
  /** 防抖延迟时间（毫秒） */
  debounceDelay?: number
}

interface UseSmartScrollReturn {
  /** 容器ref */
  containerRef: React.RefObject<HTMLDivElement | null>
  /** 是否在底部附近 */
  isNearBottom: boolean
  /** 是否显示滚动到底部按钮 */
  showScrollButton: boolean
  /** 是否启用了自动滚动 */
  shouldAutoScroll: boolean
  /** 当前滚动位置 */
  scrollTop: number
  /** 容器总高度 */
  scrollHeight: number
  /** 容器可视高度 */
  clientHeight: number
  /** 滚动到底部 */
  scrollToBottom: (behavior?: ScrollBehavior) => void
  /** 滚动到指定位置 */
  scrollTo: (position: number, behavior?: ScrollBehavior) => void
  /** 恢复上次滚动位置 */
  restoreScrollPosition: () => void
  /** 暂停自动滚动 */
  pauseAutoScroll: () => void
  /** 恢复自动滚动 */
  resumeAutoScroll: () => void
  /** 手动设置shouldAutoScroll */
  setShouldAutoScroll: (value: boolean) => void
}

export function useSmartScroll(options: UseSmartScrollOptions = {}): UseSmartScrollReturn {
  const {
    bottomThreshold = 80,
    manualScrollThreshold = 100,
    autoScrollEnabled = true,
    storageKey = "smart-scroll-position",
    rememberPosition = true,
    debounceDelay = 50,
  } = options

  const containerRef = useRef<HTMLDivElement>(null)
  const [isNearBottom, setIsNearBottom] = useState(true)
  const [showScrollButton, setShowScrollButton] = useState(false)
  const [shouldAutoScroll, setShouldAutoScroll] = useState(autoScrollEnabled)
  const [scrollTop, setScrollTop] = useState(0)
  const [scrollHeight, setScrollHeight] = useState(0)
  const [clientHeight, setClientHeight] = useState(0)

  // 用于节流的ref
  const tickingRef = useRef(false)
  const lastScrollTopRef = useRef(0)
  const isManualScrollRef = useRef(false)
  // 用于检测用户是否正在主动向上滚动的ref
  const isScrollingUpRef = useRef(false)
  const lastScrollTimestampRef = useRef(0)
  // 内容变化防抖定时器
  const contentChangeTimerRef = useRef<NodeJS.Timeout | null>(null)
  // 最后内容高度，用于检测内容变化
  const lastContentHeightRef = useRef(0)
  // 是否正在自动滚动中
  const isAutoScrollingRef = useRef(false)

  /**
   * 检查是否在底部附近
   */
  const checkIsNearBottom = useCallback((element: HTMLDivElement): boolean => {
    const { scrollTop, scrollHeight, clientHeight } = element
    const distanceToBottom = scrollHeight - scrollTop - clientHeight
    return distanceToBottom <= bottomThreshold
  }, [bottomThreshold])

  /**
   * 计算滚动距离
   */
  const getScrollDistance = useCallback((currentScrollTop: number): number => {
    return Math.abs(currentScrollTop - lastScrollTopRef.current)
  }, [])

  /**
   * 保存滚动位置到sessionStorage
   */
  const saveScrollPosition = useCallback((position: number) => {
    if (rememberPosition && typeof window !== "undefined") {
      try {
        sessionStorage.setItem(storageKey, position.toString())
      } catch (e) {
        console.warn("Failed to save scroll position:", e)
      }
    }
  }, [rememberPosition, storageKey])

  /**
   * 从sessionStorage读取滚动位置
   */
  const getSavedScrollPosition = useCallback((): number | null => {
    if (typeof window === "undefined") return null
    try {
      const saved = sessionStorage.getItem(storageKey)
      return saved ? parseInt(saved, 10) : null
    } catch (e) {
      console.warn("Failed to get scroll position:", e)
      return null
    }
  }, [storageKey])

  /**
   * 检查浏览器是否支持平滑滚动
   */
  const supportsSmoothScroll = useCallback((): boolean => {
    if (typeof window === "undefined") return false
    return "scrollBehavior" in document.documentElement.style
  }, [])

  /**
   * 滚动到底部
   */
  const scrollToBottom = useCallback((behavior: ScrollBehavior = "smooth") => {
    const container = containerRef.current
    if (!container) return

    // 如果浏览器不支持平滑滚动，使用polyfill
    const useBehavior = supportsSmoothScroll() ? behavior : "auto"
    
    isAutoScrollingRef.current = true
    
    container.scrollTo({
      top: container.scrollHeight,
      behavior: useBehavior,
    })

    // 滚动完成后重置标志
    setTimeout(() => {
      isAutoScrollingRef.current = false
    }, 300)
  }, [supportsSmoothScroll])

  /**
   * 滚动到指定位置
   */
  const scrollTo = useCallback((position: number, behavior: ScrollBehavior = "smooth") => {
    const container = containerRef.current
    if (!container) return

    const useBehavior = supportsSmoothScroll() ? behavior : "auto"
    
    isAutoScrollingRef.current = true
    
    container.scrollTo({
      top: position,
      behavior: useBehavior,
    })

    setTimeout(() => {
      isAutoScrollingRef.current = false
    }, 300)
  }, [supportsSmoothScroll])

  /**
   * 恢复上次滚动位置
   */
  const restoreScrollPosition = useCallback(() => {
    const savedPosition = getSavedScrollPosition()
    if (savedPosition !== null) {
      scrollTo(savedPosition, "auto")
    }
  }, [getSavedScrollPosition, scrollTo])

  /**
   * 暂停自动滚动
   */
  const pauseAutoScroll = useCallback(() => {
    setShouldAutoScroll(false)
  }, [])

  /**
   * 恢复自动滚动
   */
  const resumeAutoScroll = useCallback(() => {
    setShouldAutoScroll(true)
    scrollToBottom()
  }, [scrollToBottom])

  /**
   * 处理滚动事件（带16ms节流）
   */
  const handleScroll = useCallback(() => {
    if (tickingRef.current) return

    tickingRef.current = true
    
    requestAnimationFrame(() => {
      const container = containerRef.current
      if (!container) {
        tickingRef.current = false
        return
      }

      const currentScrollTop = container.scrollTop
      const currentScrollHeight = container.scrollHeight
      const currentClientHeight = container.clientHeight
      const now = Date.now()

      // 检测滚动方向
      const scrollDirection = currentScrollTop - lastScrollTopRef.current
      isScrollingUpRef.current = scrollDirection < -5 // 向上滚动超过5px

      // 更新状态
      setScrollTop(currentScrollTop)
      setScrollHeight(currentScrollHeight)
      setClientHeight(currentClientHeight)

      // 检测是否为手动滚动（滚动距离超过阈值）
      const scrollDistance = getScrollDistance(currentScrollTop)
      
      // 排除自动滚动触发的滚动事件
      if (!isAutoScrollingRef.current && scrollDistance > manualScrollThreshold) {
        isManualScrollRef.current = true
        lastScrollTimestampRef.current = now
      }

      // 检测是否在底部附近
      const nearBottom = checkIsNearBottom(container)
      setIsNearBottom(nearBottom)

      // 显示/隐藏滚动按钮
      setShowScrollButton(!nearBottom && currentScrollHeight > currentClientHeight)

      // 如果是手动向上滚动，暂停自动滚动
      if (isScrollingUpRef.current && autoScrollEnabled && !isAutoScrollingRef.current) {
        setShouldAutoScroll(false)
      }

      // 如果滚动到底部，恢复自动滚动
      if (nearBottom && autoScrollEnabled) {
        setShouldAutoScroll(true)
        isManualScrollRef.current = false
        isScrollingUpRef.current = false
      }

      // 保存滚动位置
      saveScrollPosition(currentScrollTop)

      // 更新最后滚动位置
      lastScrollTopRef.current = currentScrollTop
      tickingRef.current = false
    })
  }, [autoScrollEnabled, checkIsNearBottom, getScrollDistance, manualScrollThreshold, saveScrollPosition])

  /**
   * 监听容器尺寸变化（带防抖）
   */
  const updateDimensions = useCallback(() => {
    const container = containerRef.current
    if (!container) return

    const newScrollHeight = container.scrollHeight
    const newClientHeight = container.clientHeight

    // 清除之前的防抖定时器
    if (contentChangeTimerRef.current) {
      clearTimeout(contentChangeTimerRef.current)
    }

    // 只有当内容高度变化超过阈值时才触发滚动
    const heightDiff = Math.abs(newScrollHeight - lastContentHeightRef.current)
    
    if (heightDiff > 10) { // 内容变化超过10px才处理
      setScrollHeight(newScrollHeight)
      setClientHeight(newClientHeight)

      // 使用防抖延迟执行滚动
      contentChangeTimerRef.current = setTimeout(() => {
        // 只有在用户没有主动向上滚动时才自动滚动到底部
        // 且当前启用了自动滚动
        const isUserScrollingUp = isScrollingUpRef.current
        const timeSinceLastScroll = Date.now() - lastScrollTimestampRef.current
        const isRecentManualScroll = timeSinceLastScroll < 1000 // 1秒内的手动滚动视为近期操作

        if (shouldAutoScroll && !isUserScrollingUp && !isRecentManualScroll && !isAutoScrollingRef.current) {
          // 使用scrollTo实现平滑滚动
          scrollToBottom()
        }
        
        lastContentHeightRef.current = newScrollHeight
      }, debounceDelay)
    }
  }, [shouldAutoScroll, debounceDelay, scrollToBottom])

  // 绑定滚动事件
  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    // 使用passive监听器提升性能
    container.addEventListener("scroll", handleScroll, { passive: true })

    // 初始检查
    handleScroll()

    return () => {
      container.removeEventListener("scroll", handleScroll)
    }
  }, [handleScroll])

  // 监听内容变化（使用ResizeObserver）
  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    // 初始设置尺寸
    lastContentHeightRef.current = container.scrollHeight
    updateDimensions()

    // 创建ResizeObserver监听容器尺寸变化
    const resizeObserver = new ResizeObserver(() => {
      updateDimensions()
    })

    resizeObserver.observe(container)

    // 监听子元素变化（使用MutationObserver作为补充）
    const mutationObserver = new MutationObserver(() => {
      updateDimensions()
    })

    mutationObserver.observe(container, {
      childList: true,
      subtree: true,
    })

    return () => {
      resizeObserver.disconnect()
      mutationObserver.disconnect()
      if (contentChangeTimerRef.current) {
        clearTimeout(contentChangeTimerRef.current)
      }
    }
  }, [updateDimensions])

  // 组件挂载时尝试恢复滚动位置
  useEffect(() => {
    if (rememberPosition) {
      // 延迟执行，确保内容已渲染
      const timer = setTimeout(() => {
        restoreScrollPosition()
      }, 100)
      return () => clearTimeout(timer)
    }
  }, [rememberPosition, restoreScrollPosition])

  // 组件卸载时清理
  useEffect(() => {
    return () => {
      if (contentChangeTimerRef.current) {
        clearTimeout(contentChangeTimerRef.current)
      }
    }
  }, [])

  return {
    containerRef,
    isNearBottom,
    showScrollButton,
    shouldAutoScroll,
    scrollTop,
    scrollHeight,
    clientHeight,
    scrollToBottom,
    scrollTo,
    restoreScrollPosition,
    pauseAutoScroll,
    resumeAutoScroll,
    setShouldAutoScroll,
  }
}

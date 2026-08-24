import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import zhCN from '@/locales/zh-CN/common.json';
import enUS from '@/locales/en-US/common.json';

// Check if we're in the browser
const isBrowser = typeof window !== 'undefined';

// 通用的i18n配置
const i18nConfig = {
  resources: {
    'zh-CN': {
      translation: zhCN,
    },
    'en-US': {
      translation: enUS,
    },
  },
  // 强制默认语言为中文，避免显示英文
  lng: 'zh-CN',
  fallbackLng: 'zh-CN',
  debug: process.env.NODE_ENV === 'development',
  interpolation: {
    escapeValue: false,
  },
  // 确保在初始化时立即加载资源
  initImmediate: false,
  // 防止SSR/客户端hydration不匹配
  react: {
    useSuspense: false,
    bindI18n: 'languageChanged loaded',
    bindI18nStore: 'added removed',
    transEmptyNodeValue: '',
    transSupportBasicHtmlNodes: true,
    transKeepBasicHtmlNodesFor: ['br', 'strong', 'i', 'p'],
  },
};

if (isBrowser) {
  // 客户端初始化：使用语言检测器，但优先从localStorage读取
  i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      ...i18nConfig,
      detection: {
        order: ['localStorage', 'navigator'],
        caches: ['localStorage'],
        // 如果localStorage中没有语言设置，使用navigator检测
        lookupLocalStorage: 'i18nextLng',
        // 转换语言代码格式
        convertDetectedLanguage: (lng: string) => {
          // 将 'zh' 转换为 'zh-CN'，其他语言保持原样
          if (lng.startsWith('zh')) return 'zh-CN';
          if (lng.startsWith('en')) return 'en-US';
          return 'zh-CN';
        },
      },
    });
} else {
  // 服务端初始化：固定使用中文，确保SSR输出中文
  i18n.use(initReactI18next).init(i18nConfig);
}

export default i18n;

"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  SettingOutlined,
  DatabaseOutlined,
  ApiOutlined,
  FileTextOutlined,
} from "@ant-design/icons";
import { Tabs, Typography, ConfigProvider } from "antd";
import RagConfigTab from "./components/RagConfigTab";
import KnowledgeBaseTab from "./components/KnowledgeBaseTab";
import EmbeddingModelTab from "./components/EmbeddingModelTab";
import DocumentTab from "./components/DocumentTab";
import "./rag-responsive.css";
import "./rag-styles.css";

const { Title } = Typography;

const tabItems = [
  {
    key: "config",
    icon: SettingOutlined,
    label: "RAG 配置",
    color: "#1890ff",
    gradient: "linear-gradient(135deg, #1890ff 0%, #36cfc9 100%)",
  },
  {
    key: "knowledge",
    icon: DatabaseOutlined,
    label: "知识库管理",
    color: "#52c41a",
    gradient: "linear-gradient(135deg, #52c41a 0%, #95de64 100%)",
  },
  {
    key: "document",
    icon: FileTextOutlined,
    label: "文档管理",
    color: "#722ed1",
    gradient: "linear-gradient(135deg, #722ed1 0%, #b37feb 100%)",
  },
  {
    key: "embedding",
    icon: ApiOutlined,
    label: "Embedding 模型",
    color: "#fa8c16",
    gradient: "linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)",
  },
];

export default function RagManagementPage() {
  const [activeTab, setActiveTab] = useState("config");
  const [documentTabParams, setDocumentTabParams] = useState<{
    knowledgeBaseId: number;
    knowledgeBaseName: string;
  }>({ knowledgeBaseId: 0, knowledgeBaseName: "" });

  useEffect(() => {
    const handleSwitchTab = (event: Event) => {
      const customEvent = event as CustomEvent<{ knowledgeBaseId: number; knowledgeBaseName: string }>;
      setDocumentTabParams({
        knowledgeBaseId: customEvent.detail.knowledgeBaseId,
        knowledgeBaseName: customEvent.detail.knowledgeBaseName,
      });
      setActiveTab("document");
    };

    window.addEventListener("switchToDocumentTab", handleSwitchTab);
    return () => window.removeEventListener("switchToDocumentTab", handleSwitchTab);
  }, []);

  const getTabContent = (key: string) => {
    switch (key) {
      case "config":
        return <RagConfigTab />;
      case "knowledge":
        return <KnowledgeBaseTab />;
      case "document":
        return (
          <DocumentTab
            knowledgeBaseId={documentTabParams.knowledgeBaseId}
            knowledgeBaseName={documentTabParams.knowledgeBaseName}
          />
        );
      case "embedding":
        return <EmbeddingModelTab />;
      default:
        return null;
    }
  };

  const activeTabInfo = tabItems.find(item => item.key === activeTab);

  return (
    <ConfigProvider
      theme={{
        components: {
          Tabs: {
            cardBg: 'transparent',
            cardHeight: 48,
            cardPadding: '12px 24px',
          },
        },
      }}
    >
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: "easeOut" }}
        className="rag-management-page p-3 sm:p-4 md:p-6"
      >
        {/* 页面标题区域 */}
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5, delay: 0.1 }}
          className="page-header"
          style={{
            marginBottom: 16,
            padding: '16px 20px',
            background: 'linear-gradient(135deg, #f8faff 0%, #ffffff 100%)',
            borderRadius: 12,
            boxShadow: '0 4px 20px rgba(0, 0, 0, 0.06)',
            border: '1px solid rgba(24, 144, 255, 0.1)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <motion.div
              initial={{ scale: 0.8, rotate: -10 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ duration: 0.5, delay: 0.2 }}
              style={{
                width: 44,
                height: 44,
                borderRadius: 12,
                background: activeTabInfo?.gradient || 'linear-gradient(135deg, #1890ff 0%, #36cfc9 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 8px 24px rgba(24, 144, 255, 0.25)',
                flexShrink: 0,
              }}
            >
              {activeTabInfo && (
                <activeTabInfo.icon style={{ fontSize: 22, color: '#fff' }} />
              )}
            </motion.div>
            <div style={{ minWidth: 0, flex: 1 }}>
              <Title level={4} style={{ margin: 0, fontSize: 18, fontWeight: 700 }} className="sm:text-xl md:text-2xl">
                知识库管理
              </Title>
              <p style={{ margin: '4px 0 0', color: '#8c8c8c', fontSize: 12 }} className="hidden sm:block sm:text-sm">
                配置 RAG 系统、管理知识库和 Embedding 模型
              </p>
            </div>
          </div>
        </motion.div>

        {/* 自定义 Tabs 区域 */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="tabs-container"
          style={{
            background: '#fff',
            borderRadius: 12,
            boxShadow: '0 4px 24px rgba(0, 0, 0, 0.06)',
            overflow: 'hidden',
          }}
        >
          {/* 自定义 Tab 标签栏 */}
          <div
            className="custom-tabs-header"
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              background: 'linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%)',
              borderBottom: '1px solid #e8e8e8',
              padding: '6px 6px 0',
              gap: 2,
            }}
          >
            {tabItems.map((item, index) => {
              const Icon = item.icon;
              const isActive = activeTab === item.key;
              
              return (
                <motion.button
                  key={item.key}
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.3, delay: 0.1 * index }}
                  onClick={() => setActiveTab(item.key)}
                  className={`tab-button ${isActive ? 'active' : ''}`}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    padding: '10px 12px',
                    border: 'none',
                    background: isActive ? '#fff' : 'transparent',
                    borderRadius: '10px 10px 0 0',
                    cursor: 'pointer',
                    fontSize: 13,
                    fontWeight: isActive ? 600 : 500,
                    color: isActive ? item.color : '#595959',
                    position: 'relative',
                    transition: 'all 0.3s ease',
                    boxShadow: isActive ? '0 -4px 12px rgba(0, 0, 0, 0.05)' : 'none',
                    flex: '1 1 auto',
                    minWidth: '80px',
                    maxWidth: '160px',
                  }}
                  whileHover={{ 
                    backgroundColor: isActive ? '#fff' : 'rgba(0, 0, 0, 0.02)',
                  }}
                  whileTap={{ scale: 0.98 }}
                >
                  <motion.div
                    animate={{
                      scale: isActive ? 1.1 : 1,
                    }}
                    transition={{ duration: 0.2 }}
                  >
                    <Icon 
                      style={{ 
                        fontSize: 16,
                        color: isActive ? item.color : '#8c8c8c',
                      }} 
                    />
                  </motion.div>
                  <span className="tab-label" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.label}</span>
                  
                  {/* 活动指示器 */}
                  {isActive && (
                    <motion.div
                      layoutId="activeTabIndicator"
                      className="tab-indicator"
                      style={{
                        position: 'absolute',
                        bottom: 0,
                        left: 8,
                        right: 8,
                        height: 3,
                        background: item.gradient,
                        borderRadius: '3px 3px 0 0',
                      }}
                      initial={{ scaleX: 0 }}
                      animate={{ scaleX: 1 }}
                      transition={{ duration: 0.3, ease: "easeOut" }}
                    />
                  )}
                </motion.button>
              );
            })}
          </div>

          {/* Tab 内容区域 */}
          <div
            className="tab-content"
            style={{
              padding: 12,
              minHeight: 400,
              background: '#fff',
            }}
          >
            <AnimatePresence mode="wait">
              <motion.div
                key={activeTab}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3, ease: "easeOut" }}
              >
                {getTabContent(activeTab)}
              </motion.div>
            </AnimatePresence>
          </div>
        </motion.div>
      </motion.div>
    </ConfigProvider>
  );
}

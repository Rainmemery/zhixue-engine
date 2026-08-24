"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { 
  Modal, 
  Input, 
  Pagination, 
  Spin, 
  Empty, 
  Tag, 
  List, 
  Typography, 
  Space, 
  Divider, 
  Alert, 
  Button,
  Badge,
  Card,
  Tooltip,
  Avatar,
} from "antd";
import { 
  SearchOutlined, 
  FileTextOutlined, 
  FilePdfOutlined, 
  FileWordOutlined, 
  FileMarkdownOutlined, 
  ArrowUpOutlined, 
  ArrowDownOutlined,
  CloseOutlined,
  FileSearchOutlined,
  BookOutlined,
  ClockCircleOutlined,
  InfoCircleOutlined,
} from "@ant-design/icons";
import { ragService, Document, DocumentContent, SearchMatch } from "@/services/ragService";
import { ErrorHandler } from "@/lib/errorHandler";

const { Text, Title, Paragraph } = Typography;

interface DocumentPreviewProps {
  document: Document | null;
  visible: boolean;
  onClose: () => void;
}

const fileTypeIcons: Record<string, { icon: React.ReactNode; color: string; bgColor: string; label: string }> = {
  PDF: { 
    icon: <FilePdfOutlined />, 
    color: "#ff4d4f", 
    bgColor: "#fff1f0",
    label: "PDF 文档"
  },
  DOCX: { 
    icon: <FileWordOutlined />, 
    color: "#1890ff", 
    bgColor: "#e6f7ff",
    label: "Word 文档"
  },
  DOC: { 
    icon: <FileWordOutlined />, 
    color: "#1890ff", 
    bgColor: "#e6f7ff",
    label: "Word 文档"
  },
  TXT: { 
    icon: <FileTextOutlined />, 
    color: "#52c41a", 
    bgColor: "#f6ffed",
    label: "文本文件"
  },
  MD: { 
    icon: <FileMarkdownOutlined />, 
    color: "#722ed1", 
    bgColor: "#f9f0ff",
    label: "Markdown"
  },
};

const CHUNK_SIZE = 2000;

export default function DocumentPreview({ document, visible, onClose }: DocumentPreviewProps) {
  const [loading, setLoading] = useState(false);
  const [content, setContent] = useState<DocumentContent | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [pageSize] = useState(CHUNK_SIZE);
  const [isSearchFocused, setIsSearchFocused] = useState(false);

  useEffect(() => {
    if (visible && document) {
      loadContent();
    }
  }, [visible, document, currentPage, searchKeyword]);

  const loadContent = async () => {
    if (!document) return;
    
    setLoading(true);
    setContent(null);
    try {
      const data = await ragService.document.getContent(
        document.id,
        currentPage,
        pageSize,
        searchKeyword || undefined
      );
      
      if (!data) {
        throw new Error("文档内容为空");
      }
      
      if (!data.pages || data.pages.length === 0) {
        throw new Error("文档页面数据为空");
      }
      
      setContent(data);
    } catch (error: unknown) {
      console.error("加载文档内容失败:", error);
      const errorMessage = error instanceof Error ? error.message : "加载失败";
      new ErrorHandler(errorMessage)
        .setContext(`加载文档 "${document.title}" 内容`)
        .handle();
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = () => {
    setSearchKeyword(searchInput);
    setCurrentPage(1);
  };

  const handlePageChange = (page: number) => {
    setCurrentPage(page);
  };

  const highlightText = (text: string, keyword: string): React.ReactNode => {
    if (!keyword || !text) return text;
    
    const parts = text.split(new RegExp(`(${keyword})`, "gi"));
    return (
      <>
        {parts.map((part, index) => 
          part.toLowerCase() === keyword.toLowerCase() ? (
            <mark 
              key={index} 
              style={{ 
                backgroundColor: "#ffc069", 
                padding: "2px 4px", 
                borderRadius: 4,
                fontWeight: 600,
              }}
            >
              {part}
            </mark>
          ) : (
            part
          )
        )}
      </>
    );
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  };

  const goToMatch = (match: SearchMatch) => {
    setCurrentPage(match.pageNumber);
  };

  const scrollToMatch = (match: SearchMatch) => {
    setTimeout(() => {
      const element = globalThis.document?.getElementById(`match-${match.pageNumber}-${match.startOffset}`);
      if (element) {
        element.scrollIntoView({ behavior: "smooth", block: "center" });
        element.classList.add("highlight-animation");
        setTimeout(() => {
          element.classList.remove("highlight-animation");
        }, 2000);
      }
    }, 100);
  };

  const getFileTypeInfo = (type: string) => {
    return fileTypeIcons[type?.toUpperCase()] || { 
      icon: <FileTextOutlined />, 
      color: "#8c8c8c", 
      bgColor: "#f5f5f5",
      label: "未知类型"
    };
  };

  const fileTypeInfo = document ? getFileTypeInfo(document.fileType) : null;

  return (
    <Modal
      title={null}
      open={visible}
      onCancel={onClose}
      footer={null}
      width={1100}
      style={{ top: 40 }}
      destroyOnClose
      className="document-preview-modal"
      styles={{
        body: { padding: 0, maxHeight: "calc(100vh - 100px)", overflow: "hidden" },
        header: { display: 'none' },
      }}
      maskStyle={{ backdropFilter: 'blur(4px)' }}
    >
      {document && (
        <div className="flex flex-col h-full" style={{ maxHeight: "calc(100vh - 100px)" }}>
          {/* 头部区域 */}
          <div 
            className="px-6 py-4 border-b border-gray-100 flex items-center justify-between"
            style={{
              background: 'linear-gradient(135deg, #667eea08 0%, #764ba208 100%)',
            }}
          >
            <div className="flex items-center gap-4">
              {/* 文件类型图标 */}
              <div 
                className="w-12 h-12 rounded-xl flex items-center justify-center text-2xl shadow-sm"
                style={{ 
                  background: fileTypeInfo?.bgColor,
                  color: fileTypeInfo?.color,
                }}
              >
                {fileTypeInfo?.icon}
              </div>
              
              {/* 文档信息 */}
              <div>
                <h3 className="text-lg font-semibold text-gray-800 m-0 truncate max-w-[400px]">
                  {document.title || document.fileName}
                </h3>
                <div className="flex items-center gap-3 mt-1">
                  <Tag 
                    className="rounded-full px-2 py-0.5 text-xs border-0"
                    style={{ 
                      backgroundColor: fileTypeInfo?.bgColor,
                      color: fileTypeInfo?.color,
                    }}
                  >
                    {fileTypeInfo?.label}
                  </Tag>
                  <Text type="secondary" className="text-xs">
                    {formatFileSize(document.fileSize)}
                  </Text>
                  {content && (
                    <Text type="secondary" className="text-xs">
                      共 {content.totalPages} 页
                    </Text>
                  )}
                </div>
              </div>
            </div>

            {/* 关闭按钮 */}
            <Button
              type="text"
              icon={<CloseOutlined />}
              onClick={onClose}
              className="w-10 h-10 flex items-center justify-center rounded-full hover:bg-gray-100"
            />
          </div>

          {/* 统计信息栏 */}
          <div className="px-6 py-3 bg-gray-50/50 border-b border-gray-100">
            <div className="flex items-center gap-6">
              <div className="flex items-center gap-2">
                <BookOutlined className="text-gray-400" />
                <span className="text-sm text-gray-600">
                  分块数: <span className="font-semibold text-gray-800">{document.chunkCount}</span>
                </span>
              </div>
              <div className="flex items-center gap-2">
                <ClockCircleOutlined className="text-gray-400" />
                <span className="text-sm text-gray-600">
                  创建: <span className="text-gray-800">{new Date(document.createdAt).toLocaleDateString()}</span>
                </span>
              </div>
              {searchKeyword && content && (
                <Badge 
                  count={`${content.totalMatches} 个匹配`}
                  className="site-badge-count-109"
                  style={{ 
                    backgroundColor: '#52c41a20',
                    color: '#52c41a',
                    fontSize: '12px',
                    fontWeight: 600,
                  }}
                />
              )}
            </div>
          </div>

          {/* 搜索栏 */}
          <div className="px-6 py-4 border-b border-gray-100 bg-white">
            <div className="flex gap-3">
              <Input.Search
                placeholder="搜索文档内容..."
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onSearch={handleSearch}
                enterButton={
                  <Button 
                    type="primary" 
                    icon={<FileSearchOutlined />}
                    className="rounded-r-lg"
                    style={{
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      border: 'none',
                    }}
                  >
                    搜索
                  </Button>
                }
                allowClear
                className="flex-1"
                size="large"
                onFocus={() => setIsSearchFocused(true)}
                onBlur={() => setIsSearchFocused(false)}
                style={{
                  borderRadius: '8px',
                }}
              />
              {searchKeyword && (
                <Button 
                  onClick={() => {
                    setSearchKeyword("");
                    setSearchInput("");
                    setCurrentPage(1);
                  }}
                  size="large"
                  className="rounded-lg"
                >
                  清除搜索
                </Button>
              )}
            </div>
          </div>

          {/* 搜索结果展示 */}
          <AnimatePresence>
            {searchKeyword && content && content.totalMatches > 0 && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="border-b border-gray-100"
              >
                <Alert
                  message={
                    <div className="flex items-center gap-2">
                      <FileSearchOutlined className="text-green-500" />
                      <span>找到 <span className="font-semibold">{content.totalMatches}</span> 个匹配项</span>
                    </div>
                  }
                  type="success"
                  showIcon={false}
                  className="rounded-none border-0 bg-green-50"
                  description={
                    <List
                      size="small"
                      dataSource={content.searchMatches?.slice(0, 5)}
                      className="mt-2"
                      renderItem={(match) => (
                        <List.Item 
                          className="cursor-pointer px-3 py-2 rounded-lg hover:bg-white hover:shadow-sm transition-all duration-200 border-0 mb-1"
                          onClick={() => {
                            goToMatch(match);
                            scrollToMatch(match);
                          }}
                        >
                          <Space>
                            <Tag color="blue" className="rounded-full text-xs">
                              第{match.pageNumber}页
                            </Tag>
                            <Text ellipsis style={{ maxWidth: 500 }} className="text-sm text-gray-600">
                              {highlightText(match.context, searchKeyword)}
                            </Text>
                          </Space>
                        </List.Item>
                      )}
                    />
                  }
                />
              </motion.div>
            )}
          </AnimatePresence>

          {searchKeyword && content && content.totalMatches === 0 && (
            <Alert
              message={
                <div className="flex items-center gap-2">
                  <InfoCircleOutlined className="text-blue-500" />
                  <span>未找到匹配项</span>
                </div>
              }
              type="info"
              showIcon={false}
              className="rounded-none border-0 bg-blue-50"
            />
          )}

          {/* 内容区域 */}
          <div className="flex-1 overflow-hidden flex">
            {/* 左侧内容 */}
            <div className="flex-1 overflow-auto p-6 bg-gray-50/30 document-content-scroll">
              <Spin spinning={loading} tip="加载中...">
                {content ? (
                  content.pages.length > 0 ? (
                    <div className="space-y-6 max-w-4xl mx-auto">
                      {content.pages.map((page, index) => (
                        <motion.div
                          key={page.pageNumber}
                          initial={{ opacity: 0, y: 20 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ delay: index * 0.05 }}
                        >
                          <Card 
                            className="shadow-sm border-0 overflow-hidden"
                            title={
                              <div className="flex items-center gap-3 py-1">
                                <div 
                                  className="w-8 h-8 rounded-lg flex items-center justify-center text-sm"
                                  style={{ 
                                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                                  }}
                                >
                                  <span className="text-white font-semibold">
                                    {page.pageNumber}
                                  </span>
                                </div>
                                <span className="font-medium text-gray-700">
                                  第 {page.pageNumber} 页
                                </span>
                              </div>
                            }
                            headStyle={{ 
                              background: '#f8fafc',
                              borderBottom: '1px solid #e2e8f0',
                              padding: '12px 20px',
                            }}
                            bodyStyle={{ padding: '24px' }}
                          >
                            <div 
                              className="text-gray-700 leading-relaxed text-base whitespace-pre-wrap break-words"
                              style={{ 
                                fontFamily: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
                                lineHeight: 1.8,
                              }}
                            >
                              {searchKeyword 
                                ? highlightText(page.content, searchKeyword)
                                : page.content
                              }
                            </div>
                          </Card>
                        </motion.div>
                      ))}
                    </div>
                  ) : (
                    <Empty 
                      description="文档内容为空" 
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      className="py-12"
                    />
                  )
                ) : !loading ? (
                  <Empty 
                    description="加载失败" 
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    className="py-12"
                  />
                ) : null}
              </Spin>
            </div>
          </div>

          {/* 底部页码 */}
          {content && content.totalPages > 1 && (
            <div className="px-6 py-4 border-t border-gray-100 bg-white flex items-center justify-center">
              <Space size="middle">
                <Button
                  size="middle"
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage <= 1}
                  icon={<ArrowUpOutlined />}
                  className="rounded-lg"
                >
                  上一页
                </Button>
                
                <div className="flex items-center gap-2 px-4 py-2 bg-gray-50 rounded-lg">
                  <span className="text-gray-500">第</span>
                  <span className="font-semibold text-gray-800 min-w-[20px] text-center">
                    {currentPage}
                  </span>
                  <span className="text-gray-500">/ {content.totalPages} 页</span>
                </div>

                <Button
                  size="middle"
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage >= content.totalPages}
                  icon={<ArrowDownOutlined />}
                  className="rounded-lg"
                >
                  下一页
                </Button>
              </Space>
            </div>
          )}
        </div>
      )}
      
      <style jsx global>{`
        .document-preview-modal .ant-modal-content {
          border-radius: 16px;
          overflow: hidden;
          box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
        }
        
        .document-content-scroll::-webkit-scrollbar {
          width: 8px;
        }
        .document-content-scroll::-webkit-scrollbar-track {
          background: transparent;
        }
        .document-content-scroll::-webkit-scrollbar-thumb {
          background: #cbd5e1;
          border-radius: 4px;
        }
        .document-content-scroll::-webkit-scrollbar-thumb:hover {
          background: #94a3b8;
        }
        
        .highlight-animation {
          animation: highlight-pulse 2s ease-in-out;
        }
        
        @keyframes highlight-pulse {
          0%, 100% { 
            background-color: transparent;
          }
          50% { 
            background-color: rgba(255, 192, 105, 0.3);
            box-shadow: 0 0 0 4px rgba(255, 192, 105, 0.3);
            border-radius: 4px;
          }
        }
        
        .document-preview-modal .ant-input-search .ant-input {
          border-radius: 8px 0 0 8px;
          border-right: none;
        }
        
        .document-preview-modal .ant-input-search .ant-input-group-addon {
          background: transparent;
        }
        
        .document-preview-modal .ant-input-search .ant-input-group-addon .ant-btn {
          border-radius: 0 8px 8px 0;
        }
      `}</style>
    </Modal>
  );
}

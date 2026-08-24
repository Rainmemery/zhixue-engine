"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Upload,
  message,
  Popconfirm,
  Tag,
  Tooltip,
  Typography,
  Row,
  Col,
  Statistic,
  Empty,
  Alert,
  Progress,
  Input,
  Select,
  Drawer,
  Spin,
  Badge,
  Divider,
} from "antd";
import {
  PlusOutlined,
  DeleteOutlined,
  FileTextOutlined,
  ReloadOutlined,
  EyeOutlined,
  UploadOutlined,
  SearchOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  InboxOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileMarkdownOutlined,
  FileOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  CloudUploadOutlined,
} from "@ant-design/icons";
import { ragService, Document, KnowledgeBase } from "@/services/ragService";
import type { UploadFile, UploadProps } from "antd/es/upload/interface";

const { Title, Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;
const { Dragger } = Upload;

interface DocumentTabProps {
  knowledgeBaseId?: number;
  knowledgeBaseName?: string;
}

// 渐变按钮组件
interface GradientButtonProps {
  children: React.ReactNode;
  icon?: React.ReactNode;
  onClick?: () => void;
  loading?: boolean;
  type?: 'primary' | 'default' | 'danger';
  disabled?: boolean;
  gradient?: string;
  htmlType?: 'button' | 'submit';
}

const GradientButton = ({ 
  children, 
  icon, 
  onClick, 
  loading, 
  type = 'default',
  disabled,
  gradient = 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  htmlType = 'button',
}: GradientButtonProps) => {
  const isPrimary = type === 'primary';
  const isDanger = type === 'danger';
  
  return (
    <motion.div whileHover={!loading && !disabled ? { scale: 1.02 } : {}} whileTap={!loading && !disabled ? { scale: 0.98 } : {}}>
      <Button
        type={isPrimary ? 'primary' : isDanger ? 'primary' : 'default'}
        icon={icon}
        onClick={onClick}
        loading={loading}
        disabled={disabled}
        htmlType={htmlType}
        size="large"
        danger={isDanger}
        style={{
          height: 42,
          borderRadius: 10,
          fontWeight: 600,
          fontSize: 15,
          padding: '0 24px',
          background: isPrimary ? gradient : isDanger ? undefined : undefined,
          border: isPrimary || isDanger ? 'none' : undefined,
          boxShadow: isPrimary ? '0 4px 15px rgba(102, 126, 234, 0.35)' : '0 2px 8px rgba(0, 0, 0, 0.06)',
          transition: 'all 0.3s ease',
        }}
      >
        {children}
      </Button>
    </motion.div>
  );
};

// 统计卡片组件
interface StatCardProps {
  title: string;
  value: number | string;
  icon: React.ReactNode;
  color: string;
  gradient: string;
  delay?: number;
}

const StatCard = ({ title, value, icon, color, gradient, delay = 0 }: StatCardProps) => (
  <motion.div
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.4, delay }}
    whileHover={{ y: -4, boxShadow: '0 12px 32px rgba(0, 0, 0, 0.12)' }}
    style={{
      background: '#fff',
      borderRadius: 14,
      padding: 20,
      boxShadow: '0 4px 16px rgba(0, 0, 0, 0.06)',
      border: '1px solid #f0f0f0',
      transition: 'all 0.3s ease',
    }}
  >
    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
      <motion.div
        whileHover={{ scale: 1.1, rotate: 5 }}
        style={{
          width: 48,
          height: 48,
          borderRadius: 12,
          background: gradient,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: `0 4px 12px ${color}40`,
        }}
      >
        {icon}
      </motion.div>
      <div>
        <Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>{title}</Text>
        <div style={{ fontSize: 28, fontWeight: 700, color: '#262626', marginTop: 4 }}>
          {value}
        </div>
      </div>
    </div>
  </motion.div>
);

// 获取文件图标
const getFileIcon = (fileName: string) => {
  const ext = fileName.split('.').pop()?.toLowerCase();
  switch (ext) {
    case 'pdf':
      return <FilePdfOutlined style={{ color: '#ff4d4f', fontSize: 20 }} />;
    case 'docx':
    case 'doc':
      return <FileWordOutlined style={{ color: '#1890ff', fontSize: 20 }} />;
    case 'md':
      return <FileMarkdownOutlined style={{ color: '#13c2c2', fontSize: 20 }} />;
    default:
      return <FileOutlined style={{ color: '#8c8c8c', fontSize: 20 }} />;
  }
};

export default function DocumentTab({ knowledgeBaseId, knowledgeBaseName }: DocumentTabProps) {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewContent, setPreviewContent] = useState<string>("");
  const [previewTitle, setPreviewTitle] = useState<string>("");
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [previewLoading, setPreviewLoading] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<Document | null>(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<number | undefined>(knowledgeBaseId);
  const [kbLoading, setKbLoading] = useState(false);
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (knowledgeBaseId !== undefined) {
      setSelectedKbId(knowledgeBaseId);
    }
  }, [knowledgeBaseId]);

  const loadData = useCallback(async (page = 1, pageSize = 10, kbId?: number) => {
    setLoading(true);
    try {
      const targetKbId = kbId ?? selectedKbId;
      if (!targetKbId) {
        const [kbData] = await Promise.all([
          ragService.knowledge.listEnabled(),
        ]);
        if (kbData && kbData.length > 0) {
          setKnowledgeBases(kbData);
          if (!selectedKbId) {
            setSelectedKbId(kbData[0].id);
          }
        }
        return;
      }

      const [docData, kbData] = await Promise.all([
        ragService.document.list(targetKbId, page, pageSize),
        ragService.knowledge.listEnabled(),
      ]);
      
      if (docData && docData.items) {
        setDocuments(docData.items);
        setPagination(prev => ({
          ...prev,
          current: docData.page || page,
          total: docData.total || 0,
          pageSize: docData.size || pageSize,
        }));
      }
      if (kbData && kbData.length > 0) {
        setKnowledgeBases(kbData);
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "加载数据失败");
    } finally {
      setLoading(false);
    }
  }, [selectedKbId]);

  const loadKnowledgeBases = useCallback(async () => {
    setKbLoading(true);
    try {
      const kbData = await ragService.knowledge.listEnabled();
      if (kbData && kbData.length > 0) {
        setKnowledgeBases(kbData);
        if (knowledgeBaseId === undefined && !selectedKbId) {
          setSelectedKbId(kbData[0].id);
        }
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "加载知识库列表失败");
    } finally {
      setKbLoading(false);
    }
  }, [knowledgeBaseId, selectedKbId]);

  useEffect(() => {
    if (!knowledgeBaseId) {
      loadKnowledgeBases();
    } else {
      loadData(1, 10, knowledgeBaseId);
    }
  }, [knowledgeBaseId, loadData, loadKnowledgeBases]);

  const handleRefresh = () => {
    if (selectedKbId) {
      loadData(pagination.current, pagination.pageSize, selectedKbId);
    }
  };

  const handlePreview = async (record: Document) => {
    setPreviewLoading(true);
    setSelectedDocument(record);
    setPreviewTitle(record.title || record.fileName);
    setPreviewVisible(true);
    setCurrentPage(1);
    setSearchKeyword("");
    
    try {
      const content = await ragService.document.getContent(record.id, 1, 100);
      if (content) {
        setPreviewContent(content.fullContent || content.pages?.map(p => p.content).join("\n") || "");
        setTotalPages(content.totalPages || 1);
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "加载文档内容失败");
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleSearchInDocument = async () => {
    if (!selectedDocument) return;
    
    setPreviewLoading(true);
    try {
      const content = await ragService.document.getContent(
        selectedDocument.id, 
        currentPage, 
        100, 
        searchKeyword
      );
      if (content) {
        setPreviewContent(content.fullContent || content.pages?.map(p => p.content).join("\n") || "");
        setTotalPages(content.totalPages || 1);
        if (content.searchMatches && content.searchMatches.length > 0) {
          message.success(`找到 ${content.totalMatches} 处匹配`);
        }
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "搜索失败");
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await ragService.document.delete(id);
      message.success({
        content: "删除成功",
        icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      });
      if (selectedKbId) {
        loadData(pagination.current, pagination.pageSize, selectedKbId);
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "删除失败");
    }
  };

  const handleReprocess = async (id: number) => {
    try {
      await ragService.document.reprocess(id);
      message.success({
        content: "已重新处理文档",
        icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      });
      if (selectedKbId) {
        loadData(pagination.current, pagination.pageSize, selectedKbId);
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "重新处理失败");
    }
  };

  const handleKnowledgeBaseChange = (kbId: number) => {
    setSelectedKbId(kbId);
    loadData(1, pagination.pageSize, kbId);
  };

  const getStatusTag = (status: number, statusText: string) => {
    switch (status) {
      case 1:
        return (
          <motion.div
            initial={{ scale: 0.8 }}
            animate={{ scale: 1 }}
            transition={{ type: "spring", stiffness: 200 }}
          >
            <Tag icon={<CheckCircleOutlined />} color="success" style={{ padding: '4px 8px', fontSize: 13 }}>
              {statusText || "完成"}
            </Tag>
          </motion.div>
        );
      case 0:
        return (
          <Tag icon={<SyncOutlined spin />} color="processing" style={{ padding: '4px 8px', fontSize: 13 }}>
            {statusText || "处理中"}
          </Tag>
        );
      case -1:
        return (
          <Tag icon={<CloseCircleOutlined />} color="error" style={{ padding: '4px 8px', fontSize: 13 }}>
            {statusText || "失败"}
          </Tag>
        );
      default:
        return <Tag>{statusText || "未知"}</Tag>;
    }
  };

  const uploadProps: UploadProps = {
    fileList,
    onChange: ({ fileList: newFileList }) => {
      setFileList(newFileList);
    },
    beforeUpload: (file) => {
      const isAllowedType = [
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown",
      ].includes(file.type);
      
      if (!isAllowedType) {
        message.error("只能上传 PDF、DOCX、TXT 或 MD 文件");
        return Upload.LIST_IGNORE;
      }
      
      const isLt50M = file.size / 1024 / 1024 < 50;
      if (!isLt50M) {
        message.error("文件大小不能超过 50MB");
        return Upload.LIST_IGNORE;
      }
      
      return true;
    },
    customRequest: async ({ file, onSuccess, onError }) => {
      if (!selectedKbId) {
        message.error("请先选择知识库");
        onError?.(new Error("未选择知识库"));
        return;
      }

      setUploading(true);
      setUploadProgress(0);
      abortControllerRef.current = new AbortController();

      try {
        const actualFile = file as File;
        await ragService.document.uploadWithChunks(
          selectedKbId,
          actualFile,
          undefined,
          undefined,
          (progress, uploadedChunks, totalChunks) => {
            setUploadProgress(progress);
          }
        );
        
        message.success({
          content: `${actualFile.name} 上传成功`,
          icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
        });
        setFileList([]);
        setUploadModalVisible(false);
        onSuccess?.(null);
        if (selectedKbId) {
          loadData(pagination.current, pagination.pageSize, selectedKbId);
        }
      } catch (err: unknown) {
        if ((err as any).name === "AbortError") {
          message.info("已取消上传");
        } else {
          const error = err as { response?: { data?: { msg?: string } } };
          message.error(error.response?.data?.msg || "上传失败");
          onError?.(new Error(error.response?.data?.msg || "上传失败"));
        }
      } finally {
        setUploading(false);
        setUploadProgress(0);
        abortControllerRef.current = null;
      }
    },
  };

  const handleCancelUpload = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
  };

  const columns = [
    {
      title: "文档信息",
      dataIndex: "title",
      key: "title",
      render: (text: string, record: Document) => (
        <motion.div
          initial={{ opacity: 0, x: -10 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.3 }}
          style={{ display: 'flex', alignItems: 'center', gap: 12 }}
        >
          <div style={{
            width: 40,
            height: 40,
            borderRadius: 10,
            background: 'linear-gradient(135deg, #f0f5ff 0%, #e6f7ff 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: '1px solid #d9e8ff',
          }}>
            {getFileIcon(record.fileName)}
          </div>
          <Space direction="vertical" size={2}>
            <Text strong style={{ fontSize: 14 }}>{text || record.fileName}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{record.fileName}</Text>
          </Space>
        </motion.div>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 120,
      render: (status: number, record: Document) => getStatusTag(status, record.statusText),
    },
    {
      title: "文件大小",
      dataIndex: "fileSize",
      key: "fileSize",
      width: 120,
      render: (size: number) => {
        if (size >= 1024 * 1024) {
          return <Tag color="blue">{(size / 1024 / 1024).toFixed(2)} MB</Tag>;
        }
        return <Tag color="cyan">{(size / 1024).toFixed(2)} KB</Tag>;
      },
    },
    {
      title: "分块数",
      dataIndex: "chunkCount",
      key: "chunkCount",
      width: 100,
      render: (count: number) => (
        <Badge count={count || 0} style={{ backgroundColor: '#722ed1' }} />
      ),
    },
    {
      title: "上传时间",
      dataIndex: "createdAt",
      key: "createdAt",
      width: 180,
      render: (date: string) => (
        <Space size="small">
          <ClockCircleOutlined style={{ color: '#8c8c8c' }} />
          <span>{new Date(date).toLocaleString("zh-CN")}</span>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: Document) => (
        <Space size="small">
          <Tooltip title="预览">
            <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
              <Button
                type="text"
                icon={<EyeOutlined />}
                onClick={() => handlePreview(record)}
                style={{ color: '#1890ff' }}
              />
            </motion.div>
          </Tooltip>
          {record.status === -1 && (
            <Tooltip title="重新处理">
              <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                <Button
                  type="text"
                  icon={<SyncOutlined />}
                  onClick={() => handleReprocess(record.id)}
                  style={{ color: '#fa8c16' }}
                />
              </motion.div>
            </Tooltip>
          )}
          <Popconfirm
            title="确定删除该文档？"
            description="删除后无法恢复，是否继续？"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Tooltip title="删除">
              <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                <Button type="text" danger icon={<DeleteOutlined />} />
              </motion.div>
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const totalChunks = documents.reduce((sum, doc) => sum + (doc.chunkCount || 0), 0);
  const processingDocs = documents.filter(doc => doc.status === 0).length;
  const completedDocs = documents.filter(doc => doc.status === 1).length;
  const failedDocs = documents.filter(doc => doc.status === -1).length;

  const currentKb = knowledgeBases.find(kb => kb.id === selectedKbId);

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      {/* 页面标题和操作区 */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
          padding: '20px 24px',
          background: 'linear-gradient(135deg, #f8faff 0%, #ffffff 100%)',
          borderRadius: 12,
          border: '1px solid rgba(114, 46, 209, 0.1)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 44,
            height: 44,
            borderRadius: 12,
            background: 'linear-gradient(135deg, #722ed1 0%, #b37feb 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 4px 12px rgba(114, 46, 209, 0.25)',
          }}>
            <FileTextOutlined style={{ color: '#fff', fontSize: 22 }} />
          </div>
          <div>
            <Title level={5} style={{ margin: 0, fontSize: 18 }}>
              {knowledgeBaseName || currentKb?.name || "文档管理"}
            </Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              管理知识库中的文档和分块数据
            </Text>
          </div>
        </div>
        <Space size="middle">
          <GradientButton
            icon={<ReloadOutlined spin={loading} />}
            onClick={handleRefresh}
            loading={loading}
          >
            <span className="hidden sm:inline">刷新</span>
          </GradientButton>
          <GradientButton
            type="primary"
            icon={<CloudUploadOutlined />}
            onClick={() => setUploadModalVisible(true)}
            disabled={!selectedKbId || kbLoading}
            gradient="linear-gradient(135deg, #722ed1 0%, #b37feb 100%)"
          >
            <span className="hidden sm:inline">上传文档</span>
            <span className="sm:hidden">上传</span>
          </GradientButton>
        </Space>
      </motion.div>

      {/* 知识库选择器 */}
      {!knowledgeBaseId && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
          style={{ marginBottom: 24 }}
        >
          <Card
            style={{
              borderRadius: 12,
              boxShadow: '0 2px 12px rgba(0, 0, 0, 0.06)',
              border: '1px solid #f0f0f0',
            }}
            bodyStyle={{ padding: 16 }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
              <DatabaseOutlined style={{ color: '#722ed1', fontSize: 20 }} />
              <span style={{ fontWeight: 500 }}>选择知识库：</span>
              <Select
                value={selectedKbId}
                onChange={handleKnowledgeBaseChange}
                style={{ width: '100%', maxWidth: 320, flex: 1 }}
                placeholder="选择知识库"
                loading={kbLoading}
                size="large"
              >
                {knowledgeBases.map((kb) => (
                  <Option key={kb.id} value={kb.id}>
                    {kb.name}
                  </Option>
                ))}
              </Select>
            </div>
          </Card>
        </motion.div>
      )}

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="文档总数"
            value={documents.length}
            icon={<FileTextOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#722ed1"
            gradient="linear-gradient(135deg, #722ed1 0%, #b37feb 100%)"
            delay={0.1}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="处理中"
            value={processingDocs}
            icon={<SyncOutlined spin={processingDocs > 0} style={{ color: '#fff', fontSize: 24 }} />}
            color="#1890ff"
            gradient="linear-gradient(135deg, #1890ff 0%, #36cfc9 100%)"
            delay={0.2}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="已完成"
            value={completedDocs}
            icon={<CheckCircleOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#52c41a"
            gradient="linear-gradient(135deg, #52c41a 0%, #95de64 100%)"
            delay={0.3}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="分块总数"
            value={totalChunks}
            icon={<DatabaseOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#fa8c16"
            gradient="linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)"
            delay={0.4}
          />
        </Col>
      </Row>

      {/* 失败文档警告 */}
      <AnimatePresence>
        {failedDocs > 0 && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            style={{ marginBottom: 16 }}
          >
            <Alert
              message={`有 ${failedDocs} 个文档处理失败`}
              description="请检查文档格式是否正确，或尝试重新处理失败的文档。"
              type="error"
              showIcon
              closable
              style={{
                borderRadius: 10,
                border: '1px solid #ffccc7',
              }}
            />
          </motion.div>
        )}
      </AnimatePresence>

      {/* 文档列表 */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.3 }}
      >
        <Card
          style={{
            borderRadius: 16,
            boxShadow: '0 4px 20px rgba(0, 0, 0, 0.06)',
            border: '1px solid #f0f0f0',
            overflow: 'hidden',
          }}
          bodyStyle={{ padding: 0 }}
        >
          <div className="overflow-x-auto">
            <Table
              columns={columns}
              dataSource={documents}
              rowKey="id"
              loading={loading}
              scroll={{ x: 'max-content' }}
              size="small"
              locale={{
                emptyText: (
                  <Empty 
                    description={
                      selectedKbId 
                        ? "暂无文档，点击上方「上传文档」按钮添加" 
                        : "请先选择知识库"
                    }
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  >
                    {selectedKbId && (
                      <Button 
                        type="primary" 
                        icon={<UploadOutlined />}
                        onClick={() => document.querySelector('.upload-trigger')?.parentElement?.querySelector('input')?.click()}
                        size="middle"
                      >
                        立即上传
                      </Button>
                    )}
                  </Empty>
                ),
              }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: pagination.total,
                showSizeChanger: true,
                showTotal: (total: number) => `共 ${total} 条记录`,
                onChange: (page, pageSize) => {
                  if (selectedKbId) {
                    loadData(page, pageSize, selectedKbId);
                  }
                },
                size: 'small',
              }}
            />
          </div>
        </Card>
      </motion.div>

      {/* 文档预览抽屉 */}
      <Drawer
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 36,
              height: 36,
              borderRadius: 8,
              background: 'linear-gradient(135deg, #1890ff 0%, #36cfc9 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <EyeOutlined style={{ color: '#fff' }} />
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>{previewTitle}</div>
              {selectedDocument && (
                <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                  {getStatusTag(selectedDocument.status, selectedDocument.statusText)}
                </div>
              )}
            </div>
          </div>
        }
        placement="right"
        width={850}
        open={previewVisible}
        onClose={() => {
          setPreviewVisible(false);
          setPreviewContent("");
          setSelectedDocument(null);
        }}
        extra={
          <Space>
            <Input.Search
              placeholder="搜索关键词"
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onSearch={handleSearchInDocument}
              style={{ width: 220 }}
              allowClear
              size="large"
            />
          </Space>
        }
        headerStyle={{
          background: 'linear-gradient(135deg, #f8faff 0%, #ffffff 100%)',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        {previewLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 400 }}>
            <Spin size="large" tip="加载中..." />
          </div>
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
          >
            <div
              style={{
                background: '#f6f8fa',
                borderRadius: 12,
                padding: 20,
                minHeight: 400,
                fontSize: 14,
                lineHeight: 1.8,
                color: '#262626',
              }}
            >
              <pre
                style={{
                  whiteSpace: "pre-wrap",
                  wordWrap: "break-word",
                  fontFamily: 'inherit',
                  margin: 0,
                }}
                dangerouslySetInnerHTML={{
                  __html: searchKeyword
                    ? previewContent.replace(
                        new RegExp(`(${searchKeyword})`, "gi"),
                        '<mark style="background-color: #fff566; padding: 2px 4px; border-radius: 4px; font-weight: 600;">$1</mark>'
                      )
                    : previewContent
                }}
              />
            </div>
            {totalPages > 1 && (
              <div style={{ marginTop: 16, textAlign: "center" }}>
                <Text type="secondary">共 {totalPages} 页</Text>
              </div>
            )}
          </motion.div>
        )}
      </Drawer>

      {/* 上传文档模态框 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #722ed1 0%, #b37feb 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <CloudUploadOutlined style={{ color: '#fff', fontSize: 20 }} />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: 18 }}>上传文档</div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                支持 PDF、DOCX、TXT、MD 格式，最大 50MB
              </Text>
            </div>
          </div>
        }
        open={uploadModalVisible}
        onCancel={() => {
          if (!uploading) {
            setUploadModalVisible(false);
            setFileList([]);
          }
        }}
        footer={[
          <Button
            key="cancel"
            onClick={() => {
              setUploadModalVisible(false);
              setFileList([]);
            }}
            disabled={uploading}
            size="large"
          >
            取消
          </Button>,
          <GradientButton
            key="upload"
            type="primary"
            icon={<CloudUploadOutlined />}
            loading={uploading}
            disabled={fileList.length === 0 || uploading}
            gradient="linear-gradient(135deg, #722ed1 0%, #b37feb 100%)"
            htmlType="submit"
          >
            {uploading ? `上传中 ${uploadProgress}%` : '开始上传'}
          </GradientButton>,
        ]}
        width={560}
        destroyOnClose
      >
        <div style={{ padding: '24px 0' }}>
          <Dragger {...uploadProps} maxCount={1} disabled={uploading}>
            <motion.div
              initial={{ scale: 1 }}
              whileHover={{ scale: 1.05 }}
              style={{
                padding: '40px 20px',
                border: '2px dashed #d9d9d9',
                borderRadius: 12,
                background: '#fafafa',
              }}
            >
              <p style={{ fontSize: 48, marginBottom: 16 }}>
                <InboxOutlined style={{ color: '#722ed1' }} />
              </p>
              <p style={{ fontSize: 16, color: '#262626', fontWeight: 500 }}>
                点击或拖拽文件到此区域上传
              </p>
              <p style={{ fontSize: 14, color: '#8c8c8c' }}>
                支持 PDF、DOCX、TXT、MD 格式，单个文件最大 50MB
              </p>
            </motion.div>
          </Dragger>

          {/* 上传进度 */}
          <AnimatePresence>
            {uploading && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                style={{ marginTop: 24 }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <Text strong>上传进度</Text>
                  <Text type="secondary">{uploadProgress}%</Text>
                </div>
                <Progress
                  percent={uploadProgress}
                  strokeColor={{ "0%": "#722ed1", "100%": "#b37feb" }}
                  strokeWidth={8}
                  showInfo={false}
                />
                <div style={{ textAlign: 'center', marginTop: 12 }}>
                  <Button
                    size="small"
                    danger
                    onClick={handleCancelUpload}
                    icon={<CloseCircleOutlined />}
                  >
                    取消上传
                  </Button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* 文件列表 */}
          {fileList.length > 0 && !uploading && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              style={{
                marginTop: 20,
                padding: 16,
                background: '#f6ffed',
                borderRadius: 10,
                border: '1px solid #b7eb8f',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                {getFileIcon(fileList[0].name)}
                <div style={{ flex: 1 }}>
                  <Text strong>{fileList[0].name}</Text>
                  <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                    {fileList[0].size && `${(fileList[0].size / 1024 / 1024).toFixed(2)} MB`}
                  </div>
                </div>
                <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 20 }} />
              </div>
            </motion.div>
          )}
        </div>
      </Modal>
    </motion.div>
  );
}

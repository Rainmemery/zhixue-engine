"use client";

import { useState, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Switch,
  message,
  Popconfirm,
  Tag,
  Tooltip,
  Typography,
  Alert,
  Row,
  Col,
  Badge,
  Divider,
  Statistic,
} from "antd";
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckOutlined,
  ApiOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  StarOutlined,
  ThunderboltOutlined,
  ExperimentOutlined,
  CrownOutlined,
  SafetyOutlined,
} from "@ant-design/icons";
import { ragService, EmbeddingModel, EmbeddingModelValidateResult } from "@/services/ragService";
import { useDataLoading } from "@/hooks/useDataLoading";
import { ErrorHandler } from "@/lib/errorHandler";

const { Title, Text } = Typography;

// 渐变按钮组件
interface GradientButtonProps {
  children: React.ReactNode;
  icon?: React.ReactNode;
  onClick?: () => void;
  loading?: boolean;
  type?: 'primary' | 'default' | 'danger';
  disabled?: boolean;
  gradient?: string;
}

const GradientButton = ({ 
  children, 
  icon, 
  onClick, 
  loading, 
  type = 'default',
  disabled,
  gradient = 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
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
        size="large"
        danger={isDanger}
        style={{
          height: 42,
          borderRadius: 10,
          fontWeight: 600,
          fontSize: 15,
          padding: '0 24px',
          background: isPrimary ? gradient : undefined,
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

// 模型卡片组件
interface ModelCardProps {
  model: EmbeddingModel;
  index: number;
  onEdit: (model: EmbeddingModel) => void;
  onSetDefault: (id: number) => void;
  onToggle: (id: number, enabled: boolean) => void;
  onDelete: (id: number) => void;
}

const ModelCard = ({ model, index, onEdit, onSetDefault, onToggle, onDelete }: ModelCardProps) => (
  <motion.div
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.3, delay: 0.05 * index }}
    whileHover={{ y: -4, boxShadow: '0 12px 32px rgba(0, 0, 0, 0.12)' }}
    style={{
      background: '#fff',
      borderRadius: 16,
      padding: 20,
      boxShadow: '0 4px 16px rgba(0, 0, 0, 0.06)',
      border: model.isDefault ? '2px solid #faad14' : '1px solid #f0f0f0',
      position: 'relative',
      overflow: 'hidden',
    }}
  >
    {/* 默认模型标识 */}
    {model.isDefault && (
      <motion.div
        initial={{ x: 50, opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        style={{
          position: 'absolute',
          top: 12,
          right: -28,
          width: 100,
          background: '#faad14',
          color: '#fff',
          textAlign: 'center',
          padding: '4px 0',
          fontSize: 12,
          fontWeight: 600,
          transform: 'rotate(45deg)',
        }}
      >
        默认
      </motion.div>
    )}

    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
      {/* 模型图标 */}
      <motion.div
        whileHover={{ scale: 1.1, rotate: 5 }}
        style={{
          width: 56,
          height: 56,
          borderRadius: 14,
          background: model.status 
            ? 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)' 
            : 'linear-gradient(135deg, #8c8c8c 0%, #bfbfbf 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: model.status 
            ? '0 4px 12px rgba(82, 196, 26, 0.3)' 
            : '0 4px 12px rgba(0, 0, 0, 0.1)',
          flexShrink: 0,
        }}
      >
        <ApiOutlined style={{ color: '#fff', fontSize: 28 }} />
      </motion.div>

      <div style={{ flex: 1, minWidth: 0 }}>
        {/* 模型名称 */}
        <div style={{ marginBottom: 8 }}>
          <Text strong style={{ fontSize: 18, display: 'block' }}>
            {model.displayName}
          </Text>
          <Text code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 8px', borderRadius: 4 }}>
            {model.modelName}
          </Text>
        </div>

        {/* 维度标签 */}
        <div style={{ marginBottom: 12 }}>
          <Tag color="blue" style={{ fontSize: 13, padding: '4px 12px' }}>
            {model.dimension} 维向量
          </Tag>
          {model.status ? (
            <Tag color="success" icon={<CheckCircleOutlined />} style={{ marginLeft: 8 }}>
              已启用
            </Tag>
          ) : (
            <Tag color="default" icon={<CloseCircleOutlined />} style={{ marginLeft: 8 }}>
              已禁用
            </Tag>
          )}
        </div>

        {/* 描述 */}
        {model.description && (
          <Text type="secondary" style={{ fontSize: 13, display: 'block', marginBottom: 12 }}>
            {model.description}
          </Text>
        )}

        <Divider style={{ margin: '12px 0' }} />

        {/* 操作按钮 */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Switch
            checked={model.status}
            onChange={(checked) => onToggle(model.id, checked)}
            checkedChildren="启用"
            unCheckedChildren="禁用"
            size="default"
          />
          <Space>
            <Tooltip title="编辑">
              <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => onEdit(model)}
                  style={{ color: '#1890ff' }}
                />
              </motion.div>
            </Tooltip>
            {!model.isDefault && (
              <Tooltip title="设为默认">
                <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                  <Button
                    type="text"
                    icon={<CrownOutlined />}
                    onClick={() => onSetDefault(model.id)}
                    style={{ color: '#faad14' }}
                  />
                </motion.div>
              </Tooltip>
            )}
            {!model.isDefault && (
              <Popconfirm
                title="确定删除该模型？"
                description="删除后将无法恢复，是否继续？"
                onConfirm={() => onDelete(model.id)}
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
            )}
          </Space>
        </div>
      </div>
    </div>
  </motion.div>
);

export default function EmbeddingModelTab() {
  const [modalVisible, setModalVisible] = useState(false);
  const [validateModalVisible, setValidateModalVisible] = useState(false);
  const [editingModel, setEditingModel] = useState<EmbeddingModel | null>(null);
  const [validateModelName, setValidateModelName] = useState("");
  const [validateResult, setValidateResult] = useState<EmbeddingModelValidateResult | null>(null);
  const [validating, setValidating] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [models, setModels] = useState<EmbeddingModel[]>([]);
  const [form] = Form.useForm();
  const [viewMode, setViewMode] = useState<'card' | 'table'>('card');

  const { loading, reload } = useDataLoading<EmbeddingModel[]>({
    loadData: async () => {
      const data = await ragService.embeddingModel.list();
      const modelsData = data || [];
      setPagination(prev => ({
        ...prev,
        total: modelsData.length,
      }));
      setModels(modelsData);
      return modelsData;
    },
    immediate: true,
    maxRetries: 3,
    errorContext: "加载 Embedding 模型",
  });

  const handleCreate = () => {
    setEditingModel(null);
    form.resetFields();
    form.setFieldsValue({ status: true, isDefault: false });
    setModalVisible(true);
  };

  const handleEdit = (record: EmbeddingModel) => {
    setEditingModel(record);
    form.setFieldsValue({
      modelName: record.modelName,
      displayName: record.displayName,
      dimension: record.dimension,
      description: record.description,
      status: record.status,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await ragService.embeddingModel.delete(id);
      message.success({
        content: "删除成功",
        icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      });
      await reload();
    } catch (err: unknown) {
      new ErrorHandler(err)
        .setContext("删除 Embedding 模型")
        .handle();
    }
  };

  const handleSetDefault = async (id: number) => {
    try {
      await ragService.embeddingModel.setDefault(id);
      message.success({
        content: "设置默认模型成功",
        icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      });
      await reload();
    } catch (err: unknown) {
      new ErrorHandler(err)
        .setContext("设置默认模型")
        .handle();
    }
  };

  const handleToggle = async (id: number, enabled: boolean) => {
    try {
      await ragService.embeddingModel.toggle(id, enabled);
      message.success({
        content: enabled ? "模型已启用" : "模型已禁用",
        icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      });
      await reload();
    } catch (err: unknown) {
      new ErrorHandler(err)
        .setContext("切换模型状态")
        .handle();
    }
  };

  const handleValidate = async () => {
    if (!validateModelName.trim()) {
      message.warning("请输入模型名称");
      return;
    }
    setValidating(true);
    setValidateResult(null);
    try {
      const result = await ragService.embeddingModel.validate(validateModelName);
      setValidateResult(result);
    } catch (err: unknown) {
      new ErrorHandler(err)
        .setContext("验证模型可用性")
        .handle();
    } finally {
      setValidating(false);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingModel) {
        await ragService.embeddingModel.update(editingModel.id, values);
        message.success({
          content: "更新成功",
          icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
        });
      } else {
        await ragService.embeddingModel.create(values);
        message.success({
          content: "创建成功",
          icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
        });
      }
      setModalVisible(false);
      await reload();
    } catch (err: unknown) {
      new ErrorHandler(err)
        .setContext(editingModel ? "更新 Embedding 模型" : "创建 Embedding 模型")
        .handle();
    }
  };

  const columns = [
    {
      title: "模型",
      dataIndex: "modelName",
      key: "modelName",
      render: (text: string, record: EmbeddingModel) => (
        <Space direction="vertical" size={0}>
          <Text strong style={{ fontSize: 15 }}>{record.displayName}</Text>
          <Text code style={{ fontSize: 12, background: '#f5f5f5', padding: '2px 8px', borderRadius: 4 }}>
            {text}
          </Text>
        </Space>
      ),
    },
    {
      title: "向量维度",
      dataIndex: "dimension",
      key: "dimension",
      width: 120,
      render: (dim: number) => (
        <Tag color="blue" style={{ fontSize: 13 }}>{dim} 维</Tag>
      ),
    },
    {
      title: "描述",
      dataIndex: "description",
      key: "description",
      ellipsis: true,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 120,
      render: (status: boolean, record: EmbeddingModel) => (
        <Switch
          checked={status}
          onChange={(checked) => handleToggle(record.id, checked)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          size="default"
        />
      ),
    },
    {
      title: "默认",
      dataIndex: "isDefault",
      key: "isDefault",
      width: 100,
      render: (isDefault: boolean) =>
        isDefault ? (
          <Tag color="gold" icon={<CrownOutlined />}>默认</Tag>
        ) : null,
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_: unknown, record: EmbeddingModel) => (
        <Space size="small">
          <Tooltip title="编辑">
            <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
              <Button
                type="text"
                icon={<EditOutlined />}
                onClick={() => handleEdit(record)}
                style={{ color: '#1890ff' }}
              />
            </motion.div>
          </Tooltip>
          {!record.isDefault && (
            <Tooltip title="设为默认">
              <motion.div whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                <Button
                  type="text"
                  icon={<CrownOutlined />}
                  onClick={() => handleSetDefault(record.id)}
                  style={{ color: '#faad14' }}
                />
              </motion.div>
            </Tooltip>
          )}
          {!record.isDefault && (
            <Popconfirm
              title="确定删除该模型？"
              description="删除后将无法恢复，是否继续？"
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
          )}
        </Space>
      ),
    },
  ];

  const enabledModels = models.filter(m => m.status);
  const defaultModel = models.find(m => m.isDefault);

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
          border: '1px solid rgba(250, 140, 22, 0.1)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 44,
            height: 44,
            borderRadius: 12,
            background: 'linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 4px 12px rgba(250, 140, 22, 0.25)',
          }}>
            <ApiOutlined style={{ color: '#fff', fontSize: 22 }} />
          </div>
          <div>
            <Title level={5} style={{ margin: 0, fontSize: 18 }}>Embedding 模型管理</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>管理文本向量化的 Embedding 模型</Text>
          </div>
        </div>
        <Space size="middle">
          <GradientButton
            icon={<ExperimentOutlined />}
            onClick={() => setValidateModalVisible(true)}
          >
            验证模型
          </GradientButton>
          <GradientButton
            icon={<ReloadOutlined spin={loading} />}
            onClick={reload}
            loading={loading}
          >
            刷新
          </GradientButton>
          <GradientButton
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleCreate}
            gradient="linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)"
          >
            添加模型
          </GradientButton>
        </Space>
      </motion.div>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="模型总数"
            value={models.length}
            icon={<ApiOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#fa8c16"
            gradient="linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)"
            delay={0.1}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="启用中"
            value={enabledModels.length}
            icon={<CheckCircleOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#52c41a"
            gradient="linear-gradient(135deg, #52c41a 0%, #95de64 100%)"
            delay={0.2}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="已禁用"
            value={models.length - enabledModels.length}
            icon={<CloseCircleOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#8c8c8c"
            gradient="linear-gradient(135deg, #8c8c8c 0%, #bfbfbf 100%)"
            delay={0.3}
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="默认模型"
            value={defaultModel?.displayName || "-"}
            icon={<CrownOutlined style={{ color: '#fff', fontSize: 24 }} />}
            color="#faad14"
            gradient="linear-gradient(135deg, #faad14 0%, #ffc53d 100%)"
            delay={0.4}
          />
        </Col>
      </Row>

      {/* 说明提示 */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.2 }}
        style={{ marginBottom: 24 }}
      >
        <Alert
          message="模型说明"
          description="Embedding 模型用于将文本转换为向量，用于知识库检索。添加新模型前，请确保已在 Ollama 中拉取对应模型。设置默认模型后，系统将优先使用该模型进行文本向量化。"
          type="info"
          showIcon
          style={{
            borderRadius: 12,
            border: '1px solid #d9f7be',
            background: 'linear-gradient(135deg, #f6ffed 0%, #fff 100%)',
          }}
        />
      </motion.div>

      {/* 视图切换 */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.3 }}
        style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}
      >
        <Space>
          <Button
            type={viewMode === 'card' ? 'primary' : 'default'}
            onClick={() => setViewMode('card')}
            icon={<ApiOutlined />}
          >
            卡片视图
          </Button>
          <Button
            type={viewMode === 'table' ? 'primary' : 'default'}
            onClick={() => setViewMode('table')}
            icon={<SafetyOutlined />}
          >
            列表视图
          </Button>
        </Space>
      </motion.div>

      {/* 模型列表 - 卡片视图 */}
      <AnimatePresence mode="wait">
        {viewMode === 'card' ? (
          <motion.div
            key="card"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            <Row gutter={[16, 16]}>
              {models.map((model, index) => (
                <Col xs={24} md={12} lg={8} key={model.id}>
                  <ModelCard
                    model={model}
                    index={index}
                    onEdit={handleEdit}
                    onSetDefault={handleSetDefault}
                    onToggle={handleToggle}
                    onDelete={handleDelete}
                  />
                </Col>
              ))}
            </Row>
            {models.length === 0 && !loading && (
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                style={{
                  textAlign: 'center',
                  padding: '60px 20px',
                  background: '#fafafa',
                  borderRadius: 16,
                  border: '2px dashed #d9d9d9',
                }}
              >
                <ApiOutlined style={{ fontSize: 64, color: '#d9d9d9', marginBottom: 16 }} />
                <Text strong style={{ fontSize: 16, display: 'block', marginBottom: 8 }}>
                  暂无 Embedding 模型
                </Text>
                <Text type="secondary">
                  点击上方「添加模型」按钮添加新的 Embedding 模型
                </Text>
              </motion.div>
            )}
          </motion.div>
        ) : (
          <motion.div
            key="table"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
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
                  dataSource={models.slice(
                    (pagination.current - 1) * pagination.pageSize,
                    pagination.current * pagination.pageSize
                  )}
                  rowKey="id"
                  loading={loading}
                  scroll={{ x: 'max-content' }}
                  size="small"
                  pagination={{
                    current: pagination.current,
                    pageSize: pagination.pageSize,
                    total: pagination.total,
                    showSizeChanger: true,
                    showTotal: (total: number) => `共 ${total} 条记录`,
                    onChange: (page, pageSize) => {
                      setPagination(prev => ({ ...prev, current: page, pageSize }));
                    },
                    size: 'small',
                  }}
                />
              </div>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 添加/编辑模型模态框 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              {editingModel ? <EditOutlined style={{ color: '#fff', fontSize: 20 }} /> : <PlusOutlined style={{ color: '#fff', fontSize: 20 }} />}
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: 18 }}>
                {editingModel ? "编辑模型" : "添加模型"}
              </div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                {editingModel ? "修改 Embedding 模型配置" : "添加新的 Embedding 模型"}
              </Text>
            </div>
          </div>
        }
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
        width={600}
        okButtonProps={{
          style: {
            background: 'linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%)',
            border: 'none',
            borderRadius: 8,
            height: 42,
            fontWeight: 600,
          }
        }}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="modelName"
                label="模型名称"
                rules={[{ required: true, message: "请输入模型名称" }]}
                tooltip="Ollama 中的模型名称，如 nomic-embed-text"
              >
                <Input 
                  placeholder="如：nomic-embed-text" 
                  disabled={!!editingModel}
                  size="large"
                  style={{ borderRadius: 8 }}
                  prefix={<ApiOutlined style={{ color: '#8c8c8c' }} />}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="displayName"
                label="显示名称"
                rules={[{ required: true, message: "请输入显示名称" }]}
                tooltip="用于界面显示的友好名称"
              >
                <Input 
                  placeholder="如：Nomic Embed Text" 
                  size="large"
                  style={{ borderRadius: 8 }}
                  prefix={<SafetyOutlined style={{ color: '#8c8c8c' }} />}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="dimension"
                label="向量维度"
                rules={[{ required: true, message: "请输入向量维度" }]}
                tooltip="模型输出的向量维度，如 768"
              >
                <Input
                  type="number"
                  placeholder="如：768"
                  disabled={!!editingModel}
                  size="large"
                  style={{ borderRadius: 8 }}
                  prefix={<ThunderboltOutlined style={{ color: '#8c8c8c' }} />}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="status"
                label="状态"
                valuePropName="checked"
              >
                <Switch 
                  checkedChildren="启用" 
                  unCheckedChildren="禁用"
                  size="default"
                  style={{ width: 80 }}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea 
              rows={3} 
              placeholder="模型描述，可选"
              style={{ borderRadius: 8 }}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 验证模型模态框 */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <ExperimentOutlined style={{ color: '#fff', fontSize: 20 }} />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: 18 }}>验证模型可用性</div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                检测 Ollama 中的模型是否可以正常使用
              </Text>
            </div>
          </div>
        }
        open={validateModalVisible}
        onOk={handleValidate}
        onCancel={() => {
          setValidateModalVisible(false);
          setValidateResult(null);
          setValidateModelName("");
        }}
        okText="开始验证"
        confirmLoading={validating}
        width={560}
        okButtonProps={{
          style: {
            background: 'linear-gradient(135deg, #52c41a 0%, #95de64 100%)',
            border: 'none',
            borderRadius: 8,
            height: 42,
            fontWeight: 600,
          }
        }}
      >
        <div style={{ padding: '16px 0' }}>
          <Form layout="vertical">
            <Form.Item 
              label="模型名称" 
              required
              tooltip="输入 Ollama 中的 Embedding 模型名称"
            >
              <Input
                value={validateModelName}
                onChange={(e) => setValidateModelName(e.target.value)}
                placeholder="如：nomic-embed-text, mxbai-embed-large"
                onPressEnter={handleValidate}
                size="large"
                style={{ borderRadius: 8 }}
                prefix={<ApiOutlined style={{ color: '#8c8c8c' }} />}
              />
            </Form.Item>
          </Form>
          <AnimatePresence>
            {validateResult && (
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                style={{ marginTop: 16 }}
              >
                <Alert
                  message={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {validateResult.available ? (
                        <CheckCircleOutlined style={{ color: '#52c41a' }} />
                      ) : (
                        <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                      )}
                      <span style={{ fontWeight: 600 }}>
                        {validateResult.available ? "模型可用" : "模型不可用"}
                      </span>
                    </div>
                  }
                  description={
                    <Space direction="vertical" style={{ width: "100%" }} size="small">
                      <div><strong>模型名称:</strong> {validateResult.modelName || validateModelName}</div>
                      {validateResult.dimension && (
                        <div>
                          <strong>向量维度:</strong>{' '}
                          <Tag color="blue" style={{ fontSize: 13 }}>{validateResult.dimension} 维</Tag>
                        </div>
                      )}
                      <div style={{ marginTop: 8, padding: 12, background: '#f6f8fa', borderRadius: 8 }}>
                        <strong>详细信息:</strong>
                        <div style={{ marginTop: 4, color: '#595959' }}>
                          {validateResult.message || "-"}
                        </div>
                      </div>
                    </Space>
                  }
                  type={validateResult.available ? "success" : "error"}
                  showIcon={false}
                  style={{
                    borderRadius: 12,
                    border: `1px solid ${validateResult.available ? '#b7eb8f' : '#ffccc7'}`,
                  }}
                />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </Modal>
    </motion.div>
  );
}

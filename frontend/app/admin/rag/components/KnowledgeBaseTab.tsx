/* eslint-disable react-hooks/set-state-in-effect -- Data loading on mount is standard pattern */
"use client";

import { useState, useEffect, useCallback } from "react";
import {
  Card,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Switch,
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
} from "antd";
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  ReloadOutlined,
  EyeOutlined,
  StopOutlined,
  PlaySquareOutlined,
} from "@ant-design/icons";
import { ragService, KnowledgeBase, EmbeddingModel } from "@/services/ragService";

const { Title, Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

export default function KnowledgeBaseTab() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [embeddingModels, setEmbeddingModels] = useState<EmbeddingModel[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingKb, setEditingKb] = useState<KnowledgeBase | null>(null);
  const [documentModalVisible, setDocumentModalVisible] = useState(false);
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [form] = Form.useForm();
  const [checkingName, setCheckingName] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [batchLoading, setBatchLoading] = useState(false);
  const [deletingIds, setDeletingIds] = useState<Set<number>>(new Set());
  const [togglingIds, setTogglingIds] = useState<Set<number>>(new Set());

  const loadData = useCallback(async (page = 1, pageSize = 10) => {
    setLoading(true);
    try {
      const [kbData, modelData] = await Promise.all([
        ragService.knowledge.list(undefined, page, pageSize),
        ragService.embeddingModel.listEnabled(),
      ]);
      if (kbData && kbData.items) {
        setKnowledgeBases(kbData.items);
        setPagination(prev => ({
          ...prev,
          current: kbData.page || page,
          total: kbData.total || 0,
          pageSize: kbData.size || pageSize,
        }));
      }
      if (modelData) {
        setEmbeddingModels(modelData);
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } }; message?: string };
      message.error(error.response?.data?.msg || error.message || "加载数据失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = () => {
    loadData(pagination.current, pagination.pageSize);
  };

  const handleCreate = () => {
    setEditingKb(null);
    form.resetFields();
    const defaultModel = embeddingModels.find((m) => m.isDefault);
    form.setFieldsValue({
      status: true,
      embeddingModelId: defaultModel?.id,
    });
    setModalVisible(true);
  };

  const handleEdit = (record: KnowledgeBase) => {
    setEditingKb(record);
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      embeddingModelId: record.embeddingModelId,
      status: record.status,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    if (deletingIds.has(id)) return;
    
    setDeletingIds(prev => new Set(prev).add(id));
    try {
      await ragService.knowledge.delete(id);
      message.success("删除成功");
      loadData(pagination.current, pagination.pageSize);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "删除失败");
    } finally {
      setDeletingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleToggle = async (id: number, enabled: boolean) => {
    if (togglingIds.has(id)) return;
    
    setTogglingIds(prev => new Set(prev).add(id));
    try {
      await ragService.knowledge.toggle(id, enabled);
      message.success(enabled ? "知识库已启用" : "知识库已禁用");
      loadData(pagination.current, pagination.pageSize);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "操作失败");
    } finally {
      setTogglingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleViewDocuments = (record: KnowledgeBase) => {
    if (!record.id) {
      message.error("知识库ID无效");
      return;
    }
    
    // 切换到文档管理 Tab
    const event = new CustomEvent("switchToDocumentTab", { 
      detail: { knowledgeBaseId: record.id, knowledgeBaseName: record.name } 
    });
    window.dispatchEvent(event);
    message.info(`正在切换到「${record.name}」的文档管理...`);
  };

  const checkNameUnique = async (_rule: unknown, value: string) => {
    if (!value) return Promise.resolve();
    
    // 编辑时跳过当前记录
    if (editingKb && editingKb.name === value) {
      return Promise.resolve();
    }
    
    setCheckingName(true);
    try {
      const response = await ragService.knowledge.list(value, 1, 100);
      const exists = response.items?.some(kb => kb.name === value);
      if (exists) {
        return Promise.reject(new Error("知识库名称已存在"));
      }
      return Promise.resolve();
    } catch {
      return Promise.reject(new Error("验证失败，请稍后重试"));
    } finally {
      setCheckingName(false);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitLoading(true);
      if (editingKb) {
        await ragService.knowledge.update(editingKb.id, values);
        message.success("更新成功");
      } else {
        await ragService.knowledge.create(values);
        message.success("创建成功");
      }
      setModalVisible(false);
      form.resetFields();
      loadData(pagination.current, pagination.pageSize);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "操作失败");
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning("请选择要删除的知识库");
      return;
    }
    if (batchLoading) return;

    setBatchLoading(true);
    try {
      await Promise.all(selectedRowKeys.map(id => ragService.knowledge.delete(Number(id))));
      message.success(`成功删除 ${selectedRowKeys.length} 个知识库`);
      setSelectedRowKeys([]);
      loadData(pagination.current, pagination.pageSize);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "批量删除失败");
    } finally {
      setBatchLoading(false);
    }
  };

  const handleBatchToggle = async (enabled: boolean) => {
    if (selectedRowKeys.length === 0) {
      message.warning("请选择要操作的知识库");
      return;
    }
    if (batchLoading) return;

    setBatchLoading(true);
    try {
      await Promise.all(selectedRowKeys.map(id => ragService.knowledge.toggle(Number(id), enabled)));
      message.success(enabled ? `成功启用 ${selectedRowKeys.length} 个知识库` : `成功禁用 ${selectedRowKeys.length} 个知识库`);
      setSelectedRowKeys([]);
      loadData(pagination.current, pagination.pageSize);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } } };
      message.error(error.response?.data?.msg || "批量操作失败");
    } finally {
      setBatchLoading(false);
    }
  };

  const rowSelection = {
    selectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      if (!batchLoading) {
        setSelectedRowKeys(newSelectedRowKeys);
      }
    },
    getCheckboxProps: () => ({
      disabled: batchLoading || loading,
    }),
  };

  const columns = [
    {
      title: "知识库名称",
      dataIndex: "name",
      key: "name",
      render: (text: string) => <Text strong>{text}</Text>,
    },
    {
      title: "描述",
      dataIndex: "description",
      key: "description",
      ellipsis: true,
    },
    {
      title: "Embedding模型",
      dataIndex: "embeddingModelName",
      key: "embeddingModelName",
      render: (name: string, record: KnowledgeBase) => (
        <Space>
          <Text>{name}</Text>
          <Tag color="blue">{record.embeddingDimension}维</Tag>
        </Space>
      ),
    },
    {
      title: "文档统计",
      key: "stats",
      render: (_: unknown, record: KnowledgeBase) => (
        <Space direction="vertical" size="small">
          <Text type="secondary">文档: {record.documentCount || 0}</Text>
          <Text type="secondary">分块: {record.chunkCount || 0}</Text>
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: boolean, record: KnowledgeBase) => (
        <Switch
          checked={status}
          onChange={(checked) => handleToggle(record.id, checked)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          disabled={togglingIds.has(record.id) || loading}
          loading={togglingIds.has(record.id)}
        />
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: KnowledgeBase) => (
        <Space size="small">
          <Tooltip title="查看文档">
            <Button
              type="text"
              icon={<EyeOutlined />}
              onClick={() => handleViewDocuments(record)}
              disabled={loading}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button
              type="text"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
              disabled={loading || deletingIds.has(record.id)}
            />
          </Tooltip>
          <Popconfirm
            title="确定删除该知识库？"
            description="关联的文档将一并删除。"
            onConfirm={() => handleDelete(record.id)}
            okButtonProps={{ loading: deletingIds.has(record.id) }}
          >
            <Tooltip title="删除">
              <Button 
                type="text" 
                danger 
                icon={<DeleteOutlined />} 
                disabled={deletingIds.has(record.id) || loading}
                loading={deletingIds.has(record.id)}
              />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const totalDocuments = knowledgeBases.reduce(
    (sum, kb) => sum + (kb.documentCount || 0),
    0
  );
  const totalChunks = knowledgeBases.reduce(
    (sum, kb) => sum + (kb.chunkCount || 0),
    0
  );

  return (
    <Card
      title={
        <Space size="small">
          <DatabaseOutlined />
          <Title level={5} style={{ margin: 0, fontSize: 16 }} className="sm:text-lg">
            知识库管理
          </Title>
        </Space>
      }
      extra={
        <Space size="small">
          <Button 
            icon={<ReloadOutlined spin={loading} />} 
            onClick={handleRefresh}
            disabled={loading}
            size="middle"
          >
            <span className="hidden sm:inline">刷新</span>
          </Button>
          <Button 
            type="primary" 
            icon={<PlusOutlined />} 
            onClick={handleCreate}
            disabled={loading}
            size="middle"
          >
            <span className="hidden sm:inline">新建知识库</span>
            <span className="sm:hidden">新建</span>
          </Button>
        </Space>
      }
    >
      <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={12} lg={6}>
          <Statistic
            title="知识库总数"
            value={knowledgeBases.length}
            prefix={<DatabaseOutlined />}
            valueStyle={{ fontSize: 20 }}
          />
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Statistic
            title="文档总数"
            value={totalDocuments}
            prefix={<FileTextOutlined />}
            valueStyle={{ fontSize: 20 }}
          />
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Statistic 
            title="分块总数" 
            value={totalChunks}
            valueStyle={{ fontSize: 20 }}
          />
        </Col>
        <Col xs={12} sm={12} lg={6}>
          <Statistic
            title="启用中"
            value={knowledgeBases.filter((kb) => kb.status).length}
            valueStyle={{ color: "#3f8600", fontSize: 20 }}
          />
        </Col>
      </Row>

      <div className="overflow-x-auto">
        <Table
          columns={columns}
          dataSource={knowledgeBases}
          rowKey="id"
          loading={loading}
          rowSelection={rowSelection}
          locale={{
            emptyText: <Empty description="暂无知识库，点击上方按钮创建" />,
          }}
          scroll={{ x: 'max-content' }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条记录`,
            onChange: (page, pageSize) => {
              loadData(page, pageSize);
            },
            size: 'small',
          }}
          size="small"
          footer={() => (
            <div style={{ textAlign: "right", padding: "8px 0" }}>
              <Space size="small" className="flex-wrap">
                <Text className="text-xs sm:text-sm">已选择 {selectedRowKeys.length} 项</Text>
                <Button
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={handleBatchDelete}
                  disabled={selectedRowKeys.length === 0 || batchLoading}
                  loading={batchLoading}
                >
                  <span className="hidden sm:inline">批量删除</span>
                  <span className="sm:hidden">删除</span>
                </Button>
                <Button
                  size="small"
                  icon={<PlaySquareOutlined />}
                  onClick={() => handleBatchToggle(true)}
                  disabled={selectedRowKeys.length === 0 || batchLoading}
                  loading={batchLoading}
                >
                  <span className="hidden sm:inline">批量启用</span>
                  <span className="sm:hidden">启用</span>
                </Button>
                <Button
                  size="small"
                  icon={<StopOutlined />}
                  onClick={() => handleBatchToggle(false)}
                  disabled={selectedRowKeys.length === 0 || batchLoading}
                  loading={batchLoading}
                >
                  <span className="hidden sm:inline">批量禁用</span>
                  <span className="sm:hidden">禁用</span>
                </Button>
              </Space>
            </div>
          )}
        />
      </div>

      <Modal
        title={editingKb ? "编辑知识库" : "创建知识库"}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => {
          if (!submitLoading) {
            setModalVisible(false);
            form.resetFields();
          }
        }}
        confirmLoading={submitLoading}
        destroyOnClose
        width="90%"
        style={{ maxWidth: 600 }}
        maskClosable={!submitLoading}
        closable={!submitLoading}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="知识库名称"
            rules={[
              { required: true, message: "请输入知识库名称" },
              { min: 2, max: 50, message: "知识库名称长度必须在 2-50 个字符之间" },
              { validator: checkNameUnique },
            ]}
          >
            <Input 
              placeholder="请输入知识库名称" 
              disabled={checkingName}
              suffix={checkingName && <ReloadOutlined spin />}
            />
          </Form.Item>
          <Form.Item 
            name="description" 
            label="描述"
            rules={[{ max: 500, message: "描述不能超过 500 个字符" }]}
          >
            <TextArea 
              rows={3} 
              placeholder="请输入知识库描述" 
              showCount
              maxLength={500}
            />
          </Form.Item>
          <Form.Item
            name="embeddingModelId"
            label="Embedding 模型"
            rules={[{ required: true, message: "请选择 Embedding 模型" }]}
            extra={
              editingKb ? "注意：知识库创建后不可更换 Embedding 模型" : ""
            }
          >
            <Select
              placeholder="请选择 Embedding 模型"
              disabled={!!editingKb}
              showSearch
              filterOption={(input, option) =>
                String(option?.label ?? "").toLowerCase().includes(input.toLowerCase())
              }
            >
              {embeddingModels.map((model) => (
                <Option key={model.id} value={model.id}>
                  {model.displayName} ({model.dimension}维){" "}
                  {model.isDefault && "★"}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="status"
            label="状态"
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`文档管理 - ${selectedKb?.name || ""}`}
        open={documentModalVisible}
        onCancel={() => {
          setDocumentModalVisible(false);
          setSelectedKb(null);
        }}
        footer={null}
        width={900}
        destroyOnClose
      >
        <div className="p-4">
          <Alert
            message="文档管理功能"
            description="此功能正在开发中，将支持文档上传、预览、删除等操作。"
            type="info"
            showIcon
          />
        </div>
      </Modal>
    </Card>
  );
}

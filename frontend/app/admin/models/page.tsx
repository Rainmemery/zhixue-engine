"use client";

import { useState, useEffect, useCallback } from "react";
import { motion } from "framer-motion";
import { useRouter } from "next/navigation";
import {
  ApiOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
  StarOutlined,
  StarFilled,
} from "@ant-design/icons";
import {
  Card,
  Table,
  Button,
  Input,
  Space,
  Tag,
  Popconfirm,
  message,
  Modal,
  Form,
  Select,
  Switch,
  Typography,
  Tooltip,
  Divider,
  InputNumber,
} from "antd";
import {
  ModelGroupService,
  ModelGroup,
  ModelGroupCreateRequest,
  ModelGroupUpdateRequest,
} from "@/services/dispatcherService";

const { Title, Text } = Typography;
const { Option } = Select;
const { TextArea } = Input;

const modelTypeMap: Record<string, { name: string; color: string }> = {
  OLLAMA: { name: "Ollama", color: "green" },
  OPENAI: { name: "OpenAI", color: "blue" },
  CUSTOM: { name: "自定义", color: "orange" },
};

const schedulingStrategyMap: Record<string, { name: string; color: string }> = {
  ROUND_ROBIN: { name: "轮询", color: "blue" },
  WEIGHTED_ROUND_ROBIN: { name: "加权轮询", color: "green" },
  LEAST_CONNECTIONS: { name: "最少连接", color: "orange" },
};

export default function ModelGroupManagementPage() {
  const router = useRouter();
  const [groups, setGroups] = useState<ModelGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<ModelGroup | null>(null);
  const [form] = Form.useForm();

  const fetchGroups = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ModelGroupService.list();
      setGroups(data || []);
    } catch (error: unknown) {
      console.error("获取模型组列表失败:", error);
      const err = error as Error;
      message.error("获取模型组列表失败: " + (err.message || "未知错误"));
    } finally {
      setLoading(false);
    }
  }, []);

  // 页面加载时立即请求数据
  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  const handleAdd = () => {
    setEditingGroup(null);
    form.resetFields();
    form.setFieldsValue({
      enabled: true,
      isDefault: false,
      priority: 50,
      modelType: "CUSTOM",
      schedulingStrategy: "ROUND_ROBIN",
      defaultTimeoutMs: 300000,
      defaultMaxRetries: 3,
    });
    setIsModalOpen(true);
  };

  const handleEdit = (record: ModelGroup) => {
    setEditingGroup(record);
    form.setFieldsValue(record);
    setIsModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      const checkResult = await ModelGroupService.checkCanDelete(id);
      if (!checkResult.canDelete) {
        message.warning(checkResult.message || "该模型组有关联实例，无法删除");
        return;
      }
      await ModelGroupService.delete(id);
      message.success("删除成功");
      fetchGroups();
    } catch (error: unknown) {
      const err = error as Error;
      message.error("删除失败: " + (err.message || "未知错误"));
    }
  };

  const handleToggle = async (id: number, enabled: boolean) => {
    try {
      await ModelGroupService.toggle(id, enabled);
      message.success(enabled ? "已启用" : "已禁用");
      fetchGroups();
    } catch (error: unknown) {
      const err = error as Error;
      message.error("操作失败: " + (err.message || "未知错误"));
    }
  };

  const handleSetDefault = async (id: number) => {
    try {
      await ModelGroupService.setDefault(id);
      message.success("已设置为默认模型组");
      fetchGroups();
    } catch (error: unknown) {
      const err = error as Error;
      message.error("设置失败: " + (err.message || "未知错误"));
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (editingGroup) {
        await ModelGroupService.update(editingGroup.id, values as ModelGroupUpdateRequest);
        message.success("更新成功");
      } else {
        await ModelGroupService.create(values as ModelGroupCreateRequest);
        message.success("创建成功");
      }

      setIsModalOpen(false);
      fetchGroups();
    } catch (error: unknown) {
      const err = error as Error;
      message.error("操作失败: " + (err.message || "未知错误"));
    }
  };

  const handleViewInstances = (groupId: number) => {
    router.push(`/admin/models/${groupId}/instances`);
  };

  const columns = [
    {
      title: "ID",
      dataIndex: "id",
      key: "id",
      width: 60,
    },
    {
      title: "名称",
      dataIndex: "name",
      key: "name",
      width: 180,
      render: (name: string, record: ModelGroup) => (
        <Space>
          {name}
          {record.isDefault && (
            <Tooltip title="默认模型组">
              <StarFilled style={{ color: "#faad14" }} />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: "模型类型",
      dataIndex: "modelType",
      key: "modelType",
      width: 100,
      render: (type: string) => {
        const info = modelTypeMap[type];
        return <Tag color={info?.color || "default"}>{info?.name || type}</Tag>;
      },
    },
    {
      title: "基础URL",
      dataIndex: "defaultBaseUrl",
      key: "defaultBaseUrl",
      width: 200,
      ellipsis: true,
      render: (url: string) => (
        <Tooltip title={url}>
          <span>{url || "-"}</span>
        </Tooltip>
      ),
    },
    {
      title: "调度策略",
      dataIndex: "schedulingStrategy",
      key: "schedulingStrategy",
      width: 100,
      render: (strategy: string) => {
        const info = schedulingStrategyMap[strategy];
        return <Tag color={info?.color || "default"}>{info?.name || strategy}</Tag>;
      },
    },
    {
      title: "优先级",
      dataIndex: "priority",
      key: "priority",
      width: 70,
      align: "center" as const,
      render: (priority: number) => (
        <Tag color={priority >= 80 ? "red" : priority >= 50 ? "blue" : "default"}>
          {priority}
        </Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "enabled",
      key: "enabled",
      width: 80,
      render: (enabled: boolean, record: ModelGroup) => (
        <Switch
          checked={enabled}
          onChange={(checked) => handleToggle(record.id, checked)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
        />
      ),
    },
    {
      title: "实例",
      dataIndex: "totalInstances",
      key: "totalInstances",
      width: 80,
      align: "center" as const,
      render: (total: number, record: ModelGroup) => (
        <Space size={4}>
          <Tag color="purple">{total || 0}</Tag>
          {record.healthyInstances !== undefined && (
            <Tag color="green">{record.healthyInstances}</Tag>
          )}
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 240,
      render: (_: unknown, record: ModelGroup) => (
        <Space>
          <Tooltip title="编辑">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            >
              编辑
            </Button>
          </Tooltip>
          <Tooltip title="查看实例">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewInstances(record.id)}
            >
              实例
            </Button>
          </Tooltip>
          {!record.isDefault && (
            <Tooltip title="设为默认">
              <Button
                type="link"
                size="small"
                icon={<StarOutlined />}
                onClick={() => handleSetDefault(record.id)}
              >
                默认
              </Button>
            </Tooltip>
          )}
          <Popconfirm
            title="确定要删除此模型组吗？"
            description="删除前会检查是否有关联实例"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-6"
    >
      <div className="flex items-center justify-between">
        <div>
          <Title level={4} className="!mb-1">
            <ApiOutlined className="mr-2" />
            模型组管理
          </Title>
          <Text type="secondary">管理AI模型组配置和调度策略</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchGroups}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            创建模型组
          </Button>
        </Space>
      </div>

      <Card title={`模型组列表 (${groups.length})`}>
        <Table
          dataSource={groups}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
          }}
        />
      </Card>

      <Modal
        title={editingGroup ? "编辑模型组" : "创建模型组"}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
        width={600}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: "请输入模型组名称" }]}
          >
            <Input placeholder="请输入模型组名称" />
          </Form.Item>

          <Form.Item
            name="modelType"
            label="模型类型"
            rules={[{ required: true, message: "请选择模型类型" }]}
          >
            <Select placeholder="请选择模型类型">
              <Option value="OLLAMA">Ollama</Option>
              <Option value="OPENAI">OpenAI</Option>
              <Option value="CUSTOM">自定义</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="description"
            label="描述"
          >
            <TextArea rows={2} placeholder="请输入描述信息" />
          </Form.Item>

          <Divider>API配置</Divider>

          <Form.Item
            name="defaultBaseUrl"
            label="基础URL"
            rules={[{ required: true, message: "请输入基础URL" }]}
          >
            <Input placeholder="例如: http://localhost:11434 或 https://api.openai.com" />
          </Form.Item>

          <Form.Item
            name="defaultApiKey"
            label="API Key"
          >
            <Input.Password placeholder="请输入API密钥（可选）" />
          </Form.Item>

          <Form.Item
            name="defaultTimeoutMs"
            label="超时时间（毫秒）"
            extra="请求超时时间，默认300000毫秒（5分钟）"
          >
            <InputNumber min={1000} max={600000} style={{ width: "100%" }} />
          </Form.Item>

          <Form.Item
            name="defaultMaxRetries"
            label="最大重试次数"
            extra="请求失败后的最大重试次数，默认3次"
          >
            <InputNumber min={0} max={10} style={{ width: "100%" }} />
          </Form.Item>

          <Divider>调度配置</Divider>

          <Form.Item
            name="schedulingStrategy"
            label="调度策略"
            rules={[{ required: true, message: "请选择调度策略" }]}
          >
            <Select placeholder="请选择调度策略">
              <Option value="ROUND_ROBIN">轮询</Option>
              <Option value="WEIGHTED_ROUND_ROBIN">加权轮询</Option>
              <Option value="LEAST_CONNECTIONS">最少连接</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="priority"
            label="优先级"
            extra="数值越大优先级越高，范围 1-100"
          >
            <InputNumber min={1} max={100} style={{ width: "100%" }} />
          </Form.Item>

          <Form.Item
            name="enabled"
            label="是否启用"
            valuePropName="checked"
          >
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>

          <Form.Item
            name="isDefault"
            label="设为默认"
            valuePropName="checked"
            extra="设为默认后，系统将优先使用此模型组"
          >
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
        </Form>
      </Modal>
    </motion.div>
  );
}

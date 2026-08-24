"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import { useRouter, useParams } from "next/navigation";
import {
  ArrowLeftOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  HeartOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  CloseCircleOutlined,
  StopOutlined,
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
  Typography,
  Tooltip,
  InputNumber,
  Badge,
  Progress,
  Descriptions,
} from "antd";
import {
  ModelInstanceService,
  ModelGroupService,
  ModelInstance,
  ModelGroupDTO,
  ModelInstanceCreateRequest,
  ModelInstanceUpdateRequest,
  InstanceStatus,
  HealthCheckResult,
} from "@/services/dispatcherService";

const { Title, Text } = Typography;
const { Option } = Select;
const { Password } = Input;

const statusConfig: Record<InstanceStatus, { color: string; icon: React.ReactNode; text: string }> = {
  HEALTHY: { color: "green", icon: <CheckCircleOutlined />, text: "健康" },
  DEGRADED: { color: "gold", icon: <ExclamationCircleOutlined />, text: "降级" },
  UNHEALTHY: { color: "red", icon: <CloseCircleOutlined />, text: "不健康" },
  DISABLED: { color: "default", icon: <StopOutlined />, text: "已禁用" },
};

export default function ModelInstanceManagementPage() {
  const router = useRouter();
  const params = useParams();
  // 确保 params.id 是字符串，并正确转换为数字
  const idParam = typeof params.id === 'string' ? params.id : Array.isArray(params.id) ? params.id[0] : '';
  const groupId = idParam ? parseInt(idParam, 10) : 0;
  const isValidGroupId = !isNaN(groupId) && groupId > 0;

  const [instances, setInstances] = useState<ModelInstance[]>([]);
  const [group, setGroup] = useState<ModelGroupDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingInstance, setEditingInstance] = useState<ModelInstance | null>(null);
  const [form] = Form.useForm();
  const [healthModalVisible, setHealthModalVisible] = useState(false);
  const [healthCheckResult, setHealthCheckResult] = useState<HealthCheckResult | null>(null);
  const [healthCheckLoading, setHealthCheckLoading] = useState(false);
  const [statusModalVisible, setStatusModalVisible] = useState(false);
  const [selectedInstance, setSelectedInstance] = useState<ModelInstance | null>(null);
  const [statusForm] = Form.useForm();

  // 数据获取函数
  const fetchGroup = async () => {
    if (!isValidGroupId) return;
    try {
      const data = await ModelGroupService.getGroupById(groupId);
      setGroup(data);
    } catch (error: unknown) {
      console.error("获取模型组信息失败:", error);
      const err = error as Error;
      message.error("获取模型组信息失败: " + (err.message || "未知错误"));
    }
  };

  const fetchInstances = async () => {
    if (!isValidGroupId) return;
    try {
      setLoading(true);
      const result = await ModelInstanceService.getByGroupId(groupId);
      setInstances(result || []);
    } catch (error: unknown) {
      console.error("获取实例列表失败:", error);
      const err = error as Error;
      message.error("获取实例列表失败: " + (err.message || "未知错误"));
    } finally {
      setLoading(false);
    }
  };

  // 页面加载时获取数据
  useEffect(() => {
    if (isValidGroupId) {
      fetchGroup();
      fetchInstances();
    }
  }, [groupId, isValidGroupId]);

  const handleAdd = () => {
    setEditingInstance(null);
    form.resetFields();
    form.setFieldsValue({
      weight: 1,
      maxConcurrent: 10,
    });
    setModalVisible(true);
  };

  const handleEdit = (record: ModelInstance) => {
    setEditingInstance(record);
    form.setFieldsValue({
      groupId: record.groupId,
      name: record.name,
      apiEndpoint: record.apiEndpoint,
      modelName: record.modelName,
      weight: record.weight,
      maxConcurrent: record.maxConcurrent,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await ModelInstanceService.delete(id);
      message.success("删除成功");
      fetchInstances();
    } catch (error: unknown) {
      const err = error as Error;
      message.error("删除失败: " + (err.message || "未知错误"));
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (editingInstance) {
        const updateRequest: ModelInstanceUpdateRequest = {
          name: values.name,
          apiEndpoint: values.apiEndpoint,
          modelName: values.modelName,
          weight: values.weight,
          maxConcurrent: values.maxConcurrent,
        };
        if (values.apiKey) {
          updateRequest.apiKey = values.apiKey;
        }
        await ModelInstanceService.update(editingInstance.id, updateRequest);
        message.success("更新成功");
      } else {
        const createRequest: ModelInstanceCreateRequest = {
          groupId: groupId,
          name: values.name,
          apiEndpoint: values.apiEndpoint,
          modelName: values.modelName,
          apiKey: values.apiKey,
          weight: values.weight,
          maxConcurrent: values.maxConcurrent,
        };
        await ModelInstanceService.create(createRequest);
        message.success("创建成功");
      }

      setModalVisible(false);
      fetchInstances();
    } catch (error: unknown) {
      const err = error as Error;
      message.error("操作失败: " + (err.message || "未知错误"));
    }
  };

  const handleHealthCheck = async (instance: ModelInstance) => {
    setSelectedInstance(instance);
    setHealthCheckLoading(true);
    setHealthModalVisible(true);
    setHealthCheckResult(null);

    try {
      const result = await ModelInstanceService.performHealthCheck(instance.id);
      setHealthCheckResult(result);
    } catch (error: unknown) {
      const err = error as Error;
      setHealthCheckResult({
        instanceId: instance.id,
        instanceName: instance.name,
        healthy: false,
        responseTimeMs: 0,
        errorMessage: err.message || "健康检查失败",
        checkTime: new Date().toISOString(),
      });
    } finally {
      setHealthCheckLoading(false);
    }
  };

  const handleUpdateStatus = (instance: ModelInstance) => {
    setSelectedInstance(instance);
    statusForm.setFieldsValue({ status: instance.status });
    setStatusModalVisible(true);
  };

  const handleStatusSubmit = async () => {
    try {
      const values = await statusForm.validateFields();
      if (selectedInstance) {
        await ModelInstanceService.updateInstanceStatus(selectedInstance.id, values.status);
        message.success("状态更新成功");
        setStatusModalVisible(false);
        fetchInstances();
      }
    } catch (error: unknown) {
      const err = error as Error;
      message.error("状态更新失败: " + (err.message || "未知错误"));
    }
  };

  const getStatusTag = (status: InstanceStatus) => {
    const config = statusConfig[status] || statusConfig.DISABLED;
    return (
      <Tag color={config.color} icon={config.icon}>
        {config.text}
      </Tag>
    );
  };

  const getConnectionProgress = (current: number, max: number) => {
    const percent = max > 0 ? Math.round((current / max) * 100) : 0;
    let status: "success" | "normal" | "exception" = "normal";
    if (percent >= 90) {
      status = "exception";
    } else if (percent >= 70) {
      status = "normal";
    } else {
      status = "success";
    }
    return (
      <Tooltip title={`${current} / ${max}`}>
        <Progress
          percent={percent}
          size="small"
          status={status}
          format={() => `${current}/${max}`}
          style={{ width: 80 }}
        />
      </Tooltip>
    );
  };

  const columns = [
    {
      title: "ID",
      dataIndex: "id",
      key: "id",
      width: 70,
    },
    {
      title: "实例名称",
      dataIndex: "name",
      key: "name",
      width: 150,
      ellipsis: true,
    },
    {
      title: "API端点",
      dataIndex: "apiEndpoint",
      key: "apiEndpoint",
      width: 200,
      ellipsis: true,
      render: (text: string) => (
        <Tooltip title={text}>
          <Text code ellipsis style={{ maxWidth: 180, display: "inline-block" }}>
            {text}
          </Text>
        </Tooltip>
      ),
    },
    {
      title: "模型名称",
      dataIndex: "modelName",
      key: "modelName",
      width: 150,
      ellipsis: true,
    },
    {
      title: "权重",
      dataIndex: "weight",
      key: "weight",
      width: 80,
      render: (weight: number) => <Badge count={weight} style={{ backgroundColor: "#1890ff" }} />,
    },
    {
      title: "连接数",
      key: "connections",
      width: 120,
      render: (_: unknown, record: ModelInstance) =>
        getConnectionProgress(record.currentConnections || 0, record.maxConcurrent || 10),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 100,
      render: (status: InstanceStatus) => getStatusTag(status),
    },
    {
      title: "操作",
      key: "action",
      width: 220,
      render: (_: unknown, record: ModelInstance) => (
        <Space size="small">
          <Tooltip title="编辑">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Tooltip title="健康检查">
            <Button
              type="text"
              size="small"
              icon={<HeartOutlined />}
              onClick={() => handleHealthCheck(record)}
            />
          </Tooltip>
          <Tooltip title="更新状态">
            <Button
              type="text"
              size="small"
              icon={<ApiOutlined />}
              onClick={() => handleUpdateStatus(record)}
            />
          </Tooltip>
          <Popconfirm
            title="确定要删除此实例吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Tooltip title="删除">
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
              />
            </Tooltip>
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
        <div className="flex items-center gap-4">
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => router.push("/admin/models")}
          >
            返回
          </Button>
          <div>
            <Title level={4} className="!mb-1">
              模型实例管理
              {group && ` - ${group.name}`}
            </Title>
            <Text type="secondary">
              管理模型组下的实例配置和健康状态
            </Text>
          </div>
        </div>
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              fetchGroup();
              fetchInstances();
            }}
          >
            刷新
          </Button>
          <Button
            icon={<PlusOutlined />}
            type="primary"
            onClick={handleAdd}
          >
            添加实例
          </Button>
        </Space>
      </div>

      <Card title={`实例列表 (${instances.length})`}>
        <Table
          dataSource={instances}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total: number) => `共 ${total} 条`,
          }}
        />
      </Card>

      <Modal
        title={editingInstance ? "编辑实例" : "添加实例"}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={600}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="实例名称"
            rules={[{ required: true, message: "请输入实例名称" }]}
          >
            <Input placeholder="例如: qwen-main-01" />
          </Form.Item>

          <Form.Item
            name="apiEndpoint"
            label="API端点"
            rules={[
              { required: true, message: "请输入API端点" },
              { type: "url", message: "请输入有效的URL" },
            ]}
          >
            <Input placeholder="例如: http://localhost:11434/v1" />
          </Form.Item>

          <Form.Item
            name="modelName"
            label="模型名称"
            rules={[{ required: true, message: "请输入模型名称" }]}
          >
            <Input placeholder="例如: qwen2.5-coder:latest" />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label="API密钥"
          >
            <Password placeholder="输入API密钥（可选）" />
          </Form.Item>

          <Form.Item
            name="weight"
            label="权重"
            tooltip="用于加权轮询调度，权重越高被选中的概率越大"
          >
            <InputNumber min={1} max={100} style={{ width: "100%" }} />
          </Form.Item>

          <Form.Item
            name="maxConcurrent"
            label="最大并发数"
            tooltip="该实例同时处理的最大请求数"
          >
            <InputNumber min={1} max={1000} style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="健康检查结果"
        open={healthModalVisible}
        onCancel={() => setHealthModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setHealthModalVisible(false)}>
            关闭
          </Button>,
        ]}
        width={500}
      >
        {healthCheckLoading ? (
          <div style={{ textAlign: "center", padding: "40px 0" }}>
            <Text>正在执行健康检查...</Text>
          </div>
        ) : healthCheckResult ? (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="实例名称">
              {healthCheckResult.instanceName}
            </Descriptions.Item>
            <Descriptions.Item label="检查结果">
              {healthCheckResult.healthy ? (
                <Tag color="green" icon={<CheckCircleOutlined />}>健康</Tag>
              ) : (
                <Tag color="red" icon={<CloseCircleOutlined />}>不健康</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="响应时间">
              {healthCheckResult.responseTimeMs} ms
            </Descriptions.Item>
            <Descriptions.Item label="检查时间">
              {new Date(healthCheckResult.checkTime).toLocaleString()}
            </Descriptions.Item>
            {healthCheckResult.errorMessage && (
              <Descriptions.Item label="错误信息">
                <Text type="danger">{healthCheckResult.errorMessage}</Text>
              </Descriptions.Item>
            )}
          </Descriptions>
        ) : null}
      </Modal>

      <Modal
        title="更新实例状态"
        open={statusModalVisible}
        onOk={handleStatusSubmit}
        onCancel={() => setStatusModalVisible(false)}
        destroyOnClose
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item
            name="status"
            label="实例状态"
            rules={[{ required: true, message: "请选择状态" }]}
          >
            <Select placeholder="选择状态">
              <Option value="HEALTHY">
                <Tag color="green" icon={<CheckCircleOutlined />}>健康</Tag>
              </Option>
              <Option value="DEGRADED">
                <Tag color="gold" icon={<ExclamationCircleOutlined />}>降级</Tag>
              </Option>
              <Option value="UNHEALTHY">
                <Tag color="red" icon={<CloseCircleOutlined />}>不健康</Tag>
              </Option>
              <Option value="DISABLED">
                <Tag color="default" icon={<StopOutlined />}>已禁用</Tag>
              </Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </motion.div>
  );
}

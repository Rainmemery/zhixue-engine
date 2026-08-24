"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { motion } from "framer-motion";
import {
  SyncOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  ApiOutlined,
  CloudOutlined,
  LaptopOutlined,
  ReloadOutlined,
  HeartOutlined,
  DashboardOutlined,
} from "@ant-design/icons";
import {
  Card,
  Table,
  Button,
  Tag,
  Space,
  message,
  Row,
  Col,
  Progress,
  Switch,
  Tooltip,
  Spin,
  Typography,
} from "antd";
import dayjs from "dayjs";
import {
  SchedulingMonitorService,
  SchedulingOverview,
  InstanceHealthInfo,
} from "@/services/dispatcherService";

const { Title, Text } = Typography;

export default function SchedulingMonitorPage() {
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState<SchedulingOverview | null>(null);
  const [instanceHealth, setInstanceHealth] = useState<InstanceHealthInfo[]>([]);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [checkingInstanceId, setCheckingInstanceId] = useState<number | null>(null);
  const [recoveringInstanceId, setRecoveringInstanceId] = useState<number | null>(null);
  const isInitialMount = useRef(true);

  const fetchAllData = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        fetchOverview(),
        fetchInstanceHealth(),
      ]);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchOverview = async () => {
    try {
      const data = await SchedulingMonitorService.getOverview();
      setOverview(data);
    } catch (error) {
      console.error("Failed to fetch overview:", error);
    }
  };

  const fetchInstanceHealth = async () => {
    try {
      const data = await SchedulingMonitorService.getInstanceHealth();
      setInstanceHealth(data);
    } catch (error) {
      console.error("Failed to fetch instance health:", error);
    }
  };

  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      const timer = setTimeout(() => {
        fetchAllData();
      }, 0);
      return () => clearTimeout(timer);
    }
  }, []);

  useEffect(() => {
    if (autoRefresh) {
      const interval = setInterval(fetchAllData, 30000);
      return () => clearInterval(interval);
    }
  }, [autoRefresh, fetchAllData]);

  const handleHealthCheck = async (instanceId: number) => {
    setCheckingInstanceId(instanceId);
    try {
      await SchedulingMonitorService.checkInstanceHealth(instanceId);
      message.success("健康检查完成");
      fetchInstanceHealth();
    } catch (error) {
      message.error("健康检查失败");
    } finally {
      setCheckingInstanceId(null);
    }
  };

  const handleRecoverInstance = async (instanceId: number, instanceName: string) => {
    setRecoveringInstanceId(instanceId);
    try {
      const result = await SchedulingMonitorService.recoverInstance(instanceId);
      if (result.success) {
        message.success(result.message);
        fetchAllData();
      } else {
        message.error(result.message);
      }
    } catch (error) {
      message.error("恢复实例失败");
    } finally {
      setRecoveringInstanceId(null);
    }
  };

  const getHealthStateTag = (state: string) => {
    const stateMap: Record<string, { color: string; icon: React.ReactNode }> = {
      HEALTHY: { color: "success", icon: <CheckCircleOutlined /> },
      DEGRADED: { color: "warning", icon: <WarningOutlined /> },
      UNHEALTHY: { color: "error", icon: <CloseCircleOutlined /> },
      UNKNOWN: { color: "default", icon: <WarningOutlined /> },
    };
    const config = stateMap[state] || stateMap.UNKNOWN;
    return (
      <Tag color={config.color} icon={config.icon}>
        {state}
      </Tag>
    );
  };

  const getModelTypeIcon = (modelType: string) => {
    if (modelType?.toLowerCase() === "openai") return <CloudOutlined />;
    if (modelType?.toLowerCase() === "ollama") return <LaptopOutlined />;
    return <ApiOutlined />;
  };

  const healthColumns = [
    {
      title: "模型组",
      dataIndex: "modelGroupName",
      key: "modelGroupName",
      width: 120,
    },
    {
      title: "实例名称",
      dataIndex: "instanceName",
      key: "instanceName",
      width: 150,
      render: (text: string, record: InstanceHealthInfo) => (
        <Space>
          {getModelTypeIcon(record.modelType)}
          <span>{text}</span>
        </Space>
      ),
    },
    {
      title: "模型类型",
      dataIndex: "modelType",
      key: "modelType",
      width: 100,
      render: (text: string) => text?.toUpperCase(),
    },
    {
      title: "模型名称",
      dataIndex: "modelName",
      key: "modelName",
      width: 150,
    },
    {
      title: "健康状态",
      dataIndex: "healthState",
      key: "healthState",
      width: 120,
      render: (state: string) => getHealthStateTag(state),
    },
    {
      title: "可用性",
      dataIndex: "available",
      key: "available",
      width: 100,
      render: (available: boolean) => (
        <Tag color={available ? "success" : "error"}>{available ? "可用" : "不可用"}</Tag>
      ),
    },
    {
      title: "响应时间",
      dataIndex: "responseTimeMs",
      key: "responseTimeMs",
      width: 100,
      render: (ms: number | null) => {
        if (ms === null) return "-";
        const color = ms < 500 ? "#52c41a" : ms < 2000 ? "#faad14" : "#ff4d4f";
        return <span style={{ color }}>{ms}ms</span>;
      },
    },
    {
      title: "连续失败",
      dataIndex: "consecutiveFailures",
      key: "consecutiveFailures",
      width: 100,
      render: (count: number) => (
        <Tag color={count > 0 ? "error" : "success"}>{count}</Tag>
      ),
    },
    {
      title: "并发连接",
      dataIndex: "currentConnections",
      key: "currentConnections",
      width: 140,
      render: (current: number, record: InstanceHealthInfo) => {
        const max = record.maxConcurrent || 10;
        const usage = max > 0 ? (current || 0) / max : 0;
        const color = usage >= 1 ? "#ff4d4f" : usage >= 0.8 ? "#faad14" : "#52c41a";
        return (
          <Space>
            <span style={{ color, fontWeight: 500 }}>{current || 0}</span>
            <span style={{ color: "#999" }}>/ {max}</span>
            <Progress
              percent={Math.round(usage * 100)}
              size="small"
              style={{ width: 50 }}
              strokeColor={color}
              showInfo={false}
            />
          </Space>
        );
      },
    },
    {
      title: "最后检查",
      dataIndex: "lastCheckTime",
      key: "lastCheckTime",
      width: 180,
      render: (time: string | null) => (time ? dayjs(time).format("YYYY-MM-DD HH:mm:ss") : "-"),
    },
    {
      title: "操作",
      key: "actions",
      width: 100,
      fixed: "right" as const,
      render: (_: unknown, record: InstanceHealthInfo) => (
        <Space>
          <Tooltip title="健康检查">
            <Button
              type="link"
              size="small"
              icon={<SyncOutlined spin={checkingInstanceId === record.id} />}
              onClick={() => handleHealthCheck(record.id)}
              loading={checkingInstanceId === record.id}
            />
          </Tooltip>
          {!record.available && (
            <Tooltip title="恢复实例">
              <Button
                type="link"
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => handleRecoverInstance(record.id, record.instanceName)}
                loading={recoveringInstanceId === record.id}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  const overviewCards = [
    {
      title: "模型组",
      value: overview?.enabledModelGroups || 0,
      suffix: `/ ${overview?.totalModelGroups || 0}`,
      icon: <ApiOutlined style={{ fontSize: 28 }} />,
      color: "#1890ff",
      bgColor: "#e6f7ff",
    },
    {
      title: "健康实例",
      value: overview?.healthyInstances || 0,
      suffix: `/ ${overview?.totalInstances || 0}`,
      icon: <CheckCircleOutlined style={{ fontSize: 28 }} />,
      color: "#52c41a",
      bgColor: "#f6ffed",
    },
    {
      title: "故障实例",
      value: overview?.unhealthyInstances || 0,
      suffix: "",
      icon: <CloseCircleOutlined style={{ fontSize: 28 }} />,
      color: overview?.unhealthyInstances ? "#f5222d" : "#52c41a",
      bgColor: overview?.unhealthyInstances ? "#fff2f0" : "#f6ffed",
    },
    {
      title: "活跃连接",
      value: overview?.activeConnections || 0,
      suffix: "",
      icon: <ApiOutlined style={{ fontSize: 28 }} />,
      color: "#722ed1",
      bgColor: "#f9f0ff",
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
            <DashboardOutlined className="mr-2" />
            调度监控
          </Title>
          <Text type="secondary">
            实时监控模型调度状态和健康情况
            {overview && (
              <span className="ml-2 text-xs">
                · 上次更新: {dayjs().format("HH:mm:ss")}
              </span>
            )}
          </Text>
        </div>
        <Space>
          <span className="text-sm text-gray-500">自动刷新</span>
          <Switch checked={autoRefresh} onChange={setAutoRefresh} />
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={fetchAllData}
            loading={loading}
          >
            刷新数据
          </Button>
        </Space>
      </div>

      <Spin spinning={loading}>
        <Row gutter={[24, 24]}>
          {overviewCards.map((card, index) => (
            <Col xs={24} sm={12} lg={6} key={index}>
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
              >
                <Card className="hover:shadow-lg transition-all duration-300 border-0" style={{ background: "#fff" }}>
                  <div className="flex items-center justify-between">
                    <div className="flex-1">
                      <Text type="secondary" className="text-sm block mb-2">
                        {card.title}
                      </Text>
                      <div className="flex items-baseline gap-1">
                        <span
                          className="text-2xl font-bold"
                          style={{ color: card.color }}
                        >
                          {card.value}
                        </span>
                        {card.suffix && (
                          <span className="text-sm" style={{ color: "#999" }}>
                            {card.suffix}
                          </span>
                        )}
                      </div>
                    </div>
                    <div
                      className="flex items-center justify-center w-12 h-12 rounded-xl"
                      style={{ background: card.bgColor, color: card.color }}
                    >
                      {card.icon}
                    </div>
                  </div>
                </Card>
              </motion.div>
            </Col>
          ))}
        </Row>

        <Card
          title={
            <Space>
              <HeartOutlined />
              <span className="font-semibold">实例健康状态</span>
            </Space>
          }
          className="border-0 shadow-sm"
        >
          <Table
            columns={healthColumns}
            dataSource={instanceHealth}
            rowKey="id"
            scroll={{ x: 1420 }}
            pagination={false}
            size="middle"
          />
        </Card>
      </Spin>
    </motion.div>
  );
}

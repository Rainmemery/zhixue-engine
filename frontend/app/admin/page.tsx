"use client";

import { useState, useEffect } from "react";
import { motion } from "framer-motion";
import {
  DashboardOutlined,
  UserOutlined,
  FileTextOutlined,
  BarChartOutlined,
  MonitorOutlined,
  ReloadOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { Card, Row, Col, Typography, Spin, Alert, Button, message } from "antd";
import { AdminService } from "@/services/adminService";

const { Title, Text } = Typography;

interface DashboardMetrics {
  totalUsers: number | null;
  totalProblems: number | null;
  cpuUsage: number | null;
  memoryUsage: number | null;
  lastCheckTime: string;
  dataLoaded: boolean;
}

const quickActions = [
  {
    title: "用户管理",
    description: "管理系统用户账户",
    icon: <UserOutlined style={{ fontSize: 28 }} />,
    color: "#1890ff",
    bgColor: "#e6f7ff",
    href: "/admin/users",
  },
  {
    title: "题目管理",
    description: "管理编程题目",
    icon: <FileTextOutlined style={{ fontSize: 28 }} />,
    color: "#52c41a",
    bgColor: "#f6ffed",
    href: "/admin/problems",
  },
  {
    title: "调度监控",
    description: "监控判题调度",
    icon: <MonitorOutlined style={{ fontSize: 28 }} />,
    color: "#722ed1",
    bgColor: "#f9f0ff",
    href: "/admin/dispatcher/monitor",
  },
  {
    title: "RAG管理",
    description: "知识库管理",
    icon: <BarChartOutlined style={{ fontSize: 28 }} />,
    color: "#fa8c16",
    bgColor: "#fff7e6",
    href: "/admin/rag",
  },
];

export default function AdminDashboardPage() {
  const [metrics, setMetrics] = useState<DashboardMetrics>({
    totalUsers: null,
    totalProblems: null,
    cpuUsage: null,
    memoryUsage: null,
    lastCheckTime: "",
    dataLoaded: false,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [statsData, metricsData] = await Promise.all([
        AdminService.getSystemStats(),
        AdminService.getSystemMetrics(),
      ]) as [any, any];

      const totalUsers = statsData?.users?.total ?? null;
      const totalProblems = statsData?.problems?.total ?? null;
      const cpuUsage = metricsData?.cpuUsage ?? null;
      const memoryUsage = metricsData?.memoryUsage ?? null;
      const lastCheckTime = metricsData?.lastCheckTime || new Date().toISOString();

      setMetrics({
        totalUsers,
        totalProblems,
        cpuUsage,
        memoryUsage,
        lastCheckTime,
        dataLoaded: true,
      });
    } catch (err) {
      console.error("获取仪表板数据失败:", err);
      setError("获取系统数据失败，请稍后重试");
      message.error("获取系统数据失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  if (loading && !metrics.lastCheckTime) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  if (error && !metrics.lastCheckTime) {
    return (
      <div className="p-6">
        <Alert
          message="数据加载失败"
          description={error}
          type="error"
          showIcon
          action={
            <Button type="primary" icon={<ReloadOutlined />} onClick={fetchData}>
              重试
            </Button>
          }
        />
      </div>
    );
  }

  const getStatusColor = (value: number) => {
    if (value < 60) return "#52c41a";
    if (value < 80) return "#faad14";
    return "#f5222d";
  };

  const indicatorCards = [
    {
      title: "总用户数",
      value: metrics.totalUsers !== null ? metrics.totalUsers.toLocaleString() : "-",
      suffix: "",
      icon: <UserOutlined style={{ fontSize: 28 }} />,
      color: "#1890ff",
      bgColor: "#e6f7ff",
      loading: !metrics.dataLoaded,
    },
    {
      title: "题目总数",
      value: metrics.totalProblems !== null ? metrics.totalProblems.toLocaleString() : "-",
      suffix: "",
      icon: <FileTextOutlined style={{ fontSize: 28 }} />,
      color: "#52c41a",
      bgColor: "#f6ffed",
      loading: !metrics.dataLoaded,
    },
    {
      title: "CPU使用率",
      value: metrics.cpuUsage !== null ? metrics.cpuUsage.toFixed(1) : "-",
      suffix: "%",
      icon: <MonitorOutlined style={{ fontSize: 28 }} />,
      color: metrics.cpuUsage !== null ? getStatusColor(metrics.cpuUsage) : "#d9d9d9",
      bgColor: metrics.cpuUsage !== null
        ? metrics.cpuUsage < 60 ? "#f6ffed" : metrics.cpuUsage < 80 ? "#fffbe6" : "#fff2f0"
        : "#fafafa",
      loading: !metrics.dataLoaded,
    },
    {
      title: "内存使用率",
      value: metrics.memoryUsage !== null ? metrics.memoryUsage.toFixed(1) : "-",
      suffix: "%",
      icon: <MonitorOutlined style={{ fontSize: 28 }} />,
      color: metrics.memoryUsage !== null ? getStatusColor(metrics.memoryUsage) : "#d9d9d9",
      bgColor: metrics.memoryUsage !== null
        ? metrics.memoryUsage < 60 ? "#f6ffed" : metrics.memoryUsage < 80 ? "#fffbe6" : "#fff2f0"
        : "#fafafa",
      loading: !metrics.dataLoaded,
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
            管理仪表盘
          </Title>
          <Text type="secondary">
            系统关键指标监控
            {metrics.lastCheckTime && (
              <span className="ml-2 text-xs">
                · 上次更新: {new Date(metrics.lastCheckTime).toLocaleTimeString()}
              </span>
            )}
          </Text>
        </div>
        <Button
          type="primary"
          icon={<ReloadOutlined />}
          onClick={fetchData}
          loading={loading}
        >
          刷新数据
        </Button>
      </div>

      {(metrics.cpuUsage !== null && metrics.cpuUsage > 80) || (metrics.memoryUsage !== null && metrics.memoryUsage > 80) ? (
        <Alert
          message="系统警告"
          description={
            metrics.cpuUsage !== null && metrics.cpuUsage > 80 && metrics.memoryUsage !== null && metrics.memoryUsage > 80
              ? `CPU使用率 (${metrics.cpuUsage.toFixed(1)}%) 和内存使用率 (${metrics.memoryUsage.toFixed(1)}%) 均过高，建议检查系统负载`
              : metrics.cpuUsage !== null && metrics.cpuUsage > 80
                ? `CPU使用率过高 (${metrics.cpuUsage.toFixed(1)}%)，建议检查系统负载`
                : `内存使用率过高 (${metrics.memoryUsage!.toFixed(1)}%)，建议检查系统内存`
          }
          type="warning"
          showIcon
          icon={<WarningOutlined />}
        />
      ) : null}

      <Row gutter={[24, 24]}>
        {indicatorCards.map((card, index) => (
          <Col xs={24} sm={12} lg={6} key={index}>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
            >
              <Card
                className="hover:shadow-lg transition-all duration-300 border-0"
                style={{ background: "#fff" }}
              >
                <Spin spinning={card.loading} size="small">
                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <Text type="secondary" className="text-sm block mb-2">
                      {card.title}
                    </Text>
                    <div className="flex items-baseline gap-1">
                      <span
                        className="text-3xl font-bold"
                        style={{ color: card.color }}
                      >
                        {card.value}
                      </span>
                      {card.suffix && card.value !== "-" && (
                        <span className="text-lg" style={{ color: card.color }}>
                          {card.suffix}
                        </span>
                      )}
                    </div>
                  </div>
                  <div
                    className="flex items-center justify-center w-14 h-14 rounded-xl"
                    style={{ background: card.bgColor, color: card.color }}
                  >
                    {card.icon}
                  </div>
                </div>
                </Spin>
              </Card>
            </motion.div>
          </Col>
        ))}
      </Row>

      <Card
        title={<span className="font-semibold">快捷入口</span>}
        className="border-0 shadow-sm"
      >
        <Row gutter={[16, 16]}>
          {quickActions.map((action, index) => (
            <Col xs={12} sm={12} lg={6} key={index}>
              <motion.div
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.4 + index * 0.05 }}
              >
                <a href={action.href} className="block no-underline">
                  <Card
                    hoverable
                    className="text-center border-0"
                    style={{ background: "#fafafa" }}
                  >
                    <div
                      className="flex items-center justify-center w-12 h-12 rounded-lg mx-auto mb-3"
                      style={{ background: action.bgColor, color: action.color }}
                    >
                      {action.icon}
                    </div>
                    <div className="font-medium" style={{ color: "#333" }}>
                      {action.title}
                    </div>
                    <div className="text-xs mt-1" style={{ color: "#999" }}>
                      {action.description}
                    </div>
                  </Card>
                </a>
              </motion.div>
            </Col>
          ))}
        </Row>
      </Card>
    </motion.div>
  );
}

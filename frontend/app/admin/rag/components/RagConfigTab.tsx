"use client";

import { useState, useCallback, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Card,
  Form,
  Switch,
  Button,
  Space,
  message,
  Typography,
  Row,
  Col,
  Spin,
  Tag,
  Select,
} from "antd";
import {
  SettingOutlined,
  SaveOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ThunderboltOutlined,
  RobotOutlined,
  FileSearchOutlined,
  QuestionCircleOutlined,
  CodeOutlined,
} from "@ant-design/icons";
import { ragService, RagGlobalConfig, ModuleConfig, KnowledgeBase } from "@/services/ragService";
import { ErrorHandler } from "@/lib/errorHandler";

const { Title, Text } = Typography;

interface ModuleDisplay {
  code: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  color: string;
  bgColor: string;
}

const MODULE_DEFINITIONS: ModuleDisplay[] = [
  {
    code: "intelligent_question",
    name: "智能提问",
    description: "基于知识库生成针对性的学习问题和引导",
    icon: <QuestionCircleOutlined style={{ fontSize: 20 }} />,
    color: "#52c41a",
    bgColor: "#f6ffed",
  },
  {
    code: "ai_chat",
    name: "AI 智能问答",
    description: "提供基于知识库的智能问答服务",
    icon: <RobotOutlined style={{ fontSize: 20 }} />,
    color: "#1890ff",
    bgColor: "#e6f7ff",
  },
  {
    code: "code_explain",
    name: "代码解释",
    description: "解释代码逻辑和功能，结合知识库提供深度分析",
    icon: <FileSearchOutlined style={{ fontSize: 20 }} />,
    color: "#722ed1",
    bgColor: "#f9f0ff",
  },
  {
    code: "code_review",
    name: "代码评审",
    description: "提供代码质量分析和改进建议",
    icon: <CodeOutlined style={{ fontSize: 20 }} />,
    color: "#fa8c16",
    bgColor: "#fff7e6",
  },
];

export default function RagConfigTab() {
  const [saving, setSaving] = useState(false);
  const [globalConfig, setGlobalConfig] = useState<RagGlobalConfig | null>(null);
  const [moduleConfigs, setModuleConfigs] = useState<Record<string, ModuleConfig>>({});
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [form] = Form.useForm();
  const [operationLocks, setOperationLocks] = useState<Record<string, boolean>>({});
  const [refreshing, setRefreshing] = useState(false);
  const [saveSuccessVisible, setSaveSuccessVisible] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [globalData, modulesData, kbData] = await Promise.all([
        ragService.config.getGlobal(),
        ragService.config.getModules(),
        ragService.knowledge.listEnabled(),
      ]);

      if (globalData) {
        setGlobalConfig(globalData);
        form.setFieldsValue({
          ragEnabled: globalData.rag_enabled ?? false,
        });
      }

      const initializedModules: Record<string, ModuleConfig> = {};
      MODULE_DEFINITIONS.forEach((def) => {
        initializedModules[def.code] = modulesData[def.code] || {
          enabled: false,
          knowledgeBaseIds: [],
        };
      });
      setModuleConfigs(initializedModules);
      setKnowledgeBases(kbData || []);
    } catch (error) {
      console.error("加载 RAG 配置失败:", error);
      message.error("加载配置失败，请检查网络连接后重试");
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await loadData();
      message.success("数据刷新成功");
    } catch (error) {
      message.error("刷新失败，请重试");
    } finally {
      setTimeout(() => {
        setRefreshing(false);
      }, 500);
    }
  };

  const handleSaveGlobalConfig = async () => {
    setSaving(true);
    try {
      const config = {
        rag_enabled: globalConfig?.rag_enabled ?? false,
        retrieval_config: globalConfig?.retrieval_config || {
          similarityThreshold: 0.7,
          topK: 5,
          maxContextLength: 4000,
        },
        embedding_config: globalConfig?.embedding_config || {
          defaultModelId: 1,
          allowModelSwitch: true,
          ollamaBaseUrl: "",
          timeout: 60000,
          parallelism: 8,
          defaultModel: "nomic-embed-text",
          maxTextLength: 8000,
          dimension: null,
        },
        chunk_config: globalConfig?.chunk_config || {
          chunkSize: 500,
          chunkOverlap: 50,
        },
        file_config: globalConfig?.file_config || {
          uploadPath: "",
          maxSize: 52428800,
          allowedTypes: ["PDF", "DOCX", "TXT", "MD"],
        },
      };

      await ragService.config.updateGlobal(config);

      setGlobalConfig({
        ...globalConfig,
        ...config,
      } as RagGlobalConfig);

      setSaveSuccessVisible(true);
      setTimeout(() => {
        setSaveSuccessVisible(false);
      }, 2000);

      message.success("配置保存成功");
    } catch (err: unknown) {
      const error = err as { response?: { data?: { msg?: string } }; errors?: Array<{ message: string }> };
      if (error.errors && error.errors.length > 0) {
        message.error(error.errors[0].message);
      } else {
        message.error(error.response?.data?.msg || "保存失败");
      }
    } finally {
      setSaving(false);
    }
  };

  const handleToggleRag = async (enabled: boolean) => {
    const lockKey = "global_rag";
    if (operationLocks[lockKey]) return;

    setOperationLocks((prev) => ({ ...prev, [lockKey]: true }));

    try {
      setGlobalConfig((prev) =>
        prev ? { ...prev, rag_enabled: enabled } : null
      );
      form.setFieldsValue({ ragEnabled: enabled });

      await ragService.config.toggleRag(enabled);
      message.success(enabled ? "RAG 功能已启用" : "RAG 功能已禁用");
    } catch (err: unknown) {
      setGlobalConfig((prev) =>
        prev ? { ...prev, rag_enabled: !enabled } : null
      );
      form.setFieldsValue({ ragEnabled: !enabled });

      const error = err as { response?: { data?: { msg?: string } } };
      new ErrorHandler(error.response?.data?.msg || "操作失败")
        .setContext("切换 RAG 功能")
        .handle();
    } finally {
      setOperationLocks((prev) => ({ ...prev, [lockKey]: false }));
    }
  };

  const handleToggleModule = async (moduleCode: string, enabled: boolean) => {
    const lockKey = `module_${moduleCode}`;
    if (operationLocks[lockKey]) return;

    const previousConfig = moduleConfigs[moduleCode];
    if (!previousConfig) return;

    setOperationLocks((prev) => ({ ...prev, [lockKey]: true }));

    try {
      setModuleConfigs((prev) => ({
        ...prev,
        [moduleCode]: {
          ...prev[moduleCode],
          enabled,
        },
      }));

      await ragService.config.updateModule(moduleCode, {
        enabled,
        knowledgeBaseIds: previousConfig.knowledgeBaseIds || [],
      });

      const moduleName = MODULE_DEFINITIONS.find((m) => m.code === moduleCode)?.name;
      message.success(enabled ? `${moduleName} RAG 已启用` : `${moduleName} RAG 已禁用`);
    } catch (err: unknown) {
      setModuleConfigs((prev) => ({
        ...prev,
        [moduleCode]: previousConfig,
      }));

      const error = err as { response?: { data?: { msg?: string } } };
      new ErrorHandler(error.response?.data?.msg || "操作失败")
        .setContext("切换模块 RAG")
        .handle();
    } finally {
      setOperationLocks((prev) => ({ ...prev, [lockKey]: false }));
    }
  };

  const handleUpdateModuleKnowledgeBases = async (moduleCode: string, knowledgeBaseIds: number[]) => {
    const lockKey = `kb_${moduleCode}`;
    if (operationLocks[lockKey]) return;

    const previousConfig = moduleConfigs[moduleCode];
    if (!previousConfig) return;

    setOperationLocks((prev) => ({ ...prev, [lockKey]: true }));

    try {
      setModuleConfigs((prev) => ({
        ...prev,
        [moduleCode]: {
          ...prev[moduleCode],
          knowledgeBaseIds,
        },
      }));

      await ragService.config.updateModule(moduleCode, {
        enabled: previousConfig.enabled,
        knowledgeBaseIds,
      });

      message.success("知识库关联更新成功");
    } catch (err: unknown) {
      setModuleConfigs((prev) => ({
        ...prev,
        [moduleCode]: previousConfig,
      }));

      const error = err as { response?: { data?: { msg?: string } } };
      new ErrorHandler(error.response?.data?.msg || "更新失败")
        .setContext("更新模块知识库")
        .handle();
    } finally {
      setOperationLocks((prev) => ({ ...prev, [lockKey]: false }));
    }
  };

  if (loading) {
    return (
      <Card style={{ borderRadius: 16, boxShadow: "0 4px 20px rgba(0, 0, 0, 0.06)" }}>
        <div className="flex justify-center py-16">
          <Spin size="large" tip="加载配置中..." />
        </div>
      </Card>
    );
  }

  return (
    <div style={{ position: "relative" }}>
      <AnimatePresence>
        {saving && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            style={{
              position: "absolute",
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: "rgba(255, 255, 255, 0.8)",
              backdropFilter: "blur(4px)",
              zIndex: 1000,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              borderRadius: 16,
            }}
          >
            <Spin size="large" tip="正在保存配置..." />
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {saveSuccessVisible && (
          <motion.div
            initial={{ opacity: 0, scale: 0.5 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.5 }}
            style={{
              position: "fixed",
              top: "50%",
              left: "50%",
              transform: "translate(-50%, -50%)",
              zIndex: 2000,
            }}
          >
            <div
              style={{
                background: "#f6ffed",
                border: "2px solid #52c41a",
                borderRadius: 20,
                padding: "40px 56px",
                textAlign: "center",
                boxShadow: "0 12px 40px rgba(82, 196, 26, 0.3)",
              }}
            >
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: "spring", stiffness: 200, damping: 15 }}
              >
                <CheckCircleOutlined
                  style={{ fontSize: 72, color: "#52c41a", marginBottom: 16 }}
                />
              </motion.div>
              <div style={{ fontSize: 22, fontWeight: 700, color: "#52c41a", marginBottom: 8 }}>
                保存成功
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <Card
        title={
          <Space size="small">
            <SettingOutlined style={{ fontSize: 18, color: "#1890ff" }} />
            <Title level={5} style={{ margin: 0, fontSize: 16 }} className="sm:text-lg">
              RAG 配置管理
            </Title>
          </Space>
        }
        extra={
          <Space size="small" className="hidden-xs">
            <Button
              icon={<ReloadOutlined spin={refreshing} />}
              onClick={handleRefresh}
              loading={refreshing}
              size="middle"
              style={{ height: 36, borderRadius: 8, fontWeight: 500 }}
            >
              <span className="hidden sm:inline">刷新</span>
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSaveGlobalConfig}
              loading={saving}
              size="middle"
              style={{
                background: saving
                  ? undefined
                  : "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
                border: "none",
                height: 36,
                borderRadius: 8,
                fontWeight: 600,
                boxShadow: saving
                  ? undefined
                  : "0 4px 12px rgba(102, 126, 234, 0.4)",
                transition: "all 0.3s ease",
              }}
            >
              <span className="hidden sm:inline">保存配置</span>
              <span className="sm:hidden">保存</span>
            </Button>
          </Space>
        }
        style={{
          boxShadow: "0 4px 12px rgba(0, 0, 0, 0.08)",
          borderRadius: 12,
          transition: "box-shadow 0.3s ease",
        }}
      >
        <Form form={form} layout="vertical">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.1 }}
          >
            <Card
              style={{
                borderRadius: 16,
                boxShadow: "0 4px 20px rgba(0, 0, 0, 0.06)",
                border: "1px solid #f0f0f0",
                overflow: "hidden",
                marginBottom: 24,
              }}
              title={
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <motion.div
                    whileHover={{ scale: 1.1, rotate: 5 }}
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 10,
                      background: "linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
                    }}
                  >
                    <ThunderboltOutlined style={{ color: "#fff", fontSize: 20 }} />
                  </motion.div>
                  <div>
                    <Text strong style={{ fontSize: 16, display: "block" }}>
                      全局配置
                    </Text>
                  </div>
                </div>
              }
              headStyle={{
                background: "linear-gradient(135deg, #fafafa 0%, #f5f5f5 100%)",
                borderBottom: "1px solid #f0f0f0",
                padding: "16px 24px",
              }}
              bodyStyle={{ padding: 24 }}
            >
              <Row gutter={[24, 24]}>
                <Col xs={24}>
                  <motion.div
                    whileHover={{ scale: 1.01 }}
                    style={{
                      padding: 24,
                      background: "linear-gradient(135deg, #fff1f0 0%, #fff 100%)",
                      borderRadius: 12,
                      border: "1px solid #ffccc7",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                      <div>
                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                          <div
                            style={{
                              width: 8,
                              height: 8,
                              borderRadius: "50%",
                              background: globalConfig?.rag_enabled ? "#52c41a" : "#d9d9d9",
                              boxShadow: globalConfig?.rag_enabled
                                ? "0 0 8px rgba(82, 196, 26, 0.5)"
                                : "none",
                              transition: "all 0.3s ease",
                            }}
                          />
                          <span style={{ fontWeight: 600, fontSize: 16 }}>RAG 功能总开关</span>
                        </div>
                        <Text type="secondary" style={{ fontSize: 14 }}>
                          {globalConfig?.rag_enabled
                            ? "RAG 检索增强生成功能已启用，AI 对话将自动从知识库检索相关信息"
                            : "RAG 功能已禁用，AI 对话将使用原始模型能力"}
                        </Text>
                      </div>
                      <Form.Item style={{ marginBottom: 0 }}>
                        <Switch
                          checked={globalConfig?.rag_enabled ?? false}
                          checkedChildren="已启用"
                          unCheckedChildren="已禁用"
                          onChange={handleToggleRag}
                          style={{ minWidth: 80 }}
                        />
                      </Form.Item>
                    </div>
                  </motion.div>
                </Col>
              </Row>
            </Card>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.2 }}
          >
            <Card
              style={{
                borderRadius: 16,
                boxShadow: "0 4px 20px rgba(0, 0, 0, 0.06)",
                border: "1px solid #f0f0f0",
                overflow: "hidden",
              }}
              title={
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <motion.div
                    whileHover={{ scale: 1.1, rotate: 5 }}
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 10,
                      background: "linear-gradient(135deg, #52c41a 0%, #95de64 100%)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      boxShadow: "0 4px 12px rgba(82, 196, 26, 0.25)",
                    }}
                  >
                    <RobotOutlined style={{ color: "#fff", fontSize: 20 }} />
                  </motion.div>
                  <div>
                    <Text strong style={{ fontSize: 16, display: "block" }}>
                      功能模块配置
                    </Text>
                  </div>
                </div>
              }
              headStyle={{
                background: "linear-gradient(135deg, #fafafa 0%, #f5f5f5 100%)",
                borderBottom: "1px solid #f0f0f0",
                padding: "16px 24px",
              }}
              bodyStyle={{ padding: 24 }}
            >
              <Row gutter={[16, 16]}>
                {MODULE_DEFINITIONS.map((mod, index) => {
                  const config = moduleConfigs[mod.code];
                  const isEnabled = config?.enabled ?? false;

                  return (
                    <Col xs={24} sm={12} key={mod.code}>
                      <motion.div
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.3, delay: 0.1 * index }}
                        whileHover={{
                          y: -4,
                          boxShadow: "0 12px 32px rgba(0, 0, 0, 0.12)",
                        }}
                        style={{
                          padding: 24,
                          background: isEnabled ? mod.bgColor : "#fafafa",
                          borderRadius: 14,
                          border: `2px solid ${isEnabled ? mod.color + "40" : "#f0f0f0"}`,
                          transition: "all 0.3s ease",
                        }}
                      >
                        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: isEnabled ? 16 : 0 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 12, flex: 1 }}>
                            <div
                              style={{
                                width: 40,
                                height: 40,
                                borderRadius: 10,
                                background: isEnabled ? mod.color + "20" : "#f5f5f5",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                color: isEnabled ? mod.color : "#bfbfbf",
                                transition: "all 0.3s ease",
                              }}
                            >
                              {mod.icon}
                            </div>
                            <div style={{ flex: 1 }}>
                              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                <Text strong style={{ fontSize: 15 }}>{mod.name}</Text>
                                {isEnabled && (
                                  <Tag color="success" style={{ marginLeft: 4, fontSize: 11 }}>
                                    已启用
                                  </Tag>
                                )}
                              </div>
                              <Text type="secondary" style={{ fontSize: 13, lineHeight: 1.5, display: "block", marginTop: 2 }}>
                                {mod.description}
                              </Text>
                            </div>
                          </div>
                          <Switch
                            checked={isEnabled}
                            onChange={(checked) => handleToggleModule(mod.code, checked)}
                            checkedChildren="启用"
                            unCheckedChildren="禁用"
                            loading={operationLocks[`module_${mod.code}`]}
                            style={{ minWidth: 70 }}
                          />
                        </div>
                        {isEnabled && (
                          <motion.div
                            initial={{ opacity: 0, height: 0 }}
                            animate={{ opacity: 1, height: "auto" }}
                            exit={{ opacity: 0, height: 0 }}
                            style={{ marginTop: 12, paddingTop: 12, borderTop: "1px solid " + (mod.color + "20") }}
                          >
                            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                              <Text type="secondary" style={{ fontSize: 13, fontWeight: 500, whiteSpace: "nowrap" }}>
                                挂载知识库
                              </Text>
                              {config?.knowledgeBaseIds && config.knowledgeBaseIds.length > 0 && (
                                <Tag color="blue" style={{ fontSize: 11 }}>
                                  {config.knowledgeBaseIds.length} 个
                                </Tag>
                              )}
                            </div>
                            <Select
                              mode="multiple"
                              style={{ width: "100%" }}
                              placeholder="选择要挂载的知识库"
                              value={config?.knowledgeBaseIds || []}
                              onChange={(ids) => handleUpdateModuleKnowledgeBases(mod.code, ids)}
                              loading={operationLocks[`kb_${mod.code}`]}
                              allowClear
                              showSearch
                              filterOption={(input, option) =>
                                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
                              }
                              options={knowledgeBases.map((kb) => ({
                                value: kb.id,
                                label: kb.name,
                              }))}
                              notFoundContent={
                                knowledgeBases.length === 0
                                  ? "暂无可用知识库，请先创建知识库"
                                  : undefined
                              }
                            />
                          </motion.div>
                        )}
                      </motion.div>
                    </Col>
                  );
                })}
              </Row>
            </Card>
          </motion.div>
        </Form>
      </Card>
    </div>
  );
}

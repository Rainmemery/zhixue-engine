"use client";

import { useState, useEffect, useCallback } from "react";
import { motion } from "framer-motion";
import {
  UserOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
  CheckCircleOutlined,
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
  Row,
  Col,
} from "antd";
import { AdminService, AdminUser, CreateUserRequest, UpdateUserRequest } from "@/services/adminService";

const { Title, Text } = Typography;
const { Search } = Input;

export default function UsersManagementPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [form] = Form.useForm();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [searchParams, setSearchParams] = useState({
    search: "",
    isActive: "" as "" | number,
    role: "",
  });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      const params: {
        page: number;
        size: number;
        search?: string;
        isActive?: number;
        role?: string;
      } = {
        page: pagination.current,
        size: pagination.pageSize,
      };

      if (searchParams.search) params.search = searchParams.search;
      if (searchParams.isActive !== "") params.isActive = searchParams.isActive;
      if (searchParams.role) params.role = searchParams.role;

      const usersData = await AdminService.getUsers(params);

      if (usersData && Array.isArray(usersData.items)) {
        setUsers(usersData.items);
        setPagination((prev) => ({
          ...prev,
          total: usersData.total || 0,
        }));
      } else {
        setUsers([]);
        setPagination((prev) => ({
          ...prev,
          total: 0,
        }));
      }
    } catch (error) {
      console.error("获取用户数据失败:", error);
      message.error("获取用户列表失败");
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, searchParams]);

  // 页面加载时立即请求数据
  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleSearch = () => {
    setPagination((prev) => ({ ...prev, current: 1 }));
    fetchUsers();
  };

  const handleReset = () => {
    setSearchParams({ search: "", isActive: "", role: "" });
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const handleDelete = async (id: number) => {
    try {
      setLoading(true);
      await AdminService.deleteUser(id);
      message.success("删除成功");
      fetchUsers();
    } catch (error) {
      console.error("删除用户失败:", error);
      message.error("删除失败");
    } finally {
      setLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    try {
      setLoading(true);
      await AdminService.batchDeleteUsers(selectedRowKeys as number[]);
      message.success("批量删除成功");
      setSelectedRowKeys([]);
      fetchUsers();
    } catch (error) {
      console.error("批量删除失败:", error);
      message.error("批量删除失败");
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = (user: AdminUser) => {
    setEditingUser(user);
    form.setFieldsValue(user);
    setIsModalOpen(true);
  };

  const handleCreate = () => {
    setEditingUser(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  const handleSubmit = async (values: Record<string, unknown>) => {
    try {
      if (editingUser) {
        const { password: _password, newPassword, ...updateData } = values;
        await AdminService.updateUser(editingUser.id, updateData as UpdateUserRequest);
        
        if (newPassword && typeof newPassword === "string" && newPassword.trim()) {
          await AdminService.updatePassword(editingUser.id, newPassword);
        }
        
        message.success("更新成功");
      } else {
        await AdminService.createUser(values as unknown as CreateUserRequest);
        message.success("创建成功");
      }
      setIsModalOpen(false);
      form.resetFields();
      setEditingUser(null);
      fetchUsers();
    } catch (error) {
      console.error("操作失败:", error);
      message.error(editingUser ? "更新失败" : "创建失败");
    }
  };

  const getRoleColor = (role: string) => {
    const colorMap: Record<string, string> = {
      admin: "red",
      teacher: "blue",
      student: "green",
    };
    return colorMap[role] || "default";
  };

  const getRoleLabel = (role: string) => {
    const labelMap: Record<string, string> = {
      admin: "管理员",
      teacher: "教师",
      student: "学生",
    };
    return labelMap[role] || role;
  };

  const columns = [
    {
      title: "ID",
      dataIndex: "id",
      key: "id",
      width: 80,
    },
    {
      title: "用户名",
      dataIndex: "username",
      key: "username",
      width: 120,
    },
    {
      title: "真实姓名",
      dataIndex: "realName",
      key: "realName",
      width: 100,
      render: (realName: string) => realName || "-",
    },
    {
      title: "邮箱",
      dataIndex: "email",
      key: "email",
      width: 180,
    },
    {
      title: "手机号",
      dataIndex: "phone",
      key: "phone",
      width: 130,
      render: (phone: string) => phone || "-",
    },
    {
      title: "学校",
      dataIndex: "school",
      key: "school",
      width: 150,
      ellipsis: true,
      render: (school: string) => school || "-",
    },
    {
      title: "角色",
      dataIndex: "role",
      key: "role",
      width: 90,
      render: (role: string) => (
        <Tag color={getRoleColor(role)}>{getRoleLabel(role)}</Tag>
      ),
    },
    {
      title: "状态",
      dataIndex: "isActive",
      key: "isActive",
      width: 80,
      render: (isActive: number) => (
        <Tag
          icon={isActive === 1 ? <CheckCircleOutlined /> : <StopOutlined />}
          color={isActive === 1 ? "success" : "error"}
        >
          {isActive === 1 ? "活跃" : "禁用"}
        </Tag>
      ),
    },
    {
      title: "经验值",
      dataIndex: "experiencePoints",
      key: "experiencePoints",
      width: 90,
      align: "right" as const,
      render: (value: number) => value || 0,
    },
    {
      title: "金币",
      dataIndex: "coins",
      key: "coins",
      width: 80,
      align: "right" as const,
      render: (value: number) => value || 0,
    },
    {
      title: "最后活跃",
      dataIndex: "lastActiveDate",
      key: "lastActiveDate",
      width: 120,
      render: (date: string) => date ? new Date(date).toLocaleDateString("zh-CN") : "-",
    },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      key: "createdAt",
      width: 160,
      render: (date: string) => date ? new Date(date).toLocaleString("zh-CN") : "-",
    },
    {
      title: "操作",
      key: "action",
      width: 150,
      fixed: "right" as const,
      render: (_: unknown, record: AdminUser) => (
        <Space size="small">
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description="确定要删除此用户吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const rowSelection = {
    selectedRowKeys,
    onChange: (newSelectedRowKeys: React.Key[]) => {
      setSelectedRowKeys(newSelectedRowKeys);
    },
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="space-y-6"
    >
      <div className="flex items-center justify-between">
        <div>
          <Title level={4} className="!mb-1">
            <UserOutlined className="mr-2" />
            用户管理
          </Title>
          <Text type="secondary">管理系统用户，分配角色和权限</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => fetchUsers()}>
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建用户
          </Button>
        </Space>
      </div>

      <Card>
        <Row gutter={[16, 16]} className="mb-4">
          <Col xs={24} sm={12} md={6} lg={5}>
            <Input
              placeholder="搜索用户名"
              prefix={<SearchOutlined />}
              value={searchParams.search}
              onChange={(e) => setSearchParams((prev) => ({ ...prev, search: e.target.value }))}
              onPressEnter={handleSearch}
            />
          </Col>
          <Col xs={24} sm={12} md={6} lg={4}>
            <Select
              style={{ width: "100%" }}
              placeholder="状态"
              value={searchParams.isActive !== "" ? searchParams.isActive : undefined}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, isActive: value }))}
              allowClear
            >
              <Select.Option value={1}>活跃</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={6} lg={4}>
            <Select
              style={{ width: "100%" }}
              placeholder="角色"
              value={searchParams.role || undefined}
              onChange={(value) => setSearchParams((prev) => ({ ...prev, role: value }))}
              allowClear
            >
              <Select.Option value="admin">管理员</Select.Option>
              <Select.Option value="teacher">教师</Select.Option>
              <Select.Option value="student">学生</Select.Option>
            </Select>
          </Col>
          <Col xs={24} sm={12} md={6} lg={8}>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              {selectedRowKeys.length > 0 && (
                <Popconfirm
                  title="确定要删除选中的用户吗？"
                  description={`将删除 ${selectedRowKeys.length} 个用户`}
                  onConfirm={handleBatchDelete}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <Button danger icon={<DeleteOutlined />}>
                    批量删除 ({selectedRowKeys.length})
                  </Button>
                </Popconfirm>
              )}
            </Space>
          </Col>
        </Row>

        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1500 }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 条记录`,
            onChange: (page, pageSize) => {
              setPagination((prev) => ({ ...prev, current: page, pageSize: pageSize || prev.pageSize }));
            },
          }}
        />
      </Card>

      <Modal
        title={editingUser ? "编辑用户" : "新建用户"}
        open={isModalOpen}
        onCancel={() => {
          setIsModalOpen(false);
          form.resetFields();
          setEditingUser(null);
        }}
        onOk={() => form.submit()}
        width={600}
        confirmLoading={loading}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ role: "student" }}
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input disabled={!!editingUser} />
          </Form.Item>

          {!editingUser && (
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: "请输入密码" }]}
            >
              <Input.Password placeholder="请输入密码" />
            </Form.Item>
          )}

          {editingUser && (
            <Form.Item
              name="newPassword"
              label="新密码"
              extra="留空则不修改密码"
            >
              <Input.Password placeholder="输入新密码" />
            </Form.Item>
          )}

          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: "请输入邮箱" },
              { type: "email", message: "请输入有效的邮箱" },
            ]}
          >
            <Input />
          </Form.Item>

          <Form.Item name="realName" label="真实姓名">
            <Input />
          </Form.Item>

          <Form.Item name="phone" label="手机号">
            <Input />
          </Form.Item>

          <Form.Item name="school" label="学校">
            <Input />
          </Form.Item>

          <Form.Item name="major" label="专业">
            <Input />
          </Form.Item>

          <Form.Item name="grade" label="年级">
            <Input />
          </Form.Item>

          <Form.Item name="role" label="角色" rules={[{ required: true, message: "请选择角色" }]}>
            <Select>
              <Select.Option value="admin">管理员</Select.Option>
              <Select.Option value="teacher">教师</Select.Option>
              <Select.Option value="student">学生</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="isActive"
            label="状态"
            rules={[{ required: true, message: "请选择状态" }]}
          >
            <Select>
              <Select.Option value={1}>活跃</Select.Option>
              <Select.Option value={0}>禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </motion.div>
  );
}

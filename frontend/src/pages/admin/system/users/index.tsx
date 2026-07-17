/**
 * 用户管理：ProTable + 新增 Modal + 状态 Switch
 */
import { PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { Button, Form, Input, InputNumber, Modal, Select, Switch, Tag, message } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import {
  createUser,
  listRoles,
  listUsers,
  updateUser,
  type SystemRole,
  type SystemUser,
} from '@/services/system';
import { getRequestErrorMessage } from '@/utils/requestError';

const UsersPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [roles, setRoles] = useState<SystemRole[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<SystemUser | null>(null);
  const [roleIds, setRoleIds] = useState<number[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    listRoles()
      .then((res) => {
        if (res.code === 0) setRoles(res.data || []);
      })
      .catch(() => undefined);
  }, []);

  const openRoleModal = (row: SystemUser) => {
    setEditingUser(row);
    setRoleIds(roles.filter((r) => (row.roles || []).includes(r.code)).map((r) => r.id));
    setRoleOpen(true);
  };

  const columns: ProColumns<SystemUser>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    {
      title: '用户名/手机号',
      dataIndex: 'username',
      formItemProps: { name: 'keyword' },
      fieldProps: { placeholder: '搜索用户名' },
    },
    { title: '员工 ID', dataIndex: 'employeeId', search: false, width: 100 },
    {
      title: '角色',
      dataIndex: 'roles',
      search: false,
      render: (_, row) =>
        (row.roles || []).map((r) => (
          <Tag key={r} style={{ marginBottom: 2 }}>
            {r}
          </Tag>
        )),
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 100,
      render: (_, row) => (
        <Switch
          checked={row.status === 1}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          onChange={async (checked) => {
            try {
              const res = await updateUser(row.id, { status: checked ? 1 : 0 });
              if (res.code !== 0) {
                message.error(res.message || '更新失败');
                return;
              }
              message.success(checked ? '已启用' : '已禁用');
              actionRef.current?.reload();
            } catch (e) {
              message.error(getRequestErrorMessage(e));
            }
          }}
        />
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, row) => [
        <a key="roles" onClick={() => openRoleModal(row)}>
          分配角色
        </a>,
      ],
    },
  ];

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await createUser({
        username: values.username,
        employeeId: values.employeeId,
        roleIds: values.roleIds,
        password: values.password || undefined,
      });
      if (res.code !== 0) {
        message.error(res.message || '创建失败');
        return;
      }
      message.success('用户已创建');
      setCreateOpen(false);
      form.resetFields();
      actionRef.current?.reload();
    } catch (e) {
      if (e && typeof e === 'object' && 'errorFields' in e) return;
      message.error(getRequestErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveRoles = async () => {
    if (!editingUser) return;
    if (!roleIds.length) {
      message.warning('请至少选择一个角色');
      return;
    }
    try {
      setSubmitting(true);
      const res = await updateUser(editingUser.id, { roleIds });
      if (res.code !== 0) {
        message.error(res.message || '更新失败');
        return;
      }
      message.success('角色已更新');
      setRoleOpen(false);
      actionRef.current?.reload();
    } catch (e) {
      message.error(getRequestErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <ProTable<SystemUser>
        headerTitle="用户管理"
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            新增用户
          </Button>,
        ]}
        request={async (params) => {
          try {
            const res = await listUsers({
              keyword: params.keyword as string | undefined,
              page: params.current,
              pageSize: params.pageSize,
            });
            if (res.code !== 0) {
              message.error(res.message || '加载失败');
              return { data: [], success: false, total: 0 };
            }
            return {
              data: res.data?.list || [],
              success: true,
              total: res.data?.total || 0,
            };
          } catch (e) {
            message.error(getRequestErrorMessage(e));
            return { data: [], success: false, total: 0 };
          }
        }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
      />

      <Modal
        title="新增用户"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreate}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="username"
            label="用户名（手机号）"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号' },
            ]}
          >
            <Input placeholder="13800000001" maxLength={11} />
          </Form.Item>
          <Form.Item
            name="employeeId"
            label="员工 ID"
            rules={[{ required: true, message: '请输入员工 ID' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="关联 employee.id" />
          </Form.Item>
          <Form.Item
            name="roleIds"
            label="角色"
            rules={[{ required: true, message: '请选择至少一个角色' }]}
          >
            <Select
              mode="multiple"
              options={roles.map((r) => ({ label: `${r.name}（${r.code}）`, value: r.id }))}
              placeholder="选择角色"
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码（可选）"
            extra="留空则系统生成随机密码；须含大小写字母和数字，至少 8 位"
          >
            <Input.Password placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingUser ? `分配角色：${editingUser.username}` : '分配角色'}
        open={roleOpen}
        onCancel={() => setRoleOpen(false)}
        onOk={handleSaveRoles}
        confirmLoading={submitting}
      >
        <Select
          mode="multiple"
          style={{ width: '100%' }}
          value={roleIds}
          onChange={setRoleIds}
          options={roles.map((r) => ({ label: `${r.name}（${r.code}）`, value: r.id }))}
          placeholder="选择角色"
        />
      </Modal>
    </>
  );
};

export default UsersPage;

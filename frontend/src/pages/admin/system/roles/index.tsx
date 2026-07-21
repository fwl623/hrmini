/**
 * 角色管理：5 预置角色，不可新增/删除；编码只读；权限 Tree 按 module 分组
 */
import { EditOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { Button, Form, Input, Modal, Space, Table, Tag, Tree, Typography, message } from 'antd';
import type { DataNode } from 'antd/es/tree';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  listPermissions,
  listRoles,
  updateRole,
  updateRolePermissions,
  type SystemPermission,
  type SystemRole,
} from '@/services/system';
import { roleLabel } from '@/constants/roles';
import { getRequestErrorMessage } from '@/utils/requestError';

const RolesPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [roles, setRoles] = useState<SystemRole[]>([]);
  const [permissions, setPermissions] = useState<SystemPermission[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [permOpen, setPermOpen] = useState(false);
  const [current, setCurrent] = useState<SystemRole | null>(null);
  const [checkedKeys, setCheckedKeys] = useState<React.Key[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [roleRes, permRes] = await Promise.all([listRoles(), listPermissions()]);
      if (roleRes.code === 0) setRoles(roleRes.data || []);
      else message.error(roleRes.message || '加载角色失败');
      if (permRes.code === 0) setPermissions(permRes.data || []);
      else message.error(permRes.message || '加载权限失败');
    } catch (e) {
      message.error(getRequestErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const treeData: DataNode[] = useMemo(() => {
    const grouped = new Map<string, SystemPermission[]>();
    for (const p of permissions) {
      const mod = p.module || 'OTHER';
      if (!grouped.has(mod)) grouped.set(mod, []);
      grouped.get(mod)!.push(p);
    }
    return Array.from(grouped.entries()).map(([module, items]) => ({
      key: `module:${module}`,
      title: module,
      selectable: false,
      children: items.map((p) => ({
        key: String(p.id),
        title: (
          <span>
            {p.name}{' '}
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {p.code}
            </Typography.Text>
          </span>
        ),
      })),
    }));
  }, [permissions]);

  const openEdit = (row: SystemRole) => {
    setCurrent(row);
    form.setFieldsValue({ code: row.code, name: row.name, dataScope: row.dataScope });
    setEditOpen(true);
  };

  const openPerm = (row: SystemRole) => {
    setCurrent(row);
    setCheckedKeys((row.permissionIds || []).map(String));
    setPermOpen(true);
  };

  const handleEdit = async () => {
    if (!current) return;
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const res = await updateRole(current.id, { name: values.name });
      if (res.code !== 0) {
        message.error(res.message || '保存失败');
        return;
      }
      message.success('角色已更新');
      setEditOpen(false);
      load();
    } catch (e) {
      if (e && typeof e === 'object' && 'errorFields' in e) return;
      message.error(getRequestErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSavePerms = async () => {
    if (!current) return;
    const permissionIds = checkedKeys
      .map((k) => Number(k))
      .filter((id) => Number.isFinite(id) && id > 0);
    try {
      setSubmitting(true);
      const res = await updateRolePermissions(current.id, permissionIds);
      if (res.code !== 0) {
        message.error(res.message || '保存失败');
        return;
      }
      message.success('权限已更新，相关用户缓存已失效');
      setPermOpen(false);
      load();
    } catch (e) {
      message.error(getRequestErrorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        角色管理
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        系统预置 5 个角色，不可新增/删除；角色编码只读，可编辑名称并分配权限。
      </Typography.Paragraph>
      <Table<SystemRole>
        rowKey="id"
        loading={loading}
        dataSource={roles}
        pagination={false}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 80 },
          {
            title: '编码',
            dataIndex: 'code',
            render: (code: string) => <Tag>{code}</Tag>,
          },
          {
            title: '名称',
            dataIndex: 'name',
            render: (name: string, row) => roleLabel(row.code) || name,
          },
          { title: '数据范围', dataIndex: 'dataScope' },
          {
            title: '权限数',
            dataIndex: 'permissionIds',
            width: 100,
            render: (ids?: number[]) => ids?.length ?? 0,
          },
          {
            title: '操作',
            width: 220,
            render: (_, row) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>
                  编辑
                </Button>
                <Button
                  size="small"
                  type="primary"
                  ghost
                  icon={<SafetyCertificateOutlined />}
                  onClick={() => openPerm(row)}
                >
                  分配权限
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="编辑角色"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleEdit}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="code" label="角色编码">
            <Input disabled />
          </Form.Item>
          <Form.Item
            name="name"
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="dataScope" label="数据范围">
            <Input disabled />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={current ? `分配权限：${current.name}` : '分配权限'}
        open={permOpen}
        onCancel={() => setPermOpen(false)}
        onOk={handleSavePerms}
        confirmLoading={submitting}
        width={560}
        destroyOnClose
      >
        <Tree
          checkable
          defaultExpandAll
          treeData={treeData}
          checkedKeys={checkedKeys}
          onCheck={(keys) => {
            const list = Array.isArray(keys) ? keys : keys.checked;
            setCheckedKeys(list);
          }}
        />
      </Modal>
    </>
  );
};

export default RolesPage;

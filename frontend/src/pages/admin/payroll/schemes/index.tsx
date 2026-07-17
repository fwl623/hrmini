import React, { useRef, useState } from 'react';
import { Card, Button, Modal, Form, Input, Select, DatePicker, InputNumber, Space, Popconfirm, Tag, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import { getSchemes, createScheme, updateScheme, deleteScheme } from '@/services/payroll';

const SchemePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '账套名称', dataIndex: 'name' },
    { title: '生效日期', dataIndex: 'effectiveDate' },
    { title: '状态', dataIndex: 'status', render: (v: string) =>
      <Tag color={v === 'enabled' ? 'green' : 'default'}>{v === 'enabled' ? '启用' : '停用'}</Tag>
    },
    { title: '操作', width: 120, render: (_: any, r: any) => (
      <Space>
        <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(r)}>编辑</Button>
        <Popconfirm title="确认删除？" onConfirm={() => handleDelete(r.id)}>
          <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      </Space>
    )},
  ];

  const handleAdd = () => { setEditing(null); form.resetFields(); setModalOpen(true); };
  const handleEdit = (r: any) => {
    setEditing(r);
    form.setFieldsValue({
      name: r.name, effectiveDate: r.effectiveDate ? dayjs(r.effectiveDate) : undefined, status: r.status,
    });
    setModalOpen(true);
  };
  const handleDelete = async (id: number) => {
    try { await deleteScheme(id); message.success('已删除'); actionRef.current?.reload(); }
    catch (err: any) { message.error(err?.message); }
  };
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload = { ...values, effectiveDate: values.effectiveDate?.format('YYYY-MM-DD'), items: [] };
      if (editing) { await updateScheme(editing.id, payload); message.success('更新成功'); }
      else { await createScheme(payload); message.success('创建成功'); }
      setModalOpen(false); actionRef.current?.reload();
    } catch (err: any) { if (err?.message) message.error(err.message); }
    finally { setSaving(false); }
  };

  return (
    <Card title="账套管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>新建账套</Button>}>
      <ProTable rowKey="id" columns={columns}
        request={async () => { try {
          const res = await getSchemes();
          return { data: (res.data as any)?.list || [], total: (res.data as any)?.total || 0, success: true };
        } catch { return { data: [], total: 0, success: false }; }}}
        search={false} actionRef={actionRef as any} toolBarRender={false} />
      <Modal title={editing ? '编辑账套' : '新建账套'} open={modalOpen} onOk={handleSave}
        onCancel={() => setModalOpen(false)} confirmLoading={saving} width={600}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="账套名称" rules={[{ required: true, min: 2, max: 50 }]}>
            <Input placeholder="2-50字符" />
          </Form.Item>
          <Form.Item name="effectiveDate" label="生效日期" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="enabled">
            <Select options={[{ label: '启用', value: 'enabled' }, { label: '停用', value: 'disabled' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
export default SchemePage;

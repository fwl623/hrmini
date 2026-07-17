import React, { useRef } from 'react';
import {
  Card,
  Button,
  Modal,
  Form,
  Input,
  DatePicker,
  Space,
  Popconfirm,
  message,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import { getHolidays, createHoliday, updateHoliday, deleteHoliday } from '@/services/attendance';

const HolidayPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = React.useState(false);
  const [editingHoliday, setEditingHoliday] = React.useState<any>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = React.useState(false);

  // ---------- 表格列定义 ----------
  const columns: any[] = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: '日期',
      dataIndex: 'holidayDate',
      width: 140,
      render: (val: string) => val,
    },
    { title: '名称', dataIndex: 'name', width: 200 },
    {
      title: '操作',
      width: 100,
      render: (_: any, record: any) => (
        <Space>
          <Popconfirm title="确认删除该节假日？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ---------- 事件处理 ----------

  const handleAdd = () => {
    setEditingHoliday(null);
    form.resetFields();
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteHoliday(id);
      message.success('删除成功');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);

      const payload: any = {
        holidayDate: values.holidayDate?.format('YYYY-MM-DD'),
        name: values.name,
      };

      if (editingHoliday) {
        await updateHoliday(editingHoliday.id, payload);
        message.success('更新成功');
      } else {
        await createHoliday(payload);
        message.success('创建成功');
      }

      setModalOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  // ---------- 渲染 ----------
  return (
    <Card
      title="法定节假日管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新增节假日
        </Button>
      }
    >
      <ProTable<any>
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          try {
            const res = await getHolidays({ page: current, size: pageSize });
            return {
              data: res.data?.list || [],
              total: res.data?.total || 0,
              success: true,
            };
          } catch {
            return { data: [], total: 0, success: false };
          }
        }}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        search={false}
        actionRef={actionRef as any}
        toolBarRender={false}
      />

      {/* 新增弹窗 */}
      <Modal
        title="新增节假日"
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={480}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="holidayDate"
            label="日期"
            rules={[{ required: true, message: '请选择日期' }]}
          >
            <DatePicker style={{ width: '100%' }} placeholder="选择节假日日期" />
          </Form.Item>
          <Form.Item
            name="name"
            label="名称"
            rules={[
              { required: true, message: '请输入节假日名称' },
              { min: 2, max: 50, message: '名称长度 2-50 字符' },
            ]}
          >
            <Input placeholder="例如：国庆节" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default HolidayPage;

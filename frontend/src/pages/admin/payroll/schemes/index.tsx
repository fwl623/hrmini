import React, { useRef, useState } from 'react';
import { Card, Button, Modal, Form, Input, Select, DatePicker, InputNumber, Space, Popconfirm, Tag, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, MinusCircleOutlined } from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import { getSchemes, createScheme, updateScheme, deleteScheme } from '@/services/payroll';

const ITEM_CODE_OPTIONS = [
  { label: '基本工资', value: 'BASE_PAY' },
  { label: '岗位津贴', value: 'POSITION_ALLOWANCE' },
  { label: '绩效奖金', value: 'PERFORMANCE_BONUS' },
  { label: '加班费', value: 'OVERTIME_PAY' },
  { label: '迟到扣款', value: 'LATE_DEDUCT' },
  { label: '请假扣款', value: 'LEAVE_DEDUCT' },
  { label: '养老金', value: 'PENSION' },
  { label: '医疗保险', value: 'MEDICAL' },
  { label: '失业保险', value: 'UNEMPLOYMENT' },
  { label: '住房公积金', value: 'HOUSING_FUND' },
  { label: '个税', value: 'TAX' },
];

const ITEM_TYPE_OPTIONS = [
  { label: '固定项', value: 'FIXED' },
  { label: '变量项', value: 'VARIABLE' },
  { label: '考勤扣款', value: 'ATTENDANCE_DEDUCT' },
  { label: '社保扣除', value: 'SS_DEDUCT' },
  { label: '公积金扣除', value: 'HF_DEDUCT' },
  { label: '个税', value: 'TAX' },
];

const BASE_FIELD_OPTIONS = [
  { label: '无', value: 'none' },
  { label: '社保基数', value: 'ssBase' },
  { label: '公积金基数', value: 'hfBase' },
  { label: '绩效基数', value: 'performanceBase' },
];

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
    {
      title: '状态',
      dataIndex: 'status',
      render: (v: string) => (
        <Tag color={v === 'enabled' ? 'green' : 'default'}>{v === 'enabled' ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '工资项目数',
      dataIndex: 'items',
      width: 100,
      render: (items: any[]) => (items ? items.length : 0),
    },
    {
      title: '操作',
      width: 120,
      render: (_: any, r: any) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(r)}>
            编辑
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(r.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleAdd = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const handleEdit = (r: any) => {
    setEditing(r);
    form.setFieldsValue({
      name: r.name,
      effectiveDate: r.effectiveDate ? dayjs(r.effectiveDate) : undefined,
      status: r.status,
      items: r.items && r.items.length > 0 ? r.items : [],
    });
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteScheme(id);
      message.success('已删除');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message);
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const payload = {
        name: values.name,
        effectiveDate: values.effectiveDate?.format('YYYY-MM-DD'),
        status: values.status,
        items: values.items || [],
      };
      if (editing) {
        await updateScheme(editing.id, payload);
        message.success('更新成功');
      } else {
        await createScheme(payload);
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

  return (
    <Card
      title="账套管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建账套
        </Button>
      }
    >
      <ProTable
        rowKey="id"
        columns={columns}
        request={async () => {
          try {
            const res = await getSchemes();
            return {
              data: (res.data as any)?.list || [],
              total: (res.data as any)?.total || 0,
              success: true,
            };
          } catch {
            return { data: [], total: 0, success: false };
          }
        }}
        search={false}
        actionRef={actionRef as any}
        toolBarRender={false}
      />
      <Modal
        title={editing ? '编辑账套' : '新建账套'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={900}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="账套名称" rules={[{ required: true, min: 2, max: 50 }]}>
            <Input placeholder="2-50字符" />
          </Form.Item>
          <Form.Item name="effectiveDate" label="生效日期" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="enabled">
            <Select
              options={[
                { label: '启用', value: 'enabled' },
                { label: '停用', value: 'disabled' },
              ]}
            />
          </Form.Item>

          <Card
            title="工资项目配置"
            size="small"
            style={{ marginTop: 16 }}
            extra={
              <Form.List name="items">
                {(fields, { add, remove }) => (
                  <Button
                    type="dashed"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={() =>
                      add({
                        itemCode: undefined,
                        itemName: '',
                        itemType: undefined,
                        calcRule: '',
                        baseField: 'none',
                        ratio: undefined,
                        sortOrder: fields.length + 1,
                      })
                    }
                  >
                    添加项目
                  </Button>
                )}
              </Form.List>
            }
          >
            <Form.List name="items">
              {(fields, { add, remove }) => (
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>项目编码</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>项目名称</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>项目类型</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>计算规则</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>基数类型</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>比例</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'left', fontSize: 12 }}>排序号</th>
                      <th style={{ padding: 4, borderBottom: '1px solid #f0f0f0', textAlign: 'center', fontSize: 12, width: 40 }}>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {fields.map(({ key, name, ...restField }) => (
                      <tr key={key}>
                        <td style={{ padding: 4 }}>
                          <Form.Item
                            {...restField}
                            name={[name, 'itemCode']}
                            rules={[{ required: true, message: '请选择编码' }]}
                            noStyle
                          >
                            <Select
                              options={ITEM_CODE_OPTIONS}
                              placeholder="编码"
                              style={{ width: 140 }}
                              size="small"
                            />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4 }}>
                          <Form.Item
                            {...restField}
                            name={[name, 'itemName']}
                            rules={[{ required: true, message: '请输入名称' }]}
                            noStyle
                          >
                            <Input placeholder="名称" style={{ width: 100 }} size="small" />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4 }}>
                          <Form.Item
                            {...restField}
                            name={[name, 'itemType']}
                            rules={[{ required: true, message: '请选择类型' }]}
                            noStyle
                          >
                            <Select
                              options={ITEM_TYPE_OPTIONS}
                              placeholder="类型"
                              style={{ width: 110 }}
                              size="small"
                            />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4 }}>
                          <Form.Item {...restField} name={[name, 'calcRule']} noStyle>
                            <Input placeholder="规则" style={{ width: 100 }} size="small" />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4 }}>
                          <Form.Item
                            {...restField}
                            name={[name, 'baseField']}
                            initialValue="none"
                            noStyle
                          >
                            <Select
                              options={BASE_FIELD_OPTIONS}
                              style={{ width: 110 }}
                              size="small"
                            />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4 }}>
                          <Form.Item {...restField} name={[name, 'ratio']} noStyle>
                            <InputNumber
                              placeholder="比例"
                              style={{ width: 80 }}
                              size="small"
                              min={0}
                              max={100}
                              step={0.1}
                            />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4 }}>
                          <Form.Item {...restField} name={[name, 'sortOrder']} noStyle>
                            <InputNumber
                              placeholder="排序"
                              style={{ width: 60 }}
                              size="small"
                              min={1}
                            />
                          </Form.Item>
                        </td>
                        <td style={{ padding: 4, textAlign: 'center' }}>
                          <MinusCircleOutlined
                            style={{ color: '#ff4d4f', cursor: 'pointer' }}
                            onClick={() => remove(name)}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Form.List>
          </Card>
        </Form>
      </Modal>
    </Card>
  );
};
export default SchemePage;

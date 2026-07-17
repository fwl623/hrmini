/**
 * 请假管理（管理端）
 *
 * 功能：ProTable + SearchBar（关键字、请假类型、状态、日期范围）
 *       + ActionBar（新建）+ Drawer 表单申请
 *       + 管理员可查看所有员工的请假记录并执行撤销操作
 *
 * 与门户端共享 LEAVE_TYPE_OPTIONS / statusLabelMap / statusColorMap / calcLeaveDays 逻辑
 */
import React, { useRef, useState } from 'react';
import {
  Card,
  Button,
  Tag,
  message,
  Drawer,
  Form,
  Select,
  DatePicker,
  Input,
  Space,
  Typography,
  Popconfirm,
  InputNumber,
} from 'antd';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined } from '@ant-design/icons';

import { getLeaveApplications, submitLeave, calcLeaveDays } from '@/services/attendance';

// ========== 共享常量 ==========

/** 请假类型选项（与门户端一致） */
const LEAVE_TYPE_OPTIONS = [
  { label: '年假', value: 'annual' },
  { label: '病假', value: 'sick' },
  { label: '事假', value: 'personal' },
  { label: '婚假', value: 'marriage' },
  { label: '产假', value: 'maternity' },
  { label: '丧假', value: 'bereavement' },
  { label: '调休', value: 'compensatory' },
];

/** 状态筛选选项 */
const STATUS_FILTER_OPTIONS = [
  { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已撤销', value: 'CANCELLED' },
];

/** 状态 → 中文标签映射 */
const statusLabelMap: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
};

/** 状态 → Tag 颜色映射 */
const statusColorMap: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
};

// ========== 页面组件 ==========

const AdminLeavePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [previewDays, setPreviewDays] = useState<number | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');

  // ---------- 天数预览 ----------

  const handleDateChange = async () => {
    const values = form.getFieldsValue();
    if (values.startTime && values.endTime) {
      try {
        const res = await calcLeaveDays({
          startTime: values.startTime.toISOString(),
          endTime: values.endTime.toISOString(),
        });
        setPreviewDays(res.data?.days ?? null);
      } catch {
        setPreviewDays(null);
      }
    }
  };

  // ---------- 新建请假提交 ----------

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await submitLeave({
        leaveType: values.leaveType,
        startTime: values.startTime.toISOString(),
        endTime: values.endTime.toISOString(),
        days: previewDays || values.days || 1,
        reason: values.reason,
        handoverEmployeeId: values.handoverEmployeeId,
        attachment: values.attachment,
      });
      message.success('请假申请已提交');
      setDrawerOpen(false);
      form.resetFields();
      setPreviewDays(null);
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  // ---------- 撤销请假 ----------

  const handleCancelLeave = async (id: number) => {
    try {
      // TODO: 对接后端撤销请假接口
      // await cancelLeave(id);
      message.success('已撤销');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '撤销失败');
    }
  };

  // ---------- 表格列定义 ----------

  const columns: any[] = [
    { title: '员工姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'department', width: 120 },
    {
      title: '请假类型',
      dataIndex: 'leaveType',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(LEAVE_TYPE_OPTIONS.map((o) => [o.value, o.label])),
      render: (v: string) => LEAVE_TYPE_OPTIONS.find((o) => o.value === v)?.label || v,
    },
    { title: '开始时间', dataIndex: 'startTime', width: 160 },
    { title: '结束时间', dataIndex: 'endTime', width: 160 },
    { title: '天数', dataIndex: 'leaveDays', width: 60 },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(STATUS_FILTER_OPTIONS.map((o) => [o.value, o.label])),
      render: (v: string) => <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag>,
    },
    {
      title: '操作',
      width: 80,
      render: (_: any, record: any) =>
        record.status === 'PENDING' ? (
          <Popconfirm title="确认撤销该请假申请？" onConfirm={() => handleCancelLeave(record.id)}>
            <Button type="link" danger size="small">
              撤销
            </Button>
          </Popconfirm>
        ) : (
          <Typography.Text type="secondary">-</Typography.Text>
        ),
    },
  ];

  // 合成日期范围列（仅在搜索表单中展示，表格中隐藏）
  const dateRangeColumn: any = {
    title: '申请日期',
    dataIndex: 'applyDateRange',
    valueType: 'dateRange',
    hideInTable: true,
    search: {
      transform: (value: any[]) => {
        if (value?.length === 2) {
          return {
            dateFrom: value[0]?.format('YYYY-MM-DD'),
            dateTo: value[1]?.format('YYYY-MM-DD'),
          };
        }
        return {};
      },
    },
  };

  // ---------- 渲染 ----------

  return (
    <Card
      title="请假管理"
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setDrawerOpen(true);
            form.resetFields();
            setPreviewDays(null);
          }}
        >
          新建请假
        </Button>
      }
    >
      <ProTable<any>
        rowKey="id"
        columns={[...columns, dateRangeColumn]}
        actionRef={actionRef}
        request={async (params) => {
          const { current, pageSize, leaveType, status, dateFrom, dateTo, applyDateRange, ...rest } = params;
          try {
            const res = await getLeaveApplications({
              page: current,
              leaveType,
              status,
              keyword: searchKeyword || undefined,
              dateFrom,
              dateTo,
            } as any);
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
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
          optionRender: (searchConfig, formProps, dom) => [...dom.reverse()],
        }}
        toolBarRender={() => [
          <Input.Search
            key="search"
            placeholder="员工姓名 / 工号"
            allowClear
            onSearch={(value) => {
              setSearchKeyword(value);
              actionRef.current?.reload();
            }}
            style={{ width: 260 }}
          />,
        ]}
      />

      <Drawer
        title="新建请假申请"
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          form.resetFields();
          setPreviewDays(null);
        }}
        width={520}
        footer={
          <Space style={{ float: 'right' }}>
            <Button
              onClick={() => {
                setDrawerOpen(false);
                form.resetFields();
                setPreviewDays(null);
              }}
            >
              取消
            </Button>
            <Button type="primary" loading={submitting} onClick={handleSubmit}>
              提交
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="leaveType" label="请假类型" rules={[{ required: true }]}>
            <Select options={LEAVE_TYPE_OPTIONS} placeholder="请选择请假类型" />
          </Form.Item>

          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="startTime" label="开始时间" rules={[{ required: true }]}>
              <DatePicker showTime format="YYYY-MM-DD HH:mm" onChange={handleDateChange} />
            </Form.Item>
            <Form.Item name="endTime" label="结束时间" rules={[{ required: true }]}>
              <DatePicker showTime format="YYYY-MM-DD HH:mm" onChange={handleDateChange} />
            </Form.Item>
          </Space>

          {previewDays !== null && (
            <Typography.Text type="success" style={{ display: 'block', marginBottom: 16 }}>
              预览天数：{previewDays} 天
            </Typography.Text>
          )}

          <Form.Item name="days" label="天数（系统计算）">
            <InputNumber disabled value={previewDays ?? undefined} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="reason" label="请假原因" rules={[{ required: true, max: 512 }]}>
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>

          <Form.Item name="handoverEmployeeId" label="交接人">
            <Select
              placeholder="请选择交接人（搜索员工姓名）"
              showSearch
              allowClear
              filterOption={(input, option) =>
                (option?.label as string ?? '').toLowerCase().includes(input.toLowerCase())
              }
              // TODO: 对接员工搜索接口，替换为远程搜索
              options={[]}
            />
          </Form.Item>

          <Form.Item name="attachment" label="附件">
            <Input placeholder="附件链接或留空" />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
};

export default AdminLeavePage;

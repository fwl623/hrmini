/**
 * 加班管理（管理端）
 *
 * 功能：ProTable + SearchBar（关键字、日期范围）
 *       + ActionBar（新建）+ Drawer 表单申请
 *       + 当日加班 ≥4 小时黄色 Alert 提示「将触发 HR 二审」
 *       + 管理员可查看所有员工的加班记录
 *
 * 与门户端共享 statusLabelMap / statusColorMap / submitOvertime 逻辑
 */
import React, { useRef, useState, useCallback } from 'react';
import {
  Card,
  Button,
  Tag,
  message,
  Drawer,
  Form,
  DatePicker,
  TimePicker,
  Input,
  Space,
  Alert,
  Typography,
  Modal,
  Table,
  InputNumber,
} from 'antd';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined, OrderedListOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';

import { getOvertimeApplications, submitOvertime, getOvertimeLedger } from '@/services/attendance';

// ========== 共享常量 ==========

/** 状态 → 中文标签映射（与门户端一致） */
const statusLabelMap: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
};

/** 状态 → Tag 颜色映射 */
const statusColorMap: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
};

// ========== 页面组件 ==========

const AdminOvertimePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');

  // 加班台账
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [ledgerPeriod, setLedgerPeriod] = useState(dayjs().format('YYYY-MM'));
  const [ledgerData, setLedgerData] = useState<any[]>([]);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [ledgerTotal, setLedgerTotal] = useState(0);

  const rateTypeLabel: Record<number, string> = { 15: '1.5倍(工作日)', 20: '2.0倍(休息日)', 30: '3.0倍(节假日)' };

  const loadLedger = async (page = 1, pageSize = 20) => {
    setLedgerLoading(true);
    try {
      const res = await getOvertimeLedger({ period: ledgerPeriod, page, pageSize });
      setLedgerData((res.data as any)?.list || []);
      setLedgerTotal((res.data as any)?.total || 0);
    } catch (err: any) {
      message.error(err?.message || '加载加班台账失败');
    } finally {
      setLedgerLoading(false);
    }
  };

  const ledgerColumns = [
    { title: '员工ID', dataIndex: 'employeeId', width: 80 },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'departmentName', width: 120 },
    { title: '加班日期', dataIndex: 'ledgerDate', width: 110 },
    { title: '加班时长(h)', dataIndex: 'totalHours', width: 100 },
    { title: '倍率', dataIndex: 'rateType', width: 120, render: (v: number) => rateTypeLabel[v] || v },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  ];

  // ---------- 时长计算 ----------

  const [computedHours, setComputedHours] = useState(0);
  const triggerSecondReview = computedHours >= 4;

  const recalcHours = useCallback(() => {
    const values = form.getFieldsValue();
    if (values.startTime && values.endTime) {
      const start = dayjs(values.startTime.format('HH:mm'), 'HH:mm');
      const end = dayjs(values.endTime.format('HH:mm'), 'HH:mm');
      if (end.isAfter(start)) {
        setComputedHours(Math.round(end.diff(start, 'hour', true) * 100) / 100);
      } else {
        setComputedHours(0);
      }
    } else {
      setComputedHours(0);
    }
  }, [form]);

  // ---------- 新建加班提交 ----------

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const overtimeDate = values.overtimeDate.format('YYYY-MM-DD');
      const startTime = values.startTime.format('HH:mm');
      const endTime = values.endTime.format('HH:mm');

      await submitOvertime({ overtimeDate, startTime, endTime, reason: values.reason });
      message.success('加班申请已提交');
      setDrawerOpen(false);
      form.resetFields();
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  // ---------- 表格列定义 ----------

  const columns: any[] = [
    { title: '员工姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'department', width: 120 },
    { title: '加班日期', dataIndex: 'overtimeDate', width: 120 },
    { title: '开始时间', dataIndex: 'startTime', width: 100 },
    { title: '结束时间', dataIndex: 'endTime', width: 100 },
    { title: '时长(h)', dataIndex: 'hours', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_: unknown, record: { status?: string }) => {
        const v = record.status || '';
        return <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag>;
      },
    },
  ];

  // 合成日期范围列（仅在搜索表单中展示）
  const dateRangeColumn: any = {
    title: '加班日期',
    dataIndex: 'overtimeDateRange',
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
      title="加班管理"
      extra={
        <Space>
          <Button
            icon={<OrderedListOutlined />}
            onClick={() => {
              setLedgerPeriod(dayjs().format('YYYY-MM'));
              setLedgerOpen(true);
              loadLedger();
            }}
          >
            加班台账
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setDrawerOpen(true);
              form.resetFields();
            }}
          >
            新建加班
          </Button>
        </Space>
      }
    >
      <ProTable<any>
        rowKey="id"
        columns={[...columns, dateRangeColumn]}
        actionRef={actionRef}
        request={async (params) => {
          const { current, pageSize, overtimeDateRange, dateFrom, dateTo, ...rest } = params;
          try {
            const res = await getOvertimeApplications({
              page: current,
              employeeId: 0,
              keyword: searchKeyword || undefined,
              dateFrom,
              dateTo,
            });
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
        title="新建加班申请"
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          form.resetFields();
        }}
        width={520}
        footer={
          <Space style={{ float: 'right' }}>
            <Button
              onClick={() => {
                setDrawerOpen(false);
                form.resetFields();
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
          <Form.Item name="overtimeDate" label="加班日期" rules={[{ required: true }]}>
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => d && d.isBefore(dayjs(), 'day')}
            />
          </Form.Item>

          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="startTime" label="开始时间" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" onChange={recalcHours} />
            </Form.Item>
            <Form.Item name="endTime" label="结束时间" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" onChange={recalcHours} />
            </Form.Item>
          </Space>

          <Form.Item label="时长（系统计算）">
            <Typography.Text strong style={{ fontSize: 16 }}>
              {computedHours > 0 ? `${computedHours} 小时` : '请选择开始和结束时间'}
            </Typography.Text>
          </Form.Item>

          {triggerSecondReview && (
            <Alert
              type="warning"
              showIcon
              message="将触发 HR 二审"
              description="当日加班时长 ≥ 4 小时，将进入 HR 二级审批流程。"
              style={{ marginBottom: 16 }}
            />
          )}

          <Form.Item
            name="reason"
            label="加班原因"
            rules={[{ required: true }, { max: 256, message: '原因不超过 256 个字符' }]}
          >
            <Input.TextArea rows={3} maxLength={256} showCount placeholder="请说明加班原因" />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 加班台账 Modal */}
      <Modal
        title={`加班台账 - ${ledgerPeriod}`}
        open={ledgerOpen}
        onCancel={() => setLedgerOpen(false)}
        footer={null}
        width={900}
      >
        <Space style={{ marginBottom: 16 }}>
          <Input
            placeholder="账期 YYYY-MM"
            value={ledgerPeriod}
            onChange={(e) => setLedgerPeriod(e.target.value)}
            style={{ width: 140 }}
          />
          <Button type="primary" onClick={() => loadLedger()}>查询</Button>
        </Space>
        <Table
          rowKey="id"
          columns={ledgerColumns}
          dataSource={ledgerData}
          loading={ledgerLoading}
          pagination={{
            total: ledgerTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) => loadLedger(page, pageSize),
          }}
          size="small"
        />
      </Modal>
    </Card>
  );
};

export default AdminOvertimePage;

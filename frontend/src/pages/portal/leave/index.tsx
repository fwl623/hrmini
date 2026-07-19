import React, { useState, useEffect, useCallback } from 'react';
import { Card, Row, Col, Statistic, Timeline, Tag, Typography, message, Modal, Form, Select, DatePicker, InputNumber, Input, Space, Button, Table } from 'antd';
import dayjs from 'dayjs';

import { getLeaveBalances, getLeaveApplications, submitLeave, calcLeaveDays } from '@/services/attendance';
import { LEAVE_TYPE_OPTIONS, leaveTypeLabel } from '@/constants/leave';

const statusLabelMap: Record<string, string> = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已撤销' };
const statusColorMap: Record<string, string> = { PENDING: 'orange', APPROVED: 'green', REJECTED: 'red', CANCELLED: 'default' };

const LeavePage: React.FC = () => {
  const [balances, setBalances] = useState<{ leaveType: string; balance: number }[]>([]);
  const [records, setRecords] = useState<any[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [previewDays, setPreviewDays] = useState<number | null>(null);

  const loadData = useCallback(async () => {
    try {
      const [balRes, recRes] = await Promise.all([getLeaveBalances(), getLeaveApplications({})]);
      if (balRes.data) setBalances(balRes.data);
      if (recRes.data?.list) setRecords(recRes.data.list);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const handleTypeChange = async (_: any, { startTime, endTime }: any) => {
    // 预览天数
  };

  const handleDateChange = async () => {
    const values = form.getFieldsValue();
    if (values.startTime && values.endTime) {
      try {
        const res = await calcLeaveDays({
          startTime: values.startTime.toISOString(),
          endTime: values.endTime.toISOString(),
        });
        setPreviewDays(res.data?.days ?? null);
      } catch { setPreviewDays(null); }
    }
  };

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
      });
      message.success('请假申请已提交');
      setModalOpen(false);
      form.resetFields();
      setPreviewDays(null);
      await loadData();
    } catch (err: any) { if (err?.message) message.error(err.message); }
    finally { setSubmitting(false); }
  };

  const columns = [
    { title: '类型', dataIndex: 'leaveType', render: (_: unknown, r: { leaveType?: string }) => leaveTypeLabel(r.leaveType) },
    { title: '开始', dataIndex: 'startTime' },
    { title: '结束', dataIndex: 'endTime' },
    { title: '天数', dataIndex: 'leaveDays' },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    { title: '状态', dataIndex: 'status', render: (v: string) => <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag> },
  ];

  return (
    <Row gutter={[24, 24]}>
      <Col xs={24} lg={6}>
        <Card title="假期余额">
          {balances.map(b => (
            <Statistic key={b.leaveType} title={leaveTypeLabel(b.leaveType)}
              value={b.balance} suffix="天" style={{ marginBottom: 16 }} />
          ))}
          {balances.length === 0 && <Typography.Text type="secondary">暂无余额</Typography.Text>}
        </Card>
      </Col>
      <Col xs={24} lg={18}>
        <Card title="请假记录" extra={<Button type="primary" onClick={() => setModalOpen(true)}>申请请假</Button>}>
          <Table rowKey="id" columns={columns} dataSource={records} pagination={{ pageSize: 10 }} size="small" />
        </Card>
      </Col>

      <Modal title="申请请假" open={modalOpen} onOk={handleSubmit} onCancel={() => { setModalOpen(false); form.resetFields(); setPreviewDays(null); }}
        confirmLoading={submitting} width={600} okText="提交" cancelText="取消">
        <Form form={form} layout="vertical">
          <Form.Item name="leaveType" label="请假类型" rules={[{ required: true }]}>
            <Select options={LEAVE_TYPE_OPTIONS} />
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
            <Typography.Text type="success">预览天数：{previewDays} 天</Typography.Text>
          )}
          <Form.Item name="reason" label="请假原因" rules={[{ required: true, max: 512 }]}>
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Row>
  );
};

export default LeavePage;

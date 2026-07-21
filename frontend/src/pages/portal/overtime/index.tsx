import React, { useState, useEffect, useCallback } from 'react';
import { Card, Button, Table, Tag, Typography, message, Modal, Form, DatePicker, TimePicker, Input, Space, Row, Col } from 'antd';
import dayjs from 'dayjs';

import { getOvertimeApplications, submitOvertime } from '@/services/attendance';

const statusLabelMap: Record<string, string> = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' };
const statusColorMap: Record<string, string> = { PENDING: 'orange', APPROVED: 'green', REJECTED: 'red' };

const OvertimePage: React.FC = () => {
  const [records, setRecords] = useState<any[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    try {
      const res = await getOvertimeApplications({});
      if (res.data?.list) setRecords(res.data.list);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const overtimeDate = values.overtimeDate.format('YYYY-MM-DD');
      const startTime = values.startTime.format('HH:mm');
      const endTime = values.endTime.format('HH:mm');

      await submitOvertime({ overtimeDate, startTime, endTime, reason: values.reason });
      message.success('加班申请已提交');
      setModalOpen(false);
      form.resetFields();
      await loadData();
    } catch (err: any) { if (err?.message) message.error(err.message); }
    finally { setSubmitting(false); }
  };

  const columns = [
    { title: '加班日期', dataIndex: 'overtimeDate' },
    { title: '时长(h)', dataIndex: 'hours' },
    { title: '状态', dataIndex: 'status', render: (v: string) => <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag> },
  ];

  return (
    <Row gutter={[24, 24]}>
      <Col xs={24} lg={6}>
        <Card title="加班倍率说明">
          <Typography.Paragraph>工作日加班：1.5 倍</Typography.Paragraph>
          <Typography.Paragraph>休息日加班：2.0 倍</Typography.Paragraph>
          <Typography.Paragraph>法定节假日加班：3.0 倍</Typography.Paragraph>
          <Typography.Text type="warning">单日加班 ≥4 小时将触发 HR 二审</Typography.Text>
        </Card>
      </Col>
      <Col xs={24} lg={18}>
        <Card title="加班记录" extra={<Button type="primary" onClick={() => setModalOpen(true)}>申请加班</Button>}>
          <Table rowKey="id" columns={columns} dataSource={records} pagination={{ pageSize: 10 }} size="small" />
        </Card>
      </Col>

      <Modal title="申请加班" open={modalOpen} onOk={handleSubmit} onCancel={() => { setModalOpen(false); form.resetFields(); }}
        confirmLoading={submitting} width={500}>
        <Form form={form} layout="vertical">
          <Form.Item name="overtimeDate" label="加班日期" rules={[{ required: true, message: '请选择加班日期' }]}>
            <DatePicker style={{ width: '100%' }} disabledDate={(d) => d && d.isBefore(dayjs(), 'day')} />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="startTime" label="开始时间" rules={[{ required: true, message: '请选择开始时间' }]}>
              <TimePicker format="HH:mm" />
            </Form.Item>
            <Form.Item name="endTime" label="结束时间" rules={[{ required: true, message: '请选择结束时间' }]}>
              <TimePicker format="HH:mm" />
            </Form.Item>
          </Space>
          <Form.Item name="reason" label="加班原因" rules={[{ required: true, message: '请填写加班事由' }, { max: 256, message: '事由不超过256字符' }]}>
            <Input.TextArea rows={3} maxLength={256} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Row>
  );
};

export default OvertimePage;

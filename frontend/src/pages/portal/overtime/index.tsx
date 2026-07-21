import React, { useState, useEffect, useCallback } from 'react';
import {
  Card, Button, Table, Tag, Typography, message, Modal, Form,
  DatePicker, TimePicker, Input, Space, Row, Col, Drawer, Steps, Timeline,
} from 'antd';
import { PlusOutlined, EyeOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';

import { getOvertimeApplications, submitOvertime } from '@/services/attendance';
import { fetchInstanceDetail, type ApprovalTimelineItem } from '@/services/workflow';

const statusLabelMap: Record<string, string> = {
  PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回',
};
const statusColorMap: Record<string, string> = {
  PENDING: 'orange', APPROVED: 'green', REJECTED: 'red',
};

const nodeStateToStep: Record<string, 'wait' | 'process' | 'finish' | 'error'> = {
  pending: 'wait', current: 'process', done: 'finish', cancelled: 'error',
};

const OvertimePage: React.FC = () => {
  const [records, setRecords] = useState<any[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  // 审批进度
  const [progressOpen, setProgressOpen] = useState(false);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressTitle, setProgressTitle] = useState('');
  const [progressNodes, setProgressNodes] = useState<{ order: number; label: string; state: string }[]>([]);
  const [progressTimeline, setProgressTimeline] = useState<ApprovalTimelineItem[]>([]);
  const [progressStatus, setProgressStatus] = useState('');
  const [progressCurrent, setProgressCurrent] = useState('');

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

  const handleViewProgress = async (record: any) => {
    if (!record.instanceId) {
      message.warning('该申请暂无审批实例（可能提交时审批创建失败）');
      return;
    }
    setProgressOpen(true);
    setProgressLoading(true);
    setProgressTitle(`${record.overtimeDate} · ${record.hours} 小时`);
    setProgressStatus(record.status);
    try {
      const detail = await fetchInstanceDetail(record.instanceId);
      setProgressNodes(detail?.nodes ?? []);
      setProgressTimeline(detail?.timeline ?? []);
      setProgressCurrent(detail?.currentNodeLabel || '');
      if (detail?.status) setProgressStatus(detail.status);
    } catch (err: any) {
      message.error(err?.message || '加载审批进度失败');
      setProgressNodes([]);
      setProgressTimeline([]);
    } finally {
      setProgressLoading(false);
    }
  };

  const currentStepIndex = Math.max(
    0, progressNodes.findIndex((n) => n.state === 'current'),
  );

  const columns = [
    { title: '加班日期', dataIndex: 'overtimeDate', width: 130 },
    { title: '开始', dataIndex: 'startTime', width: 80, render: (v: string) => v || '-' },
    { title: '结束', dataIndex: 'endTime', width: 80, render: (v: string) => v || '-' },
    { title: '时长(h)', dataIndex: 'hours', width: 80 },
    { title: '加班原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (v: string) => <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag>,
    },
    {
      title: '操作', width: 120,
      render: (_: any, record: any) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleViewProgress(record)}>
          审批进度
        </Button>
      ),
    },
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
        <Card title="加班记录" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>申请加班</Button>}>
          <Table
            rowKey="id" columns={columns} dataSource={records}
            pagination={{ pageSize: 10 }} size="middle" style={{ marginTop: 8 }}
          />
        </Card>
      </Col>

      <Modal title="申请加班" open={modalOpen} onOk={handleSubmit}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
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

      <Drawer title="审批进度" open={progressOpen} onClose={() => setProgressOpen(false)}
        width={420} destroyOnClose>
        {progressLoading ? (
          <Typography.Text type="secondary">加载中…</Typography.Text>
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              <Typography.Text strong>{progressTitle}</Typography.Text>
              <div style={{ marginTop: 8 }}>
                <Tag color={statusColorMap[progressStatus] || 'default'}>
                  {statusLabelMap[progressStatus] || progressStatus}
                </Tag>
                {progressCurrent ? (
                  <Typography.Text type="secondary">当前：{progressCurrent}</Typography.Text>
                ) : null}
              </div>
            </div>
            {progressNodes.length > 0 ? (
              <Steps direction="vertical" size="small"
                current={currentStepIndex >= 0 ? currentStepIndex : progressNodes.length}
                items={progressNodes.map((n) => ({
                  title: n.label,
                  status: nodeStateToStep[n.state] || 'wait',
                }))}
              />
            ) : (
              <Typography.Text type="secondary">暂无审批节点信息</Typography.Text>
            )}
            <Card size="small" title="审批动态" type="inner">
              {progressTimeline.length > 0 ? (
                <Timeline
                  items={progressTimeline.map((t, i) => ({
                    key: i, children: (
                      <>
                        <Typography.Text>{t.displayText || `${t.assignee || '-'} · ${t.action || t.node || '-'}`}</Typography.Text>
                        {t.comment ? <div><Typography.Text type="secondary">{t.comment}</Typography.Text></div> : null}
                        {t.time ? <div><Typography.Text type="secondary" style={{ fontSize: 12 }}>{t.time}</Typography.Text></div> : null}
                      </>
                    ),
                  }))}
                />
              ) : (
                <Typography.Text type="secondary">暂无审批记录</Typography.Text>
              )}
            </Card>
          </Space>
        )}
      </Drawer>
    </Row>
  );
};

export default OvertimePage;

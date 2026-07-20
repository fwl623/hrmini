/**
 * 我的请假（员工门户）
 * PRD §9.3：列表及状态 / 审批进度 / 取消申请（仅待审批）
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Steps,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import dayjs from 'dayjs';

import {
  calcLeaveDays,
  cancelMyLeave,
  getLeaveApplications,
  getLeaveBalances,
  submitLeave,
} from '@/services/attendance';
import { fetchInstanceDetail, type ApprovalTimelineItem } from '@/services/workflow';
import { LEAVE_TYPE_OPTIONS, leaveTypeLabel } from '@/constants/leave';

const statusLabelMap: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
};
const statusColorMap: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
};

const nodeStateToStep: Record<string, 'wait' | 'process' | 'finish' | 'error'> = {
  pending: 'wait',
  current: 'process',
  done: 'finish',
  cancelled: 'error',
};

/** 需附件的请假类型 */
const ATTACHMENT_REQUIRED_TYPES = ['sick', 'marriage', 'maternity'];

const LeavePage: React.FC = () => {
  const [balances, setBalances] = useState<{ leaveType: string; balance: number }[]>([]);
  const [records, setRecords] = useState<API.LeaveApplicationVO[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [previewDays, setPreviewDays] = useState<number | null>(null);
  const [selectedLeaveType, setSelectedLeaveType] = useState<string>('');
  const [needAttachment, setNeedAttachment] = useState(false);

  const [progressOpen, setProgressOpen] = useState(false);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressTitle, setProgressTitle] = useState('');
  const [progressNodes, setProgressNodes] = useState<{ order: number; label: string; state: string }[]>([]);
  const [progressTimeline, setProgressTimeline] = useState<ApprovalTimelineItem[]>([]);
  const [progressStatus, setProgressStatus] = useState('');
  const [progressCurrent, setProgressCurrent] = useState('');

  const loadData = useCallback(async () => {
    try {
      const [balRes, recRes] = await Promise.all([getLeaveBalances(), getLeaveApplications({})]);
      if (balRes.data) setBalances(balRes.data);
      if (recRes.data?.list) setRecords(recRes.data.list);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

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

  const handleTypeChange = (value: string) => {
    setSelectedLeaveType(value);
    const days = previewDays || form.getFieldValue('days') || 1;
    const needsAtt = ATTACHMENT_REQUIRED_TYPES.includes(value)
      && (value === 'sick' ? (days > 1) : true);
    setNeedAttachment(needsAtt);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      // 病假>1天 或 婚假/产假 需上传附件
      const needsAtt = ATTACHMENT_REQUIRED_TYPES.includes(values.leaveType)
        && (values.leaveType === 'sick' ? ((previewDays || values.days || 1) > 1) : true);
      if (needsAtt && !values.attachment) {
        message.warning('该请假类型需要上传证明材料');
        return;
      }
      setSubmitting(true);
      await submitLeave({
        leaveType: values.leaveType,
        startTime: values.startTime.toISOString(),
        endTime: values.endTime.toISOString(),
        days: previewDays || values.days || 1,
        reason: values.reason,
        attachment: values.attachment || undefined,
      });
      message.success('请假申请已提交');
      setModalOpen(false);
      form.resetFields();
      setPreviewDays(null);
      await loadData();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (id: number) => {
    try {
      const res = await cancelMyLeave(id);
      if (res.code !== 0) {
        message.error(res.message || '撤销失败');
        return;
      }
      message.success('已撤销');
      await loadData();
    } catch (err: any) {
      message.error(err?.message || '撤销失败');
    }
  };

  const handleViewProgress = async (record: API.LeaveApplicationVO) => {
    if (!record.instanceId) {
      message.warning('该申请暂无审批实例（可能提交时审批创建失败）');
      return;
    }
    setProgressOpen(true);
    setProgressLoading(true);
    setProgressTitle(`${leaveTypeLabel(record.leaveType)} · ${record.leaveDays} 天`);
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

  const columns = [
    {
      title: '类型',
      dataIndex: 'leaveType',
      render: (_: unknown, r: API.LeaveApplicationVO) => leaveTypeLabel(r.leaveType),
    },
    { title: '开始', dataIndex: 'startTime', width: 160 },
    { title: '结束', dataIndex: 'endTime', width: 160 },
    { title: '天数', dataIndex: 'leaveDays', width: 70 },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag>,
    },
    {
      title: '操作',
      width: 180,
      render: (_: unknown, record: API.LeaveApplicationVO) => (
        <Space>
          <Button type="link" size="small" onClick={() => handleViewProgress(record)}>
            审批进度
          </Button>
          {record.status === 'PENDING' ? (
            <Popconfirm title="确认撤销该请假申请？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" size="small" danger>
                撤销
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const currentStepIndex = Math.max(
    0,
    progressNodes.findIndex((n) => n.state === 'current'),
  );

  return (
    <Row gutter={[24, 24]}>
      <Col xs={24} lg={6}>
        <Card title="假期余额">
          {balances.map((b) => (
            <Statistic
              key={b.leaveType}
              title={leaveTypeLabel(b.leaveType)}
              value={b.balance}
              suffix="天"
              style={{ marginBottom: 16 }}
            />
          ))}
          {balances.length === 0 && <Typography.Text type="secondary">暂无余额</Typography.Text>}
        </Card>
      </Col>
      <Col xs={24} lg={18}>
        <Card
          title="请假记录"
          extra={
            <Button type="primary" onClick={() => setModalOpen(true)}>
              申请请假
            </Button>
          }
        >
          <Table
            rowKey="id"
            columns={columns}
            dataSource={records}
            pagination={{ pageSize: 10 }}
            size="small"
          />
        </Card>
      </Col>

      <Modal
        title="申请请假"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
          setPreviewDays(null);
        }}
        confirmLoading={submitting}
        width={600}
        okText="提交"
        cancelText="取消"
      >
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
          {needAttachment && (
            <Alert
              type="warning"
              showIcon
              message="该请假类型需上传证明材料"
              description="病假超过1天需上传医院证明，婚假需结婚证，产假需医院证明"
              style={{ marginBottom: 16 }}
            />
          )}
          <Form.Item name="attachment" label="证明材料（附件URL）"
            rules={needAttachment ? [{ required: true, message: '该请假类型需上传证明材料' }] : []}
          >
            <Input placeholder="请输入附件URL（或使用上传组件）" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="审批进度"
        open={progressOpen}
        onClose={() => setProgressOpen(false)}
        width={420}
        destroyOnClose
      >
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
              <Steps
                direction="vertical"
                size="small"
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
                    key: i,
                    children: (
                      <>
                        <Typography.Text>
                          {t.displayText || `${t.assignee || '-'} · ${t.action || t.node || '-'}`}
                        </Typography.Text>
                        {t.comment ? (
                          <div>
                            <Typography.Text type="secondary">{t.comment}</Typography.Text>
                          </div>
                        ) : null}
                        {t.time ? (
                          <div>
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              {t.time}
                            </Typography.Text>
                          </div>
                        ) : null}
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

export default LeavePage;

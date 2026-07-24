/**
 * 我的加班（员工门户）
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Row,
  Space,
  Table,
  Tag,
  TimePicker,
  Typography,
  message,
} from 'antd';
import { EyeOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

import { getOvertimeApplications, submitOvertime } from '@/services/attendance';
import { fetchInstanceDetail, type ApprovalTimelineItem } from '@/services/workflow';
import ApprovalProgressDrawer, {
  type ApprovalProgressNode,
} from '@/components/ApprovalProgressDrawer';
import '../attendance-self.less';

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'orange' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
};

function statusMeta(code?: string) {
  const key = String(code || '').trim().toUpperCase();
  return STATUS_META[key] || { label: code || '-', color: 'default' };
}

function formatDate(value?: string) {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD') : value;
}

function formatTime(value?: string) {
  if (!value) return '-';
  if (/^\d{1,2}:\d{2}/.test(value)) return value.slice(0, 5);
  const d = dayjs(value);
  return d.isValid() ? d.format('HH:mm') : value;
}

const OvertimePage: React.FC = () => {
  const [records, setRecords] = useState<API.OvertimeApplicationVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const [progressOpen, setProgressOpen] = useState(false);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressTitle, setProgressTitle] = useState('');
  const [progressNodes, setProgressNodes] = useState<ApprovalProgressNode[]>([]);
  const [progressTimeline, setProgressTimeline] = useState<ApprovalTimelineItem[]>([]);
  const [progressStatus, setProgressStatus] = useState('');
  const [progressCurrent, setProgressCurrent] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getOvertimeApplications({});
      if (res.data?.list) setRecords(res.data.list);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

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
    } catch (err: any) {
      if (err?.errorFields) return;
      if (err?.message) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleViewProgress = async (record: API.OvertimeApplicationVO) => {
    if (!record.instanceId) {
      message.warning('该申请暂无审批实例（可能提交时审批创建失败）');
      return;
    }
    setProgressOpen(true);
    setProgressLoading(true);
    setProgressTitle(`${formatDate(record.overtimeDate)} · ${record.hours ?? '-'} 小时`);
    setProgressStatus(record.status);
    setProgressCurrent('');
    try {
      const detail = await fetchInstanceDetail(record.instanceId);
      setProgressNodes((detail?.nodes ?? []) as ApprovalProgressNode[]);
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

  const columns: ColumnsType<API.OvertimeApplicationVO> = [
    {
      title: '加班时段',
      dataIndex: 'overtimeDate',
      width: 168,
      render: (_, r) => (
        <div className="portal-att-range">
          <span className="portal-att-range__date">{formatDate(r.overtimeDate)}</span>
          <span className="portal-att-range__time">
            {formatTime(r.startTime)} — {formatTime(r.endTime)}
          </span>
        </div>
      ),
    },
    {
      title: '时长',
      dataIndex: 'hours',
      width: 88,
      align: 'right',
      render: (v) => (
        <span className="portal-att-days">
          {v ?? '-'}
          <span className="portal-att-days__unit">小时</span>
        </span>
      ),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      ellipsis: { showTitle: true },
      render: (v?: string) => (
        <Typography.Text type={v ? undefined : 'secondary'} ellipsis={{ tooltip: v }}>
          {v || '未填写'}
        </Typography.Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const meta = statusMeta(v);
        return (
          <Tag className="portal-att-status" color={meta.color}>
            {meta.label}
          </Tag>
        );
      },
    },
    {
      title: '操作',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewProgress(record)}
        >
          进度
        </Button>
      ),
    },
  ];

  return (
    <div className="portal-att-page">
      <header className="portal-att-hero">
        <div>
          <h1>我的加班</h1>
          <p>提交加班申请、查看记录与审批进度；单日满 4 小时将触发 HR 二审。</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          申请加班
        </Button>
      </header>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Card className="portal-att-side" title="加班倍率说明">
            <ul className="portal-att-rate-list">
              <li>
                <span>工作日加班</span>
                <strong>1.5 倍</strong>
              </li>
              <li>
                <span>休息日加班</span>
                <strong>2.0 倍</strong>
              </li>
              <li>
                <span>法定节假日</span>
                <strong>3.0 倍</strong>
              </li>
            </ul>
            <div className="portal-att-rate-tip">单日加班 ≥ 4 小时将触发 HR 二审</div>
          </Card>
        </Col>
        <Col xs={24} lg={18}>
          <Card className="portal-att-main" title="加班记录">
            <Table
              rowKey="id"
              columns={columns}
              dataSource={records}
              loading={loading}
              pagination={{
                pageSize: 10,
                showTotal: (t) => `共 ${t} 条`,
                style: { padding: '16px 20px' },
              }}
              scroll={{ x: 760 }}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title="申请加班"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        confirmLoading={submitting}
        width={500}
        okText="提交"
        cancelText="取消"
      >
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
          <Form.Item
            name="reason"
            label="加班原因"
            rules={[
              { required: true, message: '请填写加班事由' },
              { max: 256, message: '事由不超过256字符' },
            ]}
          >
            <Input.TextArea rows={3} maxLength={256} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <ApprovalProgressDrawer
        open={progressOpen}
        loading={progressLoading}
        title={progressTitle}
        status={progressStatus}
        currentNodeLabel={progressCurrent}
        nodes={progressNodes}
        timeline={progressTimeline}
        onClose={() => setProgressOpen(false)}
      />
    </div>
  );
};

export default OvertimePage;

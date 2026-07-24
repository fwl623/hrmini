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
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import { PlusOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile, UploadProps } from 'antd/es/upload/interface';
import dayjs from 'dayjs';

import {
  calcLeaveDays,
  cancelMyLeave,
  getLeaveApplications,
  getLeaveBalances,
  submitLeave,
} from '@/services/attendance';
import { uploadFile } from '@/services/file';
import { fetchInstanceDetail, type ApprovalTimelineItem } from '@/services/workflow';
import { LEAVE_TYPE_OPTIONS, leaveTypeLabel } from '@/constants/leave';
import ApprovalProgressDrawer, {
  type ApprovalProgressNode,
} from '@/components/ApprovalProgressDrawer';
import '../attendance-self.less';

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'orange' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
  CANCELLED: { label: '已撤销', color: 'default' },
};

const TYPE_COLOR: Record<string, string> = {
  ANNUAL: 'blue',
  SICK: 'magenta',
  PERSONAL: 'gold',
  MARRIAGE: 'purple',
  MATERNITY: 'pink',
  BEREAVEMENT: 'default',
  COMP_OFF: 'cyan',
  COMPENSATORY: 'cyan',
};

const ATTACHMENT_REQUIRED_TYPES = ['SICK', 'MARRIAGE', 'MATERNITY'];
const ACCEPT_TYPES =
  'image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,image/jpeg,image/png,image/gif,image/webp,image/bmp';
const MAX_FILE_SIZE = 10 * 1024 * 1024;

function statusMeta(code?: string) {
  const key = String(code || '').trim().toUpperCase();
  return STATUS_META[key] || { label: code || '-', color: 'default' };
}

function formatDateTime(value?: string) {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : value;
}

const LeavePage: React.FC = () => {
  const [balances, setBalances] = useState<{ leaveType: string; balance: number }[]>([]);
  const [records, setRecords] = useState<API.LeaveApplicationVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [previewDays, setPreviewDays] = useState<number | null>(null);
  const [needAttachment, setNeedAttachment] = useState(false);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const [progressOpen, setProgressOpen] = useState(false);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressTitle, setProgressTitle] = useState('');
  const [progressNodes, setProgressNodes] = useState<ApprovalProgressNode[]>([]);
  const [progressTimeline, setProgressTimeline] = useState<ApprovalTimelineItem[]>([]);
  const [progressStatus, setProgressStatus] = useState('');
  const [progressCurrent, setProgressCurrent] = useState('');

  const resetModal = () => {
    form.resetFields();
    setPreviewDays(null);
    setNeedAttachment(false);
    setFileList([]);
  };

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [balRes, recRes] = await Promise.all([getLeaveBalances(), getLeaveApplications({})]);
      if (balRes.data) setBalances(balRes.data);
      if (recRes.data?.list) setRecords(recRes.data.list);
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
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
        const leaveType = values.leaveType as string | undefined;
        if (leaveType) {
          const days = res.data?.days ?? 1;
          const needsAtt =
            ATTACHMENT_REQUIRED_TYPES.includes(leaveType) &&
            (leaveType === 'SICK' ? days > 1 : true);
          setNeedAttachment(needsAtt);
        }
      } catch {
        setPreviewDays(null);
      }
    }
  };

  const handleTypeChange = (value: string) => {
    const days = previewDays || form.getFieldValue('days') || 1;
    const needsAtt =
      ATTACHMENT_REQUIRED_TYPES.includes(value) && (value === 'SICK' ? days > 1 : true);
    setNeedAttachment(needsAtt);
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    const name = file.name.toLowerCase();
    const allowedExt = /\.(jpe?g|png|gif|webp|bmp|pdf|docx?|xlsx?|txt)$/i.test(name);
    const isImage = (file.type || '').startsWith('image/');
    if (!allowedExt && !isImage) {
      message.error('仅支持上传图片或文件（jpg/png/pdf/doc/docx/xls/xlsx 等）');
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_FILE_SIZE) {
      message.error('文件大小不能超过 10MB');
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const handleUploadChange: UploadProps['onChange'] = ({ fileList: next }) => {
    setFileList(next.slice(-1));
    const file = next[0];
    if (!file || file.status === 'removed') {
      form.setFieldsValue({ attachment: undefined });
      return;
    }
    if (file.status === 'done') {
      const url = (file.response as { url?: string } | undefined)?.url;
      form.setFieldsValue({ attachment: url });
      if (url) {
        form.validateFields(['attachment']).catch(() => undefined);
      }
    }
    if (file.status === 'error') {
      form.setFieldsValue({ attachment: undefined });
    }
  };

  const customRequest: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess, onError } = options;
    try {
      const res = await uploadFile(file as File);
      if (res.code !== 0 || !res.data?.url) {
        throw new Error(res.message || '上传失败');
      }
      onSuccess?.(res.data);
      message.success('证明材料上传成功');
    } catch (e) {
      message.error((e as Error).message || '上传失败');
      onError?.(e as Error);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const needsAtt =
        ATTACHMENT_REQUIRED_TYPES.includes(values.leaveType) &&
        (values.leaveType === 'SICK' ? (previewDays || values.days || 1) > 1 : true);
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
      resetModal();
      await loadData();
    } catch (err: any) {
      if (err?.errorFields) return;
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (id: number) => {
    try {
      const res = await cancelMyLeave(id);
      if (!res || res.code !== 0) {
        message.error(res?.message || '撤销失败');
        return;
      }
      message.success('已撤销');
      await loadData();
    } catch (err: any) {
      if (err?.name !== 'BizError') {
        message.error(err?.message || '撤销失败');
      }
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

  const columns: ColumnsType<API.LeaveApplicationVO> = [
    {
      title: '类型',
      dataIndex: 'leaveType',
      width: 100,
      render: (_, r) => {
        const code = String(r.leaveType || '').toUpperCase();
        return (
          <Tag className="portal-att-type" color={TYPE_COLOR[code] || 'processing'}>
            {leaveTypeLabel(r.leaveType)}
          </Tag>
        );
      },
    },
    {
      title: '请假时段',
      dataIndex: 'startTime',
      width: 180,
      render: (_, r) => (
        <div className="portal-att-range">
          <span className="portal-att-range__date">{formatDateTime(r.startTime)}</span>
          <span className="portal-att-range__time">至 {formatDateTime(r.endTime)}</span>
        </div>
      ),
    },
    {
      title: '天数',
      dataIndex: 'leaveDays',
      width: 80,
      align: 'right',
      render: (v) => (
        <span className="portal-att-days">
          {v ?? '-'}
          <span className="portal-att-days__unit">天</span>
        </span>
      ),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      ellipsis: { showTitle: true },
      render: (v: string) => (
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
      width: 160,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => handleViewProgress(record)}>
            进度
          </Button>
          {String(record.status).toUpperCase() === 'PENDING' ? (
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

  return (
    <div className="portal-att-page">
      <header className="portal-att-hero">
        <div>
          <h1>我的请假</h1>
          <p>查看假期余额与请假记录，提交申请并跟踪审批进度。</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          申请请假
        </Button>
      </header>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={6}>
          <Card className="portal-att-side" title="假期余额">
            <div className="portal-att-balance">
              {balances.map((b) => (
                <div key={b.leaveType} className="portal-att-balance__item">
                  <Statistic title={leaveTypeLabel(b.leaveType)} value={b.balance} suffix="天" />
                </div>
              ))}
              {balances.length === 0 ? (
                <Typography.Text type="secondary">暂无余额</Typography.Text>
              ) : null}
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={18}>
          <Card className="portal-att-main" title="请假记录">
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
              scroll={{ x: 860 }}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title="申请请假"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          resetModal();
        }}
        confirmLoading={submitting}
        width={600}
        okText="提交"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="leaveType" label="请假类型" rules={[{ required: true, message: '请选择请假类型' }]}>
            <Select options={LEAVE_TYPE_OPTIONS} onChange={handleTypeChange} />
          </Form.Item>
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="startTime" label="开始时间" rules={[{ required: true, message: '请选择开始时间' }]}>
              <DatePicker
                showTime
                format="YYYY-MM-DD HH:mm"
                onChange={handleDateChange}
                disabledDate={(d) => d && d.isBefore(dayjs(), 'day')}
              />
            </Form.Item>
            <Form.Item name="endTime" label="结束时间" rules={[{ required: true, message: '请选择结束时间' }]}>
              <DatePicker
                showTime
                format="YYYY-MM-DD HH:mm"
                onChange={handleDateChange}
                disabledDate={(d) => d && d.isBefore(dayjs(), 'day')}
              />
            </Form.Item>
          </Space>
          {previewDays !== null ? (
            <Typography.Text type="success">预览天数：{previewDays} 天</Typography.Text>
          ) : null}
          <Form.Item
            name="reason"
            label="请假原因"
            rules={[
              { required: true, message: '请填写请假事由' },
              { max: 512, message: '事由不超过512字符' },
            ]}
          >
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
          {needAttachment ? (
            <Alert
              type="warning"
              showIcon
              message="该请假类型需上传证明材料"
              description="病假超过1天需上传医院证明，婚假需结婚证，产假需医院证明"
              style={{ marginBottom: 16 }}
            />
          ) : null}
          <Form.Item
            name="attachment"
            rules={needAttachment ? [{ required: true, message: '请上传证明材料' }] : []}
            hidden
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="证明材料"
            required={needAttachment}
            extra="仅支持从本机上传图片或文件，单文件不超过 10MB"
          >
            <Upload
              accept={ACCEPT_TYPES}
              listType="picture"
              maxCount={1}
              fileList={fileList}
              beforeUpload={beforeUpload}
              customRequest={customRequest}
              onChange={handleUploadChange}
            >
              <Button icon={<UploadOutlined />}>上传本机材料</Button>
            </Upload>
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

export default LeavePage;

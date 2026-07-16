import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  createResignation,
  fetchResignationRequests,
  fetchResignationStats,
  fetchResignations,
  type ResignationItem,
  type ResignationRequestItem,
  type ResignationStats,
} from '@/services/lifecycle';

/**
 * HR 离职管理：双 Tab — 员工申请 / 正式离职
 */
export default function AdminResignationPage() {
  const [tab, setTab] = useState('requests');
  const [requests, setRequests] = useState<ResignationRequestItem[]>([]);
  const [resignations, setResignations] = useState<ResignationItem[]>([]);
  const [stats, setStats] = useState<ResignationStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [selectedReq, setSelectedReq] = useState<ResignationRequestItem | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [r, s, st] = await Promise.all([
        fetchResignationRequests({ page: 1, pageSize: 50 }),
        fetchResignations({ page: 1, pageSize: 50 }),
        fetchResignationStats(),
      ]);
      setRequests(r?.list ?? []);
      setResignations(s?.list ?? []);
      setStats(st ?? null);
    } catch (e) {
      message.error((e as Error)?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const reqCols: ColumnsType<ResignationRequestItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '期望离职日', dataIndex: 'expectedResignDate', width: 120 },
    { title: '类型', dataIndex: 'resignationType', width: 120 },
    { title: '状态', dataIndex: 'status', width: 120 },
    {
      title: '操作',
      width: 140,
      render: (_, row) =>
        row.status === 'APPROVED' ? (
          <Button
            type="link"
            onClick={() => {
              setSelectedReq(row);
              form.resetFields();
              form.setFieldsValue({
                employeeId: row.employeeId,
                requestId: row.id,
                resignationDate: dayjs(row.expectedResignDate),
                reasonCategory: row.reasonCategory,
                resignationType: row.resignationType,
                reasonDetail: row.reasonDetail,
              });
              setOpen(true);
            }}
          >
            发起正式离职
          </Button>
        ) : (
          '-'
        ),
    },
  ];

  const resignCols: ColumnsType<ResignationItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '关联申请', dataIndex: 'requestId', width: 100 },
    { title: '离职日', dataIndex: 'resignationDate', width: 120 },
    { title: '状态', dataIndex: 'status', width: 140 },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        离职管理
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        待审申请 {stats?.pendingRequest ?? '-'} · 正式离职审批中 {stats?.approving ?? '-'} · 待离职{' '}
        {stats?.pendingResign ?? '-'} · 本月已离职 {stats?.resignedThisMonth ?? '-'}
      </Typography.Paragraph>
      <Card loading={loading}>
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            {
              key: 'requests',
              label: '员工申请',
              children: (
                <Table rowKey="id" columns={reqCols} dataSource={requests} pagination={false} />
              ),
            },
            {
              key: 'resignations',
              label: '正式离职',
              children: (
                <Table rowKey="id" columns={resignCols} dataSource={resignations} pagination={false} />
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="HR 发起正式离职"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            await createResignation({
              employeeId: v.employeeId,
              requestId: v.requestId,
              resignationDate: v.resignationDate.format('YYYY-MM-DD'),
              reasonCategory: v.reasonCategory,
              resignationType: v.resignationType,
              reasonDetail: v.reasonDetail,
              handoverEmployeeId: v.handoverEmployeeId,
            });
            message.success('已发起正式离职审批');
            setOpen(false);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '提交失败');
          }
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="employeeId" label="员工 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} disabled />
          </Form.Item>
          <Form.Item name="requestId" label="关联申请 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} disabled />
          </Form.Item>
          <Form.Item name="resignationDate" label="离职日" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reasonCategory" label="原因分类" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'VOLUNTARY', label: '自愿' },
                { value: 'INVOLUNTARY', label: '非自愿' },
                { value: 'NEGOTIATED', label: '协商' },
              ]}
            />
          </Form.Item>
          <Form.Item name="resignationType" label="离职类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'resignation', label: '辞职' },
                { value: 'dismissal', label: '辞退' },
                { value: 'contract_expiry', label: '合同到期' },
                { value: 'other', label: '其他' },
              ]}
            />
          </Form.Item>
          <Form.Item name="handoverEmployeeId" label="交接人员工 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reasonDetail" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
        {selectedReq ? null : null}
      </Modal>
    </Space>
  );
}

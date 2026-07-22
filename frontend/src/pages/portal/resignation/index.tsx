/**
 * 【模块说明 · 门户离职申请】路由 /portal/resignation（双通道第一阶段）
 *
 * 干什么：员工本人登记离职意向（期望离职日、类型、原因）；查看与撤销本人记录。
 *         仅落库 PENDING，不创建审批实例；HR 在 admin/resignation 看到后发起正式离职。
 *
 * 主要状态：
 * - list / loading：本人申请记录 Table
 * - form：登记表单（有 PENDING/APPROVED 进行中申请时禁用重复提交）
 *
 * 调哪些 API（@/services/lifecycle）：
 * - fetchMyResignationRequests、createMyResignationRequest、cancelMyResignationRequest
 */
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import ProcessStatusTag from '@/components/ProcessStatusTag';
import {
  resignationReasonLabel,
  resignationTypeLabel,
} from '@/constants/workflow';
import {
  cancelMyResignationRequest,
  createMyResignationRequest,
  fetchMyResignationRequests,
  type ResignationRequestItem,
} from '@/services/lifecycle';

export default function PortalResignationPage() {
  const [list, setList] = useState<ResignationRequestItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const hasActiveRequest = list.some(
    (r) => r.status === 'PENDING' || r.status === 'APPROVED',
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchMyResignationRequests({ page: 1, pageSize: 50 });
      setList(data?.list ?? []);
    } catch (e) {
      message.error((e as Error)?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const columns: ColumnsType<ResignationRequestItem> = [
    { title: '期望离职日', dataIndex: 'expectedResignDate', width: 140 },
    {
      title: '类型',
      dataIndex: 'resignationType',
      width: 120,
      render: (v: string) => resignationTypeLabel(v),
    },
    {
      title: '原因分类',
      dataIndex: 'reasonCategory',
      width: 120,
      render: (v: string) => resignationReasonLabel(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <ProcessStatusTag status={s} />,
    },
    { title: '提交时间', dataIndex: 'createdAt', width: 180 },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        row.status === 'PENDING' ? (
          <Button
            type="link"
            danger
            onClick={async () => {
              try {
                await cancelMyResignationRequest(row.id);
                message.success('已撤销');
                load();
              } catch (e) {
                message.error((e as Error)?.message || '撤销失败');
              }
            }}
          >
            撤销
          </Button>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        我的离职申请
      </Typography.Title>
      <Card
        title="提交申请"
        extra={
          hasActiveRequest ? (
            <Typography.Text type="secondary">已有进行中的离职申请，不可重复提交</Typography.Text>
          ) : null
        }
      >
        <Form
          form={form}
          layout="vertical"
          disabled={hasActiveRequest}
          onFinish={async (v) => {
            try {
              await createMyResignationRequest({
                expectedResignDate: v.expectedResignDate.format('YYYY-MM-DD'),
                reasonCategory: v.reasonCategory,
                resignationType: v.resignationType,
                reasonDetail: v.reasonDetail,
              });
              message.success('已提交，等待 HR 发起正式离职');
              form.resetFields();
              load();
            } catch (e) {
              message.error((e as Error)?.message || '提交失败');
            }
          }}
        >
          <Form.Item
            name="expectedResignDate"
            label="期望离职日"
            rules={[{ required: true, message: '请选择期望离职日' }]}
            initialValue={dayjs().add(30, 'day')}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="reasonCategory"
            label="原因分类"
            rules={[{ required: true, message: '请选择原因分类' }]}
          >
            <Select
              options={[
                { value: 'VOLUNTARY', label: '自愿' },
                { value: 'INVOLUNTARY', label: '非自愿' },
                { value: 'NEGOTIATED', label: '协商' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="resignationType"
            label="离职类型"
            rules={[{ required: true, message: '请选择离职类型' }]}
          >
            <Select
              options={[
                { value: 'resignation', label: '辞职' },
                { value: 'dismissal', label: '辞退' },
                { value: 'contract_expiry', label: '合同到期' },
                { value: 'other', label: '其他' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reasonDetail" label="说明">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Button type="primary" htmlType="submit" disabled={hasActiveRequest}>
            提交申请
          </Button>
        </Form>
      </Card>
      <Card title="我的申请记录" loading={loading}>
        <Table rowKey="id" columns={columns} dataSource={list} pagination={false} />
      </Card>
    </Space>
  );
}

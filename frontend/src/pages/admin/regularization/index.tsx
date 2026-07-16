import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import {
  createRegularization,
  fetchPendingRegularization,
  fetchRegularizationList,
  type PendingRegularizationItem,
  type RegularizationItem,
} from '@/services/lifecycle';

/**
 * 转正管理：待转正列表 + 发起转正（PASS / EXTEND / FAIL）
 */
export default function RegularizationPage() {
  const [pending, setPending] = useState<PendingRegularizationItem[]>([]);
  const [records, setRecords] = useState<RegularizationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState<PendingRegularizationItem | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [p, r] = await Promise.all([
        fetchPendingRegularization(),
        fetchRegularizationList({ page: 1, pageSize: 50 }),
      ]);
      setPending(p);
      setRecords(r?.list ?? []);
    } catch (e) {
      message.error((e as Error)?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const pendingCols: ColumnsType<PendingRegularizationItem> = [
    { title: '工号', dataIndex: 'empNo', width: 120 },
    { title: '姓名', dataIndex: 'name' },
    { title: '入职日', dataIndex: 'hireDate', width: 120 },
    { title: '试用结束日', dataIndex: 'probationEndDate', width: 120 },
    {
      title: '操作',
      width: 120,
      render: (_, row) => (
        <Button
          type="link"
          onClick={() => {
            setCurrent(row);
            form.resetFields();
            form.setFieldsValue({ approvalResult: 'PASS', employeeId: row.employeeId });
            setOpen(true);
          }}
        >
          发起转正
        </Button>
      ),
    },
  ];

  const recordCols: ColumnsType<RegularizationItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '结果', dataIndex: 'approvalResult', width: 100 },
    { title: '状态', dataIndex: 'status', width: 120 },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        转正管理
      </Typography.Title>
      <Card title="待转正（试用结束前 7 天）" loading={loading}>
        <Table rowKey="employeeId" columns={pendingCols} dataSource={pending} pagination={false} />
      </Card>
      <Card title="转正记录" loading={loading}>
        <Table rowKey="id" columns={recordCols} dataSource={records} pagination={false} />
      </Card>

      <Modal
        title={`发起转正${current ? ` — ${current.name}` : ''}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const values = await form.validateFields();
            await createRegularization({
              employeeId: current!.employeeId,
              performanceEvaluation: values.performanceEvaluation,
              salaryAdjustment: values.salaryAdjustment,
              approvalResult: values.approvalResult,
              extendMonths: values.extendMonths,
            });
            message.success('已提交转正审批');
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
          <Form.Item name="approvalResult" label="转正结果" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio.Button value="PASS">通过</Radio.Button>
              <Radio.Button value="EXTEND">延长</Radio.Button>
              <Radio.Button value="FAIL">不通过</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.approvalResult !== c.approvalResult}>
            {({ getFieldValue }) =>
              getFieldValue('approvalResult') === 'EXTEND' ? (
                <Form.Item
                  name="extendMonths"
                  label="延长月数"
                  rules={[{ required: true, message: '请填写延长月数' }]}
                >
                  <InputNumber min={1} max={12} style={{ width: '100%' }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="performanceEvaluation"
            label="试用期表现评价"
            rules={[{ required: true, message: '请填写评价' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="salaryAdjustment" label="调薪金额（可选）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

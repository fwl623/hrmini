import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Steps,
  Table,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  createTransfer,
  fetchTransferDetail,
  fetchTransfers,
  type TransferItem,
} from '@/services/lifecycle';

/**
 * 调岗管理：列表 + 发起 + 详情三节点
 * 部门选择：依赖 A 同学部门树选择器（/departments/tree），当前用 InputNumber 暂代 departmentId。
 */
export default function TransfersPage() {
  const [list, setList] = useState<TransferItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<TransferItem | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchTransfers({ page: 1, pageSize: 50 });
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

  const columns: ColumnsType<TransferItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '原部门', dataIndex: 'fromDepartmentId', width: 100 },
    { title: '新部门', dataIndex: 'newDepartmentId', width: 100 },
    { title: '生效日', dataIndex: 'effectiveDate', width: 120 },
    { title: '状态', dataIndex: 'status', width: 120 },
    {
      title: '操作',
      width: 100,
      render: (_, row) => (
        <Button
          type="link"
          onClick={async () => {
            try {
              const d = await fetchTransferDetail(row.id);
              setDetail(d ?? row);
            } catch {
              setDetail(row);
            }
          }}
        >
          详情
        </Button>
      ),
    },
  ];

  const stepStatus = (s?: string): 'wait' | 'process' | 'finish' | 'error' => {
    if (s === 'finish') return 'finish';
    if (s === 'process') return 'process';
    if (s === 'error') return 'error';
    return 'wait';
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          调岗管理
        </Typography.Title>
        <Button type="primary" onClick={() => { form.resetFields(); setOpen(true); }}>
          发起调岗
        </Button>
      </Space>
      <Card loading={loading}>
        <Table rowKey="id" columns={columns} dataSource={list} pagination={false} />
      </Card>

      <Modal
        title="发起调岗"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            await createTransfer({
              employeeId: v.employeeId,
              newDepartmentId: v.newDepartmentId,
              newPositionId: v.newPositionId,
              newJobLevel: v.newJobLevel,
              newManagerId: v.newManagerId,
              salaryAdjustment: v.salaryAdjustment,
              effectiveDate: v.effectiveDate.format('YYYY-MM-DD'),
              reason: v.reason,
            });
            message.success('已提交调岗审批（原部门→新部门→HR）');
            setOpen(false);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '提交失败');
          }
        }}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="employeeId" label="员工 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          {/* 依赖 A 同学部门树选择器（/departments/tree），当前暂用部门 ID 输入 */}
          <Form.Item
            name="newDepartmentId"
            label="新部门 ID"
            rules={[{ required: true, message: '新部门必须变更，否则 30004' }]}
            extra="须与原部门不同，否则后端返回 30004"
          >
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="newPositionId" label="新职位 ID（可选）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="newJobLevel" label="新职级（可选）">
            <Input />
          </Form.Item>
          <Form.Item name="newManagerId" label="新汇报人 ID（可选）">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="effectiveDate" label="生效日期" rules={[{ required: true }]} initialValue={dayjs()}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="调岗原因" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="调岗详情 · 三节点审批"
        open={!!detail}
        onClose={() => setDetail(null)}
        width={480}
      >
        {detail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              {detail.employeeName} · 状态 {detail.status}
            </div>
            <div>
              部门 {detail.fromDepartmentId} → {detail.newDepartmentId}
            </div>
            <div>原因：{detail.reason}</div>
            <Steps
              direction="vertical"
              items={(detail.nodes ?? [
                { order: 1, label: '原部门确认', status: 'process' },
                { order: 2, label: '新部门接收', status: 'wait' },
                { order: 3, label: 'HR 备案', status: 'wait' },
              ]).map((n) => ({
                title: n.label,
                status: stepStatus(n.status),
              }))}
            />
          </Space>
        )}
      </Drawer>
    </Space>
  );
}

import { Button, Card, DatePicker, Form, Input, InputNumber, Modal, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  cancelDelegation,
  createDelegation,
  fetchDelegations,
  type DelegationItem,
} from '@/services/workflow';

/**
 * 委托管理简版：列表 + 新增 + 取消
 */
export default function DelegationPage() {
  const [list, setList] = useState<DelegationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchDelegations({ page: 1, pageSize: 50 });
      setList(data?.list ?? []);
    } catch (e) {
      message.error((e as Error)?.message || '加载委托失败');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const columns: ColumnsType<DelegationItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '被委托人', dataIndex: 'delegateUserName', width: 140 },
    { title: '被委托人ID', dataIndex: 'delegateUserId', width: 120 },
    { title: '开始', dataIndex: 'startDate', width: 120 },
    { title: '结束', dataIndex: 'endDate', width: 120 },
    { title: '原因', dataIndex: 'reason' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (s: string) => (
        <Tag color={s === 'ACTIVE' ? 'processing' : 'default'}>{s}</Tag>
      ),
    },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        row.status === 'ACTIVE' ? (
          <Button
            type="link"
            danger
            onClick={async () => {
              try {
                await cancelDelegation(row.id);
                message.success('已取消委托');
                load();
              } catch (e) {
                message.error((e as Error)?.message || '取消失败');
              }
            }}
          >
            取消
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            审批委托
          </Typography.Title>
          <Typography.Text type="secondary">
            同一委托人同时仅 1 条 ACTIVE（冲突码 60003）；开发期 X-User-Id=1002
          </Typography.Text>
        </div>
        <Button
          type="primary"
          onClick={() => {
            form.resetFields();
            form.setFieldsValue({
              range: [dayjs(), dayjs().add(7, 'day')],
              delegateUserId: 1004,
            });
            setOpen(true);
          }}
        >
          新增委托
        </Button>
      </Space>

      <Card>
        <Table rowKey="id" columns={columns} dataSource={list} loading={loading} pagination={false} />
      </Card>

      <Modal
        title="新增委托"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            await createDelegation({
              delegateUserId: v.delegateUserId,
              startDate: v.range[0].format('YYYY-MM-DD'),
              endDate: v.range[1].format('YYYY-MM-DD'),
              reason: v.reason,
            });
            message.success('已创建委托');
            setOpen(false);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '创建失败（可能已有 ACTIVE 委托）');
          }
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="delegateUserId"
            label="被委托人用户 ID"
            rules={[{ required: true, message: '必填' }]}
            extra="示例：1004=代审人孙七"
          >
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
          <Form.Item name="range" label="委托起止" rules={[{ required: true }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="原因">
            <Input.TextArea rows={3} placeholder="出差委托等" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

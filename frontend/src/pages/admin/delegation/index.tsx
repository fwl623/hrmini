import {
  Alert,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  cancelDelegation,
  createDelegation,
  fetchDelegations,
  type DelegationItem,
} from '@/services/workflow';
import { getRequestErrorMessage } from '@/utils/requestError';

function getBizCode(error: unknown): number | undefined {
  if (!error || typeof error !== 'object') return undefined;
  const err = error as { info?: { code?: number }; response?: { data?: { code?: number } } };
  return err.info?.code ?? err.response?.data?.code;
}

/**
 * 委托管理：列表 + 新增 + 取消
 * 业务约束：同一委托人同时仅 1 条 ACTIVE（冲突码 60003）
 */
export default function DelegationPage() {
  const [list, setList] = useState<DelegationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const hasActive = useMemo(() => list.some((d) => d.status === 'ACTIVE'), [list]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchDelegations({ page: 1, pageSize: 50 });
      setList(data?.list ?? []);
    } catch (e) {
      message.error(getRequestErrorMessage(e, '加载委托失败'));
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
                message.error(getRequestErrorMessage(e, '取消失败'));
              }
            }}
          >
            取消
          </Button>
        ) : null,
    },
  ];

  const handleCreate = async () => {
    try {
      const v = await form.validateFields();
      setSubmitting(true);
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
      const code = getBizCode(e);
      if (code === 60003) {
        Modal.warning({
          title: '无法新建委托',
          content: '您已有一条生效中的委托，同一时间只能保留 1 条。请先取消现有委托后再新建。',
          okText: '知道了',
        });
        return;
      }
      message.error(getRequestErrorMessage(e, '创建失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            审批委托
          </Typography.Title>
          <Typography.Text type="secondary">
            同一委托人同时仅允许 1 条生效中的委托
          </Typography.Text>
        </div>
        <Button
          type="primary"
          onClick={() => {
            if (hasActive) {
              Modal.warning({
                title: '无法新建委托',
                content: '您已有一条生效中的委托。请先在列表中取消后再新建。',
                okText: '知道了',
              });
              return;
            }
            form.resetFields();
            form.setFieldsValue({
              range: [dayjs(), dayjs().add(7, 'day')],
            });
            setOpen(true);
          }}
        >
          新增委托
        </Button>
      </Space>

      {hasActive ? (
        <Alert
          type="info"
          showIcon
          message="当前已有生效中的委托，需先取消后才能新建"
        />
      ) : null}

      <Card>
        <Table rowKey="id" columns={columns} dataSource={list} loading={loading} pagination={false} />
      </Card>

      <Modal
        title="新增委托"
        open={open}
        confirmLoading={submitting}
        onCancel={() => setOpen(false)}
        onOk={handleCreate}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="delegateUserId"
            label="被委托人用户 ID"
            rules={[{ required: true, message: '必填' }]}
            extra="填写被委托人的系统用户 ID（可在用户管理中查看）"
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="例如：2" />
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

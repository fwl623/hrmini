/**
 * 【模块说明 · 转正管理】路由 /admin/regularization
 *
 * 干什么：展示待转正员工（试用结束≤今天+7）与转正历史；HR 发起 PASS/EXTEND/FAIL 三分支转正。
 *         审批进度在 admin/approval 处理，本页不重复造审批按钮；FAIL 后可引导发起正式离职。
 *
 * 主要状态：
 * - pending / records：待转正列表 + 转正记录 Table
 * - open / current / form：发起转正 Modal（approvalResult 联动 extendMonths 等字段）
 * - 支持 ?employeeId= 深链，从入职页「去转正」带入
 *
 * 调哪些 API（@/services/lifecycle）：
 * - fetchPendingRegularization、fetchRegularizationList、createRegularization
 */
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { history, useSearchParams } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import ProcessStatusTag from '@/components/ProcessStatusTag';
import {
  createRegularization,
  fetchPendingRegularization,
  fetchRegularizationList,
  type PendingRegularizationItem,
  type RegularizationItem,
} from '@/services/lifecycle';

export default function RegularizationPage() {
  const [searchParams] = useSearchParams();
  const deepLinkEmployeeId = Number(searchParams.get('employeeId') || 0) || null;
  const [pending, setPending] = useState<PendingRegularizationItem[]>([]);
  const [records, setRecords] = useState<RegularizationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState<PendingRegularizationItem | null>(null);
  const [form] = Form.useForm();
  const approvalResult = Form.useWatch('approvalResult', form);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [p, r] = await Promise.all([
        fetchPendingRegularization(),
        fetchRegularizationList({ page: 1, pageSize: 50 }),
      ]);
      setPending(p);
      setRecords(r?.list ?? []);
      if (deepLinkEmployeeId) {
        const hit = p.find((x) => x.employeeId === deepLinkEmployeeId);
        if (hit) {
          setCurrent(hit);
          form.resetFields();
          form.setFieldsValue({ approvalResult: 'PASS', employeeId: hit.employeeId });
          setOpen(true);
        } else {
          message.info('该员工暂不在待转正列表（试用结束日 ≤ 今天+7 天），可稍后重试');
        }
      }
    } catch (e) {
      message.error((e as Error)?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [deepLinkEmployeeId, form]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = (row: PendingRegularizationItem) => {
    setCurrent(row);
    form.resetFields();
    form.setFieldsValue({ approvalResult: 'PASS', employeeId: row.employeeId });
    setOpen(true);
  };

  const pendingCols: ColumnsType<PendingRegularizationItem> = [
    { title: '工号', dataIndex: 'empNo', width: 120 },
    { title: '姓名', dataIndex: 'name' },
    { title: '入职日', dataIndex: 'hireDate', width: 120 },
    {
      title: '试用结束日',
      dataIndex: 'probationEndDate',
      width: 140,
      render: (d: string, row) => (
        <Space size={4}>
          <span>{d || '-'}</span>
          {row.overdue ? <Tag color="error">已逾期</Tag> : null}
        </Space>
      ),
    },
    {
      title: '操作',
      width: 120,
      render: (_, row) => (
        <Button type="link" onClick={() => openCreate(row)}>
          发起转正
        </Button>
      ),
    },
  ];

  const recordCols: ColumnsType<RegularizationItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    {
      title: '结果',
      dataIndex: 'approvalResult',
      width: 100,
      render: (v: string) =>
        ({ PASS: '通过', EXTEND: '延长', FAIL: '不通过' } as Record<string, string>)[v] || v,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <ProcessStatusTag status={s} />,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
    {
      title: '操作',
      width: 140,
      render: (_, row) =>
        row.nextAction === 'START_RESIGNATION' ||
        (row.approvalResult === 'FAIL' && row.status === 'COMPLETED') ? (
          <Button
            type="link"
            danger
            onClick={() => {
              history.push(`/admin/resignation?employeeId=${row.employeeId}&open=1`);
            }}
          >
            去发起离职
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        转正管理
      </Typography.Title>
      <Card title="待转正（试用结束日 ≤ 今天+7 天，含逾期）" loading={loading}>
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
            message.success(
              values.approvalResult === 'FAIL'
                ? '已提交「不通过」审批；通过后请发起正式离职'
                : '已提交转正审批',
            );
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
        {current ? (
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="工号">{current.empNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="姓名">{current.name}</Descriptions.Item>
            <Descriptions.Item label="试用开始（入职日）">
              {current.hireDate || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="试用结束日">
              {current.probationEndDate || '-'}
              {current.overdue ? '（已逾期）' : ''}
            </Descriptions.Item>
          </Descriptions>
        ) : null}
        <Form form={form} layout="vertical">
          <Form.Item name="approvalResult" label="转正结果" rules={[{ required: true }]}>
            <Radio.Group>
              <Radio.Button value="PASS">通过</Radio.Button>
              <Radio.Button value="EXTEND">延长试用</Radio.Button>
              <Radio.Button value="FAIL">不通过</Radio.Button>
            </Radio.Group>
          </Form.Item>
          {approvalResult === 'EXTEND' ? (
            <Form.Item
              name="extendMonths"
              label="延长月数"
              rules={[{ required: true, message: '请填写延长月数' }]}
            >
              <InputNumber min={1} max={12} style={{ width: '100%' }} />
            </Form.Item>
          ) : null}
          {approvalResult === 'FAIL' ? (
            <Typography.Paragraph type="secondary">
              选择「不通过」表示试用不合格。审批通过后员工仍为试用状态，需 HR 在离职管理发起正式离职（辞退）。
            </Typography.Paragraph>
          ) : null}
          <Form.Item
            name="performanceEvaluation"
            label="试用期表现评价"
            rules={[{ required: true, message: '请填写评价' }]}
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item
            name="salaryAdjustment"
            label="转正后基本工资（可选）"
            extra="填写则随转正审批通过后写入薪资档案；不填则不调薪"
          >
            <InputNumber style={{ width: '100%' }} min={0} placeholder="新基本工资" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

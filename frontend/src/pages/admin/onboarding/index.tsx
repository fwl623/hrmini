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
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  abandonOnboardingApplication,
  confirmOnboardingApplication,
  createOnboardingApplication,
  fetchOnboardingApplications,
  submitOnboardingApplication,
  withdrawOnboardingApplication,
  type OnboardingForm,
} from '@/services/workflow';

interface OnboardingRow {
  id: number;
  status: string;
  name: string;
  mobile?: string;
  departmentId?: number;
  positionId?: number;
  baseSalary?: number;
  expectedOnboardDate?: string;
  employeeId?: number;
  instanceId?: number;
  createdAt?: string;
}

const STATUS_COLOR: Record<string, string> = {
  draft: 'default',
  pending: 'processing',
  approved_pending: 'warning',
  onboarded: 'success',
  rejected: 'error',
  abandoned: 'default',
};

/**
 * 入职管理全链路：草稿 → 提交 → 审批中心审批 → 确认入职建档
 * 审批人开发期用 X-User-Id=1002（见 services/workflow + 审批中心）
 */
export default function OnboardingPage() {
  const [list, setList] = useState<OnboardingRow[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchOnboardingApplications({ page: 1, pageSize: 50 });
      setList((data?.list as OnboardingRow[]) ?? []);
      setStats(data?.stats ?? {});
    } catch (e) {
      message.error((e as Error)?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const columns: ColumnsType<OnboardingRow> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '姓名', dataIndex: 'name' },
    { title: '手机号', dataIndex: 'mobile', width: 130 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 140,
      render: (s: string) => <Tag color={STATUS_COLOR[s] || 'default'}>{s}</Tag>,
    },
    { title: '预计入职', dataIndex: 'expectedOnboardDate', width: 120 },
    { title: '员工ID', dataIndex: 'employeeId', width: 90 },
    {
      title: '操作',
      width: 280,
      render: (_, row) => (
        <Space wrap>
          {row.status === 'draft' && (
            <Button
              type="link"
              onClick={async () => {
                try {
                  await submitOnboardingApplication(row.id);
                  message.success('已提交，请到审批中心用 X-User-Id=1002 审批');
                  load();
                } catch (e) {
                  message.error((e as Error)?.message || '提交失败');
                }
              }}
            >
              提交审批
            </Button>
          )}
          {row.status === 'pending' && (
            <Button
              type="link"
              onClick={async () => {
                try {
                  await withdrawOnboardingApplication(row.id);
                  message.success('已撤回');
                  load();
                } catch (e) {
                  message.error((e as Error)?.message || '撤回失败');
                }
              }}
            >
              撤回
            </Button>
          )}
          {row.status === 'approved_pending' && (
            <>
              <Button
                type="link"
                onClick={async () => {
                  try {
                    await confirmOnboardingApplication(row.id);
                    message.success('已确认入职并建档');
                    load();
                  } catch (e) {
                    message.error((e as Error)?.message || '确认失败');
                  }
                }}
              >
                确认入职
              </Button>
              <Button
                type="link"
                danger
                onClick={async () => {
                  try {
                    await abandonOnboardingApplication(row.id);
                    message.success('已放弃入职');
                    load();
                  } catch (e) {
                    message.error((e as Error)?.message || '操作失败');
                  }
                }}
              >
                放弃
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            入职管理
          </Typography.Title>
          <Typography.Text type="secondary">
            草稿 {stats.draft ?? 0} · 审批中 {stats.pending ?? 0} · 待入职 {stats.approvedPending ?? 0} ·
            已入职 {stats.onboarded ?? 0}
          </Typography.Text>
        </div>
        <Button type="primary" onClick={() => { form.resetFields(); setOpen(true); }}>
          新建入职申请
        </Button>
      </Space>

      <Card loading={loading}>
        <Table rowKey="id" columns={columns} dataSource={list} pagination={false} />
      </Card>

      <Modal
        title="新建入职申请"
        open={open}
        width={640}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            const body: OnboardingForm = {
              name: v.name,
              gender: v.gender,
              mobile: v.mobile,
              email: v.email,
              idNumber: v.idNumber,
              expectedOnboardDate: v.expectedOnboardDate.format('YYYY-MM-DD'),
              departmentId: v.departmentId,
              positionId: v.positionId,
              employmentType: v.employmentType,
              probationMonths: v.probationMonths,
              probationSalaryRatio: v.probationSalaryRatio,
              managerId: v.managerId,
              baseSalary: v.baseSalary,
              positionStandard: v.positionStandard !== false,
            };
            await createOnboardingApplication(body);
            message.success('已创建草稿');
            setOpen(false);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '创建失败');
          }
        }}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            gender: 'MALE',
            employmentType: 'fulltime',
            probationMonths: 3,
            probationSalaryRatio: 0.8,
            expectedOnboardDate: dayjs().add(7, 'day'),
            departmentId: 10,
            positionId: 20,
            baseSalary: 15000,
            positionStandard: true,
          }}
        >
          <Form.Item name="name" label="姓名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="gender" label="性别" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'MALE', label: '男' },
                { value: 'FEMALE', label: '女' },
              ]}
            />
          </Form.Item>
          <Form.Item name="mobile" label="手机号" rules={[{ required: true, pattern: /^1\d{10}$/ }]}>
            <Input placeholder="唯一，将作为登录账号" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="idNumber" label="身份证号" rules={[{ required: true, len: 18 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="expectedOnboardDate" label="预计入职日" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          {/* 依赖 A 同学部门树选择器，当前暂用部门/职位 ID */}
          <Form.Item name="departmentId" label="部门 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="positionId" label="职位 ID" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="employmentType" label="用工类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'fulltime', label: '全职' },
                { value: 'parttime', label: '兼职' },
                { value: 'intern', label: '实习' },
              ]}
            />
          </Form.Item>
          <Form.Item name="probationMonths" label="试用月数" rules={[{ required: true }]}>
            <InputNumber min={0} max={12} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="probationSalaryRatio" label="试用薪资比例" rules={[{ required: true }]}>
            <InputNumber min={0.8} max={1} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="managerId" label="直属上级员工 ID">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="baseSalary"
            label="约定薪资"
            rules={[{ required: true }]}
            extra="超过 20000 将触发 HR 二审（审批人 1003）"
          >
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

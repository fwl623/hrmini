import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  TreeSelect,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { history } from '@umijs/max';
import {
  abandonOnboardingApplication,
  confirmOnboardingApplication,
  createOnboardingApplication,
  deleteOnboardingApplication,
  fetchOnboardingApplications,
  submitOnboardingApplication,
  updateOnboardingApplication,
  withdrawOnboardingApplication,
  type OnboardingForm,
  type OnboardingItem,
} from '@/services/workflow';
import {
  getDeptTree,
  listPositions,
  type DeptTreeNode,
  type PositionVO,
} from '@/services/org';
import { getEmployeeList, type EmployeeItem } from '@/services/employee';

interface TreeOption {
  title: string;
  value: number;
  children?: TreeOption[];
}

function toDeptTreeOptions(nodes: DeptTreeNode[]): TreeOption[] {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id,
    children: n.children?.length ? toDeptTreeOptions(n.children) : undefined,
  }));
}

const STATUS_COLOR: Record<string, string> = {
  draft: 'default',
  pending: 'processing',
  approved_pending: 'warning',
  onboarded: 'success',
  rejected: 'error',
  abandoned: 'default',
};

function actionsForStatus(status: string) {
  return {
    edit: status === 'draft' || status === 'rejected',
    submit: status === 'draft' || status === 'rejected',
    withdraw: status === 'pending',
    confirm: status === 'approved_pending',
    abandon: status === 'approved_pending',
    changeDate: status === 'approved_pending',
    remove: status === 'draft' || status === 'rejected',
    viewReject: status === 'rejected',
    regularize: status === 'onboarded',
  };
}

/**
 * 入职管理：StatCards + 状态按钮矩阵（含草稿编辑 / 驳回重提 / 改入职日 / 部门职位选择器）
 */
export default function OnboardingPage() {
  const [list, setList] = useState<OnboardingItem[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [dateModal, setDateModal] = useState<{ id: number; date?: string } | null>(null);
  const [detailRow, setDetailRow] = useState<OnboardingItem | null>(null);
  const [form] = Form.useForm();
  const [dateForm] = Form.useForm();
  const [deptTreeOptions, setDeptTreeOptions] = useState<TreeOption[]>([]);
  const [positions, setPositions] = useState<PositionVO[]>([]);
  const [managerOptions, setManagerOptions] = useState<{ label: string; value: number }[]>([]);
  const departmentId = Form.useWatch('departmentId', form);

  const positionOptions = useMemo(() => {
    const filtered = departmentId
      ? positions.filter((p) => !p.departmentId || p.departmentId === departmentId)
      : positions;
    return filtered.map((p) => ({
      label: `${p.name}${p.isStandard === false ? '（非标）' : ''}`,
      value: p.id,
    }));
  }, [positions, departmentId]);

  const loadManagers = useCallback(async (deptId?: number) => {
    if (!deptId) {
      setManagerOptions([]);
      return;
    }
    try {
      const res = await getEmployeeList({
        departmentIds: String(deptId),
        employmentStatus: 'probation,regular',
        page: 1,
        pageSize: 100,
      });
      const list = (res.data?.list ?? []) as EmployeeItem[];
      setManagerOptions(
        list.map((e) => ({
          label: `${e.name}${e.empNo ? `（${e.empNo}）` : ''}`,
          value: e.employeeId,
        })),
      );
    } catch {
      setManagerOptions([]);
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchOnboardingApplications({ page: 1, pageSize: 50 });
      setList(data?.list ?? []);
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

  useEffect(() => {
    (async () => {
      try {
        const [deptRes, posRes] = await Promise.all([
          getDeptTree(),
          listPositions({ page: 1, pageSize: 200 }),
        ]);
        setDeptTreeOptions(toDeptTreeOptions(deptRes.data ?? []));
        setPositions(posRes.data?.list ?? []);
      } catch {
        message.warning('部门/职位选项加载失败，可稍后刷新重试');
      }
    })();
  }, []);

  useEffect(() => {
    loadManagers(departmentId);
  }, [departmentId, loadManagers]);

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    form.setFieldsValue({
      gender: 'MALE',
      employmentType: 'fulltime',
      probationMonths: 3,
      probationSalaryRatio: 0.8,
      expectedOnboardDate: dayjs().add(7, 'day'),
      baseSalary: 15000,
      positionStandard: true,
    });
    setOpen(true);
  };

  const openEdit = (row: OnboardingItem) => {
    setEditingId(row.id);
    form.setFieldsValue({
      name: row.name,
      gender: row.gender || 'MALE',
      mobile: row.mobile,
      email: row.email,
      idNumber: row.idNumber,
      expectedOnboardDate: row.expectedOnboardDate ? dayjs(row.expectedOnboardDate) : undefined,
      departmentId: row.departmentId,
      positionId: row.positionId,
      employmentType: row.employmentType || 'fulltime',
      probationMonths: row.probationMonths ?? 3,
      probationSalaryRatio: row.probationSalaryRatio ?? 0.8,
      managerId: row.managerId,
      baseSalary: row.baseSalary,
      positionStandard: row.positionStandard !== false,
    });
    setOpen(true);
  };

  const onPositionChange = (positionId: number) => {
    const pos = positions.find((p) => p.id === positionId);
    if (!pos) return;
    form.setFieldsValue({
      probationMonths: pos.defaultProbationMonths ?? 3,
      positionStandard: pos.isStandard !== false,
    });
  };

  const buildBody = (v: Record<string, unknown>): OnboardingForm => ({
    name: v.name as string,
    gender: v.gender as string,
    mobile: v.mobile as string,
    email: v.email as string,
    idNumber: v.idNumber as string,
    expectedOnboardDate: (v.expectedOnboardDate as dayjs.Dayjs).format('YYYY-MM-DD'),
    departmentId: v.departmentId as number,
    positionId: v.positionId as number,
    employmentType: v.employmentType as string,
    probationMonths: v.probationMonths as number,
    probationSalaryRatio: v.probationSalaryRatio as number,
    managerId: v.managerId as number | undefined,
    baseSalary: v.baseSalary as number,
    positionStandard: v.positionStandard !== false,
  });

  const columns: ColumnsType<OnboardingItem> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '姓名', dataIndex: 'name' },
    { title: '手机号', dataIndex: 'mobile', width: 130 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 140,
      render: (s: string, row) => (
        <Space size={4}>
          <Tag color={STATUS_COLOR[s] || 'default'}>{s}</Tag>
          {s === 'rejected' && row.rejectReason ? (
            <Typography.Text type="danger" style={{ fontSize: 12 }} ellipsis>
              {row.rejectReason}
            </Typography.Text>
          ) : null}
        </Space>
      ),
    },
    { title: '预计入职', dataIndex: 'expectedOnboardDate', width: 120 },
    {
      title: '薪资/上限',
      width: 140,
      render: (_, row) =>
        row.baseSalary != null
          ? `${row.baseSalary}${row.gradeMaxSalary != null ? ` / ${row.gradeMaxSalary}` : ''}`
          : '-',
    },
    { title: '员工ID', dataIndex: 'employeeId', width: 90 },
    {
      title: '操作',
      width: 380,
      render: (_, row) => {
        const a = actionsForStatus(row.status);
        return (
          <Space wrap>
            {a.edit && (
              <Button type="link" onClick={() => openEdit(row)}>
                编辑
              </Button>
            )}
            {a.viewReject && (
              <Button type="link" onClick={() => setDetailRow(row)}>
                驳回详情
              </Button>
            )}
            {a.submit && (
              <Button
                type="link"
                onClick={async () => {
                  try {
                    await submitOnboardingApplication(row.id);
                    message.success(row.status === 'rejected' ? '已重新提交审批' : '已提交审批');
                    load();
                  } catch (e) {
                    message.error((e as Error)?.message || '提交失败');
                  }
                }}
              >
                {row.status === 'rejected' ? '重新提交' : '提交'}
              </Button>
            )}
            {a.withdraw && (
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
            {a.changeDate && (
              <Button
                type="link"
                onClick={() => {
                  setDateModal({ id: row.id, date: row.expectedOnboardDate });
                  dateForm.setFieldsValue({
                    expectedOnboardDate: row.expectedOnboardDate
                      ? dayjs(row.expectedOnboardDate)
                      : dayjs().add(1, 'day'),
                  });
                }}
              >
                改入职日
              </Button>
            )}
            {a.confirm && (
              <Button
                type="link"
                onClick={async () => {
                  try {
                    await confirmOnboardingApplication(row.id);
                    message.success('已确认入职（账号已开通）');
                    load();
                  } catch (e) {
                    message.error((e as Error)?.message || '确认失败');
                  }
                }}
              >
                确认入职
              </Button>
            )}
            {a.abandon && (
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
            )}
            {a.regularize && row.employeeId && (
              <Button
                type="link"
                onClick={() => {
                  history.push(`/admin/regularization?employeeId=${row.employeeId}`);
                }}
              >
                去转正
              </Button>
            )}
            {a.remove && (
              <Button
                type="link"
                danger
                onClick={async () => {
                  try {
                    await deleteOnboardingApplication(row.id);
                    message.success('已删除');
                    load();
                  } catch (e) {
                    message.error((e as Error)?.message || '删除失败');
                  }
                }}
              >
                删除
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          入职管理
        </Typography.Title>
        <Button type="primary" onClick={openCreate}>
          新建入职申请
        </Button>
      </Space>

      <Row gutter={12}>
        <Col span={4}><Card size="small"><Statistic title="草稿" value={stats.draft ?? 0} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="审批中" value={stats.pending ?? 0} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="待入职" value={stats.approvedPending ?? 0} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="已入职" value={stats.onboarded ?? 0} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="已驳回" value={stats.rejected ?? 0} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="已放弃" value={stats.abandoned ?? 0} /></Card></Col>
      </Row>

      <Card loading={loading}>
        <Table rowKey="id" columns={columns} dataSource={list} pagination={false} />
      </Card>

      <Modal
        title={editingId ? '编辑入职申请' : '新建入职申请'}
        open={open}
        width={680}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            const body = buildBody(v);
            if (editingId) {
              await updateOnboardingApplication(editingId, body);
              message.success('已保存');
            } else {
              await createOnboardingApplication(body);
              message.success('已创建草稿');
            }
            setOpen(false);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '保存失败');
          }
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
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
          <Form.Item
            name="mobile"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '请输入正确的11位手机号' },
            ]}
          >
            <Input placeholder="唯一，将作为登录账号" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '请输入正确的邮箱地址' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="idNumber"
            label="身份证号"
            rules={[
              { required: true, message: '请输入身份证号' },
              { len: 18, message: '身份证号须为18位' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="expectedOnboardDate" label="预计入职日" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="departmentId" label="部门" rules={[{ required: true, message: '请选择部门' }]}>
            <TreeSelect
              treeData={deptTreeOptions}
              placeholder="选择部门"
              allowClear
              treeDefaultExpandAll
              style={{ width: '100%' }}
              onChange={() => {
                form.setFieldValue('positionId', undefined);
                form.setFieldValue('managerId', undefined);
              }}
            />
          </Form.Item>
          <Form.Item name="positionId" label="职位" rules={[{ required: true, message: '请选择职位' }]}>
            <Select
              options={positionOptions}
              placeholder="选择职位"
              showSearch
              optionFilterProp="label"
              onChange={onPositionChange}
            />
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
          <Form.Item
            name="managerId"
            label="直属上级"
            extra="不选则默认取部门负责人"
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选择直属上级（可选）"
              options={managerOptions}
              disabled={!departmentId}
            />
          </Form.Item>
          <Form.Item name="positionStandard" hidden>
            <InputNumber />
          </Form.Item>
          <Form.Item
            name="baseSalary"
            label="约定薪资"
            rules={[{ required: true }]}
            extra="超过职位职级薪资上限将触发 HR 二审"
          >
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="修改预计入职日"
        open={!!dateModal}
        onCancel={() => setDateModal(null)}
        onOk={async () => {
          if (!dateModal) return;
          try {
            const v = await dateForm.validateFields();
            await updateOnboardingApplication(dateModal.id, {
              expectedOnboardDate: v.expectedOnboardDate.format('YYYY-MM-DD'),
            } as Partial<OnboardingForm>);
            message.success('已更新预计入职日');
            setDateModal(null);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '更新失败');
          }
        }}
        destroyOnClose
      >
        <Form form={dateForm} layout="vertical">
          <Form.Item
            name="expectedOnboardDate"
            label="预计入职日"
            rules={[{ required: true }]}
          >
            <DatePicker style={{ width: '100%' }} disabledDate={(d) => d.isBefore(dayjs(), 'day')} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="驳回详情"
        open={!!detailRow}
        footer={<Button onClick={() => setDetailRow(null)}>关闭</Button>}
        onCancel={() => setDetailRow(null)}
      >
        {detailRow && (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="姓名">{detailRow.name}</Descriptions.Item>
            <Descriptions.Item label="手机号">{detailRow.mobile}</Descriptions.Item>
            <Descriptions.Item label="驳回原因">
              <Typography.Text type="danger">
                {detailRow.rejectReason || '（无批注）'}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="操作提示">
              可点击「编辑」修改后「重新提交」
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </Space>
  );
}

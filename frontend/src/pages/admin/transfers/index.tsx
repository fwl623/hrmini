import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Steps,
  Table,
  TreeSelect,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from '@umijs/max';
import {
  createTransfer,
  fetchTransferDetail,
  fetchTransfers,
  type TransferItem,
} from '@/services/lifecycle';
import { getEmployeeDetail, getEmployeeList } from '@/services/employee';
import { getDeptTree, listPositions, type DeptTreeNode } from '@/services/org';
import { SEQUENCE_RANK_MAP } from '@/pages/admin/org/positions/constants';

type TreeOption = {
  title: string;
  value: number;
  key: string;
  children?: TreeOption[];
};

type EmpOption = { label: string; value: number };

function toDeptTreeOptions(nodes: DeptTreeNode[]): TreeOption[] {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id,
    key: `dept-${n.id}`,
    children: n.children?.length ? toDeptTreeOptions(n.children) : undefined,
  }));
}

function flattenDeptNames(nodes: DeptTreeNode[], map: Record<number, string> = {}) {
  for (const n of nodes) {
    map[n.id] = n.name;
    if (n.children?.length) flattenDeptNames(n.children, map);
  }
  return map;
}

const GRADE_OPTIONS = [
  ...SEQUENCE_RANK_MAP.M,
  ...SEQUENCE_RANK_MAP.P,
  ...SEQUENCE_RANK_MAP.S,
].map((g) => ({ label: g, value: g }));

const STATUS_LABEL: Record<string, string> = {
  APPROVING: '审批中',
  PENDING: '审批中',
  PENDING_EFFECT: '待生效',
  APPROVED: '已生效',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
};

/**
 * 调岗管理：列表 + 发起 + 详情（原部门→新部门→[财务]→HR）
 * 支持 ?employeeId=&open=1 从花名册「更多-调岗」带入
 */
export default function TransfersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [list, setList] = useState<TransferItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [detail, setDetail] = useState<TransferItem | null>(null);
  const [form] = Form.useForm();
  const [deptTreeOptions, setDeptTreeOptions] = useState<TreeOption[]>([]);
  const [deptNameMap, setDeptNameMap] = useState<Record<number, string>>({});
  const [positionOptions, setPositionOptions] = useState<{ label: string; value: number; departmentId?: number }[]>([]);
  const [allPositions, setAllPositions] = useState<{ label: string; value: number; departmentId?: number }[]>([]);
  const newDeptId = Form.useWatch('newDepartmentId', form);
  const [empOptions, setEmpOptions] = useState<EmpOption[]>([]);
  const [mgrOptions, setMgrOptions] = useState<EmpOption[]>([]);
  const [empLoading, setEmpLoading] = useState(false);
  const [mgrLoading, setMgrLoading] = useState(false);

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

  useEffect(() => {
    (async () => {
      try {
        const [deptRes, posRes] = await Promise.all([
          getDeptTree(),
          listPositions({ page: 1, pageSize: 200 }),
        ]);
        const tree = deptRes.data ?? [];
        setDeptTreeOptions(toDeptTreeOptions(tree));
        setDeptNameMap(flattenDeptNames(tree));
        const listPos = posRes.data?.list ?? [];
        setPositionOptions(listPos.map((p) => ({ label: p.name, value: p.id })));
      } catch {
        message.warning('部门/职位选项加载失败，可稍后刷新重试');
      }
    })();
  }, []);

  const searchEmployees = useCallback(async (keyword: string, forManager: boolean) => {
    if (!keyword || keyword.trim().length < 1) {
      if (forManager) setMgrOptions([]);
      else setEmpOptions([]);
      return;
    }
    if (forManager) setMgrLoading(true);
    else setEmpLoading(true);
    try {
      const res = await getEmployeeList({
        keyword: keyword.trim(),
        employmentStatus: 'probation,regular',
        page: 1,
        pageSize: 20,
      });
      const options = (res.data?.list ?? []).map((e) => ({
        label: `${e.name} · ${e.department || '未分部门'} · ${e.empNo || '-'}`,
        value: e.employeeId,
      }));
      if (forManager) setMgrOptions(options);
      else setEmpOptions(options);
    } catch {
      if (forManager) setMgrOptions([]);
      else setEmpOptions([]);
    } finally {
      if (forManager) setMgrLoading(false);
      else setEmpLoading(false);
    }
  }, []);

  useEffect(() => {
    const employeeId = searchParams.get('employeeId');
    const shouldOpen = searchParams.get('open') === '1';
    if (!employeeId || !shouldOpen) return;

    const id = Number(employeeId);
    if (!Number.isFinite(id) || id <= 0) return;

    (async () => {
      form.resetFields();
      form.setFieldsValue({ employeeId: id });
      try {
        const res = await getEmployeeDetail(id);
        if (res.code === 0 && res.data) {
          setEmpOptions([
            {
              label: `${res.data.name}（${res.data.empNo}）`,
              value: id,
            },
          ]);
        }
      } catch {
        setEmpOptions([{ label: `员工#${id}`, value: id }]);
      }
      setOpen(true);
      setSearchParams({}, { replace: true });
    })();
  }, [form, searchParams, setSearchParams]);

  const deptLabel = useCallback(
    (id?: number) => (id != null ? deptNameMap[id] || String(id) : '-'),
    [deptNameMap],
  );

  const columns: ColumnsType<TransferItem> = useMemo(
    () => [
      { title: 'ID', dataIndex: 'id', width: 70 },
      { title: '员工', dataIndex: 'employeeName' },
      {
        title: '原部门',
        dataIndex: 'fromDepartmentId',
        width: 120,
        render: (v) => deptLabel(v),
      },
      {
        title: '新部门',
        dataIndex: 'newDepartmentId',
        width: 120,
        render: (v) => deptLabel(v),
      },
      { title: '生效日', dataIndex: 'effectiveDate', width: 120 },
      {
        title: '调薪',
        dataIndex: 'salaryAdjustment',
        width: 100,
        render: (v) => (v != null ? v : '-'),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (s: string) => STATUS_LABEL[s] || s,
      },
      {
        title: '操作',
        width: 90,
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
    ],
    [deptLabel],
  );

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
        <Button
          type="primary"
          onClick={() => {
            form.resetFields();
            setEmpOptions([]);
            setMgrOptions([]);
            setOpen(true);
          }}
        >
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
            message.success(
              v.salaryAdjustment != null
                ? '已提交调岗审批（原部门→新部门→财务→HR）'
                : '已提交调岗审批（原部门→新部门→HR）',
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
        <Form form={form} layout="vertical">
          <Form.Item
            name="employeeId"
            label="调岗员工"
            rules={[{ required: true, message: '请选择员工' }]}
            extra="可从花名册「更多 → 调岗」带入；须为试用期或正式"
          >
            <Select
              showSearch
              filterOption={false}
              placeholder="搜索姓名/工号/手机号"
              options={empOptions}
              loading={empLoading}
              onSearch={(kw) => searchEmployees(kw, false)}
              notFoundContent={empLoading ? '搜索中…' : '输入关键词搜索'}
            />
          </Form.Item>
          <Form.Item
            name="newDepartmentId"
            label="新部门"
            rules={[{ required: true, message: '新部门必须变更' }]}
            extra="须与原部门不同"
          >
            <TreeSelect
              treeData={deptTreeOptions}
              placeholder="选择新部门"
              allowClear
              treeDefaultExpandAll
              showSearch
              treeNodeFilterProp="title"
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            name="newPositionId"
            label="新职位（可选）"
            extra="不选则职位保持不变；换部门时请一并选择目标职位（如财务部选「财务专员」）"
          >
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={positionOptions}
              placeholder="选择职位"
            />
          </Form.Item>
          <Form.Item name="newJobLevel" label="新职级（可选）">
            <Select allowClear options={GRADE_OPTIONS} placeholder="选择职级" />
          </Form.Item>
          <Form.Item name="newManagerId" label="新汇报人（可选）">
            <Select
              allowClear
              showSearch
              filterOption={false}
              placeholder="搜索员工"
              options={mgrOptions}
              loading={mgrLoading}
              onSearch={(kw) => searchEmployees(kw, true)}
              notFoundContent={mgrLoading ? '搜索中…' : '输入关键词搜索'}
            />
          </Form.Item>
          <Form.Item
            name="salaryAdjustment"
            label="调岗后基本工资（可选）"
            extra="填写则增加「财务调薪确认」节点；通过后按该金额更新薪资档案"
          >
            <InputNumber
              min={0.01}
              precision={2}
              style={{ width: '100%' }}
              placeholder="不填则不调薪"
            />
          </Form.Item>
          <Form.Item
            name="effectiveDate"
            label="生效日期"
            rules={[{ required: true }]}
            initialValue={dayjs()}
            extra="晚于今天时，审批通过后进入待生效，到期自动变更"
          >
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => d.isBefore(dayjs(), 'day')}
            />
          </Form.Item>
          <Form.Item name="reason" label="调岗原因" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="调岗详情 · 审批进度"
        open={!!detail}
        onClose={() => setDetail(null)}
        width={480}
      >
        {detail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              {detail.employeeName} · {STATUS_LABEL[detail.status] || detail.status}
            </div>
            <div>
              部门 {deptLabel(detail.fromDepartmentId)} → {deptLabel(detail.newDepartmentId)}
            </div>
            {detail.salaryAdjustment != null && (
              <div>调岗后基本工资：{detail.salaryAdjustment}</div>
            )}
            <div>生效日：{detail.effectiveDate || '-'}</div>
            <div>原因：{detail.reason}</div>
            <Steps
              direction="vertical"
              items={(
                detail.nodes ?? [
                  { order: 1, label: '原部门确认', status: 'process' },
                  { order: 2, label: '新部门接收', status: 'wait' },
                  { order: 3, label: 'HR 备案', status: 'wait' },
                ]
              ).map((n) => ({
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

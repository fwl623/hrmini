import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
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
import { history, useAccess, useLocation, useSearchParams } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import ProcessStatusTag from '@/components/ProcessStatusTag';
import {
  resignationReasonLabel,
  resignationTypeLabel,
} from '@/constants/workflow';
import {
  createResignation,
  fetchResignationRequests,
  fetchResignationStats,
  fetchResignations,
  type ResignationItem,
  type ResignationRequestItem,
  type ResignationStats,
} from '@/services/lifecycle';
import { getEmployeeDetail, getEmployeeList } from '@/services/employee';

/**
 * HR/管理员离职管理（双通道）
 * - Tab「员工申请」：员工登记的意向；HR 可直接发起正式离职
 * - Tab「正式离职」：正式单列表（部门负责人确认交接 → HR）；也可直提
 * - 支持 ?requestId= 从列表跳转
 * - 支持 ?employeeId=&name=&open=1 从花名册「更多-离职」带入
 * 工作交接人由部门负责人在审批中心确认。
 */
export default function AdminResignationPage() {
  const access = useAccess();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [tab, setTab] = useState('requests');
  const [requests, setRequests] = useState<ResignationRequestItem[]>([]);
  const [resignations, setResignations] = useState<ResignationItem[]>([]);
  const [stats, setStats] = useState<ResignationStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);
  const [linkRequest, setLinkRequest] = useState(false);

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

  /** 支持从审批中心跳转：?requestId=xx */
  useEffect(() => {
    const q = new URLSearchParams(location.search);
    const requestId = Number(q.get('requestId') || 0);
    if (!requestId) return;
    const openFromQuery = async () => {
      try {
        const data = await fetchResignationRequests({ page: 1, pageSize: 100 });
        const row = (data?.list ?? []).find((x) => x.id === requestId);
        if (!row) {
          message.warning('未找到对应离职申请');
          return;
        }
        if (row.status !== 'PENDING' && row.status !== 'APPROVED') {
          message.warning('该申请状态不可发起正式离职');
          setTab('requests');
          return;
        }
        openFormalFromRequest(row);
      } catch (e) {
        message.error((e as Error)?.message || '加载申请失败');
      }
    };
    openFromQuery();
    history.replace('/admin/resignation');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  /** 支持从花名册「更多-离职」带入：?employeeId=&name=&open=1 */
  useEffect(() => {
    if (!access.canManageResignation) return;
    const employeeId = searchParams.get('employeeId');
    const shouldOpen = searchParams.get('open') === '1';
    if (!employeeId || !shouldOpen) return;

    const id = Number(employeeId);
    if (!Number.isFinite(id) || id <= 0) return;

    (async () => {
      setLinkRequest(false);
      form.resetFields();
      const nameFromQuery = searchParams.get('name');
      let label = nameFromQuery ? decodeURIComponent(nameFromQuery) : `员工#${id}`;
      try {
        const res = await getEmployeeDetail(id);
        if (res.code === 0 && res.data) {
          label = `${res.data.name} · ${res.data.department || '未分部门'} · ${res.data.empNo || '-'}`;
        }
      } catch {
        // keep label
      }
      setEmpOptions([{ label, value: id }]);
      form.setFieldsValue({
        employeeId: id,
        resignationDate: dayjs().add(14, 'day'),
        reasonCategory: 'VOLUNTARY',
        resignationType: 'resignation',
      });
      setTab('resignations');
      setOpen(true);
      setSearchParams({}, { replace: true });
    })();
  }, [access.canManageResignation, form, searchParams, setSearchParams]);

  const searchEmployees = useCallback(async (keyword: string) => {
    if (!keyword || keyword.trim().length < 1) {
      setEmpOptions([]);
      return;
    }
    setEmpLoading(true);
    try {
      const res = await getEmployeeList({ keyword: keyword.trim(), page: 1, pageSize: 20 });
      const list = res.data?.list ?? [];
      setEmpOptions(
        list.map((e) => ({
          label: `${e.name} · ${e.department || '未分部门'} · ${e.empNo || '-'}`,
          value: e.employeeId,
        })),
      );
    } catch {
      setEmpOptions([]);
    } finally {
      setEmpLoading(false);
    }
  }, []);

  const openFormalFromRequest = (row: ResignationRequestItem) => {
    setLinkRequest(true);
    setEmpOptions([
      {
        label: `${row.employeeName || '员工'} · 申请#${row.id}`,
        value: row.employeeId,
      },
    ]);
    form.resetFields();
    form.setFieldsValue({
      employeeId: row.employeeId,
      requestId: row.id,
      resignationDate: dayjs(row.expectedResignDate || undefined).isValid()
        ? dayjs(row.expectedResignDate)
        : dayjs().add(14, 'day'),
      reasonCategory: row.reasonCategory || 'VOLUNTARY',
      resignationType: row.resignationType || 'resignation',
      reasonDetail: row.reasonDetail,
    });
    setOpen(true);
    setTab('resignations');
  };

  const reqCols: ColumnsType<ResignationRequestItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '期望离职日', dataIndex: 'expectedResignDate', width: 120 },
    {
      title: '类型',
      dataIndex: 'resignationType',
      width: 120,
      render: (v: string) => resignationTypeLabel(v),
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
      width: 160,
      render: (_, row) => {
        const alreadyFormal = resignations.some((r) => r.requestId === row.id);
        const canStart =
          (row.status === 'PENDING' || row.status === 'APPROVED') &&
          access.canManageResignation &&
          !alreadyFormal;
        if (canStart) {
          return (
            <Button type="link" onClick={() => openFormalFromRequest(row)}>
              发起正式离职
            </Button>
          );
        }
        return alreadyFormal ? (
          <Typography.Text type="secondary">已转正式离职</Typography.Text>
        ) : (
          '-'
        );
      },
    },
  ];

  const resignCols: ColumnsType<ResignationItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '关联申请', dataIndex: 'requestId', width: 100 },
    { title: '离职日', dataIndex: 'resignationDate', width: 120 },
    {
      title: '原因',
      dataIndex: 'reasonCategory',
      width: 100,
      render: (v: string) => resignationReasonLabel(v),
    },
    {
      title: '类型',
      dataIndex: 'resignationType',
      width: 120,
      render: (v: string) => resignationTypeLabel(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 140,
      render: (s: string) => <ProcessStatusTag status={s} />,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          离职管理
        </Typography.Title>
        {access.canManageResignation && (
          <Button
            type="primary"
            danger
            onClick={() => {
              setLinkRequest(false);
              form.resetFields();
              setEmpOptions([]);
              form.setFieldsValue({
                resignationDate: dayjs().add(14, 'day'),
                reasonCategory: 'VOLUNTARY',
                resignationType: 'resignation',
              });
              setOpen(true);
            }}
          >
            发起正式离职
          </Button>
        )}
      </Space>
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
        title={linkRequest ? '基于员工申请发起正式离职' : '发起正式离职'}
        open={open}
        okText="提交"
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            await createResignation({
              employeeId: v.employeeId,
              requestId: linkRequest ? v.requestId : undefined,
              resignationDate: v.resignationDate.format('YYYY-MM-DD'),
              reasonCategory: v.reasonCategory,
              resignationType: v.resignationType,
              reasonDetail: v.reasonDetail,
            });
            message.success('已发起正式离职，等待部门负责人确认交接并审批');
            setOpen(false);
            setTab('resignations');
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '提交失败');
          }
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          {linkRequest ? (
            <Form.Item name="requestId" label="关联申请 ID" hidden>
              <Input />
            </Form.Item>
          ) : null}
          <Form.Item
            name="employeeId"
            label="离职员工"
            rules={[{ required: true, message: '请选择离职员工' }]}
          >
            <Select
              showSearch
              disabled={linkRequest}
              placeholder="按姓名 / 部门 / 工号搜索"
              filterOption={false}
              notFoundContent={empLoading ? '搜索中…' : '请输入关键词搜索'}
              loading={empLoading}
              onSearch={searchEmployees}
              options={empOptions}
            />
          </Form.Item>
          <Form.Item
            name="resignationDate"
            label="离职日期"
            rules={[
              { required: true, message: '请选择离职日期' },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve();
                  if (value.isBefore(dayjs().startOf('day'))) {
                    return Promise.reject(new Error('离职日须 ≥ 今天'));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => !!d && d < dayjs().startOf('day')}
            />
          </Form.Item>
          <Form.Item
            name="reasonCategory"
            label="离职原因"
            rules={[{ required: true, message: '请选择离职原因' }]}
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
            <Input.TextArea rows={3} placeholder="详细离职说明" />
          </Form.Item>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            工作交接人由部门负责人在审批中心确认，本页不采集。
          </Typography.Paragraph>
        </Form>
      </Modal>
    </Space>
  );
}

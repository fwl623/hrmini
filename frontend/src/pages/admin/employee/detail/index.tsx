/**
 * 员工档案详情页
 * 对接：GET /api/v1/employees/{id}
 * 三 Tab：个人信息/工作信息/薪资合同（对齐 PRD §4.1.2~4.1.4）
 *
 * 可见性与后端 FieldPermissionFilter 对齐：
 * - 合同/账套/试用比例：仅 HR_STAFF
 * - 基本工资：HR_STAFF、FINANCE、FINANCE_MANAGER
 * - 银行信息：HR_STAFF、SYS_ADMIN
 * - SYS_ADMIN 不可看薪资金额与合同明细（PRD）
 * - 薪资档案编辑：HR_STAFF / FINANCE / FINANCE_MANAGER（对齐 JwtAuthFilter）
 */
import React, { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate, useModel } from '@umijs/max';
import {
  Alert,
  Card,
  Tabs,
  Descriptions,
  Tag,
  Button,
  Spin,
  Space,
  message,
  Typography,
  Table,
  Modal,
  Form,
  InputNumber,
  Select,
} from 'antd';
import {
  getEmployeeDetail,
  getTransferHistory,
  getSalaryProfile,
  getSalaryHistory,
  updateSalaryProfile,
  type TransferHistoryItem,
  type SalaryHistoryItem,
  type SalaryProfile,
} from '@/services/employee';
import { getSchemes } from '@/services/payroll';

/** 从津贴 JSON 中读取岗位津贴金额（仅内部解析，界面不展示 JSON） */
function parsePositionAllowance(json?: string | null): number | undefined {
  if (!json?.trim()) return undefined;
  try {
    const obj = JSON.parse(json) as Record<string, unknown>;
    const raw = obj.POSITION_ALLOWANCE ?? obj.positionAllowance;
    if (raw == null || raw === '') return undefined;
    const n = Number(raw);
    return Number.isFinite(n) ? n : undefined;
  } catch {
    return undefined;
  }
}

/** 写回岗位津贴；保留原 JSON 中其他键，界面只维护岗位津贴数字 */
function buildAllowanceJson(
  amount: number | null | undefined,
  previousJson?: string | null,
): string | undefined {
  let base: Record<string, unknown> = {};
  if (previousJson?.trim()) {
    try {
      const parsed = JSON.parse(previousJson);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        base = { ...parsed };
      }
    } catch {
      base = {};
    }
  }
  if (amount == null) {
    delete base.POSITION_ALLOWANCE;
    delete base.positionAllowance;
  } else {
    base.POSITION_ALLOWANCE = amount;
  }
  return Object.keys(base).length > 0 ? JSON.stringify(base) : undefined;
}
import SensitiveField from '@/components/SensitiveField';
import type { EmployeeDetail } from '@/services/employee';
import { ROLES } from '@/constants/roles';
import type { ColumnsType } from 'antd/es/table';

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  probation: { color: 'blue', label: '试用期' },
  regular: { color: 'green', label: '正式' },
  pending_resign: { color: 'orange', label: '待离职' },
  resigned: { color: 'default', label: '已离职' },
};

const CONTRACT_TYPE_MAP: Record<string, string> = {
  FIXED: '固定期限',
  UNFIXED: '无固定期限',
  LABOR: '劳务合同',
};

const FIELD_NAME_MAP: Record<string, string> = {
  baseSalary: '基本工资',
  ssBase: '社保基数',
  hfBase: '公积金基数',
  performanceBase: '绩效基数',
  probationRatio: '试用期比例',
};

function formatMoney(v?: number | null) {
  if (v == null) return '-';
  return `¥${Number(v).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function maskOrValue(canView: boolean, value: React.ReactNode, empty: React.ReactNode = '-') {
  if (!canView) {
    return <Typography.Text type="secondary">无权限</Typography.Text>;
  }
  if (value === null || value === undefined || value === '') {
    return empty;
  }
  return value;
}

const EmployeeDetailPage: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { initialState } = useModel('@@initialState');
  const [detail, setDetail] = useState<EmployeeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [history, setHistory] = useState<TransferHistoryItem[]>([]);
  const [salaryHistory, setSalaryHistory] = useState<SalaryHistoryItem[]>([]);
  const [salaryModalOpen, setSalaryModalOpen] = useState(false);
  const [salaryLoading, setSalaryLoading] = useState(false);
  const [salarySaving, setSalarySaving] = useState(false);
  const [schemeOptions, setSchemeOptions] = useState<{ label: string; value: number }[]>([]);
  const [salaryCreateMode, setSalaryCreateMode] = useState(false);
  const [salaryForm] = Form.useForm();
  /** 编辑时保留原津贴 JSON 中其他键，避免只改岗位津贴时冲掉其余项 */
  const allowanceJsonRef = useRef<string | undefined>(undefined);

  const roleCode = initialState?.currentUser?.roleCode;
  const canSeeContract = roleCode === ROLES.HR_STAFF;
  const canSeeSalary =
    roleCode === ROLES.HR_STAFF ||
    roleCode === ROLES.FINANCE ||
    roleCode === ROLES.FINANCE_MANAGER;
  const canSeeBank = roleCode === ROLES.HR_STAFF || roleCode === ROLES.SYS_ADMIN;
  const canEditSalary = canSeeSalary;

  const reloadDetail = async () => {
    if (!id) return;
    const tasks: Promise<unknown>[] = [
      getEmployeeDetail(Number(id)).then((detailRes) => {
        if (detailRes.code === 0) setDetail(detailRes.data);
      }),
      getTransferHistory(Number(id)).then((histRes) => {
        if (histRes.code === 0) setHistory(histRes.data ?? []);
      }),
    ];
    if (canSeeSalary) {
      tasks.push(
        getSalaryHistory(Number(id))
          .then((res) => {
            if (res.code === 0) setSalaryHistory(res.data ?? []);
          })
          .catch(() => setSalaryHistory([])),
      );
    } else {
      setSalaryHistory([]);
    }
    await Promise.all(tasks);
  };

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    reloadDetail()
      .catch(() => message.error('加载失败'))
      .finally(() => setLoading(false));
  }, [id, canSeeSalary]);

  const openSalaryModal = async () => {
    if (!id) return;
    setSalaryModalOpen(true);
    setSalaryLoading(true);
    setSalaryCreateMode(false);
    try {
      const schemesRes = await getSchemes().catch(() => null);
      const list = ((schemesRes?.data as { list?: { id: number; name: string }[] } | undefined)?.list) ?? [];
      if (list.length > 0) {
        setSchemeOptions(
          list.map((s) => ({
            label: s.name || `账套#${s.id}`,
            value: s.id,
          })),
        );
      }

      let profile: SalaryProfile | null = null;
      try {
        const profileRes = await getSalaryProfile(Number(id), { skipErrorHandler: true });
        if (profileRes.code === 0) {
          profile = profileRes.data;
        }
      } catch (err: any) {
        const code = err?.info?.code;
        if (code === 50003) {
          setSalaryCreateMode(true);
          allowanceJsonRef.current = undefined;
          salaryForm.setFieldsValue({
            schemeId: list[0]?.id,
            baseSalary: undefined,
            ssBase: undefined,
            hfBase: undefined,
            performanceBase: undefined,
            probationRatio: 1,
            positionAllowance: undefined,
          });
          return;
        }
        message.error(err?.info?.message || err?.message || '加载薪资档案失败');
        setSalaryModalOpen(false);
        return;
      }

      if (!profile) {
        message.error('加载薪资档案失败');
        setSalaryModalOpen(false);
        return;
      }

      allowanceJsonRef.current = profile.allowanceBaseJson;
      salaryForm.setFieldsValue({
        schemeId: profile.schemeId,
        baseSalary: profile.baseSalary,
        ssBase: profile.ssBase,
        hfBase: profile.hfBase,
        performanceBase: profile.performanceBase,
        probationRatio: profile.probationRatio,
        positionAllowance: parsePositionAllowance(profile.allowanceBaseJson),
      });

      if (list.length === 0 && profile.schemeId != null) {
        setSchemeOptions([
          {
            label: profile.schemeName || `账套#${profile.schemeId}`,
            value: profile.schemeId,
          },
        ]);
      }
    } catch {
      message.error('加载薪资档案失败');
      setSalaryModalOpen(false);
    } finally {
      setSalaryLoading(false);
    }
  };

  const saveSalaryProfile = async () => {
    if (!id) return;
    try {
      const values = await salaryForm.validateFields();
      setSalarySaving(true);
      const allowanceBaseJson = buildAllowanceJson(
        values.positionAllowance,
        allowanceJsonRef.current,
      );
      const res = await updateSalaryProfile(Number(id), {
        schemeId: values.schemeId,
        baseSalary: values.baseSalary,
        ssBase: values.ssBase,
        hfBase: values.hfBase,
        performanceBase: values.performanceBase,
        probationRatio: values.probationRatio,
        // 显式传字符串（含空对象）以便清空；无津贴时传 "{}" 覆盖旧值
        allowanceBaseJson: allowanceBaseJson ?? '{}',
      });
      if (res.code === 0) {
        message.success(salaryCreateMode ? '薪资档案已创建' : '薪资档案已保存');
        setSalaryModalOpen(false);
        setSalaryCreateMode(false);
        await reloadDetail();
      } else {
        message.error(res.message || '保存失败');
      }
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.message || '保存失败');
    } finally {
      setSalarySaving(false);
    }
  };

  if (loading) return <Spin style={{ display: 'block', marginTop: 100 }} />;
  if (!detail) return <div style={{ textAlign: 'center', marginTop: 100 }}>员工不存在</div>;

  const s = STATUS_MAP[detail.employmentStatus] || { color: 'default', label: detail.employmentStatus };

  const tabItems = [
    {
      key: 'personal',
      label: '个人信息',
      children: (
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="姓名" span={2}>
            {detail.name}
          </Descriptions.Item>
          <Descriptions.Item label="性别">{detail.gender === 'MALE' ? '男' : '女'}</Descriptions.Item>
          <Descriptions.Item label="手机号">{detail.mobile}</Descriptions.Item>
          <Descriptions.Item label="邮箱" span={2}>
            {detail.email}
          </Descriptions.Item>
          <Descriptions.Item label="生日">{detail.birthday || '-'}</Descriptions.Item>
          <Descriptions.Item label="身份证" span={2}>
            <SensitiveField
              employeeId={detail.employeeId}
              field="idNumber"
              label="身份证号"
              defaultValue={detail.idNumber}
            />
          </Descriptions.Item>
          <Descriptions.Item label="户籍地址" span={2}>
            {detail.householdAddress || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="现居地址" span={2}>
            {detail.residenceAddress || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="紧急联系人">{detail.emergencyContact || '-'}</Descriptions.Item>
          <Descriptions.Item label="紧急电话">{detail.emergencyPhone || '-'}</Descriptions.Item>
        </Descriptions>
      ),
    },
    {
      key: 'work',
      label: '工作信息',
      children: (
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="工号">{detail.empNo}</Descriptions.Item>
          <Descriptions.Item label="在职状态">
            <Tag color={s.color}>{s.label}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="部门">{detail.department}</Descriptions.Item>
          <Descriptions.Item label="职位">{detail.position}</Descriptions.Item>
          <Descriptions.Item label="职级">{detail.grade || '-'}</Descriptions.Item>
          <Descriptions.Item label="直接汇报人">{detail.managerName || '-'}</Descriptions.Item>
          <Descriptions.Item label="入职日期">{detail.hireDate}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{detail.createdAt || '-'}</Descriptions.Item>
          <Descriptions.Item label="入职类型">
            {detail.employmentType === 'fulltime'
              ? '全职'
              : detail.employmentType === 'parttime'
                ? '兼职'
                : detail.employmentType === 'intern'
                  ? '实习'
                  : detail.employmentType || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="工作地点">{detail.workLocation || '-'}</Descriptions.Item>
        </Descriptions>
      ),
    },
    {
      key: 'salary',
      label: '薪资合同',
      children: (
        <>
          {roleCode === ROLES.SYS_ADMIN && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message="系统管理员按规范不可查看薪资与合同明细。请使用 HR（13800001001）或财务账号查看。"
            />
          )}
          {canEditSalary && (
            <div style={{ marginBottom: 12, textAlign: 'right' }}>
              <Button type="primary" onClick={openSalaryModal}>
                编辑薪资档案
              </Button>
            </div>
          )}
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="合同类型">
              {maskOrValue(
                canSeeContract,
                CONTRACT_TYPE_MAP[detail.contractType || ''] || detail.contractType,
              )}
            </Descriptions.Item>
            <Descriptions.Item label="合同到期日">
              {maskOrValue(canSeeContract, detail.contractExpireDate)}
            </Descriptions.Item>
            <Descriptions.Item label="试用期待遇比例">
              {maskOrValue(
                canSeeContract,
                detail.probationPayRatio != null
                  ? `${(Number(detail.probationPayRatio) * 100).toFixed(0)}%`
                  : null,
              )}
            </Descriptions.Item>
            <Descriptions.Item label="薪资账套">
              {maskOrValue(
                canSeeContract,
                detail.schemeName || (detail.schemeId != null ? `账套#${detail.schemeId}` : null),
              )}
            </Descriptions.Item>
            <Descriptions.Item label="基本工资">
              {maskOrValue(
                canSeeSalary,
                detail.baseSalary != null
                  ? `¥${Number(detail.baseSalary).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    })}`
                  : null,
              )}
            </Descriptions.Item>
            <Descriptions.Item label="开户行">
              {maskOrValue(canSeeBank, detail.bankName)}
            </Descriptions.Item>
            <Descriptions.Item label="银行账号" span={2}>
              {canSeeBank ? (
                <SensitiveField
                  employeeId={detail.employeeId}
                  field="bankAccount"
                  label="银行账号"
                  defaultValue={detail.bankAccount}
                />
              ) : (
                <Typography.Text type="secondary">无权限</Typography.Text>
              )}
            </Descriptions.Item>
          </Descriptions>
          {canSeeSalary && (
            <>
              <Typography.Title level={5} style={{ marginTop: 24 }}>
                调薪历史
              </Typography.Title>
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                locale={{ emptyText: '暂无调薪记录' }}
                dataSource={salaryHistory}
                columns={
                  [
                    { title: '生效日期', dataIndex: 'effectiveDate', width: 120 },
                    {
                      title: '变更字段',
                      dataIndex: 'fieldName',
                      width: 120,
                      render: (v: string) => FIELD_NAME_MAP[v] || v || '-',
                    },
                    {
                      title: '变更前',
                      dataIndex: 'oldValue',
                      width: 120,
                      render: (v: number) => formatMoney(v),
                    },
                    {
                      title: '变更后',
                      dataIndex: 'newValue',
                      width: 120,
                      render: (v: number) => formatMoney(v),
                    },
                    { title: '原因', dataIndex: 'reason', ellipsis: true },
                    {
                      title: '操作人',
                      dataIndex: 'operatorId',
                      width: 100,
                      render: (v?: number) => (v != null ? `用户#${v}` : '-'),
                    },
                    { title: '记录时间', dataIndex: 'createdAt', width: 180 },
                  ] as ColumnsType<SalaryHistoryItem>
                }
              />
            </>
          )}
        </>
      ),
    },
    {
      key: 'transfer',
      label: '调岗历史',
      children: (
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          locale={{ emptyText: '暂无调岗记录' }}
          dataSource={history}
          columns={
            [
              { title: '调岗日期', dataIndex: 'transferDate', width: 120 },
              {
                title: '部门',
                render: (_: unknown, r: TransferHistoryItem) =>
                  `${r.fromDepartmentName || '-'} → ${r.toDepartmentName || '-'}`,
              },
              {
                title: '职位',
                render: (_: unknown, r: TransferHistoryItem) =>
                  `${r.fromPositionName || '-'} → ${r.toPositionName || '-'}`,
              },
              { title: '原因', dataIndex: 'reason', ellipsis: true },
            ] as ColumnsType<TransferHistoryItem>
          }
        />
      ),
    },
  ];

  return (
    <Card
      title="员工档案详情"
      extra={
        <Space>
          <Button type="primary" onClick={() => navigate(`/admin/employee/${id}/edit`)}>
            编辑
          </Button>
          <Button onClick={() => navigate('/admin/employee/list')}>返回列表</Button>
        </Space>
      }
    >
      <Tabs items={tabItems} />

      <Modal
        title={salaryCreateMode ? '新建薪资档案' : '编辑薪资档案'}
        open={salaryModalOpen}
        onCancel={() => {
          setSalaryModalOpen(false);
          setSalaryCreateMode(false);
        }}
        onOk={saveSalaryProfile}
        confirmLoading={salarySaving}
        destroyOnClose
        width={560}
      >
        <Spin spinning={salaryLoading}>
          <Alert
            type={salaryCreateMode ? 'warning' : 'info'}
            showIcon
            style={{ marginBottom: 16 }}
            message={
              salaryCreateMode
                ? '该员工尚无薪资档案，填写后保存将自动创建。'
                : '修改基本工资会自动写入调薪历史；账套、社保/公积金基数等一并保存。'
            }
          />
          <Form form={salaryForm} layout="vertical" preserve={false}>
            <Form.Item name="schemeId" label="薪资账套" rules={[{ required: true, message: '请选择账套' }]}>
              <Select
                options={schemeOptions}
                placeholder="选择账套"
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>
            <Form.Item
              name="baseSalary"
              label="基本工资"
              rules={[{ required: true, message: '请输入基本工资' }]}
              extra={salaryCreateMode ? '首次创建不写调薪历史' : '变更后会写入调薪历史'}
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="元" />
            </Form.Item>
            <Form.Item name="ssBase" label="社保基数" extra="不填则默认等于基本工资">
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="hfBase" label="公积金基数" extra="不填则默认等于基本工资">
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="performanceBase" label="绩效基数">
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="probationRatio"
              label="试用期比例"
              extra="范围 0.80 ~ 1.00"
            >
              <InputNumber min={0.8} max={1} step={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="positionAllowance"
              label="岗位津贴"
              extra="算薪时按账套中的岗位津贴项取用；可不填"
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} placeholder="元" />
            </Form.Item>
          </Form>
        </Spin>
      </Modal>
    </Card>
  );
};

export default EmployeeDetailPage;

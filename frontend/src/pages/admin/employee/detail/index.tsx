/**
 * 员工档案详情页
 * 对接：GET /api/v1/employees/{id}
 * 三 Tab：个人信息/工作信息/薪资合同（对齐 PRD §4.1.2~4.1.4）
 *
 * 可见性与后端 FieldPermissionFilter 对齐：
 * - 合同/账套/试用比例：仅 HR_STAFF
 * - 基本工资：HR_STAFF、FINANCE
 * - 银行信息：HR_STAFF、SYS_ADMIN
 * - SYS_ADMIN 不可看薪资金额与合同明细（PRD）
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, useModel } from '@umijs/max';
import { Alert, Card, Tabs, Descriptions, Tag, Button, Spin, Space, message, Typography } from 'antd';
import { getEmployeeDetail } from '@/services/employee';
import SensitiveField from '@/components/SensitiveField';
import type { EmployeeDetail } from '@/services/employee';
import { ROLES } from '@/constants/roles';

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

  const roleCode = initialState?.currentUser?.roleCode;
  const canSeeContract = roleCode === ROLES.HR_STAFF;
  const canSeeSalary = roleCode === ROLES.HR_STAFF || roleCode === ROLES.FINANCE;
  const canSeeBank = roleCode === ROLES.HR_STAFF || roleCode === ROLES.SYS_ADMIN;

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getEmployeeDetail(Number(id))
      .then((res) => {
        if (res.code === 0) setDetail(res.data);
      })
      .catch(() => message.error('加载失败'))
      .finally(() => setLoading(false));
  }, [id]);

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
        </>
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
    </Card>
  );
};

export default EmployeeDetailPage;

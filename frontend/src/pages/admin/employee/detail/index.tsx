/**
 * 员工档案详情页
 *
 * 三 Tab：个人信息 / 工作信息 / 薪资合同
 * 敏感字段由后端 FieldPermissionFilter 脱敏
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from '@umijs/max';
import { Card, Tabs, Descriptions, Tag, Button, Spin, Space } from 'antd';
import { getEmployeeDetail } from '@/services/employee';
import type { EmployeeDetail } from '@/services/employee';

const STATUS_LABEL: Record<string, string> = {
  probation: '试用期', regular: '正式',
  pending_resign: '待离职', resigned: '已离职',
};
const STATUS_COLOR: Record<string, string> = {
  probation: 'blue', regular: 'green',
  pending_resign: 'orange', resigned: 'default',
};

const EmployeeDetailPage: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<EmployeeDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (id) {
      setLoading(true);
      getEmployeeDetail(Number(id)).then((res) => {
        if (res.code === 0) setDetail(res.data);
      }).finally(() => setLoading(false));
    }
  }, [id]);

  if (loading) return <Spin style={{ display: 'block', marginTop: 100 }} />;
  if (!detail) return <div>员工不存在</div>;

  const tabItems = [
    {
      key: 'personal',
      label: '个人信息',
      children: (
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="姓名" span={2}>{detail.name}</Descriptions.Item>
          <Descriptions.Item label="性别">{detail.gender === 'MALE' ? '男' : '女'}</Descriptions.Item>
          <Descriptions.Item label="手机号">{detail.mobile}</Descriptions.Item>
          <Descriptions.Item label="邮箱" span={2}>{detail.email}</Descriptions.Item>
          <Descriptions.Item label="生日">{detail.personalInfo?.birthday || '-'}</Descriptions.Item>
          <Descriptions.Item label="户籍地址" span={2}>{detail.personalInfo?.householdAddress || '-'}</Descriptions.Item>
          <Descriptions.Item label="现居地址" span={2}>{detail.personalInfo?.residenceAddress || '-'}</Descriptions.Item>
          <Descriptions.Item label="紧急联系人">{detail.personalInfo?.emergencyContact || '-'}</Descriptions.Item>
          <Descriptions.Item label="紧急电话">{detail.personalInfo?.emergencyPhone || '-'}</Descriptions.Item>
          <Descriptions.Item label="身份证号">
            <span style={{ color: detail.fieldPermissions?.idCard === 'hidden' ? '#999' : undefined }}>
              {detail.personalInfo?.idCard || '***'}
            </span>
          </Descriptions.Item>
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
            <Tag color={STATUS_COLOR[detail.employmentStatus]}>
              {STATUS_LABEL[detail.employmentStatus] || detail.employmentStatus}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="部门">{detail.department}</Descriptions.Item>
          <Descriptions.Item label="职位">{detail.position}</Descriptions.Item>
          <Descriptions.Item label="职级">{detail.grade || '-'}</Descriptions.Item>
          <Descriptions.Item label="直属上级">{detail.managerName || '-'}</Descriptions.Item>
          <Descriptions.Item label="入职日期">{detail.hireDate}</Descriptions.Item>
          <Descriptions.Item label="用工类型">
            {detail.employmentType === 'fulltime' ? '全职' :
             detail.employmentType === 'parttime' ? '兼职' : '实习'}
          </Descriptions.Item>
          <Descriptions.Item label="工作地点" span={2}>{detail.workLocation || '-'}</Descriptions.Item>
        </Descriptions>
      ),
    },
    {
      key: 'salary',
      label: '薪资合同',
      children: (
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="基本工资" span={2}>
            {detail.salaryInfo ? `¥${detail.salaryInfo.baseSalary?.toLocaleString()}` : '***'}
          </Descriptions.Item>
          <Descriptions.Item label="社保基数" span={2}>
            {detail.salaryInfo ? `¥${detail.salaryInfo.ssBase?.toLocaleString()}` : '***'}
          </Descriptions.Item>
          <Descriptions.Item label="公积金基数" span={2}>
            {detail.salaryInfo ? `¥${detail.salaryInfo.hfBase?.toLocaleString()}` : '***'}
          </Descriptions.Item>
          <Descriptions.Item label="试用期比例">
            {detail.salaryInfo ? `${(detail.salaryInfo.probationRatio * 100).toFixed(0)}%` : '***'}
          </Descriptions.Item>
        </Descriptions>
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

/**
 * 员工编辑页
 *
 * 白名单字段可编辑：name, gender, email, birthday, address, emergencyContact, emergencyPhone
 * 非白名单字段 disabled + Tooltip 提示「如需修改请联系 HR」或「请走调岗/离职/手机号变更流程」
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from '@umijs/max';
import {
  Card, Form, Input, Select, DatePicker, Button, Spin, message, Descriptions, Tooltip, Space,
} from 'antd';
import { getEmployeeDetail, updateEmployee } from '@/services/employee';
import type { EmployeeDetail } from '@/services/employee';
import dayjs from 'dayjs';

/** 白名单字段 */
const ALLOWED_FIELDS = new Set([
  'name', 'gender', 'email', 'birthday', 'address',
  'emergencyContact', 'emergencyPhone', 'workLocation',
]);

/** 需走流程字段提示 */
const FLOW_HINTS: Record<string, string> = {
  departmentId: '请走调岗流程（POST /transfers）',
  positionId: '请走调岗流程（POST /transfers）',
  mobile: '请走手机号变更申请',
  idNumber: '身份证号不可编辑',
  grade: '请走调岗流程',
};

const EmployeeEditPage: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [detail, setDetail] = useState<EmployeeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (id) {
      setLoading(true);
      getEmployeeDetail(Number(id)).then((res) => {
        if (res.code === 0) {
          setDetail(res.data);
          form.setFieldsValue({
            name: res.data.name,
            gender: res.data.gender,
            email: res.data.email,
            birthday: res.data.personalInfo?.birthday ? dayjs(res.data.personalInfo.birthday) : undefined,
            address: res.data.personalInfo?.residenceAddress,
            emergencyContact: res.data.personalInfo?.emergencyContact,
            emergencyPhone: res.data.personalInfo?.emergencyPhone,
            workLocation: res.data.workLocation,
          });
        }
      }).finally(() => setLoading(false));
    }
  }, [id]);

  const onFinish = async (values: any) => {
    if (!id) return;
    setSubmitting(true);
    try {
      const payload: any = {};
      Object.keys(values).forEach((key) => {
        if (values[key] !== undefined && values[key] !== null) {
          if (key === 'birthday') {
            payload[key] = values[key].format('YYYY-MM-DD');
          } else {
            payload[key] = values[key];
          }
        }
      });
      const res = await updateEmployee(Number(id), payload);
      if (res.code === 0) {
        message.success('保存成功');
        navigate(`/admin/employee/${id}`);
      } else {
        message.error(res.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <Spin style={{ display: 'block', marginTop: 100 }} />;
  if (!detail) return <div>员工不存在</div>;

  return (
    <Card
      title={`编辑员工 — ${detail.name}（${detail.empNo}）`}
      extra={<Button onClick={() => navigate(`/admin/employee/${id}`)}>取消</Button>}
    >
      <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 600 }}>
        <Descriptions title="个人信息" column={2} bordered size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="工号">{detail.empNo}</Descriptions.Item>
          <Descriptions.Item label="在职状态">{detail.employmentStatus}</Descriptions.Item>
        </Descriptions>

        <Form.Item label="姓名" name="name">
          <Input placeholder="姓名" />
        </Form.Item>

        <Form.Item label="性别" name="gender">
          <Select>
            <Select.Option value="MALE">男</Select.Option>
            <Select.Option value="FEMALE">女</Select.Option>
          </Select>
        </Form.Item>

        <Form.Item label="邮箱" name="email">
          <Input placeholder="email@example.com" />
        </Form.Item>

        <Form.Item label="生日" name="birthday">
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item label="地址" name="address">
          <Input.TextArea rows={3} placeholder="现居地址" />
        </Form.Item>

        <Form.Item label="紧急联系人" name="emergencyContact">
          <Input placeholder="紧急联系人姓名" />
        </Form.Item>

        <Form.Item label="紧急联系电话" name="emergencyPhone">
          <Input placeholder="11位手机号" />
        </Form.Item>

        <Form.Item label="工作地点" name="workLocation">
          <Input placeholder="如：杭州" />
        </Form.Item>

        <Descriptions title="不可编辑字段" column={2} bordered size="small" style={{ marginBottom: 16 }}>
          {[
            { label: '部门', value: detail.department, hint: FLOW_HINTS.departmentId },
            { label: '职位', value: detail.position, hint: FLOW_HINTS.positionId },
            { label: '手机号', value: detail.mobile, hint: FLOW_HINTS.mobile },
            { label: '职级', value: detail.grade, hint: FLOW_HINTS.grade },
          ].map((item) => (
            <Descriptions.Item label={item.label} key={item.label}>
              <Tooltip title={item.hint}>
                <span style={{ color: '#999', cursor: 'help' }}>{item.value}</span>
              </Tooltip>
            </Descriptions.Item>
          ))}
        </Descriptions>

        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>保存</Button>
          <Button onClick={() => navigate(`/admin/employee/${id}`)}>取消</Button>
        </Space>
      </Form>
    </Card>
  );
};

export default EmployeeEditPage;

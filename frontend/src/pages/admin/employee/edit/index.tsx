/**
 * 员工编辑页
 * 对接：GET → PUT /api/v1/employees/{id}
 * 白名单字段可编辑，非白名单 disabled + Tooltip
 */
import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from '@umijs/max';
import {
  Card, Form, Input, Select, DatePicker, Button, Spin, message, Descriptions, Tooltip, Space,
} from 'antd';
import { getEmployeeDetail, updateEmployee } from '@/services/employee';
import type { EmployeeDetail } from '@/services/employee';
import dayjs from 'dayjs';

// TODO: org接口未完成 — departmentId/positionId 的禁用+Tooltip 展示依赖 org 模块组织树数据，后续可增加「点击跳转调岗申请页」链接
const FLOW_HINTS: Record<string, string> = {
  departmentId: '请走调岗流程（POST /transfers）',
  positionId:   '请走调岗流程（POST /transfers）',
  mobile:       '手机号变更请提交 MOBILE_CHANGE 申请',
  idNumber:     '身份证号不可编辑',
  grade:        '请走调岗流程',
};

const EmployeeEditPage: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [detail, setDetail] = useState<EmployeeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getEmployeeDetail(Number(id)).then((res) => {
      if (res.code === 0) {
        setDetail(res.data);
        form.setFieldsValue({
          name: res.data.name,
          gender: res.data.gender,
          email: res.data.email,
          birthday: res.data.birthday ? dayjs(res.data.birthday) : undefined,
          residenceAddress: res.data.residenceAddress,
          emergencyContact: res.data.emergencyContact,
          emergencyPhone: res.data.emergencyPhone,
          workLocation: res.data.workLocation,
        });
      }
    }).finally(() => setLoading(false));
  }, [id]);

  const onFinish = async (values: any) => {
    if (!id) return;
    setSubmitting(true);
    try {
      const payload: Record<string, any> = {};
      Object.entries(values).forEach(([k, v]) => {
        if (v !== undefined && v !== null) {
          payload[k] = k === 'birthday' ? dayjs(v as any).format('YYYY-MM-DD') : v;
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
  if (!detail) return <div style={{ textAlign: 'center', marginTop: 100 }}>员工不存在</div>;

  return (
    <Card
      title={`编辑员工 — ${detail.name}（${detail.empNo}）`}
      extra={<Button onClick={() => navigate(`/admin/employee/${id}`)}>取消</Button>}
    >
      <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 600 }}>
        <Descriptions title="基础信息" column={2} bordered size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="工号">{detail.empNo}</Descriptions.Item>
          <Descriptions.Item label="在职状态">{detail.employmentStatus === 'regular' ? '正式' : detail.employmentStatus}</Descriptions.Item>
        </Descriptions>

        <Form.Item label="姓名" name="name"><Input /></Form.Item>
        <Form.Item label="性别" name="gender">
          <Select><Select.Option value="MALE">男</Select.Option><Select.Option value="FEMALE">女</Select.Option></Select>
        </Form.Item>
        <Form.Item label="邮箱" name="email"><Input placeholder="email@example.com" /></Form.Item>
        <Form.Item label="生日" name="birthday"><DatePicker style={{ width: '100%' }} /></Form.Item>
        <Form.Item label="现居地址" name="residenceAddress"><Input.TextArea rows={2} /></Form.Item>
        <Form.Item label="紧急联系人" name="emergencyContact"><Input /></Form.Item>
        <Form.Item label="紧急电话" name="emergencyPhone"><Input /></Form.Item>
        <Form.Item label="工作地点" name="workLocation"><Input placeholder="如：杭州" /></Form.Item>

        <Descriptions title="不可编辑（须走流程）" column={2} bordered size="small" style={{ marginBottom: 16 }}>
          {[
            // TODO: org接口未完成 — department/position display 值由后端 JOIN 返回，独立 org 查询待联调
          { label: '部门', value: detail.department, key: 'departmentId' },
            { label: '职位', value: detail.position, key: 'positionId' },
            { label: '手机号', value: detail.mobile, key: 'mobile' },
            { label: '职级', value: detail.grade, key: 'grade' },
          ].map((item) => (
            <Descriptions.Item label={item.label} key={item.key}>
              <Tooltip title={FLOW_HINTS[item.key] || '如需修改请联系 HR'}>
                <span style={{ color: '#999', cursor: 'help' }}>{item.value || '-'}</span>
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

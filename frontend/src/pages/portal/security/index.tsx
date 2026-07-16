/**
 * 账号安全（员工门户）
 * 对接：PUT /profile/security/password
 *       POST /profile/security/mobile/bind
 *       GET /profile/security/login-logs
 *
 * 依赖 A 组 (hrms-auth) 登录功能：需先登录获取 Token
 * 当前为 Sprint 1，登录模块未完成，页面可预览骨架
 */
import React, { useEffect, useState } from 'react';
import { Card, Form, Input, Button, message, Table, Typography, Space, Divider, Tag, Modal } from 'antd';
import { changePassword, bindMobile, getMyLoginLogs } from '@/services/employee';
import type { LoginLogVO } from '@/services/employee';
import { history } from '@umijs/max';

const SecurityPage: React.FC = () => {
  const [pwdForm] = Form.useForm();
  const [mobileForm] = Form.useForm();
  const [loginLogs, setLoginLogs] = useState<LoginLogVO[]>([]);
  const [logLoading, setLogLoading] = useState(false);

  // 修改密码
  const handleChangePassword = async (values: any) => {
    if (values.newPassword !== values.confirmPassword) {
      message.warning('两次输入的密码不一致');
      return;
    }
    try {
      const res = await changePassword(values);
      if (res.code === 0) {
        message.success('密码修改成功');
        pwdForm.resetFields();
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('修改密码失败');
    }
  };

  // 绑定手机
  const handleBindMobile = async (values: any) => {
    try {
      const res = await bindMobile(values);
      if (res.code === 0) {
        message.success('手机绑定成功');
        mobileForm.resetFields();
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('绑定失败');
    }
  };

  // 加载登录日志
  const loadLoginLogs = async () => {
    setLogLoading(true);
    try {
      const res = await getMyLoginLogs();
      if (res.code === 0) setLoginLogs(res.data || []);
    } finally {
      setLogLoading(false);
    }
  };

  useEffect(() => { loadLoginLogs(); }, []);

  const logColumns = [
    { title: '登录时间', dataIndex: 'loginTime', width: 180 },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '设备', dataIndex: 'device', width: 120 },
    { title: '地点', dataIndex: 'location', width: 120 },
    {
      title: '结果', dataIndex: 'success', width: 80,
      render: (s: boolean) => s ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
    },
  ];

  return (
    <>
      <Typography.Title level={4}>账号安全</Typography.Title>
      <Typography.Paragraph type="secondary">
        登录功能由 A 组（hrms-auth）提供，当前页面为骨架预览，联调时需先登录获取 Token。
      </Typography.Paragraph>

      {/* 修改密码 */}
      <Card title="修改密码" style={{ marginBottom: 16 }}>
        <Form form={pwdForm} layout="vertical" onFinish={handleChangePassword} style={{ maxWidth: 400 }}>
          <Form.Item name="oldPassword" label="当前密码" rules={[{ required: true, message: '请输入当前密码' }]}>
            <Input.Password placeholder="当前密码" />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
            <Input.Password placeholder="8位以上，含大小写字母+数字" />
          </Form.Item>
          <Form.Item name="confirmPassword" label="确认新密码" rules={[{ required: true, message: '请再次输入新密码' }]}>
            <Input.Password placeholder="再次输入新密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit">修改密码</Button>
          </Form.Item>
        </Form>
      </Card>

      {/* 绑定手机 */}
      <Card title="绑定手机号" style={{ marginBottom: 16 }}>
        <Form form={mobileForm} layout="vertical" onFinish={handleBindMobile} style={{ maxWidth: 400 }}>
          <Form.Item name="mobile" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}>
            <Input placeholder="11位手机号" />
          </Form.Item>
          <Form.Item name="smsCode" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
            <Input placeholder="短信验证码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit">绑定手机</Button>
          </Form.Item>
        </Form>
      </Card>

      {/* 登录日志 */}
      <Card title="登录日志">
        <Table
          dataSource={loginLogs}
          columns={logColumns}
          rowKey="loginTime"
          loading={logLoading}
          size="small"
          pagination={{ pageSize: 10 }}
        />
      </Card>
    </>
  );
};

export default SecurityPage;

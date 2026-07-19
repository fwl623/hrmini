/**
 * 账号安全（员工门户）
 * 对接：PUT /profile/security/password
 *       POST /profile/security/mobile/bind（仅首次绑定）
 *       GET /profile/security/login-logs
 *       GET /profile/me（判断是否已绑定手机）
 *
 * 已绑定手机号：只读展示 + 申请变更（MOBILE_CHANGE），不可直接改绑。
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Card, Form, Input, Button, message, Table, Typography, Tag, Descriptions, Space } from 'antd';
import {
  changePassword,
  bindMobile,
  getMyLoginLogs,
  getMyProfile,
} from '@/services/employee';
import type { LoginLogVO, ProfileVO } from '@/services/employee';
import MobileChangeModal from '@/components/MobileChangeModal';
import { forceLogout } from '@/utils/authSession';

const SecurityPage: React.FC = () => {
  const [pwdForm] = Form.useForm();
  const [mobileForm] = Form.useForm();
  const [loginLogs, setLoginLogs] = useState<LoginLogVO[]>([]);
  const [logLoading, setLogLoading] = useState(false);
  const [profile, setProfile] = useState<ProfileVO | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [bindSubmitting, setBindSubmitting] = useState(false);
  const [mobileModalOpen, setMobileModalOpen] = useState(false);

  const loadProfile = useCallback(() => {
    setProfileLoading(true);
    getMyProfile()
      .then((res) => {
        if (res.code === 0) setProfile(res.data);
      })
      .finally(() => setProfileLoading(false));
  }, []);

  const loadLoginLogs = useCallback(async () => {
    setLogLoading(true);
    try {
      const res = await getMyLoginLogs();
      const raw = (res as any)?.data;
      const list = Array.isArray(raw) ? raw : raw?.list || [];
      if ((res as any)?.code === 0 || Array.isArray(list)) {
        setLoginLogs(list);
      }
    } catch (err: any) {
      message.error(err?.message || '加载登录日志失败');
    } finally {
      setLogLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProfile();
    loadLoginLogs();
  }, [loadProfile, loadLoginLogs]);

  const handleChangePassword = async (values: {
    oldPassword: string;
    newPassword: string;
    confirmPassword: string;
  }) => {
    if (values.newPassword !== values.confirmPassword) {
      message.warning('两次输入的密码不一致');
      return;
    }
    try {
      const res = await changePassword(values);
      if (res.code === 0) {
        message.success('密码已修改，请重新登录');
        pwdForm.resetFields();
        // 后端已拉黑 Token；清本地态并跳转登录页
        await forceLogout('密码已修改，请重新登录');
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('修改密码失败');
    }
  };

  const handleBindMobile = async (values: { mobile: string; smsCode: string }) => {
    setBindSubmitting(true);
    try {
      const res = await bindMobile(values);
      if (res.code === 0) {
        message.success('手机绑定成功');
        mobileForm.resetFields();
        loadProfile();
      } else {
        message.error(res.message);
      }
    } catch (e) {
      message.error((e as Error)?.message || '绑定失败');
    } finally {
      setBindSubmitting(false);
    }
  };

  // 已有手机号（含脱敏展示）即视为已绑定；变更只能走申请，不可再出绑定表单
  const mobileBound =
    profile?.mobileBound === true ||
    (typeof profile?.mobile === 'string' && profile.mobile.trim().length > 0);

  const logColumns = [
    { title: '登录时间', dataIndex: 'loginTime', width: 180 },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    { title: '设备', dataIndex: 'device', width: 120 },
    { title: '地点', dataIndex: 'location', width: 120 },
    {
      title: '结果',
      dataIndex: 'success',
      width: 80,
      render: (s: boolean) => (s ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>),
    },
  ];

  return (
    <>
      <Typography.Title level={4}>账号安全</Typography.Title>
      <Typography.Paragraph type="secondary">
        账号安全：改密、手机号与登录日志。手机号规则与「我的档案」一致——仅未绑定时可首次绑定；已绑定后只能申请变更。
      </Typography.Paragraph>

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
            <Button type="primary" htmlType="submit">
              修改密码
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card
        title="手机号"
        loading={profileLoading}
        style={{ marginBottom: 16 }}
        extra={
          mobileBound ? (
            <Button type="link" size="small" onClick={() => setMobileModalOpen(true)}>
              申请变更手机号
            </Button>
          ) : null
        }
      >
        {mobileBound ? (
          <Descriptions column={1} size="small">
            <Descriptions.Item label="当前手机号">{profile?.mobile || '-'}</Descriptions.Item>
            <Descriptions.Item label="说明">
              已绑定。更换请点右上角「申请变更手机号」（须 HR 审批），此处不能直接改绑。
            </Descriptions.Item>
          </Descriptions>
        ) : profileLoading ? null : (
          <>
            <Typography.Paragraph type="secondary">
              当前账号尚未绑定手机号，仅此时可做首次绑定；绑定后如需更换必须走变更申请。
            </Typography.Paragraph>
            <Form form={mobileForm} layout="vertical" onFinish={handleBindMobile} style={{ maxWidth: 400 }}>
              <Form.Item
                name="mobile"
                label="手机号"
                rules={[
                  { required: true, message: '请输入手机号' },
                  { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
                ]}
              >
                <Input placeholder="11位手机号" maxLength={11} />
              </Form.Item>
              <Form.Item name="smsCode" label="验证码" rules={[{ required: true, message: '请输入验证码' }]}>
                <Input placeholder="短信验证码（联调可用 123456）" maxLength={6} />
              </Form.Item>
              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={bindSubmitting}>
                    首次绑定
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </>
        )}
      </Card>

      <Card title="登录日志">
        <Table
          dataSource={loginLogs}
          columns={logColumns}
          rowKey={(r) => `${r.loginTime}-${r.ip}`}
          loading={logLoading}
          size="small"
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <MobileChangeModal
        open={mobileModalOpen}
        onClose={() => setMobileModalOpen(false)}
        onSuccess={loadProfile}
      />
    </>
  );
};

export default SecurityPage;

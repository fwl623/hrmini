import { LockOutlined, MobileOutlined } from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { Alert, Button, Card, Checkbox, Form, Input, Typography, message } from 'antd';
import React, { useEffect, useState } from 'react';
import ChangePasswordModal from '@/components/ChangePasswordModal';
import { getProfile, login, toCurrentUser } from '@/services/auth';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { forceLogout, getHomePath } from '@/utils/authSession';
import { startIdleDetector } from '@/utils/idleDetector';
import {
  getAccessToken,
  getRememberedUsername,
  isRememberMe,
  setRememberedUsername,
  setTokens,
  clearRememberedUsername,
} from '@/utils/token';
import { startTokenRefresher } from '@/utils/tokenRefresher';
import { getRequestErrorMessage } from '@/utils/requestError';

interface LoginForm {
  username: string;
  password: string;
  remember: boolean;
}

const LoginPage: React.FC = () => {
  const [form] = Form.useForm<LoginForm>();
  const [submitting, setSubmitting] = useState(false);
  const [changePwdOpen, setChangePwdOpen] = useState(false);
  const { setInitialState } = useModel('@@initialState');

  useEffect(() => {
    const bootstrapIfLoggedIn = async () => {
      if (!getAccessToken()) {
        return;
      }
      try {
        const profileRes = await getProfile();
        if (profileRes.code === 0 && profileRes.data) {
          history.replace(getHomePath(profileRes.data.roles));
        }
      } catch {
        // token 无效则留在登录页
      }
    };

    void bootstrapIfLoggedIn();

    const params = new URLSearchParams(window.location.search);
    const msg = params.get('msg');
    if (msg) {
      message.warning(msg);
    }
    form.setFieldsValue({
      username: getRememberedUsername(),
      remember: isRememberMe(),
    });
  }, [form]);

  const bootstrapSession = async (
    accessToken: string,
    refreshToken: string,
    remember: boolean,
    mustChangePassword: boolean,
  ) => {
    setTokens(accessToken, refreshToken, remember);
    if (remember) {
      setRememberedUsername(form.getFieldValue('username'));
    } else {
      clearRememberedUsername();
    }

    const { getProfile: fetchProfile } = await import('@/services/auth');
    const profileRes = await fetchProfile();
    if (profileRes.code !== 0 || !profileRes.data) {
      throw new Error(profileRes.message || '获取用户信息失败');
    }

    const currentUser = toCurrentUser(profileRes.data);
    useUserStore.getState().setCurrentUser(currentUser);
    usePermissionStore.getState().setPermissions(currentUser.permissions);
    await setInitialState((s: API.InitialState | undefined) => ({ ...s, currentUser }));
    startTokenRefresher();
    startIdleDetector();

    // 部门经理/HR/财务等管理端角色进后台工作台，仅普通员工进门户
    const home = getHomePath(currentUser.roles);
    if (mustChangePassword || currentUser.mustChangePassword) {
      setChangePwdOpen(true);
      return;
    }
    message.success('登录成功');
    history.replace(home);
  };

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true);
    try {
      const res = await login({
        username: values.username.trim(),
        password: values.password,
      });
      if (res.code !== 0 || !res.data) {
        message.error(res.message || '登录失败');
        return;
      }
      await bootstrapSession(
        res.data.accessToken,
        res.data.refreshToken,
        values.remember,
        res.data.mustChangePassword,
      );
    } catch (err: unknown) {
      message.error(getRequestErrorMessage(err, '登录失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const handlePasswordChanged = async () => {
    setChangePwdOpen(false);
    await forceLogout();
    message.info('请使用新密码重新登录');
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #f0f5ff 0%, #ffffff 60%)',
        padding: 24,
      }}
    >
      <Card style={{ width: 420 }}>
        <Typography.Title level={3} style={{ marginBottom: 4, textAlign: 'center' }}>
          HRMini
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center', marginBottom: 24 }}>
          人力资源管理系统
        </Typography.Paragraph>

        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 20 }}
          message="联调账号：13800001000 / Admin@12345（管理员登录后进入管理后台）"
        />

        <Form<LoginForm>
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ remember: true }}
        >
          <Form.Item
            name="username"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号' },
            ]}
          >
            <Input prefix={<MobileOutlined />} placeholder="登录账号（手机号）" maxLength={11} />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item name="remember" valuePropName="checked">
            <Checkbox>记住登录（7 天内免登录）</Checkbox>
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={submitting}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <ChangePasswordModal
        open={changePwdOpen}
        force
        onSuccess={handlePasswordChanged}
      />
    </div>
  );
};

export default LoginPage;

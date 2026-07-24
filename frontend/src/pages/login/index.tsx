import { LockOutlined, MobileOutlined } from '@ant-design/icons';
import { history, useModel } from '@umijs/max';
import { Button, Checkbox, Form, Input, message } from 'antd';
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
import TrackingOwl from './TrackingOwl';

/**
 * 登录页：居中卡片 + 猫头鹰站在卡片上方，双眼跟随鼠标。
 */
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
          history.replace(getHomePath(profileRes.data.roles, profileRes.data.permissions));
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

    const home = getHomePath(currentUser.roles, currentUser.permissions);
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
    <div className="hrms-login-page hrms-login-page--center">
      <div className="hrms-login-atmosphere" aria-hidden>
        <span className="hrms-login-orb hrms-login-orb--1" />
        <span className="hrms-login-orb hrms-login-orb--2" />
        <span className="hrms-login-orb hrms-login-orb--3" />
        <span className="hrms-login-ring hrms-login-ring--1" />
        <span className="hrms-login-ring hrms-login-ring--2" />
        <span className="hrms-login-beam" />
        <span className="hrms-login-grid" />
        <span className="hrms-login-noise" />
      </div>

      <div className="hrms-login-stage">
        <div className="hrms-login-owl-perch">
          <TrackingOwl />
        </div>

        <div className="hrms-login-card">
          <header className="hrms-login-card-head">
            <h1>HRMini</h1>
          </header>

          <Form<LoginForm>
            className="hrms-login-form"
            form={form}
            layout="vertical"
            onFinish={onFinish}
            initialValues={{ remember: true }}
            size="large"
          >
            <Form.Item
              name="username"
              label="手机号"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1\d{10}$/, message: '请输入 11 位手机号' },
              ]}
            >
              <Input prefix={<MobileOutlined />} placeholder="请输入手机号" maxLength={11} />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" />
            </Form.Item>
            <Form.Item name="remember" valuePropName="checked" className="hrms-login-remember">
              <Checkbox>记住登录</Checkbox>
            </Form.Item>
            <Form.Item className="hrms-login-submit">
              <Button type="primary" htmlType="submit" block loading={submitting}>
                登录
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>

      <ChangePasswordModal
        open={changePwdOpen}
        force
        onSuccess={handlePasswordChanged}
      />
    </div>
  );
};

export default LoginPage;

import { history, Outlet, useLocation, useModel } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect } from 'react';
import { ADMIN_ROLES, ROLES, type RoleCode } from '@/constants/roles';
import { getAccessToken } from '@/utils/token';

const AuthWrapper: React.FC = () => {
  const location = useLocation();
  const token = getAccessToken();
  const { initialState, loading } = useModel('@@initialState');

  useEffect(() => {
    if (!token) {
      history.replace('/login');
    }
  }, [token]);

  if (!token) {
    return null;
  }

  if (loading || !initialState?.currentUser) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
        <Spin size="large" tip="加载用户信息..." />
      </div>
    );
  }

  const roles = initialState.currentUser.roles ?? [];
  const isAdminRoute = location.pathname.startsWith('/admin');
  const hasAdminRole = roles.some((role: string) => ADMIN_ROLES.includes(role as RoleCode));

  if (isAdminRoute && !hasAdminRole) {
    if (roles.includes(ROLES.EMPLOYEE)) {
      history.replace('/portal/profile');
      return null;
    }
    history.replace('/login');
    return null;
  }

  return <Outlet />;
};

export default AuthWrapper;

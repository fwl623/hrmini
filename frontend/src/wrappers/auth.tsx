import { history, Outlet, useLocation, useModel } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect } from 'react';
import { ADMIN_ROLES, hasAdminEntryPermission, type RoleCode } from '@/constants/roles';
import { getHomePath } from '@/utils/authSession';
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
  const permissions = initialState.currentUser.permissions ?? [];
  const isAdminRoute = location.pathname.startsWith('/admin');
  const hasAdminRole = roles.some((role: string) => ADMIN_ROLES.includes(role as RoleCode));
  const canEnterAdmin = hasAdminRole || hasAdminEntryPermission(permissions);

  // 无管理端角色且无管理端权限码时不可进 /admin
  if (isAdminRoute && !canEnterAdmin) {
    history.replace(getHomePath(roles, permissions));
    return null;
  }

  return <Outlet />;
};

export default AuthWrapper;

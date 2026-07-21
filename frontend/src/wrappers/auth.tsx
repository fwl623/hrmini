import { history, Outlet, useLocation, useModel } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect } from 'react';
import { ADMIN_ROLES, ROLES, type RoleCode } from '@/constants/roles';
import { getHomePath, normalizeRoles } from '@/utils/authSession';
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

  const roles = normalizeRoles(initialState.currentUser.roles);
  const isAdminRoute = location.pathname.startsWith('/admin');
  const hasAdminRole =
    roles.some((role) => ADMIN_ROLES.includes(role as RoleCode)) ||
    roles.includes(ROLES.DEPT_MANAGER);

  // 无管理端角色时不可进 /admin，按角色回首页（员工→门户，其它→登录由 getHomePath 兜底）
  if (isAdminRoute && !hasAdminRole) {
    history.replace(getHomePath(roles));
    return null;
  }

  // 部门经理等管理端角色误入门户时，拉回管理后台
  const isPortalRoute = location.pathname.startsWith('/portal');
  if (isPortalRoute && hasAdminRole) {
    history.replace('/admin/workbench');
    return null;
  }

  return <Outlet />;
};

export default AuthWrapper;

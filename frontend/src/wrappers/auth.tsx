import { history, Outlet, useLocation, useModel } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect } from 'react';
import { ADMIN_ROLES, canAccessAdmin, isEmployeeOnly, type RoleCode } from '@/constants/roles';
import { getHomePath, normalizeRoles } from '@/utils/authSession';
import { getAccessToken } from '@/utils/token';

/**
 * 路由鉴权包装：无 Token 跳登录；按角色/权限码区分 /admin 与 /portal。
 * - 纯 EMPLOYEE：禁止进管理端
 * - 管理端角色误入门户：拉回工作台
 * - 菜单细粒度仍由 access.ts + AdminLayout 控制
 */
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
  const permissions = initialState.currentUser.permissions ?? [];
  const isAdminRoute = location.pathname.startsWith('/admin');
  const isPortalRoute = location.pathname.startsWith('/portal');
  const employeeOnly = isEmployeeOnly(roles);
  const hasAdminRole = roles.some((role) => ADMIN_ROLES.includes(role as RoleCode));
  const canEnterAdmin = canAccessAdmin(roles, permissions);

  // 纯普通员工：禁止进入 /admin，强制回门户
  if (employeeOnly && isAdminRoute) {
    history.replace('/portal/profile');
    return null;
  }

  // 无管理端入口时不可进 /admin
  if (isAdminRoute && !canEnterAdmin) {
    history.replace(getHomePath(roles, permissions));
    return null;
  }

  // 管理端角色误入门户时拉回工作台（纯员工可留在门户）
  if (isPortalRoute && hasAdminRole && !employeeOnly) {
    history.replace('/admin/workbench');
    return null;
  }

  return <Outlet />;
};

export default AuthWrapper;

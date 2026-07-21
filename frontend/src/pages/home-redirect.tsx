/**
 * 根路径智能跳转：已登录按角色进首页，未登录进登录页。
 * 管理员 → /admin/workbench；普通员工 → /portal/profile
 */
import { history, useModel } from '@umijs/max';
import { Spin } from 'antd';
import React, { useEffect } from 'react';
import { getProfile, toCurrentUser } from '@/services/auth';
import { getHomePath } from '@/utils/authSession';
import { getAccessToken } from '@/utils/token';

const HomeRedirect: React.FC = () => {
  const { initialState, setInitialState } = useModel('@@initialState');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!getAccessToken()) {
        history.replace('/login');
        return;
      }
      const cachedRoles = initialState?.currentUser?.roles;
      const cachedPermissions = initialState?.currentUser?.permissions;
      if (cachedRoles?.length) {
        history.replace(getHomePath(cachedRoles, cachedPermissions));
        return;
      }
      try {
        const res = await getProfile();
        if (cancelled) return;
        if (res.code === 0 && res.data) {
          const currentUser = toCurrentUser(res.data);
          await setInitialState((s: API.InitialState | undefined) => ({ ...s, currentUser }));
          history.replace(getHomePath(currentUser.roles, currentUser.permissions));
          return;
        }
      } catch {
        // ignore
      }
      if (!cancelled) {
        history.replace('/login');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [initialState?.currentUser?.roles, setInitialState]);

  return (
    <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 120 }}>
      <Spin size="large" tip="正在进入系统..." />
    </div>
  );
};

export default HomeRedirect;

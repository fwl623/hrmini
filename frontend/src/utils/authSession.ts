import { history } from '@umijs/max';
import {
  ROLES,
  canAccessAdmin,
  isEmployeeOnly,
  resolvePrimaryRole,
} from '@/constants/roles';
import { logout } from '@/services/auth';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { stopIdleDetector } from '@/utils/idleDetector';
import { getAccessToken, clearTokens } from '@/utils/token';
import { stopTokenRefresher } from '@/utils/tokenRefresher';

/** 规范化角色列表（兼容异常返回；统一大写便于比对） */
export function normalizeRoles(roles: unknown): string[] {
  if (!Array.isArray(roles)) {
    return [];
  }
  return roles
    .map((r) => String(r).trim().toUpperCase())
    .filter(Boolean);
}

/**
 * 登录后首页：
 * - 仅普通员工 → 员工门户（强制，不受权限码影响）
 * - 有管理端角色 / 管理端权限码 → 管理后台工作台
 */
export function getHomePath(roles: string[] = [], permissions: string[] = []): string {
  const list = normalizeRoles(roles);
  // 纯员工只能进门户
  if (isEmployeeOnly(list)) {
    return '/portal/profile';
  }
  if (canAccessAdmin(list, permissions)) {
    return '/admin/workbench';
  }
  const primary = resolvePrimaryRole(list);
  if (primary === ROLES.EMPLOYEE || list.includes(ROLES.EMPLOYEE)) {
    return '/portal/profile';
  }
  return '/admin/workbench';
}

export function clearAuthState() {
  clearTokens();
  useUserStore.getState().clearUser();
  usePermissionStore.getState().clear();
  stopTokenRefresher();
  stopIdleDetector();
}

/** 先调服务端 logout 黑名单，再清本地态 */
export async function forceLogout(message?: string) {
  if (getAccessToken()) {
    try {
      await logout();
    } catch {
      // 网络失败仍清本地
    }
  }
  clearAuthState();
  const search = message ? `?msg=${encodeURIComponent(message)}` : '';
  if (window.location.pathname !== '/login') {
    history.replace(`/login${search}`);
  }
}

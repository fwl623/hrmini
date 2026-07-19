import { history } from '@umijs/max';
import { ADMIN_ROLES, ROLES, resolvePrimaryRole, type RoleCode } from '@/constants/roles';
import { logout } from '@/services/auth';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { stopIdleDetector } from '@/utils/idleDetector';
import { getAccessToken, clearTokens } from '@/utils/token';
import { stopTokenRefresher } from '@/utils/tokenRefresher';

/** 规范化角色列表（兼容异常返回） */
export function normalizeRoles(roles: unknown): string[] {
  if (!Array.isArray(roles)) {
    return [];
  }
  return roles.map((r) => String(r)).filter(Boolean);
}

/**
 * 登录后首页：
 * - 有管理端角色（SYS_ADMIN / HR / 主管 / 财务）→ 管理后台工作台
 * - 仅普通员工 → 员工门户
 */
export function getHomePath(roles: string[] = []): string {
  const list = normalizeRoles(roles);
  const hasAdminRole = list.some((role) => ADMIN_ROLES.includes(role as RoleCode));
  if (hasAdminRole) {
    return '/admin/workbench';
  }
  const primary = resolvePrimaryRole(list);
  if (primary === ROLES.EMPLOYEE || list.includes(ROLES.EMPLOYEE)) {
    return '/portal/profile';
  }
  // 无角色时也进登录后的管理端占位，由鉴权再拦
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

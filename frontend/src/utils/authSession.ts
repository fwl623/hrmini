import { history } from '@umijs/max';
import { ADMIN_ROLES, ROLES, type RoleCode } from '@/constants/roles';
import { logout } from '@/services/auth';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { stopIdleDetector } from '@/utils/idleDetector';
import { getAccessToken, clearTokens } from '@/utils/token';
import { stopTokenRefresher } from '@/utils/tokenRefresher';

export function getHomePath(roles: string[] = []): string {
  const hasAdminRole = roles.some((role) => ADMIN_ROLES.includes(role as RoleCode));
  if (!hasAdminRole && roles.includes(ROLES.EMPLOYEE)) {
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

import type { RequestConfig } from '@umijs/max';
import { history, request as umiRequest } from '@umijs/max';
import { message } from 'antd';
import { getProfile, toCurrentUser } from '@/services/auth';
import { usePermissionStore } from '@/stores/permissionStore';
import { useUserStore } from '@/stores/userStore';
import { refreshAccessToken } from '@/utils/authRefresh';
import { clearAuthState, forceLogout } from '@/utils/authSession';
import { startIdleDetector } from '@/utils/idleDetector';
import { getAccessToken } from '@/utils/token';
import { startTokenRefresher } from '@/utils/tokenRefresher';
import {
  getRequestErrorMessage,
  isAuthEndpoint,
  isUnauthorizedError,
} from '@/utils/requestError';

const PUBLIC_PATHS = ['/login'];

export async function getInitialState(): Promise<API.InitialState> {
  const fetchUserInfo = async () => {
    try {
      const res = await getProfile();
      if (res.code !== 0 || !res.data) {
        clearAuthState();
        return undefined;
      }
      const currentUser = toCurrentUser(res.data);
      useUserStore.getState().setCurrentUser(currentUser);
      usePermissionStore.getState().setPermissions(currentUser.permissions);
      startTokenRefresher();
      startIdleDetector();
      return currentUser;
    } catch {
      clearAuthState();
      return undefined;
    }
  };

  if (!getAccessToken()) {
    return { fetchUserInfo };
  }

  const currentUser = await fetchUserInfo();
  return { currentUser, fetchUserInfo };
}

export function onRouteChange({ location }: { location: { pathname: string } }) {
  const token = getAccessToken();
  if (!token && !PUBLIC_PATHS.includes(location.pathname)) {
    history.replace('/login');
  }
}

export const request: RequestConfig = {
  timeout: 30000,
  errorConfig: {
    errorThrower(res: unknown) {
      const data = res as API.Result<unknown>;
      if (data && typeof data.code === 'number' && data.code !== 0) {
        const error = new Error(data.message || '请求失败') as Error & {
          name: string;
          info: API.Result<unknown>;
        };
        error.name = data.code === 20001 ? 'UnauthorizedError' : 'BizError';
        error.info = data;
        throw error;
      }
    },
    errorHandler: async (error: Error & { name?: string; info?: API.Result<unknown> }, opts: { skipErrorHandler?: boolean; url?: string; [key: string]: unknown }) => {
      if (opts?.skipErrorHandler) {
        throw error;
      }

      const url = opts?.url;

      if (isUnauthorizedError(error) && !isAuthEndpoint(url)) {
        const refreshed = await refreshAccessToken();
        if (refreshed && url) {
          return umiRequest(url, {
            ...opts,
            skipErrorHandler: false,
          });
        }
        await forceLogout('登录已过期，请重新登录');
        return;
      }

      if (error.name === 'BizError') {
        message.error(getRequestErrorMessage(error));
        return;
      }

      message.error(getRequestErrorMessage(error, '网络异常'));
    },
  },
  requestInterceptors: [
    (url: string, options: Record<string, unknown>) => {
      const headers = { ...(options.headers as Record<string, string>) };
      const token = getAccessToken();
      if (token && !isAuthEndpoint(url)) {
        headers.Authorization = `Bearer ${token}`;
      }
      return { url, options: { ...options, headers } };
    },
  ],
};

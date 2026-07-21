import type { RequestConfig } from '@umijs/max';
import { history, request as umiRequest } from '@umijs/max';
import { message, Modal } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
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

dayjs.locale('zh-cn');

/** Ant Design 全局中文 + 主题配置（简约商务蓝白风） */
export const antd = {
  locale: zhCN,
  theme: {
    token: {
      colorPrimary: '#165DFF',
      borderRadius: 6,
      colorBgContainer: '#FFFFFF',
      colorBgLayout: '#F5F7FA',
      colorText: '#1D2129',
      colorTextSecondary: '#4E5969',
      colorBorder: '#E5E6EB',
      colorBorderSecondary: '#F0F1F3',
      colorSplit: '#F0F1F3',
      colorSuccess: '#00B42A',
      colorWarning: '#FF7D00',
      colorError: '#F53F3F',
      colorInfo: '#165DFF',
      fontSize: 14,
      controlHeight: 36,
      boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
    },
    components: {
      Layout: {
        headerBg: '#FFFFFF',
        siderBg: '#001529',
        bodyBg: '#F5F7FA',
        headerHeight: 60,
      },
      Menu: {
        itemHeight: 42,
        collapsedWidth: 60,
        darkItemBg: 'transparent',
        darkItemColor: 'rgba(255,255,255,0.65)',
        darkItemHoverBg: 'rgba(255,255,255,0.08)',
        darkItemHoverColor: 'rgba(255,255,255,0.9)',
        darkItemSelectedBg: '#165DFF',
        darkItemSelectedColor: '#FFFFFF',
        darkSubMenuItemBg: 'transparent',
      },
      Table: {
        headerBg: '#F7F8FA',
        headerColor: '#4E5969',
        rowHoverBg: '#E8F0FF',
        borderColor: '#F0F1F3',
        cellPaddingBlock: 11,
        cellPaddingInline: 16,
      },
      Card: {
        paddingLG: 20,
        paddingMD: 16,
        borderRadiusLG: 8,
      },
      Button: {
        borderRadiusLG: 6,
        borderRadiusSM: 4,
        controlHeight: 36,
        controlHeightSM: 30,
        controlHeightLG: 42,
        primaryShadow: 'none',
      },
      Input: {
        controlHeight: 36,
        borderRadius: 6,
        activeShadow: '0 0 0 2px rgba(22,93,255,0.1)',
      },
      Select: {
        controlHeight: 36,
        borderRadius: 6,
      },
      DatePicker: {
        controlHeight: 36,
        borderRadius: 6,
        activeShadow: '0 0 0 2px rgba(22,93,255,0.1)',
      },
      Modal: {
        borderRadiusLG: 8,
        paddingContentHorizontalLG: 24,
        paddingMD: 24,
      },
      Pagination: {
        borderRadius: 6,
        itemSize: 32,
      },
      Tabs: {
        inkBarColor: '#165DFF',
        itemSelectedColor: '#165DFF',
        itemHoverColor: '#165DFF',
      },
      Tag: {
        borderRadiusSM: 4,
      },
      Alert: {
        borderRadiusLG: 6,
      },
      Badge: {
        dotSize: 8,
      },
      Steps: {
        finishIconBorderColor: '#165DFF',
        finishIconBg: '#E8F0FF',
      },
    },
  },
};

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
    errorHandler: async (error: Error & { name?: string; info?: API.Result<unknown>; response?: { data?: Record<string, unknown> } }, opts: { skipErrorHandler?: boolean; url?: string; [key: string]: unknown }) => {
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

      // 重复打卡检测：匹配错误码 40005 或错误消息含"重复操作"（"您已打卡，请勿重复操作"）
      const msg = getRequestErrorMessage(error);
      const isDupPunch = error.info?.code === 40005
        || (error.response?.data?.code as number) === 40005
        || msg?.includes('重复操作');

      if (isDupPunch) {
        Modal.warning({
          title: '打卡提示',
          content: '您已打卡，请勿重复操作',
          okText: '知道了',
        });
        return;
      }

      if (error.name === 'BizError') {
        message.error(msg);
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

import { refreshToken as refreshAuthToken } from '@/services/auth';
import { getRefreshToken, isRememberMe, setTokens } from '@/utils/token';
import { startTokenRefresher } from '@/utils/tokenRefresher';

let refreshingPromise: Promise<boolean> | null = null;

async function doRefresh(): Promise<boolean> {
  const rt = getRefreshToken();
  if (!rt) return false;
  try {
    const data = await refreshAuthToken({ refreshToken: rt });
    setTokens(data.accessToken, data.refreshToken, isRememberMe());
    startTokenRefresher();
    return true;
  } catch {
    return false;
  }
}

/** 并发 401 时合并为一次 refresh */
export function refreshAccessToken(): Promise<boolean> {
  if (!refreshingPromise) {
    refreshingPromise = doRefresh().finally(() => {
      refreshingPromise = null;
    });
  }
  return refreshingPromise;
}

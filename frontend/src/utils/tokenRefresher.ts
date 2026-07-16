import { refreshToken } from '@/services/auth';
import { refreshAccessToken } from '@/utils/authRefresh';
import { getAccessToken, getTokenExpiryMs } from '@/utils/token';

let timer: ReturnType<typeof setTimeout> | null = null;

async function doScheduledRefresh() {
  const ok = await refreshAccessToken();
  if (ok) {
    scheduleTokenRefresh();
  }
}

export function scheduleTokenRefresh() {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
  const token = getAccessToken();
  if (!token) return;
  const expMs = getTokenExpiryMs(token);
  if (!expMs) return;
  const delay = Math.max(expMs - Date.now() - 60_000, 5_000);
  timer = setTimeout(() => {
    void doScheduledRefresh();
  }, delay);
}

export function startTokenRefresher() {
  scheduleTokenRefresh();
}

export function stopTokenRefresher() {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
}

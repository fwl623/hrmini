const ACCESS_KEY = 'hrms_access_token';
const REFRESH_KEY = 'hrms_refresh_token';
const REMEMBER_KEY = 'hrms_remember';
const USERNAME_KEY = 'hrms_username';

function activeStorage(): Storage {
  return localStorage.getItem(REMEMBER_KEY) === '1' ? localStorage : sessionStorage;
}

export function setTokens(accessToken: string, refreshToken: string, remember: boolean) {
  localStorage.setItem(REMEMBER_KEY, remember ? '1' : '0');
  const storage = remember ? localStorage : sessionStorage;
  const other = remember ? sessionStorage : localStorage;
  storage.setItem(ACCESS_KEY, accessToken);
  storage.setItem(REFRESH_KEY, refreshToken);
  other.removeItem(ACCESS_KEY);
  other.removeItem(REFRESH_KEY);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY) || sessionStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY) || sessionStorage.getItem(REFRESH_KEY);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  sessionStorage.removeItem(ACCESS_KEY);
  sessionStorage.removeItem(REFRESH_KEY);
}

export function isRememberMe(): boolean {
  return localStorage.getItem(REMEMBER_KEY) === '1';
}

export function setRememberedUsername(username: string) {
  localStorage.setItem(USERNAME_KEY, username);
}

export function getRememberedUsername(): string {
  return localStorage.getItem(USERNAME_KEY) || '';
}

export function clearRememberedUsername() {
  localStorage.removeItem(USERNAME_KEY);
}

/** JWT payload exp（毫秒） */
export function getTokenExpiryMs(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return typeof json.exp === 'number' ? json.exp * 1000 : null;
  } catch {
    return null;
  }
}

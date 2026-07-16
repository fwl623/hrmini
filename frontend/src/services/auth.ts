import { request } from '@umijs/max';
import { API_BASE, resolvePrimaryRole } from '@/constants/roles';

export async function login(data: API.LoginRequest) {
  return request<API.Result<API.LoginResponse>>(`${API_BASE}/auth/login`, {
    method: 'POST',
    data,
    skipErrorHandler: true,
  });
}

export async function logout() {
  return request<API.Result<void>>(`${API_BASE}/auth/logout`, {
    method: 'POST',
  });
}

export async function refreshToken(data: API.RefreshTokenRequest) {
  const res = await request<API.Result<API.LoginResponse>>(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    data,
    skipErrorHandler: true,
  });
  if (res.code !== 0 || !res.data) {
    const error = new Error(res.message || '刷新 Token 失败') as Error & { info?: API.Result<unknown> };
    error.name = res.code === 20001 ? 'UnauthorizedError' : 'BizError';
    error.info = res;
    throw error;
  }
  return res.data;
}

export async function getProfile() {
  return request<API.Result<API.ProfileResponse>>(`${API_BASE}/auth/profile`, {
    method: 'GET',
    skipErrorHandler: true,
  });
}

export async function changePassword(data: API.ChangePasswordRequest) {
  // 旧密码错误后端也返回 HTTP 401，勿走全局 refresh；由调用方展示 message
  return request<API.Result<void>>(`${API_BASE}/auth/password`, {
    method: 'PUT',
    data,
    skipErrorHandler: true,
  });
}

export async function verifyPassword(password: string) {
  return request<API.Result<void>>(`${API_BASE}/auth/verify`, {
    method: 'POST',
    data: {
      verifyType: 'PASSWORD',
      verifyCode: password,
    },
  });
}

/** 将 profile 转为 Umi initialState 用户结构 */
export function toCurrentUser(profile: API.ProfileResponse): API.CurrentUser {
  const roles = profile.roles ?? [];
  const roleCode = resolvePrimaryRole(roles);
  return {
    userId: profile.userId,
    employeeId: profile.employeeId,
    username: profile.username,
    roles,
    roleCode,
    permissions: profile.permissions ?? [],
    dataScope: profile.dataScope,
    mustChangePassword: profile.mustChangePassword,
    passwordExpiredAt: profile.passwordExpiredAt,
  };
}

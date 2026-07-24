import { request } from '@umijs/max';
import { API_BASE, resolvePrimaryRole } from '@/constants/roles';
import { encryptLoginPassword } from '@/utils/loginCrypto';
import { getAccessToken } from '@/utils/token';

export async function getLoginPublicKey() {
  return request<API.Result<API.LoginPublicKey>>(`${API_BASE}/auth/crypto/public-key`, {
    method: 'GET',
    skipErrorHandler: true,
  });
}

/** 登录：先拉公钥，RSA 加密 password 后再提交（encrypted=true） */
export async function login(data: API.LoginRequest) {
  const keyRes = await getLoginPublicKey();
  if (keyRes.code !== 0 || !keyRes.data?.publicKey) {
    throw new Error(keyRes.message || '获取登录公钥失败');
  }
  const cipher = await encryptLoginPassword(data.password, keyRes.data.publicKey);
  return request<API.Result<API.LoginResponse>>(`${API_BASE}/auth/login`, {
    method: 'POST',
    data: {
      username: data.username,
      password: cipher,
      encrypted: true,
    },
    skipErrorHandler: true,
  });
}

export async function logout() {
  return request<API.Result<void>>(`${API_BASE}/auth/logout`, {
    method: 'POST',
  });
}

export async function refreshToken(data: API.RefreshTokenRequest) {
  const oldAccess = getAccessToken();
  const res = await request<API.Result<API.LoginResponse>>(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    data,
    skipErrorHandler: true,
    headers: oldAccess ? { Authorization: `Bearer ${oldAccess}` } : undefined,
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
  const roles = Array.isArray(profile.roles) ? profile.roles.map(String) : [];
  const roleCode = resolvePrimaryRole(roles);
  return {
    userId: profile.userId,
    employeeId: profile.employeeId,
    username: profile.username,
    roles,
    roleCode,
    permissions: Array.isArray(profile.permissions) ? profile.permissions.map(String) : [],
    dataScope: profile.dataScope,
    mustChangePassword: profile.mustChangePassword,
    passwordExpiredAt: profile.passwordExpiredAt,
  };
}

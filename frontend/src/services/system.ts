/**
 * 系统管理 API（用户 / 角色 / 权限 / 日志）
 */
import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';

export interface SystemUser {
  id: number;
  username: string;
  employeeId?: number;
  status: number;
  roles?: string[];
}

export interface SystemRole {
  id: number;
  code: string;
  name: string;
  dataScope?: string;
  permissionIds?: number[];
}

export interface SystemPermission {
  id: number;
  code: string;
  name: string;
  module: string;
  type: string;
}

export interface LoginLogItem {
  id: number;
  userId: number;
  loginTime: string;
  loginIp?: string;
  userAgent?: string;
  device?: string;
  location?: string;
  success: number;
  failReason?: string;
}

export interface OperationLogItem {
  id: number;
  userId: number;
  module: string;
  action: string;
  targetId?: string;
  requestIp?: string;
  detail?: string;
  createdAt: string;
}

export async function listUsers(params: { keyword?: string; page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<SystemUser>>>(`${API_BASE}/system/users`, {
    method: 'GET',
    params,
  });
}

export async function createUser(data: {
  username: string;
  employeeId: number;
  roleIds: number[];
  password?: string;
}) {
  return request<API.Result<{ userId: number }>>(`${API_BASE}/system/users`, {
    method: 'POST',
    data,
  });
}

export async function updateUser(
  id: number,
  data: { status?: number; roleIds?: number[] },
) {
  return request<API.Result<void>>(`${API_BASE}/system/users/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function listRoles() {
  return request<API.Result<SystemRole[]>>(`${API_BASE}/system/roles`, {
    method: 'GET',
  });
}

export async function updateRole(id: number, data: { name: string }) {
  return request<API.Result<void>>(`${API_BASE}/system/roles/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function updateRolePermissions(id: number, permissionIds: number[]) {
  return request<API.Result<void>>(`${API_BASE}/system/roles/${id}/permissions`, {
    method: 'PUT',
    data: { permissionIds },
  });
}

export async function listPermissions() {
  return request<API.Result<SystemPermission[]>>(`${API_BASE}/system/permissions`, {
    method: 'GET',
  });
}

export async function listLoginLogs(params: { page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<LoginLogItem>>>(`${API_BASE}/system/login-logs`, {
    method: 'GET',
    params,
  });
}

export async function listOperationLogs(params: { page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<OperationLogItem>>>(`${API_BASE}/system/operation-logs`, {
    method: 'GET',
    params,
  });
}

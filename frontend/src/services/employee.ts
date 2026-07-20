/**
 * 员工档案 API
 * Base URL: /api/v1/employees + /api/v1/profile
 */
import { request } from '@umijs/max';

// ===== 通用类型 =====
export interface Result<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  timestamp?: number;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

// ===== 员工管理 =====
export interface EmployeeItem {
  employeeId: number;
  empNo: string;
  name: string;
  department: string;
  position: string;
  grade: string;
  employmentStatus: string;
  hireDate: string;
}

export interface EmployeeDetail {
  employeeId: number;
  empNo: string;
  name: string;
  gender: string;
  mobile: string;
  email: string;
  departmentId: number;
  department: string;
  positionId: number;
  position: string;
  grade: string;
  managerId: number;
  managerName: string;
  workLocation: string;
  employmentType: string;
  employmentStatus: string;
  hireDate: string;
  createdAt?: string;
  probationPayRatio?: number;
  contractType?: string;
  contractExpireDate?: string;
  schemeId?: number;
  schemeName?: string;
  baseSalary?: number;
  idNumber?: string;
  birthday?: string;
  householdAddress?: string;
  residenceAddress?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  bankAccount?: string;
  bankName?: string;
  fieldPermissions?: Record<string, string>;
}

export interface EmployeeEditParams {
  name?: string;
  gender?: string;
  email?: string;
  birthday?: string;
  householdAddress?: string;
  residenceAddress?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
}

/** 花名册分页+高级搜索 */
export async function getEmployeeList(params: {
  keyword?: string;
  departmentIds?: string;
  positionIds?: string;
  employmentStatus?: string;
  gradeLevels?: string;
  hireDateFrom?: string;
  hireDateTo?: string;
  page?: number;
  pageSize?: number;
}): Promise<Result<PageResult<EmployeeItem>>> {
  return request('/api/v1/employees', { method: 'GET', params });
}

/** 员工详情 */
export async function getEmployeeDetail(id: number): Promise<Result<EmployeeDetail>> {
  return request(`/api/v1/employees/${id}`, { method: 'GET' });
}

/** 编辑员工（白名单） */
export async function updateEmployee(id: number, data: EmployeeEditParams): Promise<Result<void>> {
  return request(`/api/v1/employees/${id}`, { method: 'PUT', data });
}

/** 薪资档案 */
export async function getSalaryProfile(id: number): Promise<Result<any>> {
  return request(`/api/v1/employees/${id}/salary`, { method: 'GET' });
}
export async function updateSalaryProfile(id: number, data: any): Promise<Result<void>> {
  return request(`/api/v1/employees/${id}/salary`, { method: 'PUT', data });
}

/** 敏感字段 */
export async function getSensitiveField(id: number, field: string): Promise<Result<any>> {
  return request(`/api/v1/employees/${id}/sensitive/${field}`, { method: 'GET' });
}

/** 调岗历史 */
export interface TransferHistoryItem {
  id: number;
  employeeId: number;
  transferAppId?: number;
  fromDepartmentId?: number;
  fromDepartmentName?: string;
  toDepartmentId?: number;
  toDepartmentName?: string;
  fromPositionId?: number;
  fromPositionName?: string;
  toPositionId?: number;
  toPositionName?: string;
  transferDate?: string;
  reason?: string;
}

export async function getTransferHistory(id: number): Promise<Result<TransferHistoryItem[]>> {
  return request(`/api/v1/employees/${id}/transfer-history`, { method: 'GET' });
}

/** 门户：本人调岗历史 */
export async function getMyTransferHistory(): Promise<Result<TransferHistoryItem[]>> {
  return request('/api/v1/profile/transfer-history', { method: 'GET' });
}

/** HR手机号变更待办 */
export async function getMobileChangeApps(): Promise<Result<any[]>> {
  return request('/api/v1/employees/mobile-change-applications', { method: 'GET' });
}

// ===== 个人中心 =====
export interface ProfileVO {
  employeeId: number;
  empNo: string;
  name: string;
  mobile: string;
  /** 已绑定则不可直接 bind，须走变更申请 */
  mobileBound?: boolean;
  email: string;
  department?: string;
  position?: string;
  /** 基本工资（只读） */
  baseSalary?: number;
  grade?: string;
  hireDate?: string;
  residenceAddress?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  editableFields?: string[];
}

export interface LoginLogVO {
  loginTime: string;
  ip: string;
  device?: string;
  location?: string;
  success: boolean;
}

/** 本人档案 */
export async function getMyProfile(): Promise<Result<ProfileVO>> {
  return request('/api/v1/profile/me', { method: 'GET' });
}

/** 编辑本人档案 */
export async function updateMyProfile(data: {
  email?: string;
  residenceAddress?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
}): Promise<Result<void>> {
  return request('/api/v1/profile/me', { method: 'PUT', data });
}

/** 修改密码 */
export async function changePassword(data: {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}): Promise<Result<void>> {
  return request('/api/v1/profile/security/password', { method: 'PUT', data });
}

/** 绑定手机 */
export async function bindMobile(data: { mobile: string; smsCode: string }): Promise<Result<void>> {
  return request('/api/v1/profile/security/mobile/bind', { method: 'POST', data });
}

/** 本人登录日志 */
export async function getMyLoginLogs(): Promise<Result<LoginLogVO[]>> {
  return request('/api/v1/profile/security/login-logs', { method: 'GET' });
}

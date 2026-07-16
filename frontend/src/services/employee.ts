/**
 * 员工档案 API 定义（Mock 占位）
 *
 * 联调前：UMI_APP_API_BASE 指向 Apifox Mock
 * 联调后：指向 http://localhost:8080/api/v1
 */
import { request } from '@umijs/max';
import type { EmployeeItem, EmployeeDetail, EmployeeEditParams } from './types';

/** 员工列表分页查询 */
export async function getEmployeeList(
  params: {
    keyword?: string;
    departmentIds?: string;
    positionIds?: string;
    employmentStatus?: string;
    gradeLevels?: string;
    hireDateFrom?: string;
    hireDateTo?: string;
    page?: number;
    pageSize?: number;
  },
  options?: Record<string, any>,
): Promise<Result<PageResult<EmployeeItem>>> {
  return request('/api/v1/employees', {
    method: 'GET',
    params,
    ...(options || {}),
  });
}

/** 员工详情 */
export async function getEmployeeDetail(
  id: number,
  options?: Record<string, any>,
): Promise<Result<EmployeeDetail>> {
  return request(`/api/v1/employees/${id}`, {
    method: 'GET',
    ...(options || {}),
  });
}

/** 编辑员工（白名单字段） */
export async function updateEmployee(
  id: number,
  data: EmployeeEditParams,
  options?: Record<string, any>,
): Promise<Result<{ updatedFields: string[] }>> {
  return request(`/api/v1/employees/${id}`, {
    method: 'PUT',
    data,
    ...(options || {}),
  });
}

/** 薪资档案 */
export async function getSalaryProfile(id: number) {
  return request(`/api/v1/employees/${id}/salary`, { method: 'GET' });
}

export async function updateSalaryProfile(id: number, data: any) {
  return request(`/api/v1/employees/${id}/salary`, { method: 'PUT', data });
}

/** 敏感字段 */
export async function getSensitiveField(id: number, field: string) {
  return request(`/api/v1/employees/${id}/sensitive/${field}`, { method: 'GET' });
}

/** 手机号变更待办列表 */
export async function getMobileChangeApps() {
  return request('/api/v1/employees/mobile-change-applications', { method: 'GET' });
}

/** 调岗历史 */
export async function getTransferHistory(id: number) {
  return request(`/api/v1/employees/${id}/transfer-history`, { method: 'GET' });
}

// ===== 类型定义 =====

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
  personalInfo?: {
    idCard?: string;
    birthday?: string;
    householdAddress?: string;
    residenceAddress?: string;
    emergencyContact?: string;
    emergencyPhone?: string;
  };
  salaryInfo?: any;
  fieldPermissions?: Record<string, string>;
}

export interface EmployeeEditParams {
  name?: string;
  gender?: string;
  email?: string;
  birthday?: string;
  address?: string;
  emergencyContact?: string;
  emergencyPhone?: string;
  workLocation?: string;
}

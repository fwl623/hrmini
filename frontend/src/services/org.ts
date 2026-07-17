/**
 * 组织架构 API（部门 + 职位）
 * 对齐 HRMS-API-Contract §6.2 / DeptController / PositionController
 */
import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';

/** 职位序列 */
export type PositionSequence = 'M' | 'P' | 'S';

export interface DeptTreeNode {
  id: number;
  name: string;
  code: string;
  parentId?: number | null;
  level?: number;
  headEmployeeId?: number | null;
  description?: string | null;
  headcount: number;
  headcountIncludingSub: number;
  manager?: string | null;
  sortOrder?: number;
  children?: DeptTreeNode[];
}

export interface DeptHeadcount {
  departmentId: number;
  headcount: number;
  headcountIncludingSub: number;
}

export interface DeptCanDelete {
  canDelete: boolean;
  hasChildren: boolean;
  activeEmployeeCount: number;
  reason?: string | null;
}

export interface CreateDeptParams {
  name: string;
  deptCode: string;
  parentId?: number | null;
  headEmployeeId?: number | null;
  sortOrder: number;
  description?: string;
}

export type UpdateDeptParams = CreateDeptParams;

/** 部门树（含人数、负责人；树节点另含 parentId/level/description/headEmployeeId 便于详情拼装） */
export async function getDeptTree() {
  return request<API.Result<DeptTreeNode[]>>(`${API_BASE}/departments/tree`, {
    method: 'GET',
  });
}

export async function getDept(id: number) {
  return request<API.Result<DeptTreeNode>>(`${API_BASE}/departments/${id}`, {
    method: 'GET',
  });
}

export async function getDeptHeadcount(id: number) {
  return request<API.Result<DeptHeadcount>>(`${API_BASE}/departments/${id}/headcount`, {
    method: 'GET',
  });
}

export async function getDeptCanDelete(id: number) {
  return request<API.Result<DeptCanDelete>>(`${API_BASE}/departments/${id}/can-delete`, {
    method: 'GET',
  });
}

export async function createDept(data: CreateDeptParams) {
  return request<API.Result<{ id: number }>>(`${API_BASE}/departments`, {
    method: 'POST',
    data,
  });
}

export async function updateDept(id: number, data: UpdateDeptParams) {
  return request<API.Result<void>>(`${API_BASE}/departments/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteDept(id: number) {
  return request<API.Result<void>>(`${API_BASE}/departments/${id}`, {
    method: 'DELETE',
  });
}

export async function mergeDept(id: number, targetDepartmentId: number) {
  return request<API.Result<void>>(`${API_BASE}/departments/${id}/merge`, {
    method: 'PUT',
    data: { targetDepartmentId },
  });
}

// ========== 职位 ==========

export interface PositionVO {
  id: number;
  name: string;
  sequenceCode: PositionSequence;
  departmentId?: number | null;
  gradeMin: string;
  gradeMax: string;
  defaultProbationMonths: number;
  isStandard: boolean;
  description?: string | null;
  /** 归属人数：试用+正式+待离职，不含已离职（字段名 activeCount） */
  activeCount?: number;
}

export interface PositionQuery {
  page?: number;
  pageSize?: number;
  departmentId?: number;
  sequenceCode?: PositionSequence;
}

export interface CreatePositionParams {
  name: string;
  sequenceCode: PositionSequence;
  departmentId?: number | null;
  gradeMin: string;
  gradeMax: string;
  defaultProbationMonths: number;
  isStandard: boolean;
  description?: string;
}

export type UpdatePositionParams = CreatePositionParams;

export async function listPositions(params: PositionQuery = {}) {
  return request<API.Result<API.Page<PositionVO>>>(`${API_BASE}/positions`, {
    method: 'GET',
    params,
  });
}

export async function getPosition(id: number) {
  return request<API.Result<PositionVO>>(`${API_BASE}/positions/${id}`, {
    method: 'GET',
  });
}

export async function createPosition(data: CreatePositionParams) {
  return request<API.Result<{ id: number }>>(`${API_BASE}/positions`, {
    method: 'POST',
    data,
  });
}

export async function updatePosition(id: number, data: UpdatePositionParams) {
  return request<API.Result<void>>(`${API_BASE}/positions/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deletePosition(id: number) {
  return request<API.Result<void>>(`${API_BASE}/positions/${id}`, {
    method: 'DELETE',
  });
}

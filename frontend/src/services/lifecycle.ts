/**
 * 转正 / 调岗 / 离职 API
 * Base: /api/v1
 */
import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';

export interface PendingRegularizationItem {
  employeeId: number;
  empNo?: string;
  name: string;
  departmentId?: number;
  positionId?: number;
  hireDate?: string;
  probationEndDate?: string;
  employmentStatus?: string;
  overdue?: boolean;
}

export interface RegularizationItem {
  id: number;
  employeeId: number;
  employeeName?: string;
  empNo?: string;
  status: string;
  approvalResult?: string;
  extendMonths?: number;
  performanceEvaluation?: string;
  salaryAdjustment?: number;
  probationStartDate?: string;
  probationEndDate?: string;
  instanceId?: number;
  createdAt?: string;
  nextAction?: string;
}

export interface RegularizationCreateBody {
  employeeId: number;
  performanceEvaluation: string;
  salaryAdjustment?: number;
  approvalResult: 'PASS' | 'EXTEND' | 'FAIL';
  extendMonths?: number;
}

export interface TransferItem {
  id: number;
  employeeId: number;
  employeeName?: string;
  status: string;
  fromDepartmentId?: number;
  newDepartmentId: number;
  newPositionId?: number;
  newJobLevel?: string;
  newManagerId?: number;
  salaryAdjustment?: number;
  effectiveDate: string;
  reason: string;
  instanceId?: number;
  currentNodeLabel?: string;
  nodes?: { order: number; label: string; status: string }[];
  createdAt?: string;
}

export interface TransferCreateBody {
  employeeId: number;
  newDepartmentId: number;
  newPositionId?: number;
  newJobLevel?: string;
  newManagerId?: number;
  salaryAdjustment?: number;
  effectiveDate: string;
  reason: string;
}

export interface ResignationRequestItem {
  id: number;
  employeeId: number;
  employeeName?: string;
  status: string;
  expectedResignDate: string;
  reasonCategory: string;
  resignationType: string;
  reasonDetail?: string;
  instanceId?: number;
  createdAt?: string;
}

export interface ResignationItem {
  id: number;
  requestId: number;
  employeeId: number;
  employeeName?: string;
  status: string;
  resignationDate: string;
  reasonCategory: string;
  resignationType: string;
  reasonDetail?: string;
  handoverEmployeeId: number;
  instanceId?: number;
  createdAt?: string;
}

export interface ResignationStats {
  pendingRequest: number;
  approving: number;
  pendingResign: number;
  resignedThisMonth: number;
}

type PageData<T> = { list: T[]; total: number; page: number; pageSize: number };

export async function fetchPendingRegularization() {
  const res = await request<API.Result<PendingRegularizationItem[]>>(
    `${API_BASE}/regularization/applications/pending`,
    { method: 'GET' },
  );
  return res.data ?? [];
}

export async function fetchRegularizationList(params?: {
  page?: number;
  pageSize?: number;
  status?: string;
}) {
  const res = await request<API.Result<PageData<RegularizationItem>>>(
    `${API_BASE}/regularization/applications`,
    { method: 'GET', params },
  );
  return res.data;
}

export async function createRegularization(data: RegularizationCreateBody) {
  return request<API.Result<RegularizationItem>>(`${API_BASE}/regularization/applications`, {
    method: 'POST',
    data,
  });
}

export async function fetchTransfers(params?: { page?: number; pageSize?: number; status?: string }) {
  const res = await request<API.Result<PageData<TransferItem>>>(`${API_BASE}/transfers`, {
    method: 'GET',
    params,
  });
  return res.data;
}

export async function fetchTransferDetail(id: number) {
  const res = await request<API.Result<TransferItem>>(`${API_BASE}/transfers/${id}`, {
    method: 'GET',
  });
  return res.data;
}

export async function createTransfer(data: TransferCreateBody) {
  return request<API.Result<TransferItem>>(`${API_BASE}/transfers`, {
    method: 'POST',
    data,
  });
}

export async function fetchResignationRequests(params?: {
  page?: number;
  pageSize?: number;
  status?: string;
}) {
  const res = await request<API.Result<PageData<ResignationRequestItem>>>(
    `${API_BASE}/resignation-requests`,
    { method: 'GET', params },
  );
  return res.data;
}

export async function fetchResignations(params?: {
  page?: number;
  pageSize?: number;
  status?: string;
}) {
  const res = await request<API.Result<PageData<ResignationItem>>>(`${API_BASE}/resignations`, {
    method: 'GET',
    params,
  });
  return res.data;
}

export async function fetchResignationStats() {
  const res = await request<API.Result<ResignationStats>>(`${API_BASE}/resignations/stats`, {
    method: 'GET',
  });
  return res.data;
}

export async function createResignation(data: {
  employeeId: number;
  /** 可选；不传表示 HR 直提正式离职（线下协商） */
  requestId?: number;
  resignationDate: string;
  reasonCategory: string;
  resignationType: string;
  reasonDetail?: string;
  /** 可选；交接人由部门负责人在审批时确认 */
  handoverEmployeeId?: number;
}) {
  return request<API.Result<ResignationItem>>(`${API_BASE}/resignations`, {
    method: 'POST',
    data,
  });
}

export async function fetchMyResignationRequests(params?: { page?: number; pageSize?: number }) {
  const res = await request<API.Result<PageData<ResignationRequestItem>>>(
    `${API_BASE}/profile/resignation-requests`,
    { method: 'GET', params },
  );
  return res.data;
}

export async function createMyResignationRequest(data: {
  expectedResignDate: string;
  reasonCategory: string;
  resignationType: string;
  reasonDetail?: string;
}) {
  return request<API.Result<ResignationRequestItem>>(`${API_BASE}/profile/resignation-requests`, {
    method: 'POST',
    data,
  });
}

export async function cancelMyResignationRequest(id: number) {
  return request<API.Result<null>>(`${API_BASE}/profile/resignation-requests/${id}/cancel`, {
    method: 'POST',
  });
}

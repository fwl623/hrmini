/**
 * 审批中心 + 入职申请 API
 * Base: /api/v1
 */
import { request } from '@umijs/max';

const APPROVAL_PREFIX = '/api/v1/approvals';
const ONBOARDING_PREFIX = '/api/v1/onboarding/applications';

export type ProcessType =
  | 'ONBOARDING'
  | 'REGULARIZATION'
  | 'TRANSFER'
  | 'RESIGNATION'
  | 'RESIGNATION_REQUEST'
  | 'MOBILE_CHANGE'
  | 'LEAVE'
  | 'MAKEUP'
  | 'OVERTIME'
  | 'PAYROLL_BATCH';

export interface ApprovalTaskItem {
  taskId: number;
  instanceId: number;
  processType: ProcessType | string;
  title: string;
  applicantName: string;
  applicantDept?: string;
  businessNo?: string;
  businessSummary?: string;
  currentNodeLabel: string;
  createTime: string;
  dueAt?: string;
  status: string;
}

export interface ApprovalActionPayload {
  action: 'APPROVE' | 'REJECT' | 'FORWARD';
  comment?: string;
  targetUserId?: number;
}

export interface ApprovalTaskStats {
  pending: number;
  approvedToday: number;
  overdueCount: number;
}

export interface ApprovalTimelineItem {
  node: string;
  assignee: string;
  action: string;
  comment?: string;
  time?: string;
  displayText?: string;
}

export interface ApprovalTaskDetail {
  task: {
    id: number;
    status: string;
    currentNodeLabel: string;
    dueAt?: string;
  };
  instance: {
    processType: string;
    businessNo: string;
    initiator: string;
    createdAt: string;
    status?: string;
  };
  businessDetail: Record<string, unknown>;
  timeline: ApprovalTimelineItem[];
  actions: string[];
}

export interface OnboardingForm {
  name: string;
  gender: string;
  mobile: string;
  email: string;
  idNumber: string;
  expectedOnboardDate: string;
  departmentId: number;
  positionId: number;
  employmentType: string;
  probationMonths: number;
  probationSalaryRatio: number;
  managerId?: number;
  baseSalary: number;
  positionStandard?: boolean;
}

/** GET /approvals/tasks/stats */
export async function fetchTaskStats() {
  const res = await request<API.Result<ApprovalTaskStats>>(`${APPROVAL_PREFIX}/tasks/stats`, {
    method: 'GET',
    headers: { 'X-User-Id': '1002' },
  });
  return res.data;
}

/** GET /approvals/tasks */
export async function fetchTasks(params?: {
  status?: string;
  processType?: ProcessType | string;
  type?: ProcessType | string;
  keyword?: string;
  page?: number;
  pageSize?: number;
}) {
  const res = await request<
    API.Result<{ list: ApprovalTaskItem[]; total: number; page: number; pageSize: number }>
  >(`${APPROVAL_PREFIX}/tasks`, {
    method: 'GET',
    params,
    headers: { 'X-User-Id': '1002' },
  });
  return res.data;
}

/** GET /approvals/tasks/:id */
export async function fetchTaskDetail(taskId: number) {
  const res = await request<API.Result<ApprovalTaskDetail>>(`${APPROVAL_PREFIX}/tasks/${taskId}`, {
    method: 'GET',
    headers: { 'X-User-Id': '1002' },
  });
  return res.data;
}

/** POST /approvals/tasks/:id/action */
export async function postTaskAction(taskId: number, body: ApprovalActionPayload) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/tasks/${taskId}/action`, {
    method: 'POST',
    data: body,
    headers: { 'X-User-Id': '1002' },
  });
}

/** POST /approvals/tasks/:id/remind */
export async function remindTask(taskId: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/tasks/${taskId}/remind`, {
    method: 'POST',
    headers: { 'X-User-Id': '1002' },
  });
}

export interface DelegationItem {
  id: number;
  delegatorId: number;
  delegatorName?: string;
  delegateUserId: number;
  delegateUserName?: string;
  startDate: string;
  endDate: string;
  reason?: string;
  status: string;
  createdAt?: string;
}

export interface DelegationForm {
  delegateUserId: number;
  startDate: string;
  endDate: string;
  reason?: string;
}

/** GET /approvals/delegations */
export async function fetchDelegations(params?: { page?: number; pageSize?: number }) {
  const res = await request<
    API.Result<{ list: DelegationItem[]; total: number; page: number; pageSize: number }>
  >(`${APPROVAL_PREFIX}/delegations`, {
    method: 'GET',
    params,
    headers: { 'X-User-Id': '1002' },
  });
  return res.data;
}

/** POST /approvals/delegations */
export async function createDelegation(data: DelegationForm) {
  return request<API.Result<DelegationItem>>(`${APPROVAL_PREFIX}/delegations`, {
    method: 'POST',
    data,
    headers: { 'X-User-Id': '1002' },
  });
}

/** DELETE /approvals/delegations/:id */
export async function cancelDelegation(id: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/delegations/${id}`, {
    method: 'DELETE',
    headers: { 'X-User-Id': '1002' },
  });
}

/** POST /approvals/instances/:id/withdraw */
export async function withdrawInstance(instanceId: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/instances/${instanceId}/withdraw`, {
    method: 'POST',
    headers: { 'X-User-Id': '1001' },
  });
}

/** GET /approvals/instances — 我发起的 */
export async function fetchMyInstances(params?: { page?: number; pageSize?: number }) {
  const res = await request<
    API.Result<{ list: ApprovalTaskItem[]; total: number; page: number; pageSize: number }>
  >(`${APPROVAL_PREFIX}/instances`, {
    method: 'GET',
    params,
    headers: { 'X-User-Id': '1001' },
  });
  return res.data;
}

/** 入职列表（含 stats） */
export async function fetchOnboardingApplications(params?: {
  status?: string;
  page?: number;
  pageSize?: number;
}) {
  const res = await request<
    API.Result<{
      list: Array<{
        id: number;
        status: string;
        name: string;
        mobile?: string;
        departmentId?: number;
        positionId?: number;
        baseSalary?: number;
        expectedOnboardDate?: string;
        employeeId?: number;
        instanceId?: number;
        createdAt?: string;
      }>;
      total: number;
      stats?: Record<string, number>;
    }>
  >(`${ONBOARDING_PREFIX}`, {
    method: 'GET',
    params,
    headers: { 'X-User-Id': '1001' },
  });
  return res.data;
}

export async function createOnboardingApplication(data: OnboardingForm) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}`, {
    method: 'POST',
    data,
    headers: { 'X-User-Id': '1001' },
  });
}

export async function updateOnboardingApplication(id: number, data: Partial<OnboardingForm>) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'PUT',
    data,
    headers: { 'X-User-Id': '1001' },
  });
}

export async function deleteOnboardingApplication(id: number) {
  return request<API.Result<null>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'DELETE',
    headers: { 'X-User-Id': '1001' },
  });
}

export async function submitOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/submit`, {
    method: 'POST',
    headers: { 'X-User-Id': '1001' },
  });
}

export async function withdrawOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/withdraw`, {
    method: 'POST',
    headers: { 'X-User-Id': '1001' },
  });
}

export async function confirmOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/confirm`, {
    method: 'POST',
    headers: { 'X-User-Id': '1001' },
  });
}

export async function abandonOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/abandon`, {
    method: 'POST',
    headers: { 'X-User-Id': '1001' },
  });
}

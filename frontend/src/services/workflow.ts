/**
 * 瀹℃壒涓績 + 鍏ヨ亴鐢宠 API
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
  /** 正式离职部门负责人同意时必填 */
  handoverEmployeeId?: number;
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

export interface OnboardingItem {
  id: number;
  status: string;
  name: string;
  gender?: string;
  mobile?: string;
  email?: string;
  idNumber?: string;
  departmentId?: number;
  positionId?: number;
  employmentType?: string;
  probationMonths?: number;
  probationSalaryRatio?: number;
  managerId?: number;
  baseSalary?: number;
  expectedOnboardDate?: string;
  employeeId?: number;
  instanceId?: number;
  createdAt?: string;
  positionStandard?: boolean;
  gradeMaxSalary?: number;
  rejectReason?: string;
}

/** GET /approvals/tasks/stats */
export async function fetchTaskStats() {
  const res = await request<API.Result<ApprovalTaskStats>>(`${APPROVAL_PREFIX}/tasks/stats`, {
    method: 'GET',
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
  });
  return res.data;
}

/** GET /approvals/tasks/:id */
export async function fetchTaskDetail(taskId: number) {
  const res = await request<API.Result<ApprovalTaskDetail>>(`${APPROVAL_PREFIX}/tasks/${taskId}`, {
    method: 'GET',
  });
  return res.data;
}

/** POST /approvals/tasks/:id/action */
export async function postTaskAction(taskId: number, body: ApprovalActionPayload) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/tasks/${taskId}/action`, {
    method: 'POST',
    data: body,
  });
}

/** POST /approvals/tasks/:id/remind */
export async function remindTask(taskId: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/tasks/${taskId}/remind`, {
    method: 'POST',
  });
}

export interface HandoverCandidate {
  employeeId: number;
  name: string;
  empNo?: string;
  department?: string;
}

/** GET /approvals/handover-candidates — 正式离职交接人选人（非花名册） */
export async function searchHandoverCandidates(keyword: string) {
  const res = await request<API.Result<HandoverCandidate[]>>(
    `${APPROVAL_PREFIX}/handover-candidates`,
    {
      method: 'GET',
      params: { keyword },
    },
  );
  return res.data ?? [];
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
  });
  return res.data;
}

/** POST /approvals/delegations（冲突 60003 由页面处理，避免误关弹窗/误报成功） */
export async function createDelegation(data: DelegationForm) {
  return request<API.Result<DelegationItem>>(`${APPROVAL_PREFIX}/delegations`, {
    method: 'POST',
    data,
    skipErrorHandler: true,
  });
}

/** DELETE /approvals/delegations/:id */
export async function cancelDelegation(id: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/delegations/${id}`, {
    method: 'DELETE',
  });
}

/** GET /approvals/instances/:id — 发起人查看审批进度 */
export async function fetchInstanceDetail(instanceId: number) {
  const res = await request<
    API.Result<{
      instanceId: number;
      processType: string;
      title: string;
      status: string;
      currentNodeLabel?: string;
      createdAt?: string;
      nodes?: { order: number; label: string; state: string }[];
      timeline?: ApprovalTimelineItem[];
      businessDetail?: Record<string, unknown>;
    }>
  >(`${APPROVAL_PREFIX}/instances/${instanceId}`, {
    method: 'GET',
  });
  return res.data;
}

/** POST /approvals/instances/:id/withdraw */
export async function withdrawInstance(instanceId: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/instances/${instanceId}/withdraw`, {
    method: 'POST',
  });
}

/** GET /approvals/instances 鈥?鎴戝彂璧风殑 */
export async function fetchMyInstances(params?: { page?: number; pageSize?: number }) {
  const res = await request<
    API.Result<{ list: ApprovalTaskItem[]; total: number; page: number; pageSize: number }>
  >(`${APPROVAL_PREFIX}/instances`, {
    method: 'GET',
    params,
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
      list: OnboardingItem[];
      total: number;
      stats?: Record<string, number>;
    }>
  >(`${ONBOARDING_PREFIX}`, {
    method: 'GET',
    params,
  });
  return res.data;
}

/** GET /onboarding/applications/:id */
export async function fetchOnboardingApplication(id: number) {
  const res = await request<API.Result<OnboardingItem>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'GET',
  });
  return res.data;
}

export async function createOnboardingApplication(data: OnboardingForm) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}`, {
    method: 'POST',
    data,
  });
}

export async function updateOnboardingApplication(id: number, data: Partial<OnboardingForm>) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteOnboardingApplication(id: number) {
  return request<API.Result<null>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'DELETE',
  });
}

export async function submitOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/submit`, {
    method: 'POST',
  });
}

export async function withdrawOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/withdraw`, {
    method: 'POST',
  });
}

export async function confirmOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/confirm`, {
    method: 'POST',
  });
}

export async function abandonOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/abandon`, {
    method: 'POST',
  });
}


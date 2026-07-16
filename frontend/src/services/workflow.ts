/**
 * 审批中心 API 占位（Day1 仅定义，页面用 Mock）
 * Base: /api/v1
 */

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
  processType: ProcessType;
  title: string;
  applicantName: string;
  applicantDept?: string;
  currentNodeLabel: string;
  createTime: string;
  dueAt?: string;
  status: 'pending' | 'approved' | 'rejected' | 'cancelled';
}

export interface ApprovalActionPayload {
  action: 'APPROVE' | 'REJECT' | 'FORWARD';
  comment?: string;
  targetUserId?: number;
}

/** GET /approvals/tasks/stats */
export async function fetchTaskStats() {
  // TODO: return request('/api/v1/approvals/tasks/stats')
  return Promise.resolve({ pending: 0, approvedToday: 0, overdueCount: 0 });
}

/** GET /approvals/tasks */
export async function fetchTasks(_params?: {
  status?: string;
  type?: ProcessType;
  page?: number;
  pageSize?: number;
}) {
  return Promise.resolve({ list: [] as ApprovalTaskItem[], total: 0 });
}

/** GET /approvals/tasks/:id */
export async function fetchTaskDetail(_taskId: number) {
  return Promise.resolve(null);
}

/** POST /approvals/tasks/:id/action */
export async function postTaskAction(_taskId: number, _body: ApprovalActionPayload) {
  return Promise.resolve({ ok: true });
}

/** POST /approvals/instances/:id/withdraw */
export async function withdrawInstance(_instanceId: number) {
  return Promise.resolve({ ok: true });
}

/** GET /approvals/instances — 我发起的 */
export async function fetchMyInstances() {
  return Promise.resolve({ list: [], total: 0 });
}

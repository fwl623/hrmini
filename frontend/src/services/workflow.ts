/**
 * 【入转调离/审批 API 封装】前缀对齐 /api/v1。
 * 入职 CRUD/提交/确认、审批 tasks/action/withdraw、委托等具名函数；页面只关心业务语义。
 * 字段与后端 VO 对齐（expectedOnboardDate、employmentStatus、processType 等）。
 * 联调直打后端，不以前端 Mock 为主路径；失败由 Umi request + getRequestErrorMessage 展示。
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
  nodes?: ApprovalNodeProgress[];
  timeline: ApprovalTimelineItem[];
  actions: string[];
}

export interface ApprovalNodeProgress {
  order: number;
  label: string;
  state: string;
  assigneeName?: string;
  actualAssigneeName?: string;
  taskStatus?: string;
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
  /** 10试用/20正式/30待离职/40已离职 */
  employmentStatus?: number;
  instanceId?: number;
  createdAt?: string;
  positionStandard?: boolean;
  gradeMaxSalary?: number;
  rejectReason?: string;
}

/**
 * GET /api/v1/approvals/tasks/stats
 * 获取审批中心顶部统计：待办数、今日已办、超期数。
 * 调用页面：admin/approval（审批工作台）。
 * 后端：ApprovalController.taskStats()
 */
export async function fetchTaskStats() {
  const res = await request<API.Result<ApprovalTaskStats>>(`${APPROVAL_PREFIX}/tasks/stats`, {
    method: 'GET',
  });
  return res.data;
}

/**
 * GET /api/v1/approvals/tasks
 * 分页查询当前用户的审批任务；status=pending 为待办，done 为已办，可按 processType/keyword 筛选。
 * 调用页面：admin/approval（「我的待办」「我的已办」Tab）；覆盖入职/转正/调岗/离职/请假/加班/薪资等全部流程类型。
 * 后端：ApprovalController.listTasks()
 */
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

/**
 * GET /api/v1/approvals/tasks/:id
 * 获取单条待办详情：任务信息、业务摘要、审批节点进度、时间线、可用操作（actions）。
 * 调用页面：admin/approval（点击待办/已办行打开 Drawer）。
 * 后端：ApprovalController.taskDetail()
 */
export async function fetchTaskDetail(taskId: number) {
  const res = await request<API.Result<ApprovalTaskDetail>>(`${APPROVAL_PREFIX}/tasks/${taskId}`, {
    method: 'GET',
  });
  return res.data;
}

/**
 * POST /api/v1/approvals/tasks/:id/action
 * 执行审批操作：APPROVE（同意）/ REJECT（驳回，comment 必填）/ FORWARD（转交）。
 * 正式离职第一岗同意时可传 handoverEmployeeId 指定工作交接人。
 * 调用页面：admin/approval（ApprovalActions 组件回调）；间接处理 leave/overtime 等待办（同一审批中心入口）。
 * 后端：ApprovalController.action()
 */
export async function postTaskAction(taskId: number, body: ApprovalActionPayload) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/tasks/${taskId}/action`, {
    method: 'POST',
    data: body,
  });
}

/**
 * POST /api/v1/approvals/tasks/:id/remind
 * 对当前待办任务立即催办（触发通知，不等 48h 延迟队列）。
 * 调用页面：admin/approval（「我发起的」Tab 中催办按钮）。
 * 后端：ApprovalController.remind()
 */
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

/**
 * GET /api/v1/approvals/handover-candidates
 * 按关键词搜索正式离职工作交接候选人（同部门在职员工，非全量花名册）。
 * 调用页面：admin/approval（RESIGNATION 流程第一岗同意前选手动交接人）。
 * 后端：ApprovalController.handoverCandidates()
 */
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

export interface DelegateCandidate {
  userId: number;
  name: string;
  username?: string;
  empNo?: string;
  department?: string;
}

/**
 * GET /api/v1/approvals/delegate-candidates
 * 搜索可被委托的审批人（须具备审批权限的用户）。
 * 调用页面：admin/delegation（新增委托 Modal 选人）。
 * 后端：ApprovalController.delegateCandidates()
 */
export async function searchDelegateCandidates(keyword?: string) {
  const res = await request<API.Result<DelegateCandidate[]>>(
    `${APPROVAL_PREFIX}/delegate-candidates`,
    {
      method: 'GET',
      params: { keyword: keyword || undefined },
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

/**
 * GET /api/v1/approvals/delegations
 * 分页查询当前用户创建的审批委托规则（ACTIVE / CANCELLED）。
 * 调用页面：admin/delegation（委托管理列表）。
 * 后端：ApprovalController.listDelegations()
 */
export async function fetchDelegations(params?: { page?: number; pageSize?: number }) {
  const res = await request<
    API.Result<{ list: DelegationItem[]; total: number; page: number; pageSize: number }>
  >(`${APPROVAL_PREFIX}/delegations`, {
    method: 'GET',
    params,
  });
  return res.data;
}

/**
 * POST /api/v1/approvals/delegations
 * 新增审批委托：指定代理人、起止日期；同一时段已有 ACTIVE 委托时返回 60003，由页面提示。
 * 调用页面：admin/delegation（新增委托 Modal 提交）。
 * 后端：ApprovalController.createDelegation()
 */
export async function createDelegation(data: DelegationForm) {
  return request<API.Result<DelegationItem>>(`${APPROVAL_PREFIX}/delegations`, {
    method: 'POST',
    data,
    skipErrorHandler: true,
  });
}

/**
 * DELETE /api/v1/approvals/delegations/:id
 * 取消指定委托规则（状态变为 CANCELLED）。
 * 调用页面：admin/delegation（列表行「取消」操作）。
 * 后端：ApprovalController.cancelDelegation()
 */
export async function cancelDelegation(id: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/delegations/${id}`, {
    method: 'DELETE',
  });
}

/**
 * GET /api/v1/approvals/instances/:id
 * 按审批实例 ID 查询进度：节点 Steps、审批日志 Timeline、业务摘要。
 * 调用页面：admin/onboarding（「审批进度」Drawer）、admin/approval（「我发起的」详情）、
 *           portal/leave、portal/overtime（申请记录查看审批进度）。
 * 后端：ApprovalController.instanceDetail()
 */
export async function fetchInstanceDetail(instanceId: number) {
  const res = await request<
    API.Result<{
      instanceId: number;
      processType: string;
      title: string;
      status: string;
      currentNodeLabel?: string;
      createdAt?: string;
      nodes?: {
        order: number;
        label: string;
        state: string;
        assigneeName?: string;
        actualAssigneeName?: string;
        taskStatus?: string;
      }[];
      timeline?: ApprovalTimelineItem[];
      businessDetail?: Record<string, unknown>;
    }>
  >(`${APPROVAL_PREFIX}/instances/${instanceId}`, {
    method: 'GET',
  });
  return res.data;
}

/**
 * POST /api/v1/approvals/instances/:id/withdraw
 * 发起人撤回尚未结束的审批实例（业务单据回到可编辑/可重提状态）。
 * 调用页面：admin/approval（「我发起的」Tab，ApprovalActions 撤回）。
 * 后端：ApprovalController.withdraw()
 */
export async function withdrawInstance(instanceId: number) {
  return request<API.Result<null>>(`${APPROVAL_PREFIX}/instances/${instanceId}/withdraw`, {
    method: 'POST',
  });
}

/**
 * GET /api/v1/approvals/instances
 * 分页查询当前用户作为发起人创建的全部审批实例。
 * 调用页面：admin/approval（「我发起的」Tab）。
 * 后端：ApprovalController.myInstances()
 */
export async function fetchMyInstances(params?: { page?: number; pageSize?: number }) {
  const res = await request<
    API.Result<{ list: ApprovalTaskItem[]; total: number; page: number; pageSize: number }>
  >(`${APPROVAL_PREFIX}/instances`, {
    method: 'GET',
    params,
  });
  return res.data;
}

/**
 * GET /api/v1/onboarding/applications
 * 分页查询入职申请列表，响应含各状态统计 stats（draft/pending/approved_pending 等）。
 * 调用页面：admin/onboarding（列表 + 顶部 Statistic 卡片）。
 * 后端：OnboardingController.list()
 */
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

/**
 * GET /api/v1/onboarding/applications/:id
 * 按 ID 获取单条入职申请详情（含 instanceId、employmentStatus 等）。
 * 调用页面：暂无直接引用（契约预留；当前 admin/onboarding 用列表行数据 + fetchInstanceDetail 看进度）。
 * 后端：OnboardingController.detail()
 */
export async function fetchOnboardingApplication(id: number) {
  const res = await request<API.Result<OnboardingItem>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'GET',
  });
  return res.data;
}

/**
 * POST /api/v1/onboarding/applications
 * 新建入职申请草稿（status=draft），校验手机号唯一等。
 * 调用页面：admin/onboarding（「新建入职」Modal 提交）。
 * 后端：OnboardingController.create()
 */
export async function createOnboardingApplication(data: OnboardingForm) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}`, {
    method: 'POST',
    data,
  });
}

/**
 * PUT /api/v1/onboarding/applications/:id
 * 更新入职申请（仅 draft/rejected 可编辑；approved_pending 可改 expectedOnboardDate）。
 * 调用页面：admin/onboarding（编辑 Modal、改入职日 Modal）。
 * 后端：OnboardingController.update()
 */
export async function updateOnboardingApplication(id: number, data: Partial<OnboardingForm>) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'PUT',
    data,
  });
}

/**
 * DELETE /api/v1/onboarding/applications/:id
 * 删除入职申请（仅 draft/rejected 状态允许）。
 * 调用页面：admin/onboarding（列表行「删除」）。
 * 后端：OnboardingController.delete()
 */
export async function deleteOnboardingApplication(id: number) {
  return request<API.Result<null>>(`${ONBOARDING_PREFIX}/${id}`, {
    method: 'DELETE',
  });
}

/**
 * POST /api/v1/onboarding/applications/:id/submit
 * 提交入职审批：创建 approval_instance + 首节点待办（draft/rejected → pending）。
 * 调用页面：admin/onboarding（列表行「提交/重新提交」）。
 * 后端：OnboardingController.submit()
 */
export async function submitOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/submit`, {
    method: 'POST',
  });
}

/**
 * POST /api/v1/onboarding/applications/:id/withdraw
 * 撤回进行中的入职审批（pending → draft，作废当前实例）。
 * 调用页面：admin/onboarding（列表行「撤回」）。
 * 后端：OnboardingController.withdraw()
 */
export async function withdrawOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/withdraw`, {
    method: 'POST',
  });
}

/**
 * POST /api/v1/onboarding/applications/:id/confirm
 * 确认入职：创建员工档案、生成工号、开通账号（approved_pending → onboarded）；E2E 主链路节点。
 * 调用页面：admin/onboarding（列表行「确认入职」，需预计入职日已到）。
 * 后端：OnboardingController.confirm() → OnboardingService 联动 employee/auth
 */
export async function confirmOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/confirm`, {
    method: 'POST',
  });
}

/**
 * POST /api/v1/onboarding/applications/:id/abandon
 * 放弃入职（approved_pending → abandoned，不再建档）。
 * 调用页面：admin/onboarding（列表行「放弃入职」）。
 * 后端：OnboardingController.abandon()
 */
export async function abandonOnboardingApplication(id: number) {
  return request<API.Result<unknown>>(`${ONBOARDING_PREFIX}/${id}/abandon`, {
    method: 'POST',
  });
}


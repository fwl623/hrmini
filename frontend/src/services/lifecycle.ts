/**
 * 【技术评审锚点 · 转正/调岗/离职 API 封装】前缀对齐 /api/v1。
 * 与 workflow.ts 分工：审批中心 + 入职在 workflow，本文件管转正/调岗/离职业务单据。
 * 离职双通道：createMyResignationRequest（门户登记，不进审批） vs createResignation（HR 正式离职）。
 * 每个 export async function 上方注释含：METHOD + 路径、用途、调用页面、后端 Controller 方法。
 */import { request } from '@umijs/max';
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

/**
 * GET /api/v1/regularization/applications/pending
 * 查询待转正员工：试用期结束日 ≤ 今天+7 天（含已逾期），供 HR 主动发起转正评估。
 * 调用页面：admin/regularization（「待转正」表格区域）。
 * 后端：RegularizationController.pending() → RegularizationService.listPending()
 */
export async function fetchPendingRegularization() {
  const res = await request<API.Result<PendingRegularizationItem[]>>(
    `${API_BASE}/regularization/applications/pending`,
    { method: 'GET' },
  );
  return res.data ?? [];
}

/**
 * GET /api/v1/regularization/applications
 * 分页查询转正申请历史记录，可按 status 筛选（PENDING/APPROVED/REJECTED 等）。
 * 调用页面：admin/regularization（「转正记录」表格）。
 * 后端：RegularizationController.list() → RegularizationService.list()
 */
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

/**
 * POST /api/v1/regularization/applications
 * 发起转正申请并创建审批实例；approvalResult=PASS（通过）/ EXTEND（延长试用）/ FAIL（不通过）。
 * 调用页面：admin/regularization（发起转正 Modal 提交）；后续审批在 admin/approval 处理。
 * 后端：RegularizationController.create() → RegularizationService.create()
 */
export async function createRegularization(data: RegularizationCreateBody) {
  return request<API.Result<RegularizationItem>>(`${API_BASE}/regularization/applications`, {
    method: 'POST',
    data,
  });
}

/**
 * GET /api/v1/transfers
 * 分页查询调岗申请列表，可按 status 筛选（APPROVING/PENDING_EFFECT/APPROVED/REJECTED 等）。
 * 调用页面：admin/transfers（主列表 Table）。
 * 后端：TransferController.list() → TransferService.list()
 */
export async function fetchTransfers(params?: { page?: number; pageSize?: number; status?: string }) {
  const res = await request<API.Result<PageData<TransferItem>>>(`${API_BASE}/transfers`, {
    method: 'GET',
    params,
  });
  return res.data;
}

/**
 * GET /api/v1/transfers/:id
 * 获取单条调岗详情，含三节点审批进度（原部门→新部门→HR），供详情 Drawer Steps 展示。
 * 调用页面：admin/transfers（列表行「详情」打开 Drawer）。
 * 后端：TransferController.detail() → TransferService.detail()
 */
export async function fetchTransferDetail(id: number) {
  const res = await request<API.Result<TransferItem>>(`${API_BASE}/transfers/${id}`, {
    method: 'GET',
  });
  return res.data;
}

/**
 * POST /api/v1/transfers
 * 发起调岗申请并创建三节点审批实例；newDepartmentId 与原部门相同会返回 30004。
 * 调用页面：admin/transfers（发起调岗 Modal 提交）；审批操作在 admin/approval 处理。
 * 后端：TransferController.create() → TransferService.create()
 */
export async function createTransfer(data: TransferCreateBody) {
  return request<API.Result<TransferItem>>(`${API_BASE}/transfers`, {
    method: 'POST',
    data,
  });
}

/**
 * GET /api/v1/resignation-requests
 * HR 分页查询员工在门户登记的离职意向（双通道第一阶段）；已转入正式离职的会被过滤。
 * 调用页面：admin/resignation（Tab「员工申请」列表）。
 * 后端：ResignationController.listRequests() → ResignationService.listRequests()
 */
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

/**
 * GET /api/v1/resignations
 * 分页查询 HR 发起的正式离职审批单（双通道第二阶段），含审批中与已生效记录。
 * 调用页面：admin/resignation（Tab「正式离职」列表）。
 * 后端：ResignationController.listResignations() → ResignationService.listResignations()
 */
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

/**
 * GET /api/v1/resignations/stats
 * 获取离职管理台顶部统计：待受理申请 / 审批中 / 待离职 / 本月已离职。
 * 调用页面：admin/resignation（Statistic 卡片区域）。
 * 后端：ResignationController.stats() → ResignationService.stats()
 */
export async function fetchResignationStats() {
  const res = await request<API.Result<ResignationStats>>(`${API_BASE}/resignations/stats`, {
    method: 'GET',
  });
  return res.data;
}

/**
 * POST /api/v1/resignations
 * HR 发起正式离职并创建审批实例（部门负责人 → HR）；可关联 requestId 承接员工门户申请。
 * handoverEmployeeId 可选，工作交接人通常由部门负责人在 admin/approval 第一岗同意时确认。
 * 调用页面：admin/resignation（「发起正式离职」Modal）；审批在 admin/approval 处理。
 * 后端：ResignationController.createResignation() → ResignationService.createResignation()
 */
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

/**
 * GET /api/v1/profile/resignation-requests
 * 员工本人分页查询离职意向登记记录（双通道第一阶段，不创建审批实例）。
 * 调用页面：portal/resignation（历史记录 Table）。
 * 后端：ResignationController.myRequests() → ResignationService.listMyRequests()
 */
export async function fetchMyResignationRequests(params?: { page?: number; pageSize?: number }) {
  const res = await request<API.Result<PageData<ResignationRequestItem>>>(
    `${API_BASE}/profile/resignation-requests`,
    { method: 'GET', params },
  );
  return res.data;
}

/**
 * POST /api/v1/profile/resignation-requests
 * 员工本人登记离职意向（期望离职日、原因等）；仅落库 PENDING，不进审批中心。
 * HR 在 admin/resignation 看到后，再发起正式离职审批（createResignation）。
 * 调用页面：portal/resignation（申请表单提交）。
 * 后端：ResignationController.createMyRequest() → ResignationService.createMyRequest()
 */
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

/**
 * POST /api/v1/profile/resignation-requests/:id/cancel
 * 员工撤销本人 PENDING 状态的离职意向登记（已转正式离职或已受理的不可撤）。
 * 调用页面：portal/resignation（记录行「撤销」按钮）。
 * 后端：ResignationController.cancelMyRequest() → ResignationService.cancelMyRequest()
 */
export async function cancelMyResignationRequest(id: number) {
  return request<API.Result<null>>(`${API_BASE}/profile/resignation-requests/${id}/cancel`, {
    method: 'POST',
  });
}

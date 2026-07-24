/**
 * 考勤模块 API 服务
 *
 * 封装考勤组管理、工作日/节假日配置、打卡管理、补卡管理、
 * 假期请假、加班申请以及月考勤汇总等功能的 API 请求方法。
 * 所有方法均基于 @umijs/max 的 request 工具发送 HTTP 请求。
 *
 * @see API契约文档/考勤请假-后端系分.md
 *
 * TODO: 联调前替换 mock 数据为真实请求
 */

import { request } from '@umijs/max';

const API_PREFIX = '/api/v1/attendance';

// ========== 考勤组管理 ==========

/**
 * 创建考勤组
 *
 * 新增一个考勤组，包含考勤规则、打卡时间段等配置信息。
 *
 * @param data - 考勤组创建参数，包含组名称、考勤规则、打卡时间窗口等字段
 * @returns 返回新创建考勤组的唯一标识 id
 */
export async function createGroup(data: API.AttendanceGroupDTO) {
  return request<API.Result<{ id: number }>>(`${API_PREFIX}/groups`, {
    method: 'POST',
    data,
  });
}

/**
 * 查询考勤组列表（分页）
 *
 * 按分页参数获取考勤组列表，支持页码和每页条数控制。
 *
 * @param params - 分页查询参数
 * @param params.page - 当前页码（从 1 开始），默认值通常为 1
 * @param params.size - 每页记录数，默认值由服务端决定
 * @returns 返回分页后的考勤组列表数据，包含记录列表及总条数
 */
export async function getGroups(params: { page?: number; size?: number }) {
  return request<API.Result<API.Page<API.AttendanceGroupVO>>>(`${API_PREFIX}/groups`, {
    method: 'GET',
    params,
  });
}

/**
 * 查询考勤组详情
 *
 * 根据考勤组 ID 获取单个考勤组的完整配置信息。
 *
 * @param id - 考勤组唯一标识
 * @returns 返回考勤组的详细信息，包含规则、参与人员等
 */
export async function getGroupDetail(id: number) {
  return request<API.Result<API.AttendanceGroupVO>>(`${API_PREFIX}/groups/${id}`, {
    method: 'GET',
  });
}

/**
 * 更新考勤组
 *
 * 修改指定考勤组的配置信息，如考勤规则、打卡时间等。
 *
 * @param id - 要更新的考勤组唯一标识
 * @param data - 更新后的考勤组配置参数
 * @returns 返回操作是否成功的布尔标识
 */
export async function updateGroup(id: number, data: API.AttendanceGroupDTO) {
  return request<API.Result<{ success: boolean }>>(`${API_PREFIX}/groups/${id}`, {
    method: 'PUT',
    data,
  });
}

/**
 * 删除考勤组
 *
 * 根据 ID 删除指定的考勤组及其关联规则。
 *
 * @param id - 要删除的考勤组唯一标识
 * @returns 返回操作是否成功的布尔标识
 */
export async function deleteGroup(id: number) {
  return request<API.Result<{ success: boolean }>>(`${API_PREFIX}/groups/${id}`, {
    method: 'DELETE',
  });
}

// ========== 工作日 & 节假日 ==========

/**
 * 查询工作日配置
 *
 * 获取系统中设置的各星期工作日/非工作日标记列表。
 *
 * @returns 返回工作日配置数组，每项包含星期几及其是否为工作日的标记
 */
export async function getWorkdays() {
  return request<API.Result<API.WorkdayConfigVO[]>>(`${API_PREFIX}/workdays`);
}

/**
 * 更新工作日配置
 *
 * 批量更新各星期的工作日/非工作日标记。
 *
 * @param data - 工作日配置数组，每项包含星期标识及是否为工作日的标记
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function updateWorkdays(data: API.WorkdayConfigDTO[]) {
  return request<API.Result<null>>(`${API_PREFIX}/workdays`, {
    method: 'PUT',
    data,
  });
}

/**
 * 节假日列表（分页）
 *
 * 按分页参数获取系统中配置的特殊节假日列表。
 *
 * @param params - 分页查询参数
 * @param params.page - 当前页码（从 1 开始）
 * @param params.size - 每页记录数
 * @returns 返回分页后的节假日列表，包含日期、名称等信息
 */
export async function getHolidays(params: { page?: number; size?: number }) {
  return request<API.Result<API.Page<API.HolidayVO>>>(`${API_PREFIX}/holidays`, {
    method: 'GET',
    params,
  });
}

/**
 * 新增节假日
 *
 * 向系统中添加一条特殊节假日记录（如法定节假日、公司额外假期等）。
 *
 * @param data - 节假日信息
 * @param data.holidayDate - 节假日日期（格式：YYYY-MM-DD）
 * @param data.name - 节假日名称，如"国庆节"
 * @returns 返回新创建的节假日唯一标识 id
 */
export async function createHoliday(data: { holidayDate: string; name: string }) {
  return request<API.Result<{ id: number }>>(`${API_PREFIX}/holidays`, {
    method: 'POST',
    data,
  });
}

/**
 * 更新节假日
 *
 * 修改指定节假日的日期或名称信息。
 *
 * @param id - 要更新的节假日唯一标识
 * @param data - 更新后的节假日信息
 * @param data.holidayDate - 节假日日期（格式：YYYY-MM-DD）
 * @param data.name - 节假日名称
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function updateHoliday(id: number, data: { holidayDate: string; name: string }) {
  return request<API.Result<null>>(`${API_PREFIX}/holidays/${id}`, {
    method: 'PUT',
    data,
  });
}

/**
 * 删除节假日
 *
 * 根据 ID 删除指定的节假日记录。
 *
 * @param id - 要删除的节假日唯一标识
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function deleteHoliday(id: number) {
  return request<API.Result<null>>(`${API_PREFIX}/holidays/${id}`, {
    method: 'DELETE',
  });
}

// ========== 打卡管理 ==========

/**
 * 员工打卡
 *
 * 提交打卡记录（上班签到或下班签退），
 * 支持携带打卡时间以及地理位置信息。
 *
 * @param data - 打卡参数
 * @param data.type - 打卡类型：'in' 表示上班签到，'out' 表示下班签退
 * @param data.punchTime - 打卡时间（可选），格式 ISO 8601；为空则默认为当前服务端时间
 * @param data.latitude - 打卡地点的纬度（可选），用于地理位置校验
 * @param data.longitude - 打卡地点的经度（可选），用于地理位置校验
 * @returns 返回打卡状态标识，如 "NORMAL"（正常）、"LATE"（迟到）、"EARLY"（早退）等
 */
export async function punch(data: { type: 'in' | 'out'; punchTime?: string; latitude?: number; longitude?: number; employeeId?: number }) {
  return request<API.Result<{ punchStatus: string }>>(`${API_PREFIX}/punch`, {
    method: 'POST',
    data,
  });
}

/**
 * 今日打卡状态
 *
 * 获取当前员工今日的打卡概况，包含上下班打卡状态等。
 *
 * @returns 返回今日打卡状态视图对象，包含签到/签退时间、打卡状态等
 */
export async function getTodayPunchStatus() {
  return request<API.Result<API.TodayPunchVO>>(`${API_PREFIX}/punch/today`);
}

/**
 * 本月打卡统计（门户）
 *
 * 获取当前员工本月打卡统计，含应打卡次数、实际打卡、迟到/早退/缺卡。
 *
 * @returns 返回本月打卡统计数据
 */
export async function getMonthlyPunchStatus() {
  return request<API.Result<API.TodayPunchVO>>(`/api/v1/profile/attendance/punch/monthly`);
}

/**
 * 打卡记录（分页）
 *
 * 按筛选条件分页查询员工的打卡历史记录。
 *
 * @param params - 查询参数
 * @param params.page - 当前页码（从 1 开始）
 * @param params.size - 每页记录数
 * @param params.keyword - 搜索关键字（可选），按员工姓名或工号模糊匹配
 * @param params.dateFrom - 打卡日期范围起始（可选），格式 YYYY-MM-DD
 * @param params.dateTo - 打卡日期范围结束（可选），格式 YYYY-MM-DD
 * @returns 返回分页后的打卡记录列表
 */
export async function getPunchRecords(params: { page?: number; size?: number; keyword?: string; dateFrom?: string; dateTo?: string }) {
  return request<API.Result<API.Page<API.PunchRecordVO>>>(`${API_PREFIX}/punch/records`, {
    method: 'GET',
    params,
  });
}

// ========== 补卡管理 ==========

/**
 * 补卡申请
 *
 * 提交补卡请求，用于员工因漏打卡等异常情况申请补正。
 *
 * @param data - 补卡参数
 * @param data.punchDate - 需要补卡的日期（格式：YYYY-MM-DD）
 * @param data.type - 补卡类型：'in' 表示补上班卡，'out' 表示补下班卡
 * @param data.punchTime - 补卡的时间点（格式：HH:mm）
 * @param data.reason - 补卡原因说明
 * @returns 返回补卡申请记录 id 及当前审批状态
 */
export async function applyPunchFix(data: { punchDate: string; type: 'in' | 'out'; punchTime: string; reason: string; employeeId?: number }) {
  return request<API.Result<{ id: number; status: string }>>(`${API_PREFIX}/punch-fix`, {
    method: 'POST',
    data,
  });
}

/**
 * 补卡剩余次数
 *
 * 查询当前员工当月的补卡额度及已使用/剩余次数。
 *
 * @returns 返回总额度、已使用额度及剩余可用额度
 */
export async function getPunchFixQuota() {
  return request<API.Result<{ totalQuota: number; usedQuota: number; remainingQuota: number }>>(`${API_PREFIX}/punch-fix/quota`);
}

/** 昨日打卡概览（管理端） */
export async function getYesterdayOverview() {
  return request<API.Result<API.TodayPunchVO>>(`${API_PREFIX}/punch/yesterday-overview`);
}

// ========== 假期 & 请假 ==========

/**
 * 假期余额
 *
 * 查询指定员工的各类假期（年假、病假、事假等）可用余额。
 * 若不传 employeeId 则查询当前登录员工余额。
 *
 * @param employeeId - 员工唯一标识（可选），为空时表示当前登录员工
 * @returns 返回各类假期余额视图对象数组，包含假期类型、总天数、已用天数、剩余天数等
 */
export async function getLeaveBalances(employeeId?: number) {
  return request<API.Result<API.LeaveBalanceVO[]>>(`/api/v1/leaves/balances`, {
    method: 'GET',
    params: { employeeId },
  });
}

/**
 * 请假列表（分页）
 *
 * 按筛选条件分页查询员工的请假申请记录。
 * 管理端应传 employeeId=0 查全部；门户不传则查当前登录员工。
 *
 * @param params - 查询参数
 * @param params.page - 当前页码（从 1 开始）
 * @param params.leaveType - 请假类型筛选（可选），如 "ANNUAL"（年假）、"SICK"（病假）、"PERSONAL"（事假）等
 * @param params.status - 审批状态筛选（可选），如 "PENDING"（待审批）、"APPROVED"（已通过）、"REJECTED"（已驳回）等
 * @param params.employeeId - 员工 ID；传 0 表示查全部（管理端）
 * @returns 返回分页后的请假申请列表
 */
export async function getLeaveApplications(params: {
  page?: number;
  pageSize?: number;
  leaveType?: string;
  status?: string;
  employeeId?: number;
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
}) {
  return request<API.Result<API.Page<API.LeaveApplicationVO>>>(`/api/v1/leaves/applications`, {
    method: 'GET',
    params,
  });
}

/**
 * 提交请假
 *
 * 提交一条请假申请，进入审批流程。
 *
 * @param data - 请假申请参数，包含请假类型、起止时间、原因说明等
 * @returns 返回请假申请记录 id 及当前审批状态（通常是 "PENDING"）
 */
export async function submitLeave(data: API.LeaveApplicationDTO) {
  return request<API.Result<{ id: number; status: string }>>(`/api/v1/leaves/applications`, {
    method: 'POST',
    data,
  });
}

/**
 * 撤销请假（仅待审批）— 管理端
 */
export async function cancelLeave(id: number) {
  return request<API.Result<{ status: string }>>(`/api/v1/leaves/applications/${id}/cancel`, {
    method: 'PUT',
  });
}

/**
 * 本人撤销请假（门户）
 */
export async function cancelMyLeave(id: number) {
  return request<API.Result<{ status: string }>>(`/api/v1/profile/leave/applications/${id}/cancel`, {
    method: 'PUT',
  });
}

/**
 * 预览请假天数
 *
 * 根据起止时间按系统工作日/节假日规则计算实际请假天数，
 * 供提交前预览确认。
 *
 * @param params - 查询参数
 * @param params.startTime - 请假开始时间（格式 ISO 8601）
 * @param params.endTime - 请假结束时间（格式 ISO 8601）
 * @returns 返回计算后的请假天数（自然日或工作日，取决于系统配置）
 */
export async function calcLeaveDays(params: { startTime: string; endTime: string }) {
  return request<API.Result<{ days: number }>>(`/api/v1/leaves/calc-days`, {
    method: 'GET',
    params,
  });
}

// ========== 加班 ==========

/**
 * 加班列表（分页）
 *
 * 分页查询加班申请记录。
 * 管理端应传 employeeId=0 查全部；门户不传则查当前登录员工。
 *
 * @param params - 查询参数
 * @param params.page - 当前页码（从 1 开始）
 * @param params.employeeId - 员工 ID；传 0 表示查全部（管理端）
 * @returns 返回分页后的加班申请列表
 */
export async function getOvertimeApplications(params: {
  page?: number;
  pageSize?: number;
  employeeId?: number;
  status?: string;
  keyword?: string;
  dateFrom?: string;
  dateTo?: string;
}) {
  return request<API.Result<API.Page<API.OvertimeApplicationVO>>>(`/api/v1/overtime/applications`, {
    method: 'GET',
    params,
  });
}

/**
 * 提交加班
 *
 * 提交一条加班申请，进入审批流程。
 *
 * @param data - 加班申请参数，包含加班日期、时长、原因等
 * @returns 返回加班申请记录 id 及当前审批状态
 */
export async function submitOvertime(data: API.OvertimeApplicationDTO) {
  return request<API.Result<{ id: number; status: string }>>(`/api/v1/overtime/applications`, {
    method: 'POST',
    data,
  });
}

// ========== 加班台账 ==========

/**
 * 查询加班台账
 *
 * 按账期分页查询加班批准台账，用于薪资核算时核对加班数据。
 *
 * @param params - 查询参数
 * @param params.period - 账期，格式 "YYYY-MM"，如 "2026-07"
 * @param params.page - 当前页码
 * @param params.pageSize - 每页条数
 * @returns 返回分页后的加班台账列表
 */
export async function getOvertimeLedger(params: { period: string; page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<API.OvertimeLedgerVO>>>(`/api/v1/overtime/ledger`, {
    method: 'GET',
    params,
  });
}

// ========== 月考勤汇总 & 统计 ==========

/**
 * 导出月汇总
 *
 * 获取指定周期的月考勤汇总 Excel 文件。
 *
 * @param period - 汇总周期，格式 "YYYY-MM"
 */
export async function exportMonthlySummary(period: string): Promise<void> {
  return request(`/api/v1/attendance/monthly-summary/export-excel`, {
    method: 'GET',
    params: { period },
    responseType: 'blob',
  }).then((blob: any) => {
    const url = window.URL.createObjectURL(blob as Blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `考勤月报-${period}.xlsx`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  });
}

/**
 * 月汇总查看
 *
 * 获取指定周期（月份）的考勤汇总统计数据，
 * 可筛选部门，支持分页。
 *
 * @param params - 查询参数
 * @param params.period - 汇总周期，格式 "YYYY-MM"，如 "2026-07"
 * @param params.departmentId - 部门唯一标识（可选），筛选指定部门的汇总数据
 * @param params.page - 当前页码（从 1 开始）
 * @param params.pageSize - 每页记录数
 * @returns 返回月度考勤汇总视图，包含应出勤天数、实际出勤、迟到/早退/缺勤等统计
 */
export async function getMonthlySummary(params: { period: string; departmentId?: number; page?: number; pageSize?: number }) {
  return request<API.Result<API.MonthlySummaryVO>>(`/api/v1/attendance/monthly-summary`, {
    method: 'GET',
    params,
  });
}

/**
 * 月汇总锁定/解锁
 *
 * 设置指定月份的考勤汇总数据为锁定（不可修改）或解锁状态。
 * 锁定后该月数据不再接受打卡变更、补卡等操作。
 *
 * @param data - 操作参数
 * @param data.locked - true 表示锁定汇总数据；false 表示解锁
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function updateMonthlySummaryLock(data: { period: string; locked: boolean }) {
  return request<API.Result<null>>(`/api/v1/attendance/monthly-summary`, {
    method: 'PUT',
    data,
  });
}

/**
 * 手动生成月考勤汇总
 *
 * 根据打卡记录重新生成指定月份的考勤汇总数据。
 *
 * @param period - 汇总周期，格式 "YYYY-MM"，如 "2026-07"
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function generateMonthlySummary(period: string) {
  return request<API.Result<null>>(`/api/v1/attendance/monthly-summary/generate`, {
    method: 'POST',
    params: { period },
  });
}

// ========== 考勤统计 ==========

/**
 * 个人考勤统计
 *
 * 获取指定员工在指定月份的考勤汇总指标。
 *
 * @param params - 查询参数
 * @param params.employeeId - 员工唯一标识
 * @param params.period - 统计月份，格式 "YYYY-MM"，如 "2026-07"
 * @returns 返回个人考勤 8 项指标：应出勤/实际出勤/迟到/早退/旷工/请假/加班/年假余额
 */
export async function getPersonalStatistics(params: { employeeId: number; period: string }) {
  return request<API.Result<API.PersonalStatisticsVO>>(`${API_PREFIX}/statistics/personal`, {
    method: 'GET',
    params,
  });
}

/**
 * 部门考勤统计
 *
 * 获取指定部门在指定月份的考勤率指标。
 *
 * @param params - 查询参数
 * @param params.departmentId - 部门唯一标识
 * @param params.period - 统计月份，格式 "YYYY-MM"，如 "2026-07"
 * @returns 返回部门考勤 3 项率：出勤率/迟到率/请假率
 */
export async function getDepartmentStatistics(params: { departmentId: number; period: string }) {
  return request<API.Result<API.DepartmentStatisticsVO>>(`${API_PREFIX}/statistics/department`, {
    method: 'GET',
    params,
  });
}

// ========== 门户代理（SELF 范围） ==========

/**
 * 员工打卡（门户）
 *
 * 门户端（个人中心）的打卡接口，用于员工自主签到/签退。
 *
 * @param data - 打卡参数
 * @param data.type - 打卡类型：'in' 表示上班签到，'out' 表示下班签退
 * @param data.punchTime - 打卡时间（可选），为空则默认为当前服务端时间
 * @returns 返回打卡状态标识，如 "NORMAL"、"LATE"、"EARLY" 等
 */
export async function portalPunch(data: { type: 'in' | 'out'; punchTime?: string }) {
  return request<API.Result<{ punchStatus: string }>>(`/api/v1/profile/attendance/punch`, {
    method: 'POST',
    data,
  });
}

/**
 * 考勤日历（门户）
 *
 * 获取门户端展示的考勤日历数据，
 * 以月为单位返回每日的考勤状态摘要。
 *
 * @param period - 查询周期，格式 "YYYY-MM"，如 "2026-07"
 * @returns 返回考勤日历视图，包含当月每日的考勤状态（正常、迟到、缺勤等）
 */
export async function getAttendanceCalendar(period: string) {
  return request<API.Result<API.AttendanceCalendarVO>>(`/api/v1/profile/attendance/calendar`, {
    method: 'GET',
    params: { period },
  });
}

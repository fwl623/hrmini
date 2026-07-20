/**
 * 薪资模块 API 服务
 *
 * 封装薪资账套管理、核算批次管理、工资条查看和成本报表等功能的 API 请求方法。
 * 所有方法均基于 @umijs/max 的 request 工具发送 HTTP 请求。
 *
 * @see API契约文档/薪资-后端系分.md
 *
 * TODO: 联调前替换 mock 数据为真实请求
 */

import { request } from '@umijs/max';

const API_PREFIX = '/api/v1/payroll';

// ========== 账套管理 ==========

/**
 * 查询账套列表
 *
 * 获取系统中所有薪资账套的列表，包含账套基本信息及其关联的工资项目。
 *
 * @returns 返回分页后的账套列表，每项包含账套名称、生效日期、状态及工资项目数组
 */
export async function getSchemes() {
  return request<API.Result<API.Page<API.PayrollSchemeVO>>>(`${API_PREFIX}/schemes`);
}

/**
 * 创建薪资账套
 *
 * 新增一个薪资账套，需指定账套名称、生效日期、适用范围及工资项目列表。
 * 工资项目支持固定项和 SpEL 公式计算项。
 *
 * @param data - 账套创建参数，包含名称、生效日期、适用范围（部门/岗位/职级）及工资项目配置
 * @returns 返回新创建账套的唯一标识 id
 */
export async function createScheme(data: API.PayrollSchemeDTO) {
  return request<API.Result<{ id: number }>>(`${API_PREFIX}/schemes`, {
    method: 'POST',
    data,
  });
}

/**
 * 更新薪资账套
 *
 * 修改指定账套的配置信息，包括基本信息、适用范围及工资项目。
 *
 * @param id - 要更新的账套唯一标识
 * @param data - 更新后的账套配置参数
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function updateScheme(id: number, data: API.PayrollSchemeDTO) {
  return request<API.Result<null>>(`${API_PREFIX}/schemes/${id}`, {
    method: 'PUT',
    data,
  });
}

/**
 * 删除薪资账套
 *
 * 根据 ID 删除指定的账套，已关联员工的账套不可删除。
 *
 * @param id - 要删除的账套唯一标识
 * @returns 无返回数据，仅表示操作成功或失败
 */
export async function deleteScheme(id: number) {
  return request<API.Result<null>>(`${API_PREFIX}/schemes/${id}`, {
    method: 'DELETE',
  });
}

// ========== 核算批次 ==========

/**
 * 创建核算批次
 *
 * 新建一个指定账期的薪资核算批次，批次状态初始为 DRAFT。
 * 同一账期只能创建一个批次。
 *
 * @param data - 创建参数
 * @param data.period - 核算账期，格式 YYYY-MM，例如 "2026-07"
 * @returns 返回新创建批次的 id 及当前状态（draft）
 */
export async function createBatch(data: { period: string }) {
  return request<API.Result<{ id: number; status: string }>>(`${API_PREFIX}/batches`, {
    method: 'POST',
    data,
  });
}

/**
 * 查询核算批次列表（分页）
 *
 * 按条件分页查询核算批次列表，可筛选账期。
 *
 * @param params - 查询参数
 * @param params.period - 账期筛选（可选），格式 YYYY-MM
 * @param params.page - 当前页码，默认 1
 * @param params.pageSize - 每页条数，默认 20
 * @returns 返回分页后的核算批次列表，包含各批次的汇总统计
 */
export async function getBatches(params: { period?: string; page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<API.PayrollBatchVO>>>(`${API_PREFIX}/batches`, {
    method: 'GET',
    params,
  });
}

/**
 * 查询批次详情
 *
 * 获取指定批次的完整信息，包括状态、进度、人数统计等。
 * 前端可用于轮询核算进度。
 *
 * @param id - 批次唯一标识
 * @returns 返回批次详情，含状态、总人数、成功数、异常数、进度百分比
 */
export async function getBatchDetail(id: number) {
  return request<API.Result<API.PayrollBatchDetailVO>>(`${API_PREFIX}/batches/${id}`);
}

/**
 * 开始核算（触发异步计算）
 *
 * 将批次状态从 DRAFT 切换为 CALCULATING，并发送 MQ 消息触发异步核算。
 * 前端的轮询进度通过 getBatchDetail 实现。
 *
 * @param id - 批次唯一标识
 * @returns 无返回数据，仅表示计算任务已提交
 */
export async function startCalculate(id: number) {
  return request<API.Result<null>>(`${API_PREFIX}/batches/${id}/calculate`, {
    method: 'POST',
  });
}

/**
 * 查询核算明细（分页）
 *
 * 获取指定批次下所有员工的核算明细，包括应发实发、异常标记等。
 *
 * @param id - 批次唯一标识
 * @param params - 分页参数
 * @param params.page - 当前页码，默认 1
 * @param params.pageSize - 每页条数，默认 20
 * @returns 返回分页后的核算明细列表，含员工信息、薪资明细、异常标记
 */
export async function getBatchDetails(id: number, params: { page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<API.PayrollDetailVO>>>(`${API_PREFIX}/batches/${id}/details`, {
    method: 'GET',
    params,
  });
}

/**
 * 手工调整核算明细
 *
 * HR 对某员工的核算结果进行手动调整（如补发、扣款等），
 * 调整后该记录被标记为 manualAdjusted。
 *
 * @param batchId - 批次唯一标识
 * @param detailId - 明细唯一标识
 * @param data - 调整参数
 * @param data.itemCode - 调整的工资项目编码
 * @param data.adjustAmount - 调整金额（正数=增加，负数=减少）
 * @param data.reason - 调整原因，不超过 256 字符
 * @returns 返回调整后的手工标记状态
 */
export async function adjustDetail(batchId: number, detailId: number, data: { itemCode: string; adjustAmount: number; reason: string }) {
  return request<API.Result<{ manualAdjusted: boolean }>>(`${API_PREFIX}/batches/${batchId}/details/${detailId}`, {
    method: 'PUT',
    data,
  });
}

/**
 * 提交审批
 *
 * 将待确认的批次提交至审批流程，状态变为 APPROVING。
 * 满足老板审批条件（实发总额≥30万/调薪员工占比>15%/单人调薪>40%）时自动路由至老板审批。
 *
 * @param id - 批次唯一标识
 * @returns 返回提交后的状态
 */
export async function submitBatchApprove(id: number) {
  return request<API.Result<{ status: string }>>(`${API_PREFIX}/batches/${id}/submit`, {
    method: 'POST',
  });
}

/**
 * 审批通过
 *
 * 对审批中的批次执行审批通过操作，状态变为 APPROVED。
 *
 * @param id - 批次唯一标识
 * @returns 返回审批后的状态
 */
export async function approveBatch(id: number) {
  return request<API.Result<{ status: string }>>(`${API_PREFIX}/batches/${id}/approve`, {
    method: 'POST',
  });
}

/**
 * 发放确认
 *
 * 对已通过审批的批次执行最终发放确认操作，状态变为 DISTRIBUTED。
 * 发放后员工可在门户查看工资条。
 *
 * @param id - 批次唯一标识
 * @returns 返回发放后的状态
 */
export async function distributeBatch(id: number) {
  return request<API.Result<{ status: string }>>(`${API_PREFIX}/batches/${id}/distribute`, {
    method: 'POST',
  });
}

// ========== 工资条 ==========

/**
 * 查询工资条列表（管理端）
 *
 * HR/财务端查看所有员工的工资条列表，支持按账期和部门筛选。
 *
 * @param params - 查询参数
 * @param params.period - 账期筛选（可选），格式 YYYY-MM
 * @param params.departmentId - 部门筛选（可选）
 * @param params.page - 当前页码，默认 1
 * @param params.pageSize - 每页条数，默认 20
 * @returns 返回分页后的工资条列表
 */
export async function getPayslips(params: { period?: string; departmentId?: number; page?: number; pageSize?: number }) {
  return request<API.Result<API.Page<API.PayslipVO>>>(`${API_PREFIX}/payslips`, {
    method: 'GET',
    params,
  });
}

/**
 * 查询工资条详情（管理端）
 *
 * 获取指定月份某位员工的完整工资条明细，包含各薪资项及扣款明细。
 *
 * @param month - 月份标识，格式 YYYY-MM
 * @returns 返回工资条完整详情，含员工信息、收入项、扣款项及实发金额
 */
export async function getPayslipDetail(month: string) {
  return request<API.Result<API.PayslipDetailVO>>(`${API_PREFIX}/payslips/${month}`);
}

/**
 * 查询成本报表
 *
 * 获取指定时间范围内的薪资成本趋势及部门分布数据。
 *
 * @param params - 查询参数
 * @param params.periodFrom - 起始月份，格式 YYYY-MM（必填）
 * @param params.periodTo - 结束月份，格式 YYYY-MM（必填）
 * @param params.departmentId - 部门筛选（可选）
 * @returns 返回成本报表数据，含趋势图和部门分布
 */
export async function getCostReport(params: { periodFrom: string; periodTo: string; departmentId?: number }) {
  return request<API.Result<API.CostReportVO>>(`${API_PREFIX}/cost-report`, {
    method: 'GET',
    params,
  });
}

// ========== 门户工资条 ==========

/**
 * 查询工资条列表（门户/员工端）
 *
 * 员工查看本人的工资条列表，仅返回 SELF 范围数据。
 *
 * @returns 返回该员工的历史工资条列表
 */
export async function portalGetPayslips() {
  return request<API.Result<API.PayslipVO[]>>(`/api/v1/profile/payslips`);
}

/**
 * 查询工资条趋势（门户/员工端）
 *
 * 获取员工近 6 个月的实发工资趋势数据。
 *
 * @returns 返回近 6 个月的工资趋势数组
 */
export async function portalGetPayslipTrend() {
  return request<API.Result<API.PayslipTrendVO[]>>(`/api/v1/profile/payslips/trend`);
}

/**
 * 查询工资条详情（门户/员工端）
 *
 * 员工查看某月工资条的完整明细，需先通过二次验证（verifyPayslip）。
 * 未验证时返回 60004 错误码。
 *
 * @param period - 月份标识，格式 YYYY-MM
 * @returns 返回工资条完整详情
 */
export async function portalGetPayslipDetail(period: string) {
  return request<API.Result<API.PayslipDetailVO>>(`/api/v1/profile/payslips/${period}`);
}

/**
 * 二次验证（门户/工资条查看前验证）
 *
 * 员工查看工资条详情前需进行安全验证（密码或短信），
 * 验证通过后 30 分钟内免验证（Redis 缓存）。
 *
 * @param data - 验证参数
 * @param data.password - 验证密码
 * @returns 返回验证结果
 */
export async function verifyPayslip(data: { password: string }) {
  return request<API.Result<{ verified: boolean }>>(`/api/v1/profile/payslips/verify`, {
    method: 'POST',
    data,
  });
}

/**
 * 下载工资条 PDF（门户/员工端）
 *
 * 获取指定月份工资条的 PDF 格式文件下载流。
 *
 * @param period - 月份标识，格式 YYYY-MM
 * @returns 返回 PDF 文件的二进制流，可用于前端下载或预览
 */
export async function downloadPayslipPdf(period: string) {
  return request<Blob>(`/api/v1/profile/payslips/${period}/pdf`, {
    method: 'GET',
    responseType: 'blob',
  });
}

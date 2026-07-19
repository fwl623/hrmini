/** 审批流程类型 → 中文 */
export const PROCESS_TYPE_LABEL: Record<string, string> = {
  ONBOARDING: '入职',
  REGULARIZATION: '转正',
  TRANSFER: '调岗',
  RESIGNATION: '离职',
  RESIGNATION_REQUEST: '离职申请',
  MOBILE_CHANGE: '手机号变更',
  LEAVE: '请假',
  OVERTIME: '加班',
  PUNCH_FIX: '补卡',
  PAYROLL: '薪资核算',
};

export function processTypeLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim().toUpperCase();
  return PROCESS_TYPE_LABEL[key] || code;
}

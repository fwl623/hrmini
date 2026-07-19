/** 请假类型（与后端 leave_type 枚举对齐，统一大写） */
export const LEAVE_TYPE_OPTIONS = [
  { label: '年假', value: 'ANNUAL' },
  { label: '病假', value: 'SICK' },
  { label: '事假', value: 'PERSONAL' },
  { label: '婚假', value: 'MARRIAGE' },
  { label: '产假', value: 'MATERNITY' },
  { label: '丧假', value: 'BEREAVEMENT' },
  { label: '调休', value: 'COMP_OFF' },
];

const LEAVE_TYPE_LABEL: Record<string, string> = {
  ANNUAL: '年假',
  SICK: '病假',
  PERSONAL: '事假',
  MARRIAGE: '婚假',
  MATERNITY: '产假',
  BEREAVEMENT: '丧假',
  COMP_OFF: '调休',
  /** 历史/测试数据别名 */
  COMPENSATORY: '调休',
};

/** 将后端请假类型码转为中文展示 */
export function leaveTypeLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim().toUpperCase();
  return LEAVE_TYPE_LABEL[key] || code;
}

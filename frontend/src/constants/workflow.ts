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
  MAKEUP: '补卡',
  PUNCH_FIX: '补卡',
  PAYROLL: '薪资核算',
  PAYROLL_BATCH: '薪资核算',
};

/** 审批操作 → 中文 */
export const APPROVAL_ACTION_LABEL: Record<string, string> = {
  SUBMIT: '提交',
  APPROVE: '同意',
  REJECT: '驳回',
  FORWARD: '转交',
  WITHDRAW: '撤回',
  REMIND: '催办',
};

/** 时间线节点状态（wait/process/finish/error）→ 中文 */
export const TIMELINE_STATUS_LABEL: Record<string, string> = {
  wait: '等待',
  process: '进行中',
  finish: '已完成',
  error: '已驳回',
};

/** 流程节点 state → 中文 */
export const NODE_STATE_LABEL: Record<string, string> = {
  pending: '未到达',
  current: '审批中',
  done: '已完成',
  cancelled: '已取消',
};

/** 任务状态 → 中文 */
export const TASK_STATUS_LABEL: Record<string, string> = {
  pending: '待审批',
  approved: '已同意',
  rejected: '已驳回',
  cancelled: '已取消',
  done: '已完成',
};

/** 离职类型 → 中文 */
export const RESIGNATION_TYPE_LABEL: Record<string, string> = {
  resignation: '辞职',
  dismissal: '辞退',
  contract_expiry: '合同到期',
  other: '其他',
};

/** 离职原因分类 → 中文 */
export const RESIGNATION_REASON_LABEL: Record<string, string> = {
  VOLUNTARY: '自愿',
  INVOLUNTARY: '非自愿',
  NEGOTIATED: '协商',
};

/** 用工类型 → 中文 */
export const EMPLOYMENT_TYPE_LABEL: Record<string, string> = {
  fulltime: '全职',
  parttime: '兼职',
  intern: '实习',
  FULLTIME: '全职',
  PARTTIME: '兼职',
  INTERN: '实习',
};

export function processTypeLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim().toUpperCase();
  return PROCESS_TYPE_LABEL[key] || code;
}

export function approvalActionLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim().toUpperCase();
  return APPROVAL_ACTION_LABEL[key] || code;
}

export function timelineStatusLabel(code?: string | null): string {
  if (!code) return '-';
  return TIMELINE_STATUS_LABEL[code] || code;
}

export function nodeStateLabel(code?: string | null): string {
  if (!code) return '-';
  return NODE_STATE_LABEL[code] || code;
}

export function taskStatusLabel(code?: string | null): string {
  if (!code) return '-';
  return TASK_STATUS_LABEL[String(code).toLowerCase()] || code;
}

export function resignationTypeLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim().toLowerCase();
  return RESIGNATION_TYPE_LABEL[key] || code;
}

export function resignationReasonLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim().toUpperCase();
  return RESIGNATION_REASON_LABEL[key] || code;
}

export function employmentTypeLabel(code?: string | null): string {
  if (!code) return '-';
  const key = String(code).trim();
  return EMPLOYMENT_TYPE_LABEL[key] || EMPLOYMENT_TYPE_LABEL[key.toLowerCase()] || code;
}

/** 把时间线文案中的英文流程类型/操作码替换为中文（兼容历史数据） */
export function localizeTimelineText(text?: string | null): string {
  if (!text) return '';
  let s = String(text);
  for (const [k, v] of Object.entries(PROCESS_TYPE_LABEL)) {
    s = s.replace(new RegExp(k, 'gi'), v);
  }
  for (const [k, v] of Object.entries(APPROVAL_ACTION_LABEL)) {
    s = s.replace(new RegExp(`\\b${k}\\b`, 'gi'), v);
  }
  return s;
}

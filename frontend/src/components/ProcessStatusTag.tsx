import { Tag } from 'antd';

const STATUS_META: Record<string, { color: string; label: string }> = {
  draft: { color: 'default', label: '草稿' },
  DRAFT: { color: 'default', label: '草稿' },
  pending: { color: 'processing', label: '审批中' },
  PENDING: { color: 'processing', label: '待审批' },
  approved_pending: { color: 'warning', label: '待入职' },
  onboarded: { color: 'success', label: '已入职' },
  rejected: { color: 'error', label: '已拒绝' },
  REJECTED: { color: 'error', label: '已驳回' },
  abandoned: { color: 'error', label: '已放弃' },
  approved: { color: 'success', label: '已通过' },
  APPROVED: { color: 'success', label: '已通过' },
  PENDING_RESIGN: { color: 'warning', label: '待离职' },
  pending_resign: { color: 'warning', label: '待离职' },
  RESIGNED: { color: 'default', label: '已离职' },
  resigned: { color: 'default', label: '已离职' },
  cancelled: { color: 'default', label: '已取消' },
  CANCELLED: { color: 'default', label: '已撤销' },
  PASS: { color: 'success', label: '转正通过' },
  EXTEND: { color: 'warning', label: '延长试用' },
  FAIL: { color: 'error', label: '不通过' },
  node_1: { color: 'processing', label: '原部门确认中' },
  node_2: { color: 'processing', label: '新部门确认中' },
  node_3: { color: 'processing', label: 'HR 备案中' },
};

export interface ProcessStatusTagProps {
  status?: string | null;
  /** 覆盖展示文案 */
  label?: string;
}

/**
 * 流程状态彩色 Tag（入职/调岗/离职/审批任务等）
 */
export default function ProcessStatusTag({ status, label }: ProcessStatusTagProps) {
  if (!status) {
    return <Tag>—</Tag>;
  }
  const meta = STATUS_META[status] ?? { color: 'default', label: status };
  return <Tag color={meta.color}>{label ?? meta.label}</Tag>;
}

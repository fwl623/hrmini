import { Tag } from 'antd';

const STATUS_META: Record<string, { color: string; label: string }> = {
  draft: { color: 'default', label: '草稿' },
  DRAFT: { color: 'default', label: '草稿' },
  pending: { color: 'processing', label: '审批中' },
  PENDING: { color: 'processing', label: '待审批' },
  APPROVING: { color: 'processing', label: '审批中' },
  approving: { color: 'processing', label: '审批中' },
  approved_pending: { color: 'warning', label: '待入职' },
  onboarded: { color: 'success', label: '已入职' },
  rejected: { color: 'error', label: '已拒绝' },
  REJECTED: { color: 'error', label: '已驳回' },
  abandoned: { color: 'error', label: '已放弃' },
  approved: { color: 'success', label: '已通过' },
  APPROVED: { color: 'success', label: '已通过' },
  COMPLETED: { color: 'success', label: '已完成' },
  completed: { color: 'success', label: '已完成' },
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
 * 【流程状态 Tag 通用组件】
 *
 * 按 status 码映射 Ant Design Tag 颜色与中文标签；兼容大小写（draft/PENDING/approved_pending 等）。
 * 支持 label prop 覆盖展示文案。
 *
 * 复用方：
 * - pages/admin/approval/index.tsx（列表与详情状态）
 * - pages/admin/regularization/index.tsx（转正记录状态）
 * - pages/admin/resignation/index.tsx（员工申请 / 正式离职 Tab）
 * - pages/portal/resignation/index.tsx（本人申请记录）
 */
export default function ProcessStatusTag({ status, label }: ProcessStatusTagProps) {
  if (!status) {
    return <Tag>—</Tag>;
  }
  const meta = STATUS_META[status] ?? { color: 'default', label: status };
  return <Tag color={meta.color}>{label ?? meta.label}</Tag>;
}

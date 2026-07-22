import { Timeline, Tag, Typography } from 'antd';
import type { TimelineItemProps } from 'antd';
import { timelineStatusLabel } from '@/constants/workflow';

export type TimelineNodeStatus = 'wait' | 'process' | 'finish' | 'error';

export interface ApprovalTimelineNode {
  key: string;
  title: string;
  /** 审批人姓名 */
  assigneeName?: string;
  comment?: string;
  time?: string;
  status: TimelineNodeStatus;
  /** 代审文案，如「孙强 代 李明 审批」 */
  displayText?: string;
}

export interface ApprovalTimelineProps {
  nodes: ApprovalTimelineNode[];
  /** 可选：当前节点索引高亮 */
  current?: number;
}

const colorMap: Record<TimelineNodeStatus, string> = {
  wait: 'gray',
  process: 'blue',
  finish: 'green',
  error: 'red',
};

/**
 * 【审批时间线通用组件】
 *
 * 将审批实例节点 + 日志渲染为 Ant Design Timeline；节点含审批人、状态 Tag、意见、时间、代审文案。
 * 文案中文化走 @/constants/workflow（timelineStatusLabel / localizeTimelineText）。
 *
 * 复用方：
 * - pages/admin/approval/index.tsx（详情 Drawer 中部）
 *
 * 类似但未直接引用本组件的页面（内联 Steps/Timeline + fetchInstanceDetail）：
 * - pages/admin/onboarding/index.tsx（审批进度 Drawer）
 * - pages/portal/leave/index.tsx、pages/portal/overtime/index.tsx（查看申请审批进度）
 */
export default function ApprovalTimeline({ nodes }: ApprovalTimelineProps) {
  const items: TimelineItemProps[] = nodes.map((n) => ({
    color: colorMap[n.status],
    children: (
      <div>
        <Typography.Text strong>{n.title}</Typography.Text>
        {n.assigneeName ? (
          <Typography.Text type="secondary"> · {n.assigneeName}</Typography.Text>
        ) : null}
        <div>
          <Tag>{timelineStatusLabel(n.status)}</Tag>
          {n.displayText ? (
            <Typography.Text type="secondary" style={{ marginLeft: 4 }}>
              {n.displayText}
            </Typography.Text>
          ) : null}
        </div>
        {n.comment ? <Typography.Paragraph style={{ marginBottom: 0 }}>{n.comment}</Typography.Paragraph> : null}
        {n.time ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {n.time}
          </Typography.Text>
        ) : null}
      </div>
    ),
  }));

  return <Timeline items={items} />;
}

/**
 * 审批进度抽屉（门户 / 管理端复用）
 * - 状态中文化（pending → 待审批）
 * - 时间线英文码替换（OVERTIME → 加班）
 * - 节点展示审批人
 */
import React from 'react';
import { Card, Drawer, Space, Steps, Tag, Timeline, Typography } from 'antd';
import type { ApprovalTimelineItem } from '@/services/workflow';
import {
  localizeTimelineText,
  nodeStateLabel,
  taskStatusLabel,
} from '@/constants/workflow';

export type ApprovalProgressNode = {
  order: number;
  label: string;
  state: string;
  assigneeName?: string;
  actualAssigneeName?: string;
  taskStatus?: string;
};

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'orange' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
  CANCELLED: { label: '已撤销', color: 'default' },
};

function statusMeta(code?: string) {
  const key = String(code || '').trim().toUpperCase();
  return STATUS_META[key] || { label: code || '-', color: 'default' };
}

function nodeAssigneeText(n: ApprovalProgressNode) {
  if (n.actualAssigneeName) {
    return `原审批人 ${n.assigneeName || '-'}，已转交 ${n.actualAssigneeName}`;
  }
  if (n.assigneeName) return `审批人：${n.assigneeName}`;
  return '审批人：待指定';
}

export type ApprovalProgressDrawerProps = {
  open: boolean;
  loading?: boolean;
  title?: string;
  status?: string;
  currentNodeLabel?: string;
  nodes?: ApprovalProgressNode[];
  timeline?: ApprovalTimelineItem[];
  onClose: () => void;
  width?: number;
};

const ApprovalProgressDrawer: React.FC<ApprovalProgressDrawerProps> = ({
  open,
  loading,
  title,
  status,
  currentNodeLabel,
  nodes = [],
  timeline = [],
  onClose,
  width = 460,
}) => {
  const currentStepIndex = Math.max(
    0,
    nodes.findIndex((n) => n.state === 'current'),
  );
  const currentNode = nodes.find((n) => n.state === 'current');
  const currentAssignee =
    currentNode?.actualAssigneeName || currentNode?.assigneeName || '';
  const meta = statusMeta(status);

  return (
    <Drawer
      title={title ? `审批进度 · ${title}` : '审批进度'}
      open={open}
      onClose={onClose}
      width={width}
      destroyOnClose
    >
      {loading ? (
        <Typography.Text type="secondary">加载中…</Typography.Text>
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <div>
            <div style={{ marginBottom: 8 }}>
              状态：
              <Tag color={meta.color} style={{ borderRadius: 999, marginInlineStart: 4 }}>
                {meta.label}
              </Tag>
            </div>
            {currentNodeLabel ? (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
                当前节点：{currentNodeLabel}
              </Typography.Paragraph>
            ) : null}
            {currentAssignee ? (
              <Typography.Paragraph style={{ marginBottom: 0 }}>
                待审批人：<Typography.Text strong>{currentAssignee}</Typography.Text>
              </Typography.Paragraph>
            ) : null}
          </div>

          {nodes.length > 0 ? (
            <Steps
              direction="vertical"
              size="small"
              current={currentStepIndex >= 0 ? currentStepIndex : nodes.length}
              items={nodes.map((n) => {
                const st = n.taskStatus
                  ? `${nodeStateLabel(n.state)} · ${taskStatusLabel(n.taskStatus)}`
                  : nodeStateLabel(n.state);
                return {
                  title: n.label,
                  description: (
                    <Space direction="vertical" size={0}>
                      <Typography.Text type="secondary">{nodeAssigneeText(n)}</Typography.Text>
                      <Typography.Text type="secondary">{st}</Typography.Text>
                    </Space>
                  ),
                  status:
                    n.state === 'done'
                      ? 'finish'
                      : n.state === 'current'
                        ? 'process'
                        : n.state === 'cancelled'
                          ? 'error'
                          : 'wait',
                };
              })}
            />
          ) : (
            <Typography.Text type="secondary">暂无审批节点信息</Typography.Text>
          )}

          <Card size="small" title="审批动态" type="inner">
            {timeline.length > 0 ? (
              <Timeline
                items={timeline.map((t, i) => ({
                  key: i,
                  children: (
                    <>
                      <Typography.Text>
                        {localizeTimelineText(t.displayText)
                          || `${t.assignee || '-'} · ${localizeTimelineText(t.action || t.node) || '-'}`}
                      </Typography.Text>
                      {t.comment ? (
                        <div>
                          <Typography.Text type="secondary">
                            {localizeTimelineText(t.comment)}
                          </Typography.Text>
                        </div>
                      ) : null}
                      {t.time ? (
                        <div>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {t.time}
                          </Typography.Text>
                        </div>
                      ) : null}
                    </>
                  ),
                }))}
              />
            ) : (
              <Typography.Text type="secondary">暂无审批记录</Typography.Text>
            )}
          </Card>
        </Space>
      )}
    </Drawer>
  );
};

export default ApprovalProgressDrawer;

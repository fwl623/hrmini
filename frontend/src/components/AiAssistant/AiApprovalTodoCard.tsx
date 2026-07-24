/**
 * 聊天内「我的待审批」列表：通过 / 驳回调 postTaskAction
 */
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Button, Input, Space, Tag, message } from 'antd';
import dayjs from 'dayjs';
import React, { useMemo, useState } from 'react';
import type { AiAction, AiApprovalTaskItem } from '@/services/ai';
import { postTaskAction } from '@/services/workflow';
import { getRequestErrorMessage } from '@/utils/requestError';

type Props = {
  action: AiAction;
  disabled?: boolean;
  onSuccess?: (tip: string) => void;
  onNavigate?: (route: string) => void;
};

function isToday(createTime?: string) {
  if (!createTime) return false;
  const d = dayjs(createTime);
  return d.isValid() && d.isSame(dayjs(), 'day');
}

const PROCESS_LABEL: Record<string, string> = {
  LEAVE: '请假',
  OVERTIME: '加班',
  ONBOARDING: '入职',
  REGULARIZATION: '转正',
  TRANSFER: '调岗',
  RESIGNATION: '离职',
  PUNCH_SUPPLEMENT: '补卡',
};

const AiApprovalTodoCard: React.FC<Props> = ({ action, disabled, onSuccess, onNavigate }) => {
  const [tasks, setTasks] = useState<AiApprovalTaskItem[]>(() => action.tasks ?? []);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [comment, setComment] = useState('');

  const empty = useMemo(() => tasks.length === 0, [tasks]);

  const runAction = async (taskId: number, act: 'APPROVE' | 'REJECT', cmt?: string) => {
    if (act === 'REJECT' && !cmt?.trim()) {
      message.warning('驳回请填写意见');
      return;
    }
    setBusyId(taskId);
    try {
      await postTaskAction(taskId, {
        action: act,
        comment: cmt?.trim() || undefined,
      });
      setTasks((prev) => prev.filter((t) => t.taskId !== taskId));
      setRejectingId(null);
      setComment('');
      const tip =
        act === 'APPROVE'
          ? `已通过待办 #${taskId}`
          : `已驳回待办 #${taskId}`;
      onSuccess?.(tip);
    } catch (e) {
      message.error(getRequestErrorMessage(e, '操作失败'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="ai-biz-card ai-todo-card" onClick={(e) => e.stopPropagation()}>
      <div className="ai-biz-card__title">{action.label || '我的待审批'}</div>
      <div className="ai-biz-card__hint">
        {empty ? '当前没有待办' : `共 ${tasks.length} 条，可在此通过或驳回`}
      </div>
      {!empty && (
        <div className="ai-todo-list">
          {tasks.map((t) => (
            <div key={t.taskId} className="ai-todo-item">
              <div className="ai-todo-item__head">
                <span className="ai-todo-item__title">{t.title || `待办 #${t.taskId}`}</span>
                {isToday(t.createTime) ? <Tag color="blue">今日</Tag> : null}
              </div>
              <div className="ai-todo-item__meta">
                {t.processType ? PROCESS_LABEL[t.processType] || t.processType : ''}
                {t.applicantName ? ` · ${t.applicantName}` : ''}
                {t.currentNodeLabel ? ` · ${t.currentNodeLabel}` : ''}
              </div>
              {t.businessSummary ? (
                <div className="ai-todo-item__summary">{t.businessSummary}</div>
              ) : null}
              {rejectingId === t.taskId ? (
                <div className="ai-todo-item__reject">
                  <Input.TextArea
                    rows={2}
                    value={comment}
                    placeholder="驳回意见（必填）"
                    maxLength={200}
                    onChange={(e) => setComment(e.target.value)}
                  />
                  <Space size={8} style={{ marginTop: 8 }}>
                    <Button
                      size="small"
                      danger
                      loading={busyId === t.taskId}
                      disabled={disabled}
                      onClick={() => void runAction(t.taskId, 'REJECT', comment)}
                    >
                      确认驳回
                    </Button>
                    <Button
                      size="small"
                      onClick={() => {
                        setRejectingId(null);
                        setComment('');
                      }}
                    >
                      取消
                    </Button>
                  </Space>
                </div>
              ) : (
                <Space size={8} style={{ marginTop: 8 }}>
                  <Button
                    size="small"
                    type="primary"
                    icon={<CheckOutlined />}
                    loading={busyId === t.taskId}
                    disabled={disabled}
                    onClick={() => void runAction(t.taskId, 'APPROVE')}
                  >
                    通过
                  </Button>
                  <Button
                    size="small"
                    danger
                    icon={<CloseOutlined />}
                    disabled={disabled || busyId === t.taskId}
                    onClick={() => {
                      setRejectingId(t.taskId);
                      setComment('');
                    }}
                  >
                    驳回
                  </Button>
                </Space>
              )}
            </div>
          ))}
        </div>
      )}
      {action.route ? (
        <Button
          type="link"
          size="small"
          style={{ paddingLeft: 0, marginTop: 4 }}
          onClick={() => onNavigate?.(action.route!)}
        >
          打开审批中心
        </Button>
      ) : null}
    </div>
  );
};

export default AiApprovalTodoCard;

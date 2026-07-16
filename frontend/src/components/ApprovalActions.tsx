import { Button, Input, Modal, Space, message } from 'antd';
import { useState } from 'react';

export type ApprovalActionType = 'APPROVE' | 'REJECT' | 'FORWARD' | 'WITHDRAW';

export interface ApprovalActionsProps {
  /** 是否展示撤回（发起人 + 第一级） */
  canWithdraw?: boolean;
  /** 是否当前审批人可操作 */
  canAct?: boolean;
  loading?: boolean;
  onAction?: (action: ApprovalActionType, payload: { comment?: string; targetUserId?: number }) => void | Promise<void>;
}

/**
 * 审批操作按钮组：同意 / 驳回 / 转交 / 撤回
 */
export default function ApprovalActions({
  canWithdraw = false,
  canAct = true,
  loading = false,
  onAction,
}: ApprovalActionsProps) {
  const [rejectOpen, setRejectOpen] = useState(false);
  const [forwardOpen, setForwardOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [targetUserId, setTargetUserId] = useState<string>('');

  const run = async (action: ApprovalActionType, payload: { comment?: string; targetUserId?: number } = {}) => {
    try {
      await onAction?.(action, payload);
      message.success(`${action} 已提交（Mock）`);
    } catch (e) {
      message.error((e as Error)?.message || '操作失败');
    }
  };

  return (
    <>
      <Space wrap>
        <Button
          type="primary"
          disabled={!canAct}
          loading={loading}
          onClick={() => run('APPROVE', { comment })}
        >
          同意
        </Button>
        <Button danger disabled={!canAct} loading={loading} onClick={() => setRejectOpen(true)}>
          驳回
        </Button>
        <Button disabled={!canAct} loading={loading} onClick={() => setForwardOpen(true)}>
          转交
        </Button>
        {canWithdraw ? (
          <Button
            loading={loading}
            onClick={() =>
              Modal.confirm({
                title: '确认撤回？',
                content: '仅第一级节点可撤回',
                onOk: () => run('WITHDRAW'),
              })
            }
          >
            撤回
          </Button>
        ) : null}
      </Space>

      <Modal
        title="驳回理由"
        open={rejectOpen}
        onCancel={() => setRejectOpen(false)}
        onOk={async () => {
          if (!comment.trim()) {
            message.warning('驳回必须填写意见');
            return;
          }
          await run('REJECT', { comment });
          setRejectOpen(false);
          setComment('');
        }}
      >
        <Input.TextArea
          rows={4}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="请输入驳回意见（必填）"
        />
      </Modal>

      <Modal
        title="转交给"
        open={forwardOpen}
        onCancel={() => setForwardOpen(false)}
        onOk={async () => {
          const id = Number(targetUserId);
          if (!id) {
            message.warning('请填写目标用户 ID（Mock）');
            return;
          }
          await run('FORWARD', { comment, targetUserId: id });
          setForwardOpen(false);
          setTargetUserId('');
        }}
      >
        <Input
          placeholder="targetUserId（Mock 输入数字）"
          value={targetUserId}
          onChange={(e) => setTargetUserId(e.target.value)}
        />
        <Input.TextArea
          style={{ marginTop: 8 }}
          rows={3}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="转交说明（可选）"
        />
      </Modal>
    </>
  );
}

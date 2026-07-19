import { Button, Input, Modal, Space, message } from 'antd';
import { useState } from 'react';

export type ApprovalActionType = 'APPROVE' | 'REJECT' | 'FORWARD' | 'WITHDRAW' | 'REMIND';

export interface ApprovalActionsProps {
  canWithdraw?: boolean;
  canAct?: boolean;
  canRemind?: boolean;
  loading?: boolean;
  onAction?: (
    action: ApprovalActionType,
    payload: { comment?: string; targetUserId?: number },
  ) => void | boolean | Promise<void | boolean>;
}

/**
 * 审批操作：同意 / 驳回 / 转交 / 催办 / 撤回
 */
export default function ApprovalActions({
  canWithdraw = false,
  canAct = true,
  canRemind = false,
  loading = false,
  onAction,
}: ApprovalActionsProps) {
  const [rejectOpen, setRejectOpen] = useState(false);
  const [forwardOpen, setForwardOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [targetUserId, setTargetUserId] = useState<string>('');

  const run = async (
    action: ApprovalActionType,
    payload: { comment?: string; targetUserId?: number } = {},
  ) => {
    try {
      const deferred = await onAction?.(action, payload);
      if (deferred === false) {
        return;
      }
      const tip =
        action === 'REMIND'
          ? '已催办'
          : action === 'WITHDRAW'
            ? '已撤回'
            : action === 'APPROVE'
              ? '已同意'
              : action === 'REJECT'
                ? '已驳回'
                : '已转交';
      message.success(tip);
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
        {canRemind ? (
          <Button loading={loading} onClick={() => run('REMIND')}>
            催办
          </Button>
        ) : null}
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
            message.warning('请填写目标用户 ID');
            return;
          }
          await run('FORWARD', { comment, targetUserId: id });
          setForwardOpen(false);
          setTargetUserId('');
        }}
      >
        <Input
          placeholder="targetUserId"
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

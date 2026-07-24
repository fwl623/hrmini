/**
 * 聊天内加班办事卡片
 */
import { CheckOutlined } from '@ant-design/icons';
import { Button, DatePicker, Form, Input, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { submitOvertime } from '@/services/attendance';
import type { AiAction } from '@/services/ai';
import { getRequestErrorMessage } from '@/utils/requestError';

type Props = {
  action: AiAction;
  disabled?: boolean;
  onSuccess?: (tip: string) => void;
  onNavigate?: (route: string) => void;
};

const AiOvertimeFormCard: React.FC<Props> = ({ action, disabled, onSuccess, onNavigate }) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    const p = action.prefill || {};
    form.setFieldsValue({
      overtimeDate: p.overtimeDate ? dayjs(String(p.overtimeDate)) : undefined,
      startTime: p.startTime ? dayjs(String(p.startTime)) : undefined,
      endTime: p.endTime ? dayjs(String(p.endTime)) : undefined,
      reason: (p.reason as string) || undefined,
    });
  }, [action, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await submitOvertime({
        overtimeDate: (values.overtimeDate as Dayjs).format('YYYY-MM-DD'),
        startTime: (values.startTime as Dayjs).toISOString(),
        endTime: (values.endTime as Dayjs).toISOString(),
        reason: values.reason,
      });
      setSubmitted(true);
      onSuccess?.('加班申请已提交，可在加班页查看审批进度。');
    } catch (err: unknown) {
      if ((err as { errorFields?: unknown })?.errorFields) return;
      message.error(getRequestErrorMessage(err, '提交失败'));
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted) {
    return (
      <div className="ai-biz-card ai-biz-card--done">
        <CheckOutlined className="ai-biz-card__done-icon" />
        <div>
          <div className="ai-biz-card__title">已提交加班申请</div>
          <div className="ai-biz-card__hint">可前往加班页查看进度</div>
        </div>
        {action.route ? (
          <Button size="small" type="link" onClick={() => onNavigate?.(action.route!)}>
            查看加班页
          </Button>
        ) : null}
      </div>
    );
  }

  return (
    <div className="ai-biz-card" onClick={(e) => e.stopPropagation()}>
      <div className="ai-biz-card__title">加班申请</div>
      <div className="ai-biz-card__hint">填写后确认提交，将进入审批流程</div>
      <Form form={form} layout="vertical" size="small" requiredMark={false} disabled={disabled}>
        <Form.Item
          name="overtimeDate"
          label="加班日期"
          rules={[{ required: true, message: '必填' }]}
        >
          <DatePicker style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="startTime" label="开始时间" rules={[{ required: true, message: '必填' }]}>
          <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="endTime" label="结束时间" rules={[{ required: true, message: '必填' }]}>
          <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="reason" label="加班原因" rules={[{ required: true, message: '必填' }]}>
          <Input.TextArea rows={2} maxLength={200} placeholder="请说明加班事由" />
        </Form.Item>
        <Button
          type="primary"
          block
          icon={<CheckOutlined />}
          loading={submitting}
          disabled={disabled}
          onClick={() => void handleSubmit()}
        >
          {action.label || '确认加班'}
        </Button>
      </Form>
    </div>
  );
};

export default AiOvertimeFormCard;

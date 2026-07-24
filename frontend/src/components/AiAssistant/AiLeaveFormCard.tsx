/**
 * 聊天内请假办事卡片：填表 → 确认 → 调 submitLeave
 */
import { CheckOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, DatePicker, Form, Input, InputNumber, Select, Upload, message } from 'antd';
import type { UploadFile, UploadProps } from 'antd/es/upload/interface';
import dayjs, { type Dayjs } from 'dayjs';
import React, { useEffect, useState } from 'react';
import { LEAVE_TYPE_OPTIONS } from '@/constants/leave';
import { calcLeaveDays, submitLeave } from '@/services/attendance';
import type { AiAction } from '@/services/ai';
import { uploadFile } from '@/services/file';
import { getRequestErrorMessage } from '@/utils/requestError';

const ATTACHMENT_REQUIRED_TYPES = ['SICK', 'MARRIAGE', 'MATERNITY'];
const MAX_FILE_SIZE = 10 * 1024 * 1024;

type Props = {
  action: AiAction;
  disabled?: boolean;
  onSuccess?: (tip: string) => void;
  onNavigate?: (route: string) => void;
};

const AiLeaveFormCard: React.FC<Props> = ({ action, disabled, onSuccess, onNavigate }) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [previewDays, setPreviewDays] = useState<number | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const leaveType = Form.useWatch('leaveType', form);
  const startTime = Form.useWatch('startTime', form) as Dayjs | undefined;
  const endTime = Form.useWatch('endTime', form) as Dayjs | undefined;

  useEffect(() => {
    const p = action.prefill || {};
    form.setFieldsValue({
      leaveType: (p.leaveType as string) || undefined,
      startTime: p.startTime ? dayjs(String(p.startTime)) : undefined,
      endTime: p.endTime ? dayjs(String(p.endTime)) : undefined,
      days: typeof p.days === 'number' ? p.days : undefined,
      reason: (p.reason as string) || undefined,
      attachment: (p.attachment as string) || undefined,
    });
  }, [action, form]);

  useEffect(() => {
    if (!startTime || !endTime || !startTime.isValid() || !endTime.isValid()) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await calcLeaveDays({
          startTime: startTime.toISOString(),
          endTime: endTime.toISOString(),
        });
        if (!cancelled && res.code === 0 && res.data?.days != null) {
          setPreviewDays(res.data.days);
          form.setFieldsValue({ days: res.data.days });
        }
      } catch {
        /* ignore preview errors */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [startTime, endTime, form]);

  const daysValue = previewDays ?? form.getFieldValue('days') ?? 1;
  const needsAttachment =
    ATTACHMENT_REQUIRED_TYPES.includes(leaveType) &&
    (leaveType === 'SICK' ? Number(daysValue) > 1 : true);

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    const name = file.name.toLowerCase();
    const allowedExt = /\.(jpe?g|png|gif|webp|bmp|pdf|docx?|xlsx?|txt)$/i.test(name);
    const isImage = (file.type || '').startsWith('image/');
    if (!allowedExt && !isImage) {
      message.error('仅支持上传图片或文件（jpg/png/pdf/doc/docx/xls/xlsx 等）');
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_FILE_SIZE) {
      message.error('文件大小不能超过 10MB');
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  const customRequest: UploadProps['customRequest'] = async (options) => {
    const { file, onSuccess: ok, onError } = options;
    try {
      const res = await uploadFile(file as File);
      if (res.code !== 0 || !res.data?.url) {
        throw new Error(res.message || '上传失败');
      }
      ok?.(res.data);
      form.setFieldsValue({ attachment: res.data.url });
      message.success('证明材料上传成功');
    } catch (e) {
      message.error((e as Error).message || '上传失败');
      onError?.(e as Error);
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (needsAttachment && !values.attachment) {
        message.warning('该请假类型需要上传证明材料');
        return;
      }
      setSubmitting(true);
      await submitLeave({
        leaveType: values.leaveType,
        startTime: (values.startTime as Dayjs).toISOString(),
        endTime: (values.endTime as Dayjs).toISOString(),
        days: previewDays || values.days || 1,
        reason: values.reason || '',
        attachment: values.attachment || undefined,
      });
      setSubmitted(true);
      onSuccess?.('请假申请已提交，可在请假页查看审批进度。');
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
          <div className="ai-biz-card__title">已提交请假申请</div>
          <div className="ai-biz-card__hint">可前往请假页查看审批进度</div>
        </div>
        {action.route ? (
          <Button size="small" type="link" onClick={() => onNavigate?.(action.route!)}>
            查看请假页
          </Button>
        ) : null}
      </div>
    );
  }

  return (
    <div className="ai-biz-card" onClick={(e) => e.stopPropagation()}>
      <div className="ai-biz-card__title">请假申请</div>
      <div className="ai-biz-card__hint">填写后确认提交，将进入审批流程</div>
      <Form form={form} layout="vertical" size="small" requiredMark={false} disabled={disabled}>
        <Form.Item
          name="leaveType"
          label="请假类型"
          rules={[{ required: true, message: '请选择类型' }]}
        >
          <Select options={LEAVE_TYPE_OPTIONS} placeholder="选择类型" allowClear />
        </Form.Item>
        <Form.Item
          name="startTime"
          label="开始时间"
          rules={[{ required: true, message: '必填' }]}
        >
          <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="endTime"
          label="结束时间"
          rules={[{ required: true, message: '必填' }]}
        >
          <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="days" label="天数">
          <InputNumber min={0.5} step={0.5} style={{ width: '100%' }} disabled />
        </Form.Item>
        {previewDays != null ? (
          <div className="ai-biz-card__days">预览天数：{previewDays} 天</div>
        ) : null}
        <Form.Item name="reason" label="请假原因">
          <Input.TextArea rows={2} placeholder="选填" maxLength={200} />
        </Form.Item>
        <Form.Item name="attachment" hidden>
          <Input />
        </Form.Item>
        {needsAttachment ? (
          <Form.Item label="证明材料" required>
            <Upload
              maxCount={1}
              fileList={fileList}
              beforeUpload={beforeUpload}
              customRequest={customRequest}
              onChange={({ fileList: next }) => {
                setFileList(next.slice(-1));
                const f = next[0];
                if (!f || f.status === 'removed') {
                  form.setFieldsValue({ attachment: undefined });
                }
              }}
            >
              <Button icon={<UploadOutlined />} size="small">
                上传材料
              </Button>
            </Upload>
            <div className="ai-biz-card__hint">病假超过1天需医院证明；婚假/产假需证明材料</div>
          </Form.Item>
        ) : null}
        <Button
          type="primary"
          block
          icon={<CheckOutlined />}
          loading={submitting}
          disabled={disabled}
          onClick={() => void handleSubmit()}
        >
          {action.label || '确认请假'}
        </Button>
      </Form>
    </div>
  );
};

export default AiLeaveFormCard;

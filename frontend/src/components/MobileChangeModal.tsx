/**
 * 手机号变更申请 Modal
 * 员工在档案页或门户点击「变更手机号」→ 弹出此 Modal → 提交申请
 *
 * ✅ 无依赖（UI 先行），联调时对接 POST /api/v1/profile/mobile-change-applications
 */
import React, { useState } from 'react';
import { Modal, Form, Input, message, Typography } from 'antd';
import { request } from '@umijs/max';

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

const MobileChangeModal: React.FC<Props> = ({ open, onClose, onSuccess }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: any) => {
    setLoading(true);
    try {
      const res = await request('/api/v1/profile/mobile-change-applications', {
        method: 'POST',
        data: values,
      });
      if (res.code === 0) {
        message.success('手机号变更申请已提交，请等待 HR 审批');
        form.resetFields();
        onSuccess?.();
        onClose();
      } else {
        message.error(res.message || '提交失败');
      }
    } catch {
      message.error('提交失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="手机号变更申请"
      open={open}
      onOk={() => form.submit()}
      onCancel={() => { form.resetFields(); onClose(); }}
      confirmLoading={loading}
      okText="提交申请"
      cancelText="取消"
      destroyOnClose
    >
      <Typography.Paragraph type="secondary">
        提交后需 HR 审批通过方可生效，期间原手机号可正常使用。
      </Typography.Paragraph>
      <Form form={form} layout="vertical" onFinish={handleSubmit}>
        <Form.Item name="newMobile" label="新手机号" rules={[
          { required: true, message: '请输入新手机号' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
        ]}>
          <Input placeholder="11 位手机号" maxLength={11} />
        </Form.Item>
        <Form.Item name="smsCode" label="短信验证码" rules={[{ required: true, message: '请输入验证码' }]}>
          <Input placeholder="短信验证码" maxLength={6} />
        </Form.Item>
        <Form.Item name="reason" label="变更原因">
          <Input.TextArea rows={2} placeholder="选填：说明变更原因" maxLength={256} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default MobileChangeModal;

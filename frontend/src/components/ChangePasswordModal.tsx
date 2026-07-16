import { Form, Input, Modal, message } from 'antd';
import React, { useState } from 'react';
import { PASSWORD_PATTERN, PASSWORD_RULE_MESSAGE } from '@/constants/password';
import { changePassword } from '@/services/auth';
import { getRequestErrorMessage } from '@/utils/requestError';

interface ChangePasswordModalProps {
  open: boolean;
  force?: boolean;
  onSuccess: () => void;
  onCancel?: () => void;
}

const ChangePasswordModal: React.FC<ChangePasswordModalProps> = ({
  open,
  force = false,
  onSuccess,
  onCancel,
}) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (values.newPassword !== values.confirmPassword) {
        message.error('两次输入的新密码不一致');
        return;
      }
      setSubmitting(true);
      const res = await changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      if (res.code !== 0) {
        message.error(res.message || '修改密码失败');
        return;
      }
      message.success('密码修改成功，请重新登录');
      form.resetFields();
      onSuccess();
    } catch (err: unknown) {
      // 表单校验失败无 response；业务/401（旧密码错误）走这里
      if ((err as { errorFields?: unknown })?.errorFields) {
        return;
      }
      message.error(getRequestErrorMessage(err, '修改密码失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={force ? '首次登录须修改密码' : '修改密码'}
      open={open}
      onOk={handleOk}
      onCancel={force ? undefined : onCancel}
      confirmLoading={submitting}
      closable={!force}
      maskClosable={!force}
      cancelButtonProps={force ? { style: { display: 'none' } } : undefined}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="oldPassword"
          label="当前密码"
          rules={[{ required: true, message: '请输入当前密码' }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          extra={PASSWORD_RULE_MESSAGE}
          rules={[
            { required: true, message: '请输入新密码' },
            {
              pattern: PASSWORD_PATTERN,
              message: PASSWORD_RULE_MESSAGE,
            },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          rules={[{ required: true, message: '请再次输入新密码' }]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ChangePasswordModal;

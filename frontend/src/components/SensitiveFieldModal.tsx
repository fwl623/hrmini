/**
 * 敏感字段查看 Modal
 * - 首次点击弹出密码输入框
 * - 调用 GET /api/v1/employees/{id}/sensitive/{field} + X-Sensitive-Password
 * - 成功后展示明文值，30分钟内免密
 */
import React, { useState } from 'react';
import { Modal, Input, message, Typography, Space } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';

interface Props {
  employeeId: number;
  field: string;        // idNumber | bankAccount
  label: string;        // "身份证号"
  defaultValue?: string; // 脱敏后默认显示值 "3301**********1234"
  onSuccess?: (value: string) => void;
}

const SensitiveFieldModal: React.FC<Props> = ({ employeeId, field, label, defaultValue, onSuccess }) => {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [revealed, setRevealed] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleVerify = async () => {
    if (!password) { message.warning('请输入登录密码'); return; }
    setLoading(true);
    try {
      const res = await request(`/api/v1/employees/${employeeId}/sensitive/${field}`, {
        method: 'GET',
        headers: { 'X-Sensitive-Password': password },
      });
      if (res.code === 0 && res.data?.value) {
        setRevealed(res.data.value);
        message.success('验证通过');
        onSuccess?.(res.data.value);
        setOpen(false);
      } else {
        message.error(res.message || '验证失败');
      }
    } catch {
      message.error('敏感字段查看失败');
    } finally {
      setLoading(false);
    }
  };

  const displayValue = revealed || defaultValue || '***';

  return (
    <>
      <span
        style={{ cursor: revealed ? 'text' : 'pointer', color: revealed ? undefined : '#1890ff' }}
        onClick={() => !revealed && setOpen(true)}
      >
        {revealed ? (
          <Typography.Text strong>{revealed}</Typography.Text>
        ) : (
          <Space size={4}>
            <EyeOutlined />
            <span>{displayValue}</span>
          </Space>
        )}
      </span>

      <Modal
        title={`查看${label}`}
        open={open}
        onOk={handleVerify}
        onCancel={() => { setOpen(false); setPassword(''); }}
        confirmLoading={loading}
        okText="验证并查看"
      >
        <Typography.Paragraph type="secondary">
          查看敏感信息需要进行身份验证，请输入您的登录密码。
        </Typography.Paragraph>
        <Input.Password
          placeholder="请输入登录密码"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onPressEnter={handleVerify}
          autoFocus
        />
      </Modal>
    </>
  );
};

export default SensitiveFieldModal;

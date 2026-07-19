/**
 * 敏感字段展示组件
 * - 脱敏展示（默认 "***"）
 * - 点击弹出密码验证弹窗
 * - 成功后显示明文
 * - 不占用工资条 verify Key
 */
import React, { useState } from 'react';
import { Modal, Input, message, Typography, Space } from 'antd';
import { EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';

interface Props {
  employeeId: number;
  field: string;         // idNumber | bankAccount
  label: string;         // "身份证号"
  defaultValue?: string; // 脱敏默认值
}

const SensitiveField: React.FC<Props> = ({ employeeId, field, label, defaultValue }) => {
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
        setOpen(false);
      } else {
        message.error(res.message || '验证失败');
      }
    } catch {
      message.error('查看失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <span
        onClick={() => !revealed && setOpen(true)}
        style={{ cursor: revealed ? 'text' : 'pointer', color: revealed ? 'inherit' : '#1890ff' }}
      >
        <Space size={4}>
          {revealed ? <EyeInvisibleOutlined /> : <EyeOutlined />}
          <span>{revealed || defaultValue || '***'}</span>
        </Space>
      </span>

      <Modal
        title={`查看${label}`}
        open={open}
        onOk={handleVerify}
        onCancel={() => { setOpen(false); setPassword(''); }}
        confirmLoading={loading}
        okText="验证并查看"
        cancelText="取消"
      >
        <Typography.Paragraph type="secondary">
          查看敏感信息需验证身份，请输入您的登录密码。
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

export default SensitiveField;

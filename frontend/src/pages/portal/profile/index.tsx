/**
 * 我的档案（员工门户）
 * 对接：GET/PUT /api/v1/profile/me、GET /api/v1/profile/transfer-history
 */
import React, { useEffect, useState } from 'react';
import { Card, Descriptions, Form, Input, Button, Spin, message, Space, Typography, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  getMyProfile,
  updateMyProfile,
  getMyTransferHistory,
  type ProfileVO,
  type TransferHistoryItem,
} from '@/services/employee';
import MobileChangeModal from '@/components/MobileChangeModal';

const ProfilePage: React.FC = () => {
  const [profile, setProfile] = useState<ProfileVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [mobileModalOpen, setMobileModalOpen] = useState(false);
  const [history, setHistory] = useState<TransferHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [form] = Form.useForm();

  const loadProfile = () => {
    setLoading(true);
    getMyProfile()
      .then((res) => {
        if (res.code === 0) {
          setProfile(res.data);
          form.setFieldsValue({
            email: res.data.email,
            residenceAddress: res.data.residenceAddress,
            emergencyContact: res.data.emergencyContact,
            emergencyPhone: res.data.emergencyPhone,
          });
        }
      })
      .finally(() => setLoading(false));
  };

  const loadHistory = () => {
    setHistoryLoading(true);
    getMyTransferHistory()
      .then((res) => {
        if (res.code === 0) setHistory(res.data ?? []);
      })
      .catch(() => setHistory([]))
      .finally(() => setHistoryLoading(false));
  };

  useEffect(() => {
    loadProfile();
    loadHistory();
  }, []);

  const handleSave = async (values: any) => {
    setSubmitting(true);
    try {
      const res = await updateMyProfile(values);
      if (res.code === 0) {
        message.success('保存成功');
        setEditing(false);
        loadProfile();
      } else message.error(res.message);
    } finally {
      setSubmitting(false);
    }
  };

  const historyCols: ColumnsType<TransferHistoryItem> = [
    { title: '调岗日期', dataIndex: 'transferDate', width: 120 },
    {
      title: '部门',
      render: (_, r) => `${r.fromDepartmentName || '-'} → ${r.toDepartmentName || '-'}`,
    },
    {
      title: '职位',
      render: (_, r) => `${r.fromPositionName || '-'} → ${r.toPositionName || '-'}`,
    },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
  ];

  if (loading) return <Spin style={{ display: 'block', marginTop: 100 }} />;
  if (!profile) return <div style={{ textAlign: 'center', marginTop: 100 }}>加载失败</div>;

  return (
    <>
      <Typography.Title level={4}>我的档案</Typography.Title>
      <Card style={{ marginBottom: 16 }}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="姓名">{profile.name}</Descriptions.Item>
          <Descriptions.Item label="工号">{profile.empNo}</Descriptions.Item>
          <Descriptions.Item label="手机号">{profile.mobile}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{profile.email}</Descriptions.Item>
          <Descriptions.Item label="职级">{profile.grade || '-'}</Descriptions.Item>
          <Descriptions.Item label="入职日期">{profile.hireDate || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        title={`调岗历史${history.length ? `（${history.length}）` : ''}`}
        style={{ marginBottom: 16 }}
        loading={historyLoading}
      >
        <Table
          rowKey="id"
          size="small"
          columns={historyCols}
          dataSource={history}
          pagination={false}
          locale={{ emptyText: '暂无调岗记录' }}
        />
      </Card>

      <Card
        title="可编辑信息"
        extra={
          editing ? (
            <Space>
              <Button size="small" onClick={() => setEditing(false)}>
                取消
              </Button>
              <Button
                size="small"
                type="primary"
                loading={submitting}
                onClick={() => form.submit()}
              >
                保存
              </Button>
            </Space>
          ) : (
            <Button size="small" type="primary" onClick={() => setEditing(true)}>
              编辑
            </Button>
          )
        }
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="邮箱" span={2}>
              {editing ? (
                <Form.Item name="email" noStyle>
                  <Input />
                </Form.Item>
              ) : (
                profile.email || '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="现居地址" span={2}>
              {editing ? (
                <Form.Item name="residenceAddress" noStyle>
                  <Input />
                </Form.Item>
              ) : (
                profile.residenceAddress || '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="紧急联系人">
              {editing ? (
                <Form.Item name="emergencyContact" noStyle>
                  <Input />
                </Form.Item>
              ) : (
                profile.emergencyContact || '-'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="紧急电话">
              {editing ? (
                <Form.Item name="emergencyPhone" noStyle>
                  <Input />
                </Form.Item>
              ) : (
                profile.emergencyPhone || '-'
              )}
            </Descriptions.Item>
          </Descriptions>
        </Form>
      </Card>

      <Card
        title="不可编辑（如需修改请联系 HR）"
        style={{ marginTop: 16 }}
        extra={
          <Button size="small" onClick={() => setMobileModalOpen(true)}>
            申请变更手机号
          </Button>
        }
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="部门">{profile.department || '-'}</Descriptions.Item>
          <Descriptions.Item label="职位">{profile.position || '-'}</Descriptions.Item>
          <Descriptions.Item label="基本工资">
            {profile.baseSalary != null
              ? `¥${Number(profile.baseSalary).toLocaleString('zh-CN', {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                })}`
              : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="手机号">{profile.mobile}</Descriptions.Item>
          <Descriptions.Item label="身份证号">***（敏感信息）</Descriptions.Item>
        </Descriptions>
      </Card>

      <MobileChangeModal
        open={mobileModalOpen}
        onClose={() => setMobileModalOpen(false)}
        onSuccess={loadProfile}
      />
    </>
  );
};

export default ProfilePage;

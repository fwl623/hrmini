/**
 * 我的档案（员工门户）
 * 对接：GET/PUT /api/v1/profile/me、GET /api/v1/profile/transfer-history
 */
import React, { useEffect, useState } from 'react';
import {
  BankOutlined,
  CalendarOutlined,
  EditOutlined,
  EnvironmentOutlined,
  IdcardOutlined,
  LockOutlined,
  MailOutlined,
  MobileOutlined,
  PhoneOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Space,
  Spin,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  getMyProfile,
  updateMyProfile,
  getMyTransferHistory,
  type ProfileVO,
  type TransferHistoryItem,
} from '@/services/employee';
import MobileChangeModal from '@/components/MobileChangeModal';
import './profile.less';

function avatarText(name?: string) {
  const n = (name || '').trim();
  if (!n) return '员';
  return n.length <= 2 ? n : n.slice(-2);
}

function Field({
  label,
  icon,
  value,
  span,
  editing,
  children,
}: {
  label: string;
  icon?: React.ReactNode;
  value?: React.ReactNode;
  span?: 2 | 3;
  editing?: boolean;
  children?: React.ReactNode;
}) {
  const spanClass = span === 3 ? 'profile-field--span3' : span === 2 ? 'profile-field--span2' : '';
  const empty = value == null || value === '' || value === '-';
  return (
    <div className={`profile-field ${editing ? 'profile-field--editing' : ''} ${spanClass}`.trim()}>
      <div className="profile-field__label">
        {icon}
        {label}
      </div>
      {editing ? (
        children
      ) : (
        <div className={`profile-field__value${empty ? ' profile-field__value--muted' : ''}`}>
          {empty ? '-' : value}
        </div>
      )}
    </div>
  );
}

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

  const handleSave = async (values: {
    email?: string;
    residenceAddress?: string;
    emergencyContact?: string;
    emergencyPhone?: string;
  }) => {
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

  if (loading) {
    return (
      <div className="profile-loading">
        <Spin size="large" />
      </div>
    );
  }
  if (!profile) {
    return (
      <div className="profile-empty">
        <Empty description="档案加载失败" />
      </div>
    );
  }

  const salaryText =
    profile.baseSalary != null
      ? `¥${Number(profile.baseSalary).toLocaleString('zh-CN', {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}`
      : '-';

  return (
    <div className="profile-page">
      <section className="profile-hero">
        <div className="profile-hero__avatar" aria-hidden>
          {avatarText(profile.name)}
        </div>
        <div className="profile-hero__body">
          <h1 className="profile-hero__name">{profile.name || '未命名'}</h1>
          <div className="profile-hero__meta">
            {profile.position ? (
              <span className="profile-hero__chip profile-hero__chip--primary">{profile.position}</span>
            ) : null}
            {profile.department ? (
              <span className="profile-hero__chip">
                <TeamOutlined />
                {profile.department}
              </span>
            ) : null}
            {profile.empNo ? (
              <span className="profile-hero__chip">
                <IdcardOutlined />
                工号 {profile.empNo}
              </span>
            ) : null}
            {profile.grade ? <span className="profile-hero__chip">职级 {profile.grade}</span> : null}
          </div>
          <div className="profile-hero__stats">
            <div>
              <span className="profile-hero__stat-label">手机号</span>
              <span className="profile-hero__stat-value">{profile.mobile || '-'}</span>
            </div>
            <div>
              <span className="profile-hero__stat-label">邮箱</span>
              <span className="profile-hero__stat-value">{profile.email || '-'}</span>
            </div>
            <div>
              <span className="profile-hero__stat-label">入职日期</span>
              <span className="profile-hero__stat-value">{profile.hireDate || '-'}</span>
            </div>
          </div>
        </div>
      </section>

      <Card
        className="profile-section"
        title="基本信息"
        bordered={false}
      >
        <div className="profile-grid">
          <Field label="姓名" icon={<UserOutlined />} value={profile.name} />
          <Field label="工号" icon={<IdcardOutlined />} value={profile.empNo} />
          <Field label="职级" icon={<BankOutlined />} value={profile.grade || '-'} />
          <Field label="手机号" icon={<MobileOutlined />} value={profile.mobile} />
          <Field label="邮箱" icon={<MailOutlined />} value={profile.email} />
          <Field label="入职日期" icon={<CalendarOutlined />} value={profile.hireDate || '-'} />
        </div>
      </Card>

      <div className="profile-row">
        <Card
          className="profile-section profile-section--editable"
          bordered={false}
          title="可编辑信息"
          extra={
            editing ? (
              <Space size={8}>
                <Button
                  size="small"
                  onClick={() => {
                    setEditing(false);
                    form.setFieldsValue({
                      email: profile.email,
                      residenceAddress: profile.residenceAddress,
                      emergencyContact: profile.emergencyContact,
                      emergencyPhone: profile.emergencyPhone,
                    });
                  }}
                >
                  取消
                </Button>
                <Button size="small" type="primary" loading={submitting} onClick={() => form.submit()}>
                  保存
                </Button>
              </Space>
            ) : (
              <Button size="small" type="primary" icon={<EditOutlined />} onClick={() => setEditing(true)}>
                编辑
              </Button>
            )
          }
        >
          <p className="profile-hint">以下信息可由本人维护，保存后即时生效</p>
          <Form form={form} layout="vertical" onFinish={handleSave} requiredMark={false}>
            <div className="profile-grid profile-grid--2">
              <Field label="邮箱" icon={<MailOutlined />} editing={editing} span={2} value={profile.email}>
                <Form.Item name="email" noStyle>
                  <Input placeholder="请输入邮箱" allowClear />
                </Form.Item>
              </Field>
              <Field
                label="现居地址"
                icon={<EnvironmentOutlined />}
                editing={editing}
                span={2}
                value={profile.residenceAddress}
              >
                <Form.Item name="residenceAddress" noStyle>
                  <Input placeholder="请输入现居地址" allowClear />
                </Form.Item>
              </Field>
              <Field
                label="紧急联系人"
                icon={<UserOutlined />}
                editing={editing}
                value={profile.emergencyContact}
              >
                <Form.Item name="emergencyContact" noStyle>
                  <Input placeholder="紧急联系人" allowClear />
                </Form.Item>
              </Field>
              <Field
                label="紧急电话"
                icon={<PhoneOutlined />}
                editing={editing}
                value={profile.emergencyPhone}
              >
                <Form.Item name="emergencyPhone" noStyle>
                  <Input placeholder="紧急联系电话" allowClear />
                </Form.Item>
              </Field>
            </div>
          </Form>
        </Card>

        <Card
          className="profile-section profile-section--locked"
          bordered={false}
          title="任职与敏感信息"
          extra={
            <Button size="small" icon={<MobileOutlined />} onClick={() => setMobileModalOpen(true)}>
              申请变更手机号
            </Button>
          }
        >
          <p className="profile-hint">如需修改请联系 HR；手机号须走变更审批</p>
          <div className="profile-grid profile-grid--2">
            <Field label="部门" icon={<TeamOutlined />} value={profile.department || '-'} />
            <Field label="职位" icon={<BankOutlined />} value={profile.position || '-'} />
            <Field label="基本工资" icon={<LockOutlined />} value={salaryText} />
            <Field label="手机号" icon={<MobileOutlined />} value={profile.mobile} />
            <Field
              label="身份证号"
              icon={<LockOutlined />}
              span={2}
              value={
                <Tag bordered={false} color="default">
                  ***（敏感信息）
                </Tag>
              }
            />
          </div>
        </Card>
      </div>

      <Card
        className="profile-section"
        bordered={false}
        title={`调岗历史${history.length ? `（${history.length}）` : ''}`}
        loading={historyLoading}
      >
        {history.length === 0 && !historyLoading ? (
          <div className="profile-empty">
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无调岗记录" />
          </div>
        ) : (
          <Table
            rowKey="id"
            size="middle"
            columns={historyCols}
            dataSource={history}
            pagination={false}
          />
        )}
      </Card>

      <MobileChangeModal
        open={mobileModalOpen}
        onClose={() => setMobileModalOpen(false)}
        onSuccess={loadProfile}
      />
    </div>
  );
};

export default ProfilePage;

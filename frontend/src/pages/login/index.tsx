import { Button, Card, Space, Typography } from 'antd';
import { history } from '@umijs/max';

/** 登录页临时欢迎 — Sprint 1 按系分 §2.2.1 实现真实登录 */
export default function LoginPage() {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #f0f5ff 0%, #ffffff 60%)',
      }}
    >
      <Card style={{ width: 440, textAlign: 'center' }}>
        <Typography.Title level={3} style={{ marginBottom: 8 }}>
          HRMini 人力资源管理系统
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
          校企培训项目 · Sprint 0 骨架环境
        </Typography.Paragraph>
        <Typography.Text type="secondary">
          登录表单与 JWT 鉴权将在 Sprint 1 接入，当前可预览双端布局。
        </Typography.Text>
        <Space direction="vertical" size="middle" style={{ width: '100%', marginTop: 28 }}>
          <Button type="primary" block onClick={() => history.push('/admin/workbench')}>
            进入管理后台（预览）
          </Button>
          <Button block onClick={() => history.push('/portal/profile')}>
            进入员工门户（预览）
          </Button>
        </Space>
      </Card>
    </div>
  );
}

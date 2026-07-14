import { Card, Descriptions, Typography } from 'antd';

/** 我的档案临时欢迎 — Sprint 1 按系分 §2.2.13 对接 /profile/me */
export default function ProfilePage() {
  return (
    <>
      <Typography.Title level={4}>我的档案</Typography.Title>
      <Typography.Paragraph type="secondary">
        员工自助门户 · 面向普通员工（EMPLOYEE）
      </Typography.Paragraph>
      <Card style={{ marginTop: 16 }}>
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="姓名">（Sprint 1 从 API 加载）</Descriptions.Item>
          <Descriptions.Item label="工号">—</Descriptions.Item>
          <Descriptions.Item label="部门">—</Descriptions.Item>
          <Descriptions.Item label="职位">—</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card style={{ marginTop: 16 }} title="开发说明">
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          本页位于 <Typography.Text code>/portal/profile</Typography.Text>，使用{' '}
          <Typography.Text strong>PortalLayout</Typography.Text>（员工门户菜单）。
          数据接口为 <Typography.Text code>/api/v1/profile/me</Typography.Text>，与页面路由分离。
        </Typography.Paragraph>
      </Card>
    </>
  );
}

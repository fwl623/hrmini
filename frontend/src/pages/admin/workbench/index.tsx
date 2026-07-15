import { Card, Col, Row, Typography } from 'antd';

/** 工作台临时欢迎 — Sprint 1 按系分 §2.2.2 实现 KPI 与图表 */
export default function WorkbenchPage() {
  return (
    <>
      <Typography.Title level={4}>欢迎回来</Typography.Title>
      <Typography.Paragraph type="secondary">
        管理后台工作台 · 面向 HR、部门主管、财务、系统管理员
      </Typography.Paragraph>
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {['待审批', '本月入职', '待转正', '考勤异常'].map((title) => (
          <Col key={title} xs={24} sm={12} lg={6}>
            <Card size="small" title={title}>
              <Typography.Text type="secondary">Sprint 1 接入数据</Typography.Text>
            </Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 16 }} title="开发说明">
        <Typography.Paragraph style={{ marginBottom: 0 }}>
          本页位于 <Typography.Text code>/admin/workbench</Typography.Text>，使用{' '}
          <Typography.Text strong>AdminLayout</Typography.Text>（左侧管理菜单 + 顶栏）。
          真实统计卡片与图表见前端系分 §2.2.2。
        </Typography.Paragraph>
      </Card>
    </>
  );
}

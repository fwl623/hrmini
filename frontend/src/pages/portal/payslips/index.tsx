import React from 'react';
import { Card, Row, Col, Empty } from 'antd';

const PortalPayslipPage: React.FC = () => {
  return (
    <Row gutter={[24, 24]}>
      <Col span={24}>
        <Card title="近6月实发趋势">
          <Empty description="暂无数据" />
        </Card>
      </Col>
      <Col span={24}>
        <Card title="我的工资条">
          <Empty description="暂无数据" />
        </Card>
      </Col>
    </Row>
  );
};
export default PortalPayslipPage;

import React from 'react';
import { Card, Row, Col, Statistic, Empty } from 'antd';

const CostReportPage: React.FC = () => {
  return (
    <Card title="成本报表">
      <Row gutter={[24, 24]}>
        <Col span={24}>
          <Card title="成本趋势" size="small">
            <Empty description="暂无数据" />
          </Card>
        </Col>
        <Col span={24}>
          <Card title="部门分布" size="small">
            <Empty description="暂无数据" />
          </Card>
        </Col>
      </Row>
    </Card>
  );
};
export default CostReportPage;

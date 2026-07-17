import React, { useState, useEffect } from 'react';
import { Card, Row, Col, DatePicker, TreeSelect, Space, Spin, Empty, message } from 'antd';
import { Line, Column } from '@ant-design/plots';
import dayjs from 'dayjs';

import { getCostReport } from '@/services/payroll';

const { RangePicker } = DatePicker;

// 模拟部门树数据 —— 联调时替换为真实接口
const mockDeptTree = [
  {
    title: '总公司',
    value: 0,
    children: [
      { title: '技术部', value: 1 },
      { title: '市场部', value: 2 },
      { title: '财务部', value: 3 },
      { title: '人事部', value: 4 },
    ],
  },
];

const CostReportPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [periodRange, setPeriodRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>([
    dayjs().subtract(5, 'month').startOf('month'),
    dayjs().endOf('month'),
  ]);
  const [departmentId, setDepartmentId] = useState<number | undefined>(undefined);
  const [trendData, setTrendData] = useState<{ period: string; grossTotal: number; netTotal: number }[]>([]);
  const [deptData, setDeptData] = useState<{ deptName: string; grossTotal: number; netTotal: number }[]>([]);

  const fetchData = async (from?: string, to?: string, deptId?: number) => {
    if (!from || !to) return;
    setLoading(true);
    try {
      const res = await getCostReport({ periodFrom: from, periodTo: to, departmentId: deptId });
      const data = res.data as API.CostReportVO;
      if (data) {
        setTrendData(data.trend || []);
        setDeptData(data.deptDistribution || []);
      }
    } catch (err: any) {
      message.error(err?.message || '获取成本报表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (periodRange?.[0] && periodRange?.[1]) {
      fetchData(
        periodRange[0].format('YYYY-MM'),
        periodRange[1].format('YYYY-MM'),
        departmentId,
      );
    }
  }, []);

  const handleSearch = () => {
    if (!periodRange?.[0] || !periodRange?.[1]) {
      message.warning('请选择账期范围');
      return;
    }
    fetchData(
      periodRange[0].format('YYYY-MM'),
      periodRange[1].format('YYYY-MM'),
      departmentId,
    );
  };

  // Line chart data: transform to flat structure with series field
  const lineChartData = trendData.flatMap((item) => [
    { period: item.period, value: item.grossTotal, type: '应发总额' },
    { period: item.period, value: item.netTotal, type: '实发总额' },
  ]);

  const lineConfig = {
    data: lineChartData,
    xField: 'period' as const,
    yField: 'value' as const,
    seriesField: 'type' as const,
    color: ['#1677ff', '#52c41a'],
    smooth: true,
    point: { size: 3 },
    legend: {
      position: 'top' as const,
    },
    yAxis: {
      label: {
        formatter: (v: number) => `${(v / 10000).toFixed(2)}万`,
      },
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: datum.type,
        value: `${datum.value?.toFixed(2)} 元`,
      }),
    },
    height: 300,
  };

  const columnConfig = {
    data: deptData,
    xField: 'deptName' as const,
    yField: 'grossTotal' as const,
    color: '#1677ff',
    label: {
      position: 'top' as const,
      formatter: (datum: any) => `${(datum.grossTotal / 10000).toFixed(2)}万`,
    },
    yAxis: {
      label: {
        formatter: (v: number) => `${(v / 10000).toFixed(2)}万`,
      },
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: '薪资总额',
        value: `${datum.grossTotal?.toFixed(2)} 元`,
      }),
    },
    height: 300,
  };

  return (
    <Card title="成本报表">
      <Space style={{ marginBottom: 24 }} wrap>
        <RangePicker
          picker="month"
          value={periodRange as any}
          onChange={(dates) => setPeriodRange(dates as any)}
          allowClear={false}
        />
        <TreeSelect
          treeData={mockDeptTree}
          placeholder="选择部门"
          allowClear
          style={{ width: 200 }}
          value={departmentId}
          onChange={(val) => setDepartmentId(val)}
          treeDefaultExpandAll
        />
        <span>
          <a onClick={handleSearch} style={{ cursor: 'pointer' }}>
            查询
          </a>
        </span>
      </Space>

      <Spin spinning={loading}>
        <Row gutter={[24, 24]}>
          <Col span={24}>
            <Card title="薪资成本月度趋势" size="small">
              {lineChartData.length > 0 ? (
                <Line {...lineConfig} />
              ) : (
                <Empty description="暂无趋势数据" />
              )}
            </Card>
          </Col>
          <Col span={24}>
            <Card title="部门薪资分布" size="small">
              {deptData.length > 0 ? (
                <Column {...columnConfig} />
              ) : (
                <Empty description="暂无分布数据" />
              )}
            </Card>
          </Col>
        </Row>
      </Spin>
    </Card>
  );
};
export default CostReportPage;

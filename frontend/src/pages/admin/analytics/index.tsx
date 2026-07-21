/**
 * 人力资源数据概览（对齐中台「数据分析」版式，指标换成 HR 语义）
 */
import { Column, Line, Pie } from '@ant-design/plots';
import { history, useAccess } from '@umijs/max';
import {
  Alert,
  Card,
  Col,
  DatePicker,
  Empty,
  Radio,
  Row,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getAnalyticsOverview,
  type AnalyticsOverview,
  type KpiRow,
} from '@/services/analytics';
import { getRequestErrorMessage } from '@/utils/requestError';

const { RangePicker } = DatePicker;

type RangeKey = 'today' | '7d' | '30d' | 'custom';

const EMPTY: AnalyticsOverview = {
  from: '',
  to: '',
  headcountTrend: [],
  deptDistribution: [],
  workflowThroughput: [],
  costTrend: [],
  kpiTable: [],
};

function resolveRange(key: RangeKey, custom?: [Dayjs, Dayjs] | null): { from: string; to: string } {
  const to = dayjs();
  if (key === 'today') {
    return { from: to.format('YYYY-MM-DD'), to: to.format('YYYY-MM-DD') };
  }
  if (key === '7d') {
    return { from: to.subtract(6, 'day').format('YYYY-MM-DD'), to: to.format('YYYY-MM-DD') };
  }
  if (key === '30d') {
    return { from: to.subtract(29, 'day').format('YYYY-MM-DD'), to: to.format('YYYY-MM-DD') };
  }
  if (custom?.[0] && custom?.[1]) {
    return {
      from: custom[0].format('YYYY-MM-DD'),
      to: custom[1].format('YYYY-MM-DD'),
    };
  }
  return { from: to.subtract(29, 'day').format('YYYY-MM-DD'), to: to.format('YYYY-MM-DD') };
}

function formatChange(rate?: number | null) {
  if (rate == null || Number.isNaN(rate)) return '—';
  const pct = (rate * 100).toFixed(1);
  return `${rate > 0 ? '+' : ''}${pct}%`;
}

const AnalyticsPage: React.FC = () => {
  const access = useAccess();
  const [rangeKey, setRangeKey] = useState<RangeKey>('30d');
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [loading, setLoading] = useState(true);
  const [degraded, setDegraded] = useState(false);
  const [data, setData] = useState<AnalyticsOverview>(EMPTY);

  const load = useCallback(async () => {
    const { from, to } = resolveRange(rangeKey, customRange);
    setLoading(true);
    setDegraded(false);
    try {
      const res = await getAnalyticsOverview({ from, to });
      if (res?.code === 0 && res.data) {
        setData(res.data);
      } else {
        setData(EMPTY);
        setDegraded(true);
        message.warning(res?.message || '概览加载失败');
      }
    } catch (e) {
      setData(EMPTY);
      setDegraded(true);
      message.error(getRequestErrorMessage(e, '概览加载失败'));
    } finally {
      setLoading(false);
    }
  }, [rangeKey, customRange]);

  useEffect(() => {
    void load();
  }, [load]);

  const headcountLineData = useMemo(() => {
    const rows: { date: string; type: string; value: number }[] = [];
    for (const p of data.headcountTrend || []) {
      rows.push({ date: p.date.slice(5), type: '入职', value: p.hires ?? 0 });
      rows.push({ date: p.date.slice(5), type: '离职', value: p.resignations ?? 0 });
    }
    return rows;
  }, [data.headcountTrend]);

  const deptPieData = useMemo(
    () =>
      (data.deptDistribution || []).map((d) => ({
        type: d.deptName,
        value: d.headcount,
      })),
    [data.deptDistribution],
  );

  const workflowColumnData = useMemo(() => {
    const rows: { label: string; type: string; value: number }[] = [];
    for (const w of data.workflowThroughput || []) {
      const name = w.label || w.processType || '其他';
      rows.push({ label: name, type: '发起', value: w.submitted ?? 0 });
      rows.push({ label: name, type: '通过', value: w.approved ?? 0 });
    }
    return rows;
  }, [data.workflowThroughput]);

  const costAreaData = useMemo(
    () =>
      (data.costTrend || []).map((c) => ({
        period: c.period,
        netTotal: c.netTotal ?? 0,
      })),
    [data.costTrend],
  );

  const kpiColumns = [
    { title: '指标', dataIndex: 'metric', key: 'metric' },
    { title: '数值', dataIndex: 'value', key: 'value' },
    {
      title: '环比变化',
      dataIndex: 'changeRate',
      key: 'changeRate',
      render: (v: number | null | undefined) => formatChange(v),
    },
    {
      title: '趋势',
      dataIndex: 'trend',
      key: 'trend',
      render: (t: string) => {
        if (t === '上升') return <Tag color="success">上升</Tag>;
        if (t === '下降') return <Tag color="error">下降</Tag>;
        return <Tag>持平</Tag>;
      },
    },
  ];

  return (
    <>
      <div className="hrms-page-header" style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ marginBottom: 4 }}>数据分析</h2>
          <Typography.Text type="secondary">全公司人事与考勤数据概览</Typography.Text>
        </div>
        <Space wrap>
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            value={rangeKey}
            onChange={(e) => {
              const key = e.target.value as RangeKey;
              setRangeKey(key);
              if (key !== 'custom') setCustomRange(null);
            }}
            options={[
              { label: '今日', value: 'today' },
              { label: '近7天', value: '7d' },
              { label: '近30天', value: '30d' },
              { label: '自定义', value: 'custom' },
            ]}
          />
          {rangeKey === 'custom' ? (
            <RangePicker
              value={customRange}
              onChange={(vals) => {
                if (vals?.[0] && vals?.[1]) {
                  setCustomRange([vals[0], vals[1]]);
                } else {
                  setCustomRange(null);
                }
              }}
              allowClear={false}
            />
          ) : null}
        </Space>
      </div>

      {degraded && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="部分统计暂时不可用，已降级为空数据"
        />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="人力变动趋势" extra="入职 & 离职对比" loading={loading}>
            {headcountLineData.some((d) => d.value > 0) ? (
              <Line
                data={headcountLineData}
                xField="date"
                yField="value"
                seriesField="type"
                height={280}
                smooth
                point={{ size: 2 }}
                legend={{ position: 'top' }}
                meta={{ value: { alias: '人数' } }}
              />
            ) : (
              <Empty description="所选期间暂无入职/离职数据" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="组织分布" extra="各部门在职人数占比" loading={loading}>
            {deptPieData.length ? (
              <Pie
                data={deptPieData}
                angleField="value"
                colorField="type"
                radius={0.9}
                innerRadius={0.55}
                height={280}
                legend={{ position: 'bottom' }}
                label={{
                  text: 'value',
                  style: { fontSize: 12 },
                }}
                tooltip={{ title: 'type' }}
              />
            ) : (
              <Empty description="暂无部门分布" />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={14}>
          <Card title="流程吞吐" extra="发起量与通过量对比" loading={loading}>
            {workflowColumnData.some((d) => d.value > 0) ? (
              <Column
                data={workflowColumnData}
                xField="label"
                yField="value"
                seriesField="type"
                isGroup
                height={280}
                legend={{ position: 'top' }}
              />
            ) : (
              <Empty description="所选期间暂无审批数据" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card
            title="人力成本趋势"
            extra="近期实发合计（元）"
            loading={loading}
            actions={
              access.canViewPayroll
                ? [
                    <Typography.Link key="cost" onClick={() => history.push('/admin/payroll/cost-report')}>
                      查看成本报表
                    </Typography.Link>,
                  ]
                : undefined
            }
          >
            {costAreaData.length ? (
              <Column
                data={costAreaData}
                xField="period"
                yField="netTotal"
                height={280}
                meta={{ netTotal: { alias: '实发合计' } }}
              />
            ) : (
              <Empty description={access.canViewPayroll ? '暂无成本数据' : '无薪资权限或暂无批次数据'} />
            )}
          </Card>
        </Col>
      </Row>

      <Card title="数据汇总" style={{ marginTop: 16 }} loading={loading}>
        <Table<KpiRow>
          rowKey="metric"
          size="middle"
          pagination={false}
          columns={kpiColumns}
          dataSource={data.kpiTable || []}
          locale={{ emptyText: <Empty description="暂无汇总指标" /> }}
        />
      </Card>
    </>
  );
};

export default AnalyticsPage;

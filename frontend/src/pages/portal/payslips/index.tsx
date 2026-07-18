import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Table, Tag, Drawer, Descriptions, Modal, Input, message, Spin, Empty, Button, Divider } from 'antd';
import { EyeOutlined, LockOutlined } from '@ant-design/icons';
import { Line } from '@ant-design/plots';

import {
  portalGetPayslips,
  portalGetPayslipTrend,
  portalGetPayslipDetail,
  verifyPayslip,
} from '@/services/payroll';

const statusLabel: Record<string, string> = {
  NORMAL: '已发放',
  ADJUSTED: '已调整',
  FROZEN: '已冻结',
};

const statusColor: Record<string, string> = {
  NORMAL: 'green',
  ADJUSTED: 'orange',
  FROZEN: 'blue',
};

const PortalPayslipPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<API.PayslipVO[]>([]);
  const [trend, setTrend] = useState<API.PayslipTrendVO[]>([]);

  // Drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detail, setDetail] = useState<API.PayslipDetailVO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Verification modal
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [verifyPassword, setVerifyPassword] = useState('');
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [selectedPeriod, setSelectedPeriod] = useState<string>('');

  // Load list and trend on mount
  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [listRes, trendRes] = await Promise.all([
        portalGetPayslips(),
        portalGetPayslipTrend(),
      ]);
      setList((listRes.data as any)?.list || listRes.data || []);
      setTrend(trendRes.data || []);
    } catch (err: any) {
      message.error(err?.message || '获取工资条数据失败');
    } finally {
      setLoading(false);
    }
  };

  // Open verification modal
  const handleViewDetail = (period: string) => {
    setSelectedPeriod(period);
    setVerifyPassword('');
    setVerifyOpen(true);
  };

  // On verification success, fetch detail and open drawer
  const handleVerify = async () => {
    if (!verifyPassword) {
      message.warning('请输入密码');
      return;
    }
    setVerifyLoading(true);
    try {
      const res = await verifyPayslip({ password: verifyPassword });
      const verified = (res.data as any)?.verified ?? (res as any)?.data?.verified;
      if (verified) {
        setVerifyOpen(false);
        fetchDetail(selectedPeriod);
      } else {
        message.error('密码验证失败');
      }
    } catch (err: any) {
      message.error(err?.message || '验证失败');
    } finally {
      setVerifyLoading(false);
    }
  };

  const fetchDetail = async (period: string) => {
    setDetailLoading(true);
    setDrawerOpen(true);
    try {
      const res = await portalGetPayslipDetail(period);
      setDetail(res.data as API.PayslipDetailVO);
    } catch (err: any) {
      message.error(err?.message || '获取工资条详情失败');
      setDrawerOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  // Line chart config for trend
  const trendData = (trend || []).map((item) => ({
    period: item.period,
    value: item.netSalary,
  }));

  const lineConfig = {
    data: trendData,
    xField: 'period' as const,
    yField: 'value' as const,
    smooth: true,
    color: '#1677ff',
    point: { size: 4 },
    yAxis: {
      label: {
        formatter: (v: number) => `${(v / 10000).toFixed(2)}万`,
      },
    },
    tooltip: {
      formatter: (datum: any) => ({
        name: '实发工资',
        value: `${datum.value?.toFixed(2)} 元`,
      }),
    },
    height: 280,
  };

  const columns = [
    { title: '账期', dataIndex: 'period', width: 120 },
    {
      title: '应发金额',
      dataIndex: 'grossSalary',
      width: 120,
      render: (v: number) => v?.toFixed(2),
    },
    {
      title: '实发金额',
      dataIndex: 'netSalary',
      width: 120,
      render: (v: number) => <span style={{ fontWeight: 600 }}>{v?.toFixed(2)}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => (
        <Tag color={statusColor[v] || 'default'}>
          {statusLabel[v] || v}
        </Tag>
      ),
    },
    {
      title: '操作',
      width: 100,
      render: (_: any, r: API.PayslipVO) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewDetail(r.period)}
        >
          查看详情
        </Button>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      <Row gutter={[24, 24]}>
        <Col span={24}>
          <Card title="近6月实发趋势" size="small">
            {trendData.length > 0 ? (
              <Line {...lineConfig} />
            ) : (
              <Empty description="暂无趋势数据" />
            )}
          </Card>
        </Col>
        <Col span={24}>
          <Card title="我的工资条" size="small">
            <Table
              rowKey="period"
              columns={columns}
              dataSource={list}
              pagination={false}
            />
          </Card>
        </Col>
      </Row>

      {/* Password verification modal */}
      <Modal
        title="安全验证"
        open={verifyOpen}
        onOk={handleVerify}
        onCancel={() => setVerifyOpen(false)}
        confirmLoading={verifyLoading}
        okText="验证"
      >
        <div style={{ textAlign: 'center', padding: '16px 0' }}>
          <LockOutlined style={{ fontSize: 48, color: '#1677ff', marginBottom: 16 }} />
          <p>查看工资条详情需要进行安全验证</p>
          <Input.Password
            placeholder="请输入登录密码"
            value={verifyPassword}
            onChange={(e) => setVerifyPassword(e.target.value)}
            onPressEnter={handleVerify}
            autoFocus
          />
        </div>
      </Modal>

      {/* Detail drawer */}
      <Drawer
        title={`工资条详情 - ${detail?.period || ''}`}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={520}
        extra={
          <Button type="link" onClick={() => setDrawerOpen(false)}>
            关闭
          </Button>
        }
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <>
              <Descriptions column={1} bordered size="small" style={{ marginBottom: 24 }}>
                <Descriptions.Item label="姓名">{detail.employee?.name}</Descriptions.Item>
                <Descriptions.Item label="工号">{detail.employee?.employeeNo}</Descriptions.Item>
                <Descriptions.Item label="部门">{detail.employee?.department}</Descriptions.Item>
              </Descriptions>

              <Divider orientation="left" plain>
                收入明细
              </Divider>
              <Table
                rowKey="name"
                dataSource={detail.earnings || []}
                pagination={false}
                size="small"
                columns={[
                  { title: '项目', dataIndex: 'name' },
                  {
                    title: '金额',
                    dataIndex: 'amount',
                    render: (v: number) => v?.toFixed(2),
                  },
                ]}
                summary={() =>
                  detail.earnings?.length ? (
                    <Table.Summary.Row>
                      <Table.Summary.Cell>
                        <strong>应发合计</strong>
                      </Table.Summary.Cell>
                      <Table.Summary.Cell>
                        <strong>{detail.grossSalary?.toFixed(2)}</strong>
                      </Table.Summary.Cell>
                    </Table.Summary.Row>
                  ) : null
                }
              />

              <Divider orientation="left" plain>
                扣除明细
              </Divider>
              <Table
                rowKey="name"
                dataSource={detail.deductions || []}
                pagination={false}
                size="small"
                columns={[
                  { title: '项目', dataIndex: 'name' },
                  {
                    title: '金额',
                    dataIndex: 'amount',
                    render: (v: number) => v?.toFixed(2),
                  },
                ]}
                summary={() =>
                  detail.deductions?.length ? (
                    <>
                      <Table.Summary.Row>
                        <Table.Summary.Cell>
                          <strong>扣除合计</strong>
                        </Table.Summary.Cell>
                        <Table.Summary.Cell>
                          <strong>{detail.totalDeduction?.toFixed(2)}</strong>
                        </Table.Summary.Cell>
                      </Table.Summary.Row>
                      <Table.Summary.Row>
                        <Table.Summary.Cell>
                          <strong>实发金额</strong>
                        </Table.Summary.Cell>
                        <Table.Summary.Cell>
                          <strong style={{ color: '#52c41a', fontSize: 16 }}>
                            {detail.netSalary?.toFixed(2)}
                          </strong>
                        </Table.Summary.Cell>
                      </Table.Summary.Row>
                    </>
                  ) : null
                }
              />
            </>
          ) : (
            <Empty description="暂无详情数据" />
          )}
        </Spin>
      </Drawer>
    </Spin>
  );
};
export default PortalPayslipPage;

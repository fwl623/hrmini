/**
 * 工作台：4 KPI + 快捷入口 + 访问趋势 + 最近操作
 * 接口失败友好降级，不白屏
 */
import {
  AuditOutlined,
  ClusterOutlined,
  TeamOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Line } from '@ant-design/plots';
import { history, useAccess } from '@umijs/max';
import { Alert, Card, Col, Empty, List, Row, Space, Statistic, Typography, message } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { getWorkbenchSummary, type WorkbenchSummary } from '@/services/workbench';
import { getRequestErrorMessage } from '@/utils/requestError';

const EMPTY_SUMMARY: WorkbenchSummary = {
  totalEmployees: 0,
  newHiresThisMonth: 0,
  pendingApprovals: 0,
  attendanceAnomalies: 0,
  departmentStats: [],
  visitTrend: [],
  recentOperations: [],
};

type QuickLink = { title: string; path: string; show: boolean };

const WorkbenchPage: React.FC = () => {
  const access = useAccess();
  const [loading, setLoading] = useState(true);
  const [degraded, setDegraded] = useState(false);
  const [summary, setSummary] = useState<WorkbenchSummary>(EMPTY_SUMMARY);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await getWorkbenchSummary();
        if (cancelled) return;
        if (res.code === 0 && res.data) {
          setSummary({ ...EMPTY_SUMMARY, ...res.data });
          setDegraded(false);
        } else {
          setSummary(EMPTY_SUMMARY);
          setDegraded(true);
          message.warning(res.message || '工作台数据暂不可用，已降级展示');
        }
      } catch (e) {
        if (cancelled) return;
        setSummary(EMPTY_SUMMARY);
        setDegraded(true);
        message.warning(getRequestErrorMessage(e, '工作台数据加载失败，已降级展示'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const quickLinks: QuickLink[] = useMemo(
    () =>
      [
        { title: '花名册', path: '/admin/employee/list', show: !!access.canViewEmployee },
        { title: '部门管理', path: '/admin/org/departments', show: !!access.canViewDept },
        { title: '职位管理', path: '/admin/org/positions', show: !!access.canViewPosition },
        { title: '考勤组', path: '/admin/attendance/groups', show: !!access.canManageAttendance || !!access.canHr },
        {
          title: '账套管理',
          path: '/admin/payroll/schemes',
          show: !!access.canViewPayroll,
        },
        { title: '用户管理', path: '/admin/system/users', show: !!access.canManageSystem },
      ].filter((i) => i.show),
    [access],
  );

  const trendData = (summary.visitTrend || []).map((p) => ({
    date: p.date,
    count: p.count,
  }));

  const kpiCards = [
    {
      title: '在职总人数',
      value: summary.totalEmployees,
      icon: <TeamOutlined />,
      color: '#1677ff',
    },
    {
      title: '本月入职',
      value: summary.newHiresThisMonth,
      icon: <UserOutlined />,
      color: '#52c41a',
    },
    {
      title: '待审批',
      value: summary.pendingApprovals,
      icon: <AuditOutlined />,
      color: '#1677ff',
    },
    {
      title: '考勤异常',
      value: summary.attendanceAnomalies,
      icon: <WarningOutlined />,
      color: '#ff4d4f',
    },
  ];

  return (
    <>
      <Typography.Title level={4} style={{ marginTop: 0 }}>
        工作台
      </Typography.Title>
      {degraded && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="部分统计暂时不可用，已降级为空数据，不影响其他功能使用"
        />
      )}

      <Row gutter={[16, 16]}>
        {kpiCards.map((card) => (
          <Col key={card.title} xs={24} sm={12} lg={6}>
            <Card loading={loading} size="small">
              <Statistic
                title={
                  <Space>
                    <span style={{ color: card.color }}>{card.icon}</span>
                    {card.title}
                  </Space>
                }
                value={card.value}
                valueStyle={{ color: card.color }}
              />
            </Card>
          </Col>
        ))}
      </Row>

      {summary.todayPunchRate != null && (
        <Card size="small" style={{ marginTop: 16 }} loading={loading}>
          <Statistic
            title="今日打卡率"
            value={Math.round(summary.todayPunchRate * 10000) / 100}
            suffix="%"
            prefix={<ClusterOutlined />}
          />
        </Card>
      )}

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={10}>
          <Card title="快捷入口" loading={loading}>
            {quickLinks.length ? (
              <Space wrap>
                {quickLinks.map((link) => (
                  <Card
                    key={link.path}
                    size="small"
                    hoverable
                    style={{ width: 140, textAlign: 'center' }}
                    onClick={() => history.push(link.path)}
                  >
                    {link.title}
                  </Card>
                ))}
              </Space>
            ) : (
              <Empty description="暂无可用快捷入口" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card title="近 7 日访问趋势（登录成功）" loading={loading}>
            {trendData.some((d) => d.count > 0) ? (
              <Line
                data={trendData}
                xField="date"
                yField="count"
                height={220}
                point={{ size: 3 }}
                smooth
              />
            ) : (
              <Empty description="暂无访问数据" />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="部门人数 Top" loading={loading}>
            {(summary.departmentStats || []).length ? (
              <List
                size="small"
                dataSource={summary.departmentStats}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta title={item.deptName} />
                    <div>{item.headcount} 人</div>
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="暂无部门统计" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="最近操作" loading={loading}>
            {(summary.recentOperations || []).length ? (
              <List
                size="small"
                dataSource={summary.recentOperations}
                renderItem={(item) => (
                  <List.Item>
                    <List.Item.Meta
                      title={`${item.module} / ${item.action}`}
                      description={`用户 ${item.userId}${item.targetId ? ` · 目标 ${item.targetId}` : ''}`}
                    />
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.createdAt || ''}
                    </Typography.Text>
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="暂无操作记录" />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
};

export default WorkbenchPage;

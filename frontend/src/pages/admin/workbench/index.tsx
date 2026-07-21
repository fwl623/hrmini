/**
 * 工作台：4 KPI（可跳转）+ 可自定义快捷入口 + 访问趋势 + 最近操作
 * 接口失败友好降级，不白屏
 */
import {
  AuditOutlined,
  ClusterOutlined,
  PlusOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Line } from '@ant-design/plots';
import { history, useAccess, useModel } from '@umijs/max';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Empty,
  List,
  Modal,
  Row,
  Space,
  Statistic,
  Typography,
  message,
} from 'antd';
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

type ShortcutDef = {
  title: string;
  path: string;
  /** access 字段名；不填则始终可选 */
  accessKey?:
    | 'canViewEmployee'
    | 'canViewAnalytics'
    | 'canViewDept'
    | 'canViewPosition'
    | 'canManageWorkflow'
    | 'canManageResignation'
    | 'canApprove'
    | 'canManageAttendance'
    | 'canViewPayroll'
    | 'canManageSystem';
};

const SHORTCUT_CATALOG: ShortcutDef[] = [
  { title: '数据分析', path: '/admin/analytics', accessKey: 'canViewAnalytics' },
  { title: '花名册', path: '/admin/employee/list', accessKey: 'canViewEmployee' },
  { title: '手机号变更', path: '/admin/employee/mobile-change', accessKey: 'canViewEmployee' },
  { title: '部门管理', path: '/admin/org/departments', accessKey: 'canViewDept' },
  { title: '职位管理', path: '/admin/org/positions', accessKey: 'canViewPosition' },
  { title: '入职管理', path: '/admin/onboarding', accessKey: 'canManageWorkflow' },
  { title: '转正管理', path: '/admin/regularization', accessKey: 'canManageWorkflow' },
  { title: '调岗管理', path: '/admin/transfers', accessKey: 'canManageWorkflow' },
  { title: '离职管理', path: '/admin/resignation', accessKey: 'canManageResignation' },
  { title: '审批中心', path: '/admin/approval', accessKey: 'canApprove' },
  { title: '审批委托', path: '/admin/delegation', accessKey: 'canApprove' },
  { title: '考勤组', path: '/admin/attendance/groups', accessKey: 'canManageAttendance' },
  { title: '打卡中心', path: '/admin/attendance/punch', accessKey: 'canManageAttendance' },
  { title: '考勤统计', path: '/admin/attendance/statistics', accessKey: 'canManageAttendance' },
  { title: '月考勤汇总', path: '/admin/attendance/summary', accessKey: 'canManageAttendance' },
  { title: '请假列表', path: '/admin/leave/list', accessKey: 'canManageAttendance' },
  { title: '加班列表', path: '/admin/overtime/list', accessKey: 'canManageAttendance' },
  { title: '账套管理', path: '/admin/payroll/schemes', accessKey: 'canViewPayroll' },
  { title: '核算批次', path: '/admin/payroll/batches', accessKey: 'canViewPayroll' },
  { title: '用户管理', path: '/admin/system/users', accessKey: 'canManageSystem' },
];

const DEFAULT_SHORTCUT_PATHS = [
  '/admin/employee/list',
  '/admin/org/departments',
  '/admin/org/positions',
  '/admin/attendance/groups',
  '/admin/payroll/schemes',
  '/admin/approval',
];

function storageKey(userKey: string) {
  return `hrms.workbench.shortcuts.${userKey}`;
}

function loadShortcutPaths(userKey: string): string[] | null {
  try {
    const raw = localStorage.getItem(storageKey(userKey));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return null;
    return parsed.filter((p): p is string => typeof p === 'string');
  } catch {
    return null;
  }
}

function saveShortcutPaths(userKey: string, paths: string[]) {
  localStorage.setItem(storageKey(userKey), JSON.stringify(paths));
}

const WorkbenchPage: React.FC = () => {
  const access = useAccess();
  const { initialState } = useModel('@@initialState');
  const userKey = String(
    initialState?.currentUser?.userId ?? initialState?.currentUser?.username ?? 'anonymous',
  );

  const [loading, setLoading] = useState(true);
  const [degraded, setDegraded] = useState(false);
  const [summary, setSummary] = useState<WorkbenchSummary>(EMPTY_SUMMARY);
  const [editOpen, setEditOpen] = useState(false);
  const [draftPaths, setDraftPaths] = useState<string[]>([]);
  const [selectedPaths, setSelectedPaths] = useState<string[]>(DEFAULT_SHORTCUT_PATHS);

  useEffect(() => {
    const saved = loadShortcutPaths(userKey);
    setSelectedPaths(saved?.length ? saved : DEFAULT_SHORTCUT_PATHS);
  }, [userKey]);

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

  const allowedCatalog = useMemo(
    () =>
      SHORTCUT_CATALOG.filter((item) => {
        if (!item.accessKey) return true;
        return !!access[item.accessKey];
      }),
    [access],
  );

  const quickLinks = useMemo(() => {
    const byPath = new Map(allowedCatalog.map((i) => [i.path, i]));
    return selectedPaths.map((p) => byPath.get(p)).filter((i): i is ShortcutDef => !!i);
  }, [allowedCatalog, selectedPaths]);

  const openEdit = () => {
    setDraftPaths(quickLinks.map((i) => i.path));
    setEditOpen(true);
  };

  const saveEdit = () => {
    const allowed = new Set(allowedCatalog.map((i) => i.path));
    const next = draftPaths.filter((p) => allowed.has(p));
    setSelectedPaths(next);
    saveShortcutPaths(userKey, next);
    setEditOpen(false);
    message.success('快捷入口已更新');
  };

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
      path: access.canViewEmployee ? '/admin/employee/list' : undefined,
      hint: '查看花名册',
    },
    {
      title: '本月入职',
      value: summary.newHiresThisMonth,
      icon: <UserOutlined />,
      color: '#52c41a',
      path: access.canManageWorkflow || access.canHr ? '/admin/onboarding' : undefined,
      hint: '进入入职管理',
    },
    {
      title: '待审批',
      value: summary.pendingApprovals,
      icon: <AuditOutlined />,
      color: '#1677ff',
      path: access.canApprove ? '/admin/approval' : undefined,
      hint: '进入审批中心',
    },
    {
      title: '考勤异常',
      value: summary.attendanceAnomalies,
      icon: <WarningOutlined />,
      color: '#ff4d4f',
      path: access.canManageAttendance ? '/admin/attendance/statistics' : undefined,
      hint: '查看考勤统计',
    },
  ];

  return (
    <>
      <div className="hrms-page-header">
        <h2>工作台</h2>
        <p>欢迎回来，以下是系统概览</p>
      </div>
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
            <Card
              loading={loading}
              size="small"
              hoverable={!!card.path}
              className="hrms-stat-card"
              onClick={() => {
                if (card.path) history.push(card.path);
              }}
              style={card.path ? { cursor: 'pointer' } : undefined}
            >
              <Statistic
                title={
                  <Space>
                    <span style={{ color: card.color }}>{card.icon}</span>
                    {card.title}
                  </Space>
                }
                value={card.value}
              />
              {card.path ? (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  点击{card.hint}
                </Typography.Text>
              ) : null}
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
          <Card
            title="快捷入口"
            loading={loading}
            extra={
              <Button type="link" size="small" icon={<SettingOutlined />} onClick={openEdit}>
                自定义
              </Button>
            }
          >
            {quickLinks.length ? (
              <Space wrap>
                {quickLinks.map((link) => (
                  <Card
                    key={link.path}
                    size="small"
                    hoverable
                    style={{ width: 148, textAlign: 'center' }}
                    onClick={() => history.push(link.path)}
                  >
                    {link.title}
                  </Card>
                ))}
                <Card
                  size="small"
                  hoverable
                  style={{
                    width: 148,
                    textAlign: 'center',
                    borderStyle: 'dashed',
                    color: '#1677ff',
                  }}
                  onClick={openEdit}
                >
                  <PlusOutlined /> 添加
                </Card>
              </Space>
            ) : (
              <Empty
                description="暂无快捷入口"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              >
                <Button type="primary" icon={<PlusOutlined />} onClick={openEdit}>
                  添加快捷入口
                </Button>
              </Empty>
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

      <Modal
        title="自定义快捷入口"
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={saveEdit}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
        width={520}
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          勾选需要展示的入口（按权限过滤），保存后仅对本账号生效。
        </Typography.Paragraph>
        <Checkbox.Group
          style={{ width: '100%' }}
          value={draftPaths}
          onChange={(vals) => setDraftPaths(vals as string[])}
        >
          <Row gutter={[8, 8]}>
            {allowedCatalog.map((item) => (
              <Col span={12} key={item.path}>
                <Checkbox value={item.path}>{item.title}</Checkbox>
              </Col>
            ))}
          </Row>
        </Checkbox.Group>
        {!allowedCatalog.length ? <Empty description="当前角色暂无可选入口" /> : null}
      </Modal>
    </>
  );
};

export default WorkbenchPage;

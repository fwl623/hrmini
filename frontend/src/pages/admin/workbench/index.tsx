/**
 * 工作台：KPI + 可自定义快捷入口 + 访问趋势 + 最近操作
 */
import {
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  CalendarOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  FileTextOutlined,
  PlusOutlined,
  SettingOutlined,
  TeamOutlined,
  UserAddOutlined,
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
  Modal,
  Progress,
  Row,
  Typography,
  message,
} from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { getWorkbenchSummary, type WorkbenchSummary } from '@/services/workbench';
import { getRequestErrorMessage } from '@/utils/requestError';
import './workbench.less';

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
  icon?: React.ReactNode;
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
  { title: '数据分析', path: '/admin/analytics', accessKey: 'canViewAnalytics', icon: <BarChartOutlined /> },
  { title: '花名册', path: '/admin/employee/list', accessKey: 'canViewEmployee', icon: <TeamOutlined /> },
  { title: '手机号变更', path: '/admin/employee/mobile-change', accessKey: 'canViewEmployee', icon: <UserOutlined /> },
  { title: '部门管理', path: '/admin/org/departments', accessKey: 'canViewDept', icon: <ClusterOutlined /> },
  { title: '职位管理', path: '/admin/org/positions', accessKey: 'canViewPosition', icon: <BankOutlined /> },
  { title: '入职管理', path: '/admin/onboarding', accessKey: 'canManageWorkflow', icon: <UserAddOutlined /> },
  { title: '转正管理', path: '/admin/regularization', accessKey: 'canManageWorkflow', icon: <AuditOutlined /> },
  { title: '调岗管理', path: '/admin/transfers', accessKey: 'canManageWorkflow', icon: <FileTextOutlined /> },
  { title: '离职管理', path: '/admin/resignation', accessKey: 'canManageResignation', icon: <FileTextOutlined /> },
  { title: '审批中心', path: '/admin/approval', accessKey: 'canApprove', icon: <AuditOutlined /> },
  { title: '审批委托', path: '/admin/delegation', accessKey: 'canApprove', icon: <SettingOutlined /> },
  { title: '考勤组', path: '/admin/attendance/groups', accessKey: 'canManageAttendance', icon: <ClusterOutlined /> },
  { title: '打卡中心', path: '/admin/attendance/punch', accessKey: 'canManageAttendance', icon: <ClockCircleOutlined /> },
  { title: '考勤统计', path: '/admin/attendance/statistics', accessKey: 'canManageAttendance', icon: <BarChartOutlined /> },
  { title: '月考勤汇总', path: '/admin/attendance/summary', accessKey: 'canManageAttendance', icon: <CalendarOutlined /> },
  { title: '请假列表', path: '/admin/leave/list', accessKey: 'canManageAttendance', icon: <CalendarOutlined /> },
  { title: '加班列表', path: '/admin/overtime/list', accessKey: 'canManageAttendance', icon: <ClockCircleOutlined /> },
  { title: '账套管理', path: '/admin/payroll/schemes', accessKey: 'canViewPayroll', icon: <BankOutlined /> },
  { title: '核算批次', path: '/admin/payroll/batches', accessKey: 'canViewPayroll', icon: <FileTextOutlined /> },
  { title: '用户管理', path: '/admin/system/users', accessKey: 'canManageSystem', icon: <UserOutlined /> },
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

  const maxHeadcount = Math.max(1, ...(summary.departmentStats || []).map((d) => d.headcount || 0));
  const punchPct =
    summary.todayPunchRate != null
      ? Math.min(100, Math.max(0, Math.round(summary.todayPunchRate * 10000) / 100))
      : null;

  const kpiCards = [
    {
      title: '在职总人数',
      value: summary.totalEmployees,
      icon: <TeamOutlined />,
      color: '#165dff',
      bg: 'rgba(22, 93, 255, 0.1)',
      path: access.canViewEmployee ? '/admin/employee/list' : undefined,
      hint: '查看花名册',
    },
    {
      title: '本月入职',
      value: summary.newHiresThisMonth,
      icon: <UserAddOutlined />,
      color: '#00b42a',
      bg: 'rgba(0, 180, 42, 0.1)',
      path: access.canManageWorkflow || access.canHr ? '/admin/onboarding' : undefined,
      hint: '进入入职管理',
    },
    {
      title: '待审批',
      value: summary.pendingApprovals,
      icon: <AuditOutlined />,
      color: '#0fc6c2',
      bg: 'rgba(15, 198, 194, 0.12)',
      path: access.canApprove ? '/admin/approval' : undefined,
      hint: '进入审批中心',
    },
    {
      title: '考勤异常',
      value: summary.attendanceAnomalies,
      icon: <WarningOutlined />,
      color: '#f53f3f',
      bg: 'rgba(245, 63, 63, 0.1)',
      path: access.canManageAttendance ? '/admin/attendance/statistics' : undefined,
      hint: '查看考勤统计',
    },
  ];

  return (
    <div className="wb-page">
      <section className="wb-hero">
        <div>
          <h1>工作台</h1>
          <p>今日概览与常用入口，点击指标可直达对应功能</p>
        </div>
      </section>

      {degraded && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="部分统计暂时不可用，已降级为空数据，不影响其他功能使用"
        />
      )}

      <div className="wb-kpi-grid">
        {kpiCards.map((card) => (
          <div
            key={card.title}
            className={`wb-kpi${card.path ? ' is-clickable' : ''}`}
            onClick={() => {
              if (card.path) history.push(card.path);
            }}
            role={card.path ? 'button' : undefined}
          >
            <div className="wb-kpi__accent" style={{ background: card.color }} />
            <div className="wb-kpi__top">
              <span className="wb-kpi__label">{card.title}</span>
              <span className="wb-kpi__icon" style={{ color: card.color, background: card.bg }}>
                {card.icon}
              </span>
            </div>
            <div className="wb-kpi__value">{loading ? '—' : card.value}</div>
            {card.path ? <div className="wb-kpi__hint">点击{card.hint}</div> : null}
          </div>
        ))}
      </div>

      {punchPct != null && (
        <div className="wb-punch">
          <div className="wb-punch__meta">
            <span className="wb-punch__label">今日打卡率</span>
            <span className="wb-punch__value">{loading ? '—' : `${punchPct}%`}</span>
          </div>
          <div className="wb-punch__bar">
            <Progress
              percent={loading ? 0 : punchPct}
              strokeColor={{ from: '#4080ff', to: '#165dff' }}
              trailColor="#e8f0ff"
              showInfo={false}
              size={['100%', 10]}
            />
          </div>
        </div>
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={10}>
          <Card
            className="wb-section"
            bordered={false}
            title="快捷入口"
            loading={loading}
            extra={
              <Button type="link" size="small" icon={<SettingOutlined />} onClick={openEdit}>
                自定义
              </Button>
            }
          >
            {quickLinks.length ? (
              <div className="wb-shortcut-grid">
                {quickLinks.map((link) => (
                  <button
                    key={link.path}
                    type="button"
                    className="wb-shortcut"
                    onClick={() => history.push(link.path)}
                  >
                    <span className="wb-shortcut__icon">{link.icon || <FileTextOutlined />}</span>
                    <span className="wb-shortcut__title">{link.title}</span>
                  </button>
                ))}
                <button type="button" className="wb-shortcut wb-shortcut--add" onClick={openEdit}>
                  <span className="wb-shortcut__icon">
                    <PlusOutlined />
                  </span>
                  <span className="wb-shortcut__title">添加</span>
                </button>
              </div>
            ) : (
              <Empty description="暂无快捷入口" image={Empty.PRESENTED_IMAGE_SIMPLE}>
                <Button type="primary" icon={<PlusOutlined />} onClick={openEdit}>
                  添加快捷入口
                </Button>
              </Empty>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card className="wb-section" bordered={false} title="近 7 日访问趋势" loading={loading}>
            {trendData.some((d) => d.count > 0) ? (
              <Line
                data={trendData}
                xField="date"
                yField="count"
                height={240}
                point={{ size: 3 }}
                smooth
                color="#165dff"
              />
            ) : (
              <Empty description="暂无访问数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 0 }}>
        <Col xs={24} lg={12}>
          <Card className="wb-section" bordered={false} title="部门人数 Top" loading={loading}>
            {(summary.departmentStats || []).length ? (
              <div>
                {summary.departmentStats!.map((item, idx) => (
                  <div key={`${item.deptName}-${idx}`} className="wb-dept-item">
                    <span className={`wb-dept-rank${idx < 3 ? ' is-top' : ''}`}>{idx + 1}</span>
                    <div className="wb-dept-body">
                      <div className="wb-dept-name">
                        <span>{item.deptName}</span>
                        <span>{item.headcount} 人</span>
                      </div>
                      <Progress
                        percent={Math.round(((item.headcount || 0) / maxHeadcount) * 100)}
                        showInfo={false}
                        size={['100%', 6]}
                        strokeColor="#165dff"
                        trailColor="#eef2f8"
                      />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <Empty description="暂无部门统计" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card className="wb-section" bordered={false} title="最近操作" loading={loading}>
            {(summary.recentOperations || []).length ? (
              <div>
                {summary.recentOperations!.map((item) => (
                  <div key={item.id} className="wb-op-item">
                    <div>
                      <div className="wb-op-title">
                        {item.module} / {item.action}
                      </div>
                      <div className="wb-op-desc">
                        用户 {item.userId}
                        {item.targetId ? ` · 目标 ${item.targetId}` : ''}
                      </div>
                    </div>
                    <div className="wb-op-time">{item.createdAt || ''}</div>
                  </div>
                ))}
              </div>
            ) : (
              <Empty description="暂无操作记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
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
    </div>
  );
};

export default WorkbenchPage;

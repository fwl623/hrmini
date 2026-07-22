import React, { useState, useEffect, useCallback } from 'react';
import {
  Card, Row, Col, Statistic, Typography, message, Space, Tabs, Select, Spin, Empty, Descriptions, Tag,
} from 'antd';
import {
  CalendarOutlined, TeamOutlined, UserOutlined, CheckCircleOutlined,
  ClockCircleOutlined, CloseCircleOutlined, FieldTimeOutlined,
  CoffeeOutlined, RiseOutlined, FallOutlined, WarningOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';

import { getPersonalStatistics, getDepartmentStatistics } from '@/services/attendance';
import { getEmployeeList } from '@/services/employee';
import { getDeptTree } from '@/services/org';
import type { DeptTreeNode } from '@/services/org';

const { TabPane } = Tabs;

/** 月份选择器（最近 12 个月） */
const MonthPicker: React.FC<{ value?: string; onChange?: (v: string) => void }> = ({ value, onChange }) => {
  const options = Array.from({ length: 12 }, (_, i) => {
    const m = dayjs().subtract(i, 'month');
    return { label: m.format('YYYY年MM月'), value: m.format('YYYY-MM') };
  });
  return (
    <Select
      value={value}
      onChange={onChange}
      options={options}
      style={{ width: 160 }}
      placeholder="选择月份"
    />
  );
};

/** 将部门树递归拍平为扁平的选项列表（Select 用） */
const flattenDeptTree = (nodes: DeptTreeNode[]): { label: string; value: number; key: string }[] => {
  const result: { label: string; value: number; key: string }[] = [];
  const walk = (list: DeptTreeNode[]) => {
    for (const n of list) {
      result.push({ label: n.name, value: n.id, key: `dept-${n.id}` });
      if (n.children && n.children.length > 0) walk(n.children);
    }
  };
  walk(nodes);
  return result;
};

/* ───────────────────── 指标配置 ───────────────────── */
interface MetricDef {
  key: string;
  label: string;
  icon: React.ReactNode;
  color: string;
  suffix?: string;
  precision?: number;
}

const personalMetrics: MetricDef[] = [
  { key: 'shouldAttendDays', label: '应出勤', icon: <CalendarOutlined />, color: '#1890ff', suffix: '天' },
  { key: 'actualAttendDays', label: '实际出勤', icon: <CheckCircleOutlined />, color: '#52c41a', suffix: '天' },
  { key: 'lateCount', label: '迟到', icon: <ClockCircleOutlined />, color: '#faad14', suffix: '次' },
  { key: 'earlyLeaveCount', label: '早退', icon: <FieldTimeOutlined />, color: '#faad14', suffix: '次' },
  { key: 'absentDays', label: '旷工', icon: <CloseCircleOutlined />, color: '#ff4d4f', suffix: '天' },
  { key: 'leaveDays', label: '请假', icon: <CoffeeOutlined />, color: '#722ed1', suffix: '天' },
  { key: 'overtimeHours', label: '加班', icon: <RiseOutlined />, color: '#13c2c2', suffix: 'h' },
  { key: 'annualBalance', label: '年假余额', icon: <FallOutlined />, color: '#eb2f96', suffix: '天', precision: 1 },
];

/* ═══════════════════════════════════════════════════
   考勤统计页面
   ═══════════════════════════════════════════════════ */
const AttendanceStatisticsPage: React.FC = () => {
  // ── 当前 Tab ──
  const [tab, setTab] = useState<string>('personal');

  // ── 个人统计 ──
  const [empSearchText, setEmpSearchText] = useState('');
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);
  const [selectedEmployee, setSelectedEmployee] = useState<number | undefined>();
  const [personalPeriod, setPersonalPeriod] = useState(dayjs().format('YYYY-MM'));
  const [personalData, setPersonalData] = useState<any>(null);
  const [personalLoading, setPersonalLoading] = useState(false);
  const [personalError, setPersonalError] = useState(false);

  // ── 部门统计 ──
  const [deptTree, setDeptTree] = useState<DeptTreeNode[]>([]);
  const [selectedDept, setSelectedDept] = useState<number | undefined>();
  const [selectedDeptName, setSelectedDeptName] = useState<string>('');
  const [deptPeriod, setDeptPeriod] = useState(dayjs().format('YYYY-MM'));
  const [deptData, setDeptData] = useState<any>(null);
  const [deptLoading, setDeptLoading] = useState(false);
  const [deptError, setDeptError] = useState(false);

  // ── 初始化加载部门树 ──
  useEffect(() => {
    (async () => {
      try {
        const res = await getDeptTree();
        setDeptTree(res.data ?? []);
      } catch { /* silent */ }
    })();
  }, []);

  // ── 搜索员工 ──
  const searchEmployees = useCallback(async (keyword: string) => {
    setEmpSearchText(keyword);
    if (!keyword || keyword.length < 1) { setEmpOptions([]); return; }
    setEmpLoading(true);
    try {
      const res = await getEmployeeList({ keyword, page: 1, pageSize: 20 });
      const list = res.data?.list ?? [];
      setEmpOptions(list.map((e) => ({
        label: `${e.name} (${e.empNo}) - ${e.department || ''}`,
        value: e.employeeId,
      })));
    } catch { setEmpOptions([]); }
    finally { setEmpLoading(false); }
  }, []);

  // ── 查询个人统计 ──
  const loadPersonal = useCallback(async () => {
    if (!selectedEmployee) return;
    setPersonalLoading(true);
    setPersonalError(false);
    try {
      const res = await getPersonalStatistics({ employeeId: selectedEmployee, period: personalPeriod });
      if (res.data) {
        setPersonalData(res.data);
      } else {
        setPersonalData(null);
        setPersonalError(true);
      }
    } catch {
      setPersonalData(null);
      setPersonalError(true);
    } finally {
      setPersonalLoading(false);
    }
  }, [selectedEmployee, personalPeriod]);

  useEffect(() => { loadPersonal(); }, [loadPersonal]);

  // ── 查询部门统计 ──
  const loadDepartment = useCallback(async () => {
    if (!selectedDept) return;
    setDeptLoading(true);
    setDeptError(false);
    try {
      const res = await getDepartmentStatistics({ departmentId: selectedDept, period: deptPeriod });
      if (res.data) {
        setDeptData(res.data);
      } else {
        setDeptData(null);
        setDeptError(true);
      }
    } catch {
      setDeptData(null);
      setDeptError(true);
    } finally {
      setDeptLoading(false);
    }
  }, [selectedDept, deptPeriod]);

  useEffect(() => { loadDepartment(); }, [loadDepartment]);

  /* ── 渲染指标卡片 ── */
  const renderMetricCards = (metrics: MetricDef[], data: any, loading: boolean) => {
    if (loading) {
      return (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" tip="加载中..." />
        </div>
      );
    }
    if (!data) {
      return <Empty description="请选择查询条件后查看统计" />;
    }
    return (
      <Row gutter={[16, 16]}>
        {metrics.map((m) => {
          const val = data[m.key];
          const displayVal = val != null ? (m.precision != null ? Number(val).toFixed(m.precision) : val) : '-';
          return (
            <Col xs={12} sm={8} md={6} key={m.key}>
              <Card hoverable size="small">
                <Statistic
                  title={
                    <Space size={4}>
                      <span style={{ color: m.color }}>{m.icon}</span>
                      {m.label}
                    </Space>
                  }
                  value={displayVal}
                  suffix={m.suffix}
                  valueStyle={{ color: m.color, fontSize: 24 }}
                />
              </Card>
            </Col>
          );
        })}
      </Row>
    );
  };

  /* ── 渲染比率卡片（部门统计） ── */
  const renderRateCards = (data: any, loading: boolean) => {
    if (loading) {
      return (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" tip="加载中..." />
        </div>
      );
    }
    if (!data) {
      return <Empty description="请选择部门与月份后查看统计" />;
    }

    const rateConfigs = [
      { key: 'attendanceRate', label: '出勤率', color: '#52c41a', icon: <CheckCircleOutlined /> },
      { key: 'lateRate', label: '迟到率', color: '#faad14', icon: <WarningOutlined /> },
      { key: 'leaveRate', label: '请假率', color: '#722ed1', icon: <CoffeeOutlined /> },
    ];

    return (
      <Row gutter={[24, 24]}>
        {rateConfigs.map((cfg) => {
          const val = data[cfg.key];
          const displayVal = val != null ? `${(Number(val) * 100).toFixed(1)}%` : '-';
          return (
            <Col xs={24} sm={8} key={cfg.key}>
              <Card hoverable>
                <Statistic
                  title={
                    <Space size={4}>
                      <span style={{ color: cfg.color }}>{cfg.icon}</span>
                      {cfg.label}
                    </Space>
                  }
                  value={displayVal}
                  valueStyle={{ color: cfg.color, fontSize: 32, fontWeight: 600 }}
                />
              </Card>
            </Col>
          );
        })}
      </Row>
    );
  };

  /* ── 个人 Tab ── */
  const renderPersonalTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {/* 查询条件 */}
      <Card>
        <Space wrap size="middle">
          <Space>
            <UserOutlined />
            <Typography.Text strong>员工</Typography.Text>
            <Select
              showSearch
              placeholder="输入姓名或工号搜索"
              filterOption={false}
              notFoundContent={null}
              loading={empLoading}
              onSearch={searchEmployees}
              onChange={(val) => setSelectedEmployee(val as number)}
              onClear={() => { setSelectedEmployee(undefined); setPersonalData(null); }}
              allowClear
              value={selectedEmployee}
              options={empOptions}
              style={{ width: 260 }}
            />
          </Space>
          <Space>
            <CalendarOutlined />
            <Typography.Text strong>月份</Typography.Text>
            <MonthPicker value={personalPeriod} onChange={setPersonalPeriod} />
          </Space>
        </Space>
      </Card>

      {/* 指标卡片 */}
      <Card>
        {personalData && (
          <Descriptions size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="员工">{personalData.employeeName}</Descriptions.Item>
            <Descriptions.Item label="部门">{personalData.departmentName}</Descriptions.Item>
            <Descriptions.Item label="统计周期">{personalData.period}</Descriptions.Item>
          </Descriptions>
        )}
        {renderMetricCards(personalMetrics, personalData, personalLoading)}
        {personalError && !personalLoading && (
          <Typography.Text type="warning" style={{ display: 'block', marginTop: 12 }}>
            未获取到该员工 {personalPeriod} 的考勤统计数据，请确认该员工当月存在考勤记录。
          </Typography.Text>
        )}
      </Card>
    </Space>
  );

  /* ── 部门 Tab ── */
  const renderDepartmentTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {/* 查询条件 */}
      <Card>
        <Space wrap size="middle">
          <Space>
            <TeamOutlined />
            <Typography.Text strong>部门</Typography.Text>
            <Select
              showSearch
              placeholder="搜索并选择部门"
              allowClear
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
              }
              onChange={(val) => {
                setSelectedDept(val as number);
                // 从拍平的部门列表中查找对应名称
                const flat = flattenDeptTree(deptTree);
                const found = flat.find((d) => d.value === val);
                setSelectedDeptName(found?.label ?? '');
              }}
              onClear={() => { setSelectedDept(undefined); setDeptData(null); setSelectedDeptName(''); }}
              value={selectedDept}
              style={{ width: 260 }}
              options={flattenDeptTree(deptTree)}
            />
          </Space>
          <Space>
            <CalendarOutlined />
            <Typography.Text strong>月份</Typography.Text>
            <MonthPicker value={deptPeriod} onChange={setDeptPeriod} />
          </Space>
        </Space>
      </Card>

      {/* 比率卡片 */}
      <Card>
        {deptData && (
          <Descriptions size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="部门">{selectedDeptName || deptData.departmentName}</Descriptions.Item>
            <Descriptions.Item label="统计周期">{deptData.period}</Descriptions.Item>
          </Descriptions>
        )}
        {renderRateCards(deptData, deptLoading)}
        {deptError && !deptLoading && (
          <Typography.Text type="warning" style={{ display: 'block', marginTop: 12 }}>
            未获取到该部门 {deptPeriod} 的考勤统计数据。
          </Typography.Text>
        )}
      </Card>
    </Space>
  );

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>考勤统计</Typography.Title>

      <Card>
        <Tabs activeKey={tab} onChange={setTab} destroyInactiveTabPane>
          <TabPane tab="个人统计" key="personal">
            {renderPersonalTab()}
          </TabPane>
          <TabPane tab="部门统计" key="department">
            {renderDepartmentTab()}
          </TabPane>
        </Tabs>
      </Card>
    </Space>
  );
};

export default AttendanceStatisticsPage;

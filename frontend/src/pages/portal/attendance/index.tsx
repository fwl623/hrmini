/**
 * 我的考勤（员工门户）
 * PRD §9.2：打卡 + 日历视图（色块标记出勤/请假/迟到/缺卡等）
 */
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Timeline,
  TimePicker,
  Typography,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import {
  AimOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';

import {
  applyPunchFix,
  getAttendanceCalendar,
  getPunchFixQuota,
  getTodayPunchStatus,
  getMonthlyPunchStatus,
  portalPunch,
} from '@/services/attendance';

const punchStatusColorMap: Record<string, string> = {
  NORMAL: 'green',
  LATE: 'orange',
  EARLY_LEAVE: 'orange',
  ABSENT_HALF: 'red',
  ABSENT: 'red',
};

const punchStatusLabelMap: Record<string, string> = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY_LEAVE: '早退',
  ABSENT_HALF: '旷工半天',
  ABSENT: '旷工',
};

/** 日历日状态：底色 + 圆点（对齐截图样式） */
const DAY_STATUS_META: Record<string, { label: string; bg: string; dot: string }> = {
  NORMAL: { label: '出勤', bg: '#f6ffed', dot: '#52c41a' },
  LATE: { label: '迟到', bg: '#fffbe6', dot: '#faad14' },
  EARLY_LEAVE: { label: '早退', bg: '#fff7e6', dot: '#fa8c16' },
  ABSENT: { label: '旷工', bg: '#fff1f0', dot: '#ff4d4f' },
  ABSENT_HALF: { label: '旷半天', bg: '#fff1f0', dot: '#ff7875' },
  MISSING_IN: { label: '缺上班卡', bg: '#fff1f0', dot: '#f5222d' },
  MISSING_OUT: { label: '缺下班卡', bg: '#fff1f0', dot: '#f5222d' },
  LEAVE: { label: '请假', bg: '#f9f0ff', dot: '#722ed1' },
  '--': { label: '休息', bg: '#fafafa', dot: 'transparent' },
};

const LEGEND_ITEMS = [
  { key: 'NORMAL', label: '出勤' },
  { key: 'LATE', label: '迟到' },
  { key: 'EARLY_LEAVE', label: '早退' },
  { key: 'LEAVE', label: '请假' },
  { key: 'MISSING_IN', label: '缺卡' },
  { key: 'ABSENT', label: '旷工' },
] as const;

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日'];

const AttendancePunchPage: React.FC = () => {
  const [todayStatus, setTodayStatus] = useState({
    clockedCount: 0,
    totalCount: 2,
    lateCount: 0,
    earlyLeaveCount: 0,
    absentCount: 0,
  });
  const [records, setRecords] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [lastPunch, setLastPunch] = useState<string | null>(null);
  const [quota, setQuota] = useState({ totalQuota: 2, usedQuota: 0, remainingQuota: 2 });
  const [fixModalOpen, setFixModalOpen] = useState(false);
  const [fixForm] = Form.useForm();
  const [fixSubmitting, setFixSubmitting] = useState(false);

  const [calendarMonth, setCalendarMonth] = useState(() => dayjs());
  const [calendarDays, setCalendarDays] = useState<API.AttendanceCalendarDay[]>([]);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [selectedDay, setSelectedDay] = useState<API.AttendanceCalendarDay | null>(null);

  // 本月打卡统计
  const [monthTotal, setMonthTotal] = useState({ should: 0, actual: 0, late: 0, early: 0, absent: 0 });

  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const dayMap = useMemo(() => {
    const map = new Map<string, API.AttendanceCalendarDay>();
    calendarDays.forEach((d) => map.set(d.date, d));
    return map;
  }, [calendarDays]);

  const calendarCells = useMemo(() => {
    const start = calendarMonth.startOf('month');
    const startWeekday = (start.day() + 6) % 7; // 周一为起点
    const daysInMonth = calendarMonth.daysInMonth();
    const cells: { date: Dayjs | null; key: string }[] = [];
    for (let i = 0; i < startWeekday; i += 1) {
      cells.push({ date: null, key: `pad-${i}` });
    }
    for (let d = 1; d <= daysInMonth; d += 1) {
      const date = calendarMonth.date(d);
      cells.push({ date, key: date.format('YYYY-MM-DD') });
    }
    while (cells.length % 7 !== 0) {
      cells.push({ date: null, key: `tail-${cells.length}` });
    }
    return cells;
  }, [calendarMonth]);

  const loadData = useCallback(async () => {
    try {
      const [statusRes, quotaRes, monthlyRes] = await Promise.all([
        getTodayPunchStatus(), getPunchFixQuota(), getMonthlyPunchStatus()
      ]);
      const data = statusRes.data as any;
      if (data) {
        setTodayStatus(data);
        // 从 API 返回的 records 中加载今日打卡记录
        if (data.records && data.records.length > 0) {
          setRecords(data.records.map((r: any) => ({
            time: r.time,
            type: r.type === 'IN' ? '上班' : '下班',
            status: r.status,
          })));
        } else {
          setRecords([]);
        }
      }
      // 本月统计
      const mData = monthlyRes.data as any;
      if (mData) {
        setMonthTotal({
          should: mData.totalCount || 0,
          actual: mData.clockedCount || 0,
          late: mData.lateCount || 0,
          early: mData.earlyLeaveCount || 0,
          absent: mData.absentCount || 0,
        });
      }
      if (quotaRes.data) setQuota(quotaRes.data);
    } catch {
      // ignore
    }
  }, []);

  const loadCalendar = useCallback(async (month: Dayjs) => {
    setCalendarLoading(true);
    try {
      const res = await getAttendanceCalendar(month.format('YYYY-MM'));
      const days = res.code === 0 && res.data?.days ? res.data.days : [];
      setCalendarDays(days);
      // 自动选中今天
      const todayStr = dayjs().format('YYYY-MM-DD');
      const todayDay = days.find((d: API.AttendanceCalendarDay) => d.date === todayStr);
      if (todayDay) {
        setSelectedDay(todayDay);
      }
    } catch {
      setCalendarDays([]);
    } finally {
      setCalendarLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    loadCalendar(calendarMonth);
  }, [calendarMonth, loadCalendar]);

  const handlePunch = async (type: 'in' | 'out') => {
    setLoading(true);
    try {
      const res = await portalPunch({ type, punchTime: new Date().toISOString() });
      const punchStatus = res.data?.punchStatus || 'NORMAL';
      const timeStr = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
      setLastPunch(`${type === 'in' ? '上班' : '下班'} ${timeStr} — ${punchStatusLabelMap[punchStatus]}`);
      message.success(`${type === 'in' ? '上班' : '下班'}打卡成功`);
      await loadData();
      await loadCalendar(calendarMonth);
    } catch (err: any) {
      message.error(err?.message || '打卡失败');
    } finally {
      setLoading(false);
    }
  };

  const handleFixSubmit = async () => {
    try {
      const values = await fixForm.validateFields();
      setFixSubmitting(true);
      await applyPunchFix({
        punchDate: values.punchDate.format('YYYY-MM-DD'),
        type: values.type,
        punchTime: values.punchTime.format('HH:mm'),
        reason: values.reason,
      });
      message.success('补卡申请已提交，请等待审批');
      setFixModalOpen(false);
      fixForm.resetFields();
      await loadData();
      await loadCalendar(calendarMonth);
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setFixSubmitting(false);
    }
  };

  return (
    <Row gutter={[24, 24]}>
      <Col xs={24} lg={8}>
        <Card>
          <Typography.Title level={2} style={{ textAlign: 'center', marginBottom: 0 }}>
            {now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ display: 'block', textAlign: 'center' }}>
            {now.toLocaleDateString('zh-CN', {
              year: 'numeric',
              month: 'long',
              day: 'numeric',
              weekday: 'long',
            })}
          </Typography.Text>
        </Card>

        <Card style={{ marginTop: 16 }}>
          <Space direction="vertical" style={{ width: '100%' }} size="large">
            <Button
              type="primary"
              size="large"
              block
              icon={<AimOutlined />}
              loading={loading}
              onClick={() => handlePunch('in')}
              style={{ height: 48 }}
            >
              上班打卡
            </Button>
            <Button
              size="large"
              block
              icon={<AimOutlined />}
              loading={loading}
              onClick={() => handlePunch('out')}
              style={{ height: 48 }}
            >
              下班打卡
            </Button>
          </Space>
          {lastPunch && (
            <Typography.Text type="success" style={{ display: 'block', marginTop: 12, textAlign: 'center' }}>
              <CheckCircleOutlined /> 上次打卡：{lastPunch}
            </Typography.Text>
          )}
        </Card>

        <Card style={{ marginTop: 16 }} size="small">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text type="secondary">
              本月补卡剩余次数：{quota.remainingQuota} 次（最多 {quota.totalQuota} 次/月）
            </Typography.Text>
            <Button
              type="link"
              size="small"
              onClick={() => setFixModalOpen(true)}
              disabled={quota.remainingQuota <= 0}
            >
              申请补卡
            </Button>
          </Space>
        </Card>
      </Col>

      <Col xs={24} lg={16}>
        <Card title={`本月打卡 - ${dayjs().format('YYYY年MM月')}`}>
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={6}>
              <Statistic
                title="本月已打卡"
                value={monthTotal.actual}
                suffix={`/ ${monthTotal.should}`}
                valueStyle={{ color: '#1890ff' }}
              />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic
                title="本月迟到"
                value={monthTotal.late}
                valueStyle={{ color: monthTotal.late > 0 ? '#faad14' : undefined }}
                prefix={<ClockCircleOutlined />}
              />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic
                title="本月早退"
                value={monthTotal.early}
                valueStyle={{ color: monthTotal.early > 0 ? '#faad14' : undefined }}
                prefix={<ClockCircleOutlined />}
              />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic
                title="本月缺卡"
                value={monthTotal.absent}
                valueStyle={{ color: monthTotal.absent > 0 ? '#ff4d4f' : undefined }}
                prefix={<CloseCircleOutlined />}
              />
            </Col>
          </Row>
        </Card>

        <Card title="今日打卡记录" style={{ marginTop: 16 }}>
          {records.length > 0 ? (
            <Timeline
              items={records.map((r, i) => ({
                key: i,
                color: punchStatusColorMap[r.status] || 'gray',
                children: (
                  <>
                    <Typography.Text strong>{r.time}</Typography.Text>
                    <Tag color={punchStatusColorMap[r.status] || 'default'} style={{ marginLeft: 8 }}>
                      {r.type} · {punchStatusLabelMap[r.status] || r.status}
                    </Tag>
                  </>
                ),
              }))}
            />
          ) : (
            <Typography.Text type="secondary">暂无打卡记录</Typography.Text>
          )}
        </Card>
      </Col>

      <Col span={24}>
        <Card
          title="我的考勤日历"
          loading={calendarLoading}
          extra={
            <Space>
              <Button
                size="small"
                icon={<LeftOutlined />}
                onClick={() => setCalendarMonth((m) => m.subtract(1, 'month'))}
              />
              <Typography.Text>{calendarMonth.format('YYYY年MM月')}</Typography.Text>
              <Button
                size="small"
                icon={<RightOutlined />}
                onClick={() => setCalendarMonth((m) => m.add(1, 'month'))}
              />
              <Button size="small" onClick={() => setCalendarMonth(dayjs())}>
                本月
              </Button>
            </Space>
          }
        >
          <Space wrap style={{ marginBottom: 12 }}>
            {LEGEND_ITEMS.map((item) => (
              <Space key={item.key} size={4}>
                <span
                  style={{
                    display: 'inline-block',
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: DAY_STATUS_META[item.key].dot,
                  }}
                />
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {item.label}
                </Typography.Text>
              </Space>
            ))}
          </Space>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(7, 1fr)',
              gap: 4,
            }}
          >
            {WEEKDAYS.map((w) => (
              <div
                key={w}
                style={{
                  textAlign: 'center',
                  color: w === '六' || w === '日' ? '#ff4d4f' : '#8c8c8c',
                  fontSize: 13,
                  fontWeight: 500,
                  paddingBottom: 8,
                }}
              >
                {w}
              </div>
            ))}
            {calendarCells.map((cell) => {
              if (!cell.date) return <div key={cell.key} />;
              const key = cell.date.format('YYYY-MM-DD');
              const day = dayMap.get(key);
              const status = day?.dayStatus || '--';
              const isWeekend = cell.date.day() === 0 || cell.date.day() === 6;
              const meta =
                DAY_STATUS_META[status] ||
                (isWeekend
                  ? DAY_STATUS_META['--']
                  : { label: status, bg: '#f5f5f5', dot: '#bfbfbf' });
              const selected = selectedDay?.date === key;
              const isToday = cell.date.isSame(dayjs(), 'day');
              const isPast = cell.date.isBefore(dayjs(), 'day');
              return (
                <div
                  key={cell.key}
                  onClick={() => setSelectedDay(day ?? { date: key, dayStatus: status })}
                  style={{
                    borderRadius: 8,
                    background: selected ? '#e6f7ff' : meta.bg,
                    minHeight: 56,
                    cursor: 'pointer',
                    padding: '6px 4px',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'flex-start',
                    gap: 4,
                    border: selected ? '2px solid #1677ff' : isToday ? '2px solid #91caff' : '1px solid #f0f0f0',
                    transition: 'all 0.2s',
                    opacity: cell.date.isAfter(dayjs(), 'day') ? 0.5 : 1,
                  }}
                  onMouseEnter={(e) => {
                    if (!selected) e.currentTarget.style.borderColor = '#91caff';
                  }}
                  onMouseLeave={(e) => {
                    if (!selected && !isToday) e.currentTarget.style.borderColor = '#f0f0f0';
                  }}
                >
                  <span
                    style={{
                      fontWeight: isToday || selected ? 600 : 400,
                      color: status === '--' && !isWeekend ? '#bfbfbf' : '#262626',
                      fontSize: 14,
                      lineHeight: '20px',
                    }}
                  >
                    {cell.date.date()}
                  </span>
                  {status !== '--' && (
                    <span
                      style={{
                        width: 14,
                        height: 14,
                        borderRadius: '50%',
                        background: meta.dot,
                        flexShrink: 0,
                        boxShadow: `0 0 0 2px ${meta.dot}22`,
                      }}
                    />
                  )}
                </div>
              );
            })}
          </div>

          {selectedDay && (
            <Card size="small" style={{ marginTop: 12, borderLeft: `4px solid ${DAY_STATUS_META[selectedDay.dayStatus]?.dot || '#d9d9d9'}` }}>
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography.Text strong>{selectedDay.date}</Typography.Text>
                  {selectedDay.dayStatus && selectedDay.dayStatus !== '--' && (
                    <Tag color={DAY_STATUS_META[selectedDay.dayStatus]?.dot || 'default'}>
                      {DAY_STATUS_META[selectedDay.dayStatus]?.label || selectedDay.dayStatus}
                    </Tag>
                  )}
                </div>
                {selectedDay.dayStatus && selectedDay.dayStatus !== '--' ? (
                  <div style={{ display: 'flex', gap: 24 }}>
                    <div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>上班打卡</Typography.Text>
                      <div style={{ fontSize: 16, fontWeight: 500, marginTop: 2 }}>
                        {selectedDay.clockInTime || '--:--'}
                      </div>
                    </div>
                    <div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>下班打卡</Typography.Text>
                      <div style={{ fontSize: 16, fontWeight: 500, marginTop: 2 }}>
                        {selectedDay.clockOutTime || '--:--'}
                      </div>
                    </div>
                  </div>
                ) : (
                  <Typography.Text type="secondary" style={{ fontSize: 13 }}>
                    休息日或尚未生成考勤汇总
                  </Typography.Text>
                )}
              </Space>
            </Card>
          )}
        </Card>
      </Col>

      <Modal
        title="申请补卡"
        open={fixModalOpen}
        onOk={handleFixSubmit}
        onCancel={() => {
          setFixModalOpen(false);
          fixForm.resetFields();
        }}
        confirmLoading={fixSubmitting}
      >
        <Form form={fixForm} layout="vertical">
          <Form.Item name="punchDate" label="补卡日期" rules={[{ required: true, message: '请选择日期' }]}>
            <DatePicker style={{ width: '100%' }} disabledDate={(d) => !!d && d.isAfter(dayjs())} />
          </Form.Item>
          <Form.Item name="type" label="补卡类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select
              options={[
                { label: '上班卡', value: 'in' },
                { label: '下班卡', value: 'out' },
              ]}
            />
          </Form.Item>
          <Form.Item name="punchTime" label="补卡时间" rules={[{ required: true, message: '请选择时间' }]}>
            <TimePicker format="HH:mm" style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="reason"
            label="补卡原因"
            rules={[{ required: true, max: 256, message: '请输入原因（≤256字符）' }]}
          >
            <Input.TextArea rows={3} maxLength={256} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Row>
  );
};

export default AttendancePunchPage;

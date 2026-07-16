import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Button, Statistic, Timeline, Tag, Typography, message, Space } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  AimOutlined,
} from '@ant-design/icons';

/** Mock 今日打卡状态 */
const MOCK_TODAY_STATUS = {
  clockedCount: 45,   // 已打卡人数
  totalCount: 50,     // 应打卡总人数
  lateCount: 3,       // 迟到人数
  earlyLeaveCount: 1, // 早退人数
  absentCount: 1,     // 缺卡人数
};

/** Mock 今日打卡记录 */
const MOCK_TODAY_RECORDS = [
  { time: '08:55', type: '上班', status: 'NORMAL' },
  { time: '18:05', type: '下班', status: 'NORMAL' },
];

/* 打卡状态 → 颜色映射（用于 Tag 和 Timeline 圆点） */
const statusColorMap: Record<string, string> = {
  NORMAL: 'green',         // 正常 → 绿色
  LATE: 'orange',          // 迟到 → 橙色
  EARLY_LEAVE: 'orange',   // 早退 → 橙色
  ABSENT_HALF: 'red',      // 旷工半天 → 红色
  ABSENT: 'red',           // 旷工全天 → 红色
};

/* 打卡状态 → 中文标签映射 */
const statusLabelMap: Record<string, string> = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY_LEAVE: '早退',
  ABSENT_HALF: '旷工半天',
  ABSENT: '旷工',
};

const AttendancePunchPage: React.FC = () => {
  const [todayStatus] = useState(MOCK_TODAY_STATUS);
  const [records] = useState(MOCK_TODAY_RECORDS);
  const [loading, setLoading] = useState(false);
  const [lastPunch, setLastPunch] = useState<string | null>(null);

  // 当前时间用于展示
  const [now, setNow] = useState(new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const handlePunch = async (type: 'in' | 'out') => {
    setLoading(true);
    // Mock: 模拟打卡
    await new Promise((resolve) => setTimeout(resolve, 500));
    const mockStatus = type === 'in' ? 'NORMAL' : 'NORMAL';
    const timeStr = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    setLastPunch(`${type === 'in' ? '上班' : '下班'} ${timeStr} — ${statusLabelMap[mockStatus]}`);
    message.success(`${type === 'in' ? '上班' : '下班'}打卡成功`);
    setLoading(false);
  };

  return (
    <Row gutter={[24, 24]}>
      {/* 时间卡片 */}
      <Col xs={24} lg={8}>
        <Card>
          <Typography.Title level={2} style={{ textAlign: 'center', marginBottom: 0 }}>
            {now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ display: 'block', textAlign: 'center' }}>
            {now.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })}
          </Typography.Text>
        </Card>

        {/* 打卡按钮 */}
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

        {/* 补卡入口 */}
        <Card style={{ marginTop: 16 }} size="small">
          <Typography.Text type="secondary">
            本月补卡剩余次数：2 次（最多 2 次/月）
          </Typography.Text>
        </Card>
      </Col>

      {/* 今日状态 */}
      <Col xs={24} lg={16}>
        <Card title="今日打卡状态">
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={6}>
              <Statistic
                title="已打卡"
                value={todayStatus.clockedCount}
                suffix={`/ ${todayStatus.totalCount}`}
                valueStyle={{ color: '#1890ff' }}
              />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic
                title="迟到"
                value={todayStatus.lateCount}
                valueStyle={{ color: todayStatus.lateCount > 0 ? '#faad14' : undefined }}
                prefix={<ClockCircleOutlined />}
              />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic
                title="早退"
                value={todayStatus.earlyLeaveCount}
                valueStyle={{ color: todayStatus.earlyLeaveCount > 0 ? '#faad14' : undefined }}
                prefix={<ClockCircleOutlined />}
              />
            </Col>
            <Col xs={12} sm={6}>
              <Statistic
                title="缺卡"
                value={todayStatus.absentCount}
                valueStyle={{ color: todayStatus.absentCount > 0 ? '#ff4d4f' : undefined }}
                prefix={<CloseCircleOutlined />}
              />
            </Col>
          </Row>
        </Card>

        {/* 今日打卡记录 */}
        <Card title="今日打卡记录" style={{ marginTop: 16 }}>
          <Timeline
            items={records.map((r) => ({
              color: statusColorMap[r.status],
              children: (
                <>
                  <Typography.Text strong>{r.time}</Typography.Text>
                  <Tag color={statusColorMap[r.status]} style={{ marginLeft: 8 }}>
                    {r.type} · {statusLabelMap[r.status]}
                  </Tag>
                </>
              ),
            }))}
          />
        </Card>
      </Col>
    </Row>
  );
};

export default AttendancePunchPage;

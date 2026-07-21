import React, { useRef, useState, useCallback } from 'react';
import {
  Card, Row, Col, Button, Statistic, Tag, Typography, message, Space, Modal,
  Form, DatePicker, TimePicker, Input, Select,
} from 'antd';
import {
  ClockCircleOutlined, CloseCircleOutlined, AimOutlined, PlusOutlined,
} from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import {
  getPunchRecords, punch, applyPunchFix, getPunchFixQuota, getYesterdayOverview,
} from '@/services/attendance';
import { getEmployeeList } from '@/services/employee';

/* 打卡状态 → 颜色映射 */
const statusColorMap: Record<string, string> = {
  NORMAL: 'green', LATE: 'orange', EARLY_LEAVE: 'orange',
  ABSENT_HALF: 'red', ABSENT: 'red',
};

/* 打卡状态 → 中文标签 */
const statusLabelMap: Record<string, string> = {
  NORMAL: '正常', LATE: '迟到', EARLY_LEAVE: '早退',
  ABSENT_HALF: '旷工半天', ABSENT: '旷工',
};

/* 打卡来源 → 中文标签 */
const sourceLabelMap: Record<string, string> = {
  CARD: '打卡机', APP: '手机端', ADMIN: '代打', FIX: '补卡',
};

const PunchAdminPage: React.FC = () => {
  const actionRef = useRef<ActionType>();

  // ── 昨日概览 ──
  const [yesterdayStatus, setYesterdayStatus] = useState({
    clockedCount: 0, totalCount: 0, lateCount: 0, earlyLeaveCount: 0, absentCount: 0,
  });

  // ── 补卡配额 ──
  const [quota, setQuota] = useState({ totalQuota: 0, usedQuota: 0, remainingQuota: 0 });

  // ── 弹窗状态 ──
  const [punchModalOpen, setPunchModalOpen] = useState(false);
  const [fixModalOpen, setFixModalOpen] = useState(false);
  const [punchForm] = Form.useForm();
  const [fixForm] = Form.useForm();
  const [punchSubmitting, setPunchSubmitting] = useState(false);
  const [fixSubmitting, setFixSubmitting] = useState(false);

  // ── 员工搜索 ──
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);

  /** 加载昨日概览 + 配额 */
  const loadOverview = useCallback(async () => {
    try {
      const [yesterdayRes, quotaRes] = await Promise.all([
        getYesterdayOverview(),
        getPunchFixQuota(),
      ]);
      if (yesterdayRes.data) setYesterdayStatus(yesterdayRes.data);
      if (quotaRes.data) setQuota(quotaRes.data);
    } catch { /* silent */ }
  }, []);

  // 仅在首次渲染时加载
  React.useEffect(() => { loadOverview(); }, [loadOverview]);

  /** 搜索员工（自动补全） */
  const searchEmployees = useCallback(async (keyword: string) => {
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

  // ── ProTable 列定义 ──
  const columns: any[] = [
    { title: '员工', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'departmentName', width: 130, ellipsis: true },
    { title: '日期', dataIndex: 'punchDate', width: 100 },
    {
      title: '上班',
      children: [
        { title: '时间', dataIndex: 'clockInTime', width: 80, render: (v: string) => v || '-' },
        {
          title: '状态', dataIndex: 'clockInStatus', width: 80,
          render: (v: string) => v
            ? <Tag color={statusColorMap[v] || 'default'}>{statusLabelMap[v] || v}</Tag>
            : '-',
        },
      ],
    },
    {
      title: '下班',
      children: [
        { title: '时间', dataIndex: 'clockOutTime', width: 80, render: (v: string) => v || '-' },
        {
          title: '状态', dataIndex: 'clockOutStatus', width: 80,
          render: (v: string) => v
            ? <Tag color={statusColorMap[v] || 'default'}>{statusLabelMap[v] || v}</Tag>
            : '-',
        },
      ],
    },
    {
      title: '来源', dataIndex: 'source', width: 80,
      render: (v: string) => sourceLabelMap[v] || v || '-',
    },
  ];

  // ── 代打卡 ──
  const handleAdminPunch = async () => {
    try {
      const values = await punchForm.validateFields();
      setPunchSubmitting(true);
      const empId = values.employeeId;
      const empLabel = empOptions.find((o) => o.value === empId)?.label || `ID:${empId}`;
      await punch({
        type: values.type,
        punchTime: values.punchTime?.toISOString(),
        employeeId: empId,
      });
      message.success(`代打卡成功：${empLabel} ${values.type === 'in' ? '上班' : '下班'}`);
      setPunchModalOpen(false);
      punchForm.resetFields();
      setEmpSearchText('');
      setEmpOptions([]);
      await loadOverview();
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setPunchSubmitting(false);
    }
  };

  // ── 补卡 ──
  const handleFixSubmit = async () => {
    try {
      const values = await fixForm.validateFields();
      setFixSubmitting(true);
      const empId = values.employeeId;
      const empLabel = empOptions.find((o) => o.value === empId)?.label || `ID:${empId}`;
      await applyPunchFix({
        punchDate: values.punchDate.format('YYYY-MM-DD'),
        type: values.type,
        punchTime: values.punchTime.format('HH:mm'),
        reason: values.reason,
        employeeId: empId,
      });
      message.success(`补卡申请已提交：${empLabel}`);
      setFixModalOpen(false);
      fixForm.resetFields();
      setEmpSearchText('');
      setEmpOptions([]);
      await loadOverview();
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setFixSubmitting(false);
    }
  };

  // ── 共享的员工选择表单项 ──
  const renderEmployeeSelect = (formName: string) => (
    <Form.Item
      name="employeeId"
      label="选择员工"
      rules={[{ required: true, message: '请选择员工' }]}
    >
      <Select
        showSearch
        placeholder="输入姓名或工号搜索"
        filterOption={false}
        notFoundContent={null}
        loading={empLoading}
        onSearch={searchEmployees}
        options={empOptions}
        style={{ width: '100%' }}
      />
    </Form.Item>
  );

  return (
    <>
      {/* ═══════════════ 昨日打卡概览 ═══════════════ */}
      <Card style={{ marginBottom: 16 }}>
        <Typography.Title level={5} style={{ marginTop: 0, marginBottom: 16 }}>
          昨日打卡概览（{dayjs().subtract(1, 'day').format('YYYY-MM-DD')}）
        </Typography.Title>
        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8} md={4}>
            <Statistic title="应打卡" value={yesterdayStatus.totalCount} suffix="人" valueStyle={{ color: '#1890ff' }} />
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Statistic title="已打卡" value={yesterdayStatus.clockedCount} suffix="人" valueStyle={{ color: '#52c41a' }} />
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Statistic title="迟到" value={yesterdayStatus.lateCount} suffix="人"
              valueStyle={{ color: yesterdayStatus.lateCount > 0 ? '#faad14' : undefined }} prefix={<ClockCircleOutlined />} />
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Statistic title="早退" value={yesterdayStatus.earlyLeaveCount} suffix="人"
              valueStyle={{ color: yesterdayStatus.earlyLeaveCount > 0 ? '#faad14' : undefined }} prefix={<ClockCircleOutlined />} />
          </Col>
          <Col xs={12} sm={8} md={4}>
            <Statistic title="缺勤" value={yesterdayStatus.absentCount} suffix="人"
              valueStyle={{ color: yesterdayStatus.absentCount > 0 ? '#ff4d4f' : undefined }} prefix={<CloseCircleOutlined />} />
          </Col>
        </Row>
      </Card>

      {/* ═══════════════ 今日打卡记录 ═══════════════ */}
      <Card
        title={`今日打卡记录（${dayjs().format('YYYY-MM-DD')}）`}
        extra={
          <Space>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              剩余补卡次数：{quota.remainingQuota}
            </Typography.Text>
            <Button type="primary" icon={<AimOutlined />} onClick={() => setPunchModalOpen(true)}>
              代打卡
            </Button>
            <Button icon={<PlusOutlined />} onClick={() => setFixModalOpen(true)}>
              补卡
            </Button>
          </Space>
        }
      >
        <ProTable<any>
          rowKey={(r) => `${r.employeeId}-${r.punchDate}`}
          columns={columns}
          request={async (params) => {
            const { current, pageSize } = params;
            const today = dayjs().format('YYYY-MM-DD');
            try {
              const res = await getPunchRecords({
                page: current, size: pageSize,
                dateFrom: today, dateTo: today,
              });
              return {
                data: res.data?.list || [],
                total: res.data?.total || 0,
                success: true,
              };
            } catch {
              return { data: [], total: 0, success: false };
            }
          }}
          pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
          search={false}
          actionRef={actionRef}
          toolBarRender={false}
        />
      </Card>

      {/* ═══════════════ 代打卡弹窗 ═══════════════ */}
      <Modal
        title={<><AimOutlined style={{ marginRight: 8 }} />代打卡</>}
        open={punchModalOpen}
        onOk={handleAdminPunch}
        onCancel={() => { setPunchModalOpen(false); punchForm.resetFields(); setEmpOptions([]); }}
        confirmLoading={punchSubmitting}
        destroyOnClose
      >
        <Form form={punchForm} layout="vertical">
          {renderEmployeeSelect('punch')}
          <Form.Item name="type" label="打卡类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select
              options={[
                { label: '上班打卡', value: 'in' },
                { label: '下班打卡', value: 'out' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="punchTime"
            label="打卡时间"
            initialValue={dayjs()}
            rules={[{ required: true, message: '请选择时间' }]}
          >
            <TimePicker format="HH:mm" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* ═══════════════ 补卡申请弹窗 ═══════════════ */}
      <Modal
        title={<><PlusOutlined style={{ marginRight: 8 }} />补卡申请</>}
        open={fixModalOpen}
        onOk={handleFixSubmit}
        onCancel={() => { setFixModalOpen(false); fixForm.resetFields(); setEmpOptions([]); }}
        confirmLoading={fixSubmitting}
        destroyOnClose
      >
        <Form form={fixForm} layout="vertical">
          {renderEmployeeSelect('fix')}
          <Form.Item name="punchDate" label="补卡日期" rules={[{ required: true, message: '请选择日期' }]}>
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => d && d.isAfter(dayjs())}
            />
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
    </>
  );
};

export default PunchAdminPage;

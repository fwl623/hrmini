/**
 * HR 手机号变更待办列表页
 *
 * 对接：GET /api/v1/employees/mobile-change-applications
 * 功能：查看待办 → 审批通过/驳回（依赖 C 组审批引擎）
 *
 * ✅ UI 先行，联调时对接审批操作
 */
import React, { useEffect, useState } from 'react';
import { Card, Table, Tag, Button, Space, message, Typography, Modal } from 'antd';
import { request } from '@umijs/max';
import dayjs from 'dayjs';

interface MobileChangeApp {
  id: number;
  employeeId: number;
  employeeName: string;
  empNo: string;
  oldMobile: string;
  newMobile: string;
  reason: string;
  status: string;
  createdAt: string;
}

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  PENDING:   { color: 'blue',    label: '待审批' },
  APPROVED:  { color: 'green',   label: '已通过' },
  REJECTED:  { color: 'red',     label: '已驳回' },
  CANCELLED: { color: 'default', label: '已撤销' },
};

const MobileChangePage: React.FC = () => {
  const [data, setData] = useState<MobileChangeApp[]>([]);
  const [loading, setLoading] = useState(false);

  const loadData = () => {
    setLoading(true);
    request('/api/v1/employees/mobile-change-applications')
      .then((res) => { if (res.code === 0) setData(res.data || []); })
      .catch(() => message.error('加载失败'))
      .finally(() => setLoading(false));
  };

  useEffect(loadData, []);

  const handleAction = (record: MobileChangeApp, action: 'APPROVE' | 'REJECT') => {
    // ⚠️ 依赖 C 组审批引擎 — 当前仅 UI 展示，联调时对接 POST /approvals/tasks/{id}/action
    Modal.confirm({
      title: `确认${action === 'APPROVE' ? '通过' : '驳回'}？`,
      content: `${record.employeeName}（${record.empNo}）申请将手机号变更为 ${record.newMobile}`,
      onOk: () => {
        message.success(`模拟${action === 'APPROVE' ? '通过' : '驳回'}成功（联调时对接审批引擎）`);
        loadData();
      },
    });
  };

  const columns = [
    { title: '员工', dataIndex: 'employeeName', width: 100 },
    { title: '工号', dataIndex: 'empNo', width: 120 },
    { title: '原手机号', dataIndex: 'oldMobile', width: 130 },
    { title: '新手机号', dataIndex: 'newMobile', width: 130 },
    { title: '变更原因', dataIndex: 'reason', width: 200, ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (s: string) => {
        const m = STATUS_MAP[s] || { color: 'default', label: s };
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
    {
      title: '申请时间', dataIndex: 'createdAt', width: 170,
      render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', width: 140, render: (_: any, r: MobileChangeApp) => (
        r.status === 'PENDING' ? (
          <Space>
            <Button type="link" size="small" style={{ color: 'green' }}
              onClick={() => handleAction(r, 'APPROVE')}>通过</Button>
            <Button type="link" size="small" danger
              onClick={() => handleAction(r, 'REJECT')}>驳回</Button>
          </Space>
        ) : <Typography.Text type="secondary">—</Typography.Text>
      ),
    },
  ];

  return (
    <Card title="手机号变更待办">
      <Table
        dataSource={data}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 20 }}
        size="small"
      />
    </Card>
  );
};

export default MobileChangePage;

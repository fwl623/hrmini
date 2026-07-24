/**
 * HR 手机号变更待办列表页
 *
 * 对接：GET /api/v1/employees/mobile-change-applications
 *        POST .../{id}/approve（审批通过）
 *        POST .../{id}/reject（审批驳回）
 *
 * 状态机：PENDING → APPROVED（更新 mobile + 同步 auth）| REJECTED
 * ⚠️ 审批依赖 C 组审批引擎，当前为本地逻辑 + DB 操作
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

  const displayEmployee = (r: MobileChangeApp) =>
    r.employeeName || r.empNo
      ? `${r.employeeName || '-'}${r.empNo ? `（${r.empNo}）` : ''}`
      : `员工#${r.employeeId}`;

  const handleApprove = (record: MobileChangeApp) => {
    Modal.confirm({
      title: '确认通过？',
      content: `${displayEmployee(record)}申请将手机号变更为 ${record.newMobile}`,
      onOk: async () => {
        const res = await request(`/api/v1/employees/mobile-change-applications/${record.id}/approve`, { method: 'POST' });
        if (res.code === 0) {
          message.success('已通过，手机号已更新并同步 auth');
          loadData();
        } else {
          message.error(res.message || '操作失败');
        }
      },
    });
  };

  const handleReject = (record: MobileChangeApp) => {
    Modal.confirm({
      title: '确认驳回？',
      content: `${displayEmployee(record)}的变更申请将被驳回`,
      onOk: async () => {
        const res = await request(`/api/v1/employees/mobile-change-applications/${record.id}/reject`, { method: 'POST' });
        if (res.code === 0) {
          message.success('已驳回');
          loadData();
        } else {
          message.error(res.message || '操作失败');
        }
      },
    });
  };

  const columns = [
    {
      title: '员工',
      dataIndex: 'employeeName',
      width: 100,
      render: (_: string, r: MobileChangeApp) => r.employeeName || '-',
    },
    {
      title: '工号',
      dataIndex: 'empNo',
      width: 120,
      render: (_: string, r: MobileChangeApp) => r.empNo || '-',
    },
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
              onClick={() => handleApprove(r)}>通过</Button>
            <Button type="link" size="small" danger
              onClick={() => handleReject(r)}>驳回</Button>
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

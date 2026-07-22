/**
 * 加班管理（管理端）
 *
 * 功能：ProTable + 搜索（员工姓名下拉）
 *       + 加班台账查看
 *       + 管理员可查看所有员工的加班记录
 *
 * 与门户端共享 statusLabelMap / statusColorMap
 */
import React, { useRef, useState } from 'react';
import {
  Card,
  Button,
  Tag,
  message,
  Select,
  Space,
  Typography,
  Modal,
  Table,
  Input,
} from 'antd';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { OrderedListOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';

import { getOvertimeApplications, getOvertimeLedger } from '@/services/attendance';
import { getEmployeeList } from '@/services/employee';

// ========== 共享常量 ==========

/** 状态 → 中文标签映射（与门户端一致） */
const statusLabelMap: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
};

/** 状态 → Tag 颜色映射 */
const statusColorMap: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
};

// ========== 页面组件 ==========

const AdminOvertimePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [searchEmpId, setSearchEmpId] = useState<number | undefined>();
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);

  // 加班台账
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [ledgerPeriod, setLedgerPeriod] = useState(dayjs().format('YYYY-MM'));
  const [ledgerData, setLedgerData] = useState<any[]>([]);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [ledgerTotal, setLedgerTotal] = useState(0);

  const rateTypeLabel: Record<number, string> = { 15: '1.5倍(工作日)', 20: '2.0倍(休息日)', 30: '3.0倍(节假日)' };

  // ---------- 搜索员工 ----------

  const searchEmployees = async (keyword: string) => {
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
  };

  // ---------- 加班台账 ----------

  const loadLedger = async (page = 1, pageSize = 20) => {
    setLedgerLoading(true);
    try {
      const res = await getOvertimeLedger({ period: ledgerPeriod, page, pageSize });
      setLedgerData((res.data as any)?.list || []);
      setLedgerTotal((res.data as any)?.total || 0);
    } catch (err: any) {
      message.error(err?.message || '加载加班台账失败');
    } finally {
      setLedgerLoading(false);
    }
  };

  const ledgerColumns = [
    { title: '员工ID', dataIndex: 'employeeId', width: 80 },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'departmentName', width: 120 },
    { title: '加班日期', dataIndex: 'ledgerDate', width: 110 },
    { title: '加班时长(h)', dataIndex: 'totalHours', width: 100 },
    { title: '倍率', dataIndex: 'rateType', width: 120, render: (v: number) => rateTypeLabel[v] || v },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  ];

  // ---------- 表格列定义 ----------

  const columns: any[] = [
    { title: '员工姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'department', width: 120 },
    { title: '加班日期', dataIndex: 'overtimeDate', width: 120 },
    { title: '开始时间', dataIndex: 'startTime', width: 100 },
    { title: '结束时间', dataIndex: 'endTime', width: 100 },
    { title: '时长(h)', dataIndex: 'hours', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_: unknown, record: { status?: string }) => {
        const v = record.status || '';
        return <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag>;
      },
    },
  ];

  // ---------- 渲染 ----------

  return (
    <Card
      title="加班管理"
      extra={
        <Button
          icon={<OrderedListOutlined />}
          onClick={() => {
            setLedgerPeriod(dayjs().format('YYYY-MM'));
            setLedgerOpen(true);
            loadLedger();
          }}
        >
          加班台账
        </Button>
      }
    >
      <ProTable<any>
        rowKey="id"
        columns={columns}
        actionRef={actionRef}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getOvertimeApplications({
              page: current,
              employeeId: searchEmpId ?? 0,
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
        toolBarRender={() => [
          <Select
            key="empSearch"
            showSearch
            placeholder="搜索员工姓名"
            allowClear
            filterOption={false}
            notFoundContent={null}
            loading={empLoading}
            onSearch={searchEmployees}
            onChange={(val) => {
              setSearchEmpId(val as number | undefined);
              actionRef.current?.reload();
            }}
            onClear={() => {
              setSearchEmpId(undefined);
              actionRef.current?.reload();
            }}
            value={searchEmpId}
            options={empOptions}
            style={{ width: 240 }}
          />,
        ]}
      />

      {/* 加班台账 Modal */}
      <Modal
        title={`加班台账 - ${ledgerPeriod}`}
        open={ledgerOpen}
        onCancel={() => setLedgerOpen(false)}
        footer={null}
        width={900}
      >
        <Space style={{ marginBottom: 16 }}>
          <Input
            placeholder="账期 YYYY-MM"
            value={ledgerPeriod}
            onChange={(e) => setLedgerPeriod(e.target.value)}
            style={{ width: 140 }}
          />
          <Button type="primary" onClick={() => loadLedger()}>查询</Button>
        </Space>
        <Table
          rowKey="id"
          columns={ledgerColumns}
          dataSource={ledgerData}
          loading={ledgerLoading}
          pagination={{
            total: ledgerTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) => loadLedger(page, pageSize),
          }}
          size="small"
        />
      </Modal>
    </Card>
  );
};

export default AdminOvertimePage;

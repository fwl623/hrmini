/**
 * 请假管理（管理端）
 *
 * 功能：ProTable + 搜索（员工姓名下拉、请假类型）
 *       + 管理员可查看所有员工的请假记录并执行撤销操作
 *
 * 与门户端共享 LEAVE_TYPE_OPTIONS / statusLabelMap / statusColorMap / calcLeaveDays 逻辑
 */
import React, { useRef, useState } from 'react';
import {
  Card,
  Tag,
  Select,
  Space,
  Typography,
} from 'antd';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';

import { getLeaveApplications } from '@/services/attendance';
import { getEmployeeList } from '@/services/employee';
import { LEAVE_TYPE_OPTIONS, leaveTypeLabel } from '@/constants/leave';

// ========== 共享常量 ==========

/** 状态 → 中文标签映射 */
const statusLabelMap: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
};

/** 状态 → Tag 颜色映射 */
const statusColorMap: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
};

// ========== 页面组件 ==========

const AdminLeavePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [searchEmpId, setSearchEmpId] = useState<number | undefined>();
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);

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

  // ---------- 表格列定义 ----------

  const columns: any[] = [
    { title: '员工姓名', dataIndex: 'employeeName', width: 100, hideInSearch: true },
    { title: '部门', dataIndex: 'department', width: 120, hideInSearch: true },
    {
      title: '请假类型',
      dataIndex: 'leaveType',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(LEAVE_TYPE_OPTIONS.map((o) => [o.value, { text: o.label }])),
      render: (_: unknown, record: { leaveType?: string }) => leaveTypeLabel(record.leaveType),
    },
    { title: '开始时间', dataIndex: 'startTime', width: 160, hideInSearch: true },
    { title: '结束时间', dataIndex: 'endTime', width: 160, hideInSearch: true },
    { title: '天数', dataIndex: 'leaveDays', width: 60, hideInSearch: true },
    { title: '原因', dataIndex: 'reason', ellipsis: true, hideInSearch: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      hideInSearch: true,
      render: (_: unknown, record: { status?: string }) => {
        const v = record.status || '';
        return <Tag color={statusColorMap[v]}>{statusLabelMap[v] || v}</Tag>;
      },
    },
  ];

  // ---------- 渲染 ----------

  return (
    <Card title="请假管理">
      <ProTable<any>
        rowKey="id"
        columns={columns}
        actionRef={actionRef}
        request={async (params) => {
          const { current, pageSize, leaveType, ...rest } = params;
          try {
            const res = await getLeaveApplications({
              page: current,
              leaveType,
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
        search={{
          labelWidth: 'auto',
          defaultCollapsed: false,
          optionRender: (searchConfig, formProps, dom) => [...dom.reverse()],
        }}
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
    </Card>
  );
};

export default AdminLeavePage;

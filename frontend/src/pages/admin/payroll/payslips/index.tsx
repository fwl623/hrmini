import React, { useEffect, useRef, useState } from 'react';
import { Card, Tag, TreeSelect, message } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import { getPayslips } from '@/services/payroll';
import { getDeptTree, type DeptTreeNode } from '@/services/org';

type TreeOption = { title: string; value: number; children?: TreeOption[] };

function toDeptTreeOptions(nodes: DeptTreeNode[]): TreeOption[] {
  return (nodes || []).map((n) => ({
    title: n.name,
    value: n.id,
    children: n.children?.length ? toDeptTreeOptions(n.children) : undefined,
  }));
}

const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  DISTRIBUTED: '已发放',
  RELEASED: '已发放',
  PENDING_CONFIRM: '待确认',
};
const statusColor: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  DISTRIBUTED: 'green',
  RELEASED: 'green',
  PENDING_CONFIRM: 'warning',
};

const PayslipPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [deptTreeOptions, setDeptTreeOptions] = useState<TreeOption[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const res = await getDeptTree();
        setDeptTreeOptions(toDeptTreeOptions(res.data ?? []));
      } catch {
        message.warning('部门筛选项加载失败，可稍后刷新重试');
      }
    })();
  }, []);

  const columns: ProColumns<API.PayslipVO>[] = [
    {
      title: '账期',
      dataIndex: 'period',
      valueType: 'dateMonth',
      hideInTable: true,
      fieldProps: { allowClear: true, placeholder: '选择账期' },
      search: {
        transform: (value) => ({
          period: value ? dayjs(value).format('YYYY-MM') : undefined,
        }),
      },
    },
    {
      title: '部门',
      dataIndex: 'departmentId',
      hideInTable: true,
      renderFormItem: () => (
        <TreeSelect
          treeData={deptTreeOptions}
          placeholder="选择部门（含下属）"
          allowClear
          showSearch
          treeDefaultExpandAll
          treeNodeFilterProp="title"
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: '应发区间',
      dataIndex: 'grossRange',
      valueType: 'digitRange',
      hideInTable: true,
      fieldProps: { placeholder: ['最低应发', '最高应发'], min: 0, precision: 2 },
      search: {
        transform: (value) => ({
          minGross: value?.[0],
          maxGross: value?.[1],
        }),
      },
    },
    {
      title: '实发区间',
      dataIndex: 'netRange',
      valueType: 'digitRange',
      hideInTable: true,
      fieldProps: { placeholder: ['最低实发', '最高实发'], min: 0, precision: 2 },
      search: {
        transform: (value) => ({
          minNet: value?.[0],
          maxNet: value?.[1],
        }),
      },
    },
    { title: '员工ID', dataIndex: 'employeeId', width: 80, search: false },
    { title: '姓名', dataIndex: 'employeeName', width: 100, search: false },
    {
      title: '部门',
      dataIndex: 'departmentName',
      width: 140,
      search: false,
      ellipsis: true,
      render: (_, r) => r.departmentName || '-',
    },
    { title: '账期', dataIndex: 'period', width: 100, search: false },
    {
      title: '应发',
      dataIndex: 'grossSalary',
      search: false,
      render: (_, r) => `¥${(r.grossSalary || 0).toFixed(2)}`,
    },
    {
      title: '实发',
      dataIndex: 'netSalary',
      search: false,
      render: (_, r) => `¥${(r.netSalary || 0).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      width: 100,
      render: (_, r) => (
        <Tag color={statusColor[r.status] || 'default'}>
          {statusLabel[r.status] || r.status}
        </Tag>
      ),
    },
  ];

  return (
    <Card title="工资条管理">
      <ProTable<API.PayslipVO>
        rowKey={(r) => `${r.employeeId}-${r.period}`}
        columns={columns}
        actionRef={actionRef as any}
        toolBarRender={false}
        options={false}
        request={async (params) => {
          try {
            const res = await getPayslips({
              page: params.current,
              pageSize: params.pageSize,
              period: params.period as string | undefined,
              departmentId: params.departmentId as number | undefined,
              minGross: params.minGross as number | undefined,
              maxGross: params.maxGross as number | undefined,
              minNet: params.minNet as number | undefined,
              maxNet: params.maxNet as number | undefined,
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
      />
    </Card>
  );
};

export default PayslipPage;

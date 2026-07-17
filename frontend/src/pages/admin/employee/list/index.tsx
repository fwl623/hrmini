/**
 * 员工花名册列表页
 *
 * 功能：ProTable + SearchBar + StatusTag（蓝/绿/黄/灰）
 * 数据来源：Mock（联调后切换至后端 API）
 */
import React from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Button, Tag, Space } from 'antd';
import { useNavigate } from '@umijs/max';
import { getEmployeeList } from '@/services/employee';
import type { EmployeeItem } from '@/services/employee';

const STATUS_ENUM: Record<string, { text: string; status: string }> = {
  probation:     { text: '试用期', status: 'Processing' },
  regular:       { text: '正式',   status: 'Success' },
  pending_resign:{ text: '待离职', status: 'Warning' },
  resigned:      { text: '已离职', status: 'Default' },
};

const EmployeeListPage: React.FC = () => {
  const navigate = useNavigate();

  const columns: ProColumns<EmployeeItem>[] = [
    {
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
      valueType: 'textarea',
    },
    {
      title: '工号',
      dataIndex: 'empNo',
      width: 120,
      copyable: true,
      search: false,
    },
    {
      title: '姓名',
      dataIndex: 'name',
      width: 100,
      search: false,
      render: (_: any, record: EmployeeItem) => (
        <a onClick={() => navigate(`/admin/employee/${record.employeeId}`)}>
          {record.name}
        </a>
      ),
    },
    {
      // TODO: org接口未完成 — 部门列数据依赖后端 JOIN department 表返回，后续若需部门树下拉筛选需接入 GET /api/v1/departments/tree
      title: '部门',
      dataIndex: 'department',
      width: 120,
      search: false,
    },
    {
      title: '职位',
      dataIndex: 'position',
      width: 150,
      search: false,
    },
    {
      title: '职级',
      dataIndex: 'grade',
      width: 80,
      search: false,
    },
    {
      title: '在职状态',
      dataIndex: 'employmentStatus',
      width: 100,
      valueType: 'select',
      valueEnum: STATUS_ENUM,
      render: (status) => {
        const item = STATUS_ENUM[status as string];
        return <Tag>{item?.text || status}</Tag>;
      },
    },
    {
      title: '入职日期',
      dataIndex: 'hireDateRange',
      width: 120,
      valueType: 'dateRange',
      hideInTable: true,
      search: { transform: (value) => {
        if (value && Array.isArray(value) && value.length === 2) {
          return { hireDateFrom: value[0], hireDateTo: value[1] };
        }
        return {};
      }},
    },
    {
      title: '入职日期',
      dataIndex: 'hireDate',
      width: 120,
      copyable: true,
      search: false,
    },
    {
      title: '操作',
      width: 180,
      hideInSearch: true,
      render: (_: any, record: EmployeeItem) => (
        <Space>
          <Button type="link" size="small"
            onClick={() => navigate(`/admin/employee/${record.employeeId}`)}>
            详情
          </Button>
          <Button type="link" size="small"
            onClick={() => navigate(`/admin/employee/${record.employeeId}/edit`)}>
            编辑
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <ProTable<EmployeeItem>
      headerTitle="员工花名册"
      rowKey="employeeId"
      search={{
        labelWidth: 'auto',
        defaultCollapsed: false,
      }}
      request={async (params) => {
        const { current, pageSize, ...formValues } = params;
        const keyword = formValues?.keyword as string || '';
        const employmentStatus = formValues?.employmentStatus as string || '';
        const hireDateFrom = (formValues as any)?.hireDateFrom as string || '';
        const hireDateTo = (formValues as any)?.hireDateTo as string || '';
        const res = await getEmployeeList({
          page: current,
          pageSize,
          keyword,
          employmentStatus,
          hireDateFrom,
          hireDateTo,
        });
        return {
          data: res.data?.list || [],
          success: res.code === 0,
          total: res.data?.total || 0,
        };
      }}
      locale={{ emptyText: '暂无匹配的员工信息' }}
      columns={columns}
      pagination={{
        showSizeChanger: true,
        pageSizeOptions: ['10', '20', '50', '100'],
        defaultPageSize: 20,
      }}
    />
  );
};

export default EmployeeListPage;

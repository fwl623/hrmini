/**
 * 员工花名册列表页
 *
 * 功能：ProTable + SearchBar + StatusTag（蓝/绿/黄/灰）
 * 数据来源：Mock（联调后切换至后端 API）
 */
import React, { useRef, useState } from 'react';
import { ProTable, ActionType } from '@ant-design/pro-components';
import { Button, Tag, Space, Input, DatePicker, Select } from 'antd';
import { useNavigate } from '@umijs/max';
import { getEmployeeList } from '@/services/employee';
import type { EmployeeItem } from '@/services/employee';

const { RangePicker } = DatePicker;

/** 在职状态 → Tag 颜色映射（蓝/绿/黄/灰） */
const STATUS_COLOR: Record<string, string> = {
  probation: 'blue',
  regular: 'green',
  pending_resign: 'orange',
  resigned: 'default',
};

const STATUS_LABEL: Record<string, string> = {
  probation: '试用期',
  regular: '正式',
  pending_resign: '待离职',
  resigned: '已离职',
};

const EmployeeListPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState<string>('');

  const columns = [
    {
      title: '工号',
      dataIndex: 'empNo',
      width: 120,
      copyable: true,
    },
    {
      title: '姓名',
      dataIndex: 'name',
      width: 100,
      render: (_: any, record: EmployeeItem) => (
        <a onClick={() => navigate(`/admin/employee/${record.employeeId}`)}>
          {record.name}
        </a>
      ),
    },
    {
      title: '部门',
      dataIndex: 'department',
      width: 120,
    },
    {
      title: '职位',
      dataIndex: 'position',
      width: 150,
    },
    {
      title: '职级',
      dataIndex: 'grade',
      width: 80,
    },
    {
      title: '在职状态',
      dataIndex: 'employmentStatus',
      width: 100,
      render: (status: string) => (
        <Tag color={STATUS_COLOR[status] || 'default'}>
          {STATUS_LABEL[status] || status}
        </Tag>
      ),
    },
    {
      title: '入职日期',
      dataIndex: 'hireDate',
      width: 120,
      valueType: 'date',
    },
    {
      title: '操作',
      width: 180,
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
      actionRef={actionRef}
      rowKey="employeeId"
      search={{
        labelWidth: 'auto',
        defaultCollapsed: false,
        optionRender: (searchConfig, formProps, dom) => [...dom.reverse()],
      }}
      toolBarRender={() => [
        <Input.Search
          key="search"
          placeholder="姓名 / 工号 / 手机号"
          allowClear
          onSearch={(value) => {
            setKeyword(value);
            actionRef.current?.reload();
          }}
          style={{ width: 260 }}
        />,
      ]}
      request={async (params, sort, filter) => {
        const res = await getEmployeeList({
          ...params,
          keyword,
        });
        return {
          data: res.data?.list || [],
          success: res.code === 0,
          total: res.data?.total || 0,
        };
      }}
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

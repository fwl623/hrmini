import React, { useRef, useState } from 'react';
import {
  Card,
  Input,
  DatePicker,
  Space,
  Tag,
  Button,
  Form,
} from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';

import { getPunchRecords } from '@/services/attendance';

/** 打卡状态 -> 标签颜色映射 */
const STATUS_COLOR_MAP: Record<string, string> = {
  NORMAL: 'green',
  LATE: 'orange',
  EARLY_LEAVE: 'orange',
  ABSENT_HALF: 'red',
  ABSENT: 'red',
  MISSING_IN: 'purple',
  MISSING_OUT: 'blue',
};

/** 打卡状态 -> 中文名称映射 */
const STATUS_LABEL_MAP: Record<string, string> = {
  NORMAL: '正常',
  LATE: '迟到',
  EARLY_LEAVE: '早退',
  ABSENT_HALF: '半日缺勤',
  ABSENT: '缺勤',
  MISSING_IN: '上班缺卡',
  MISSING_OUT: '下班缺卡',
};

const PunchRecordPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [searchForm] = Form.useForm();
  const [searchParams, setSearchParams] = useState<{
    keyword?: string;
    dateFrom?: string;
    dateTo?: string;
  }>({});

  // ---------- 搜索 ----------
  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    const params: any = { keyword: values.keyword || undefined };
    if (values.dateRange && values.dateRange[0]) {
      params.dateFrom = values.dateRange[0].format('YYYY-MM-DD');
    }
    if (values.dateRange && values.dateRange[1]) {
      params.dateTo = values.dateRange[1].format('YYYY-MM-DD');
    }
    setSearchParams(params);
    actionRef.current?.reload();
  };

  const handleReset = () => {
    searchForm.resetFields();
    setSearchParams({});
    actionRef.current?.reload();
  };

  // ---------- 表格列定义 ----------
  const columns: ProColumns<API.PunchRecordVO>[] = [
    { title: '员工姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'departmentName', width: 120 },
    { title: '打卡日期', dataIndex: 'punchDate', width: 120 },
    {
      title: '上班打卡',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          <span>{record.clockInTime || '-'}</span>
          {record.clockInStatus && (
            <Tag color={STATUS_COLOR_MAP[record.clockInStatus] || 'default'}>
              {STATUS_LABEL_MAP[record.clockInStatus] || record.clockInStatus}
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: '下班打卡',
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          <span>{record.clockOutTime || '-'}</span>
          {record.clockOutStatus && (
            <Tag color={STATUS_COLOR_MAP[record.clockOutStatus] || 'default'}>
              {STATUS_LABEL_MAP[record.clockOutStatus] || record.clockOutStatus}
            </Tag>
          )}
        </Space>
      ),
    },
    { title: '打卡方式', dataIndex: 'source', width: 100 },
  ];

  // ---------- 渲染 ----------
  return (
    <Card title="打卡记录">
      {/* 搜索栏 */}
      <Form
        form={searchForm}
        layout="inline"
        style={{ marginBottom: 16 }}
        initialValues={{ keyword: undefined, dateRange: undefined }}
      >
        <Form.Item name="keyword">
          <Input placeholder="员工姓名 / 工号" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="dateRange">
          <DatePicker.RangePicker
            placeholder={['开始日期', '结束日期']}
            style={{ width: 240 }}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      {/* 表格 */}
      <ProTable<API.PunchRecordVO>
        rowKey={(record) => `${record.employeeId}-${record.punchDate}`}
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          try {
            const res = await getPunchRecords({
              page: current,
              size: pageSize,
              ...searchParams,
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
        actionRef={actionRef as any}
        toolBarRender={false}
      />
    </Card>
  );
};

export default PunchRecordPage;

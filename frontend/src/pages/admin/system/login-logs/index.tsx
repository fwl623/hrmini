/**
 * 登录日志分页
 */
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { Tag, message } from 'antd';
import React from 'react';
import { listLoginLogs, type LoginLogItem } from '@/services/system';
import { getRequestErrorMessage } from '@/utils/requestError';

const LoginLogsPage: React.FC = () => {
  const columns: ProColumns<LoginLogItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '用户 ID', dataIndex: 'userId', width: 100, search: false },
    { title: '登录时间', dataIndex: 'loginTime', width: 180, search: false, valueType: 'dateTime' },
    { title: 'IP', dataIndex: 'loginIp', width: 140, search: false },
    { title: '设备', dataIndex: 'device', width: 120, search: false },
    { title: '地点', dataIndex: 'location', search: false, ellipsis: true },
    {
      title: '结果',
      dataIndex: 'success',
      width: 90,
      search: false,
      render: (_, row) =>
        row.success === 1 ? <Tag color="success">成功</Tag> : <Tag color="error">失败</Tag>,
    },
    { title: '失败原因', dataIndex: 'failReason', search: false, ellipsis: true },
  ];

  return (
    <ProTable<LoginLogItem>
      headerTitle="登录日志"
      rowKey="id"
      columns={columns}
      search={false}
      request={async (params) => {
        try {
          const res = await listLoginLogs({
            page: params.current,
            pageSize: params.pageSize,
          });
          if (res.code !== 0) {
            message.error(res.message || '加载失败');
            return { data: [], success: false, total: 0 };
          }
          return {
            data: res.data?.list || [],
            success: true,
            total: res.data?.total || 0,
          };
        } catch (e) {
          message.error(getRequestErrorMessage(e));
          return { data: [], success: false, total: 0 };
        }
      }}
      pagination={{ defaultPageSize: 20, showSizeChanger: true }}
    />
  );
};

export default LoginLogsPage;

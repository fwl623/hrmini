/**
 * 操作审计日志分页
 */
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { message } from 'antd';
import React from 'react';
import { listOperationLogs, type OperationLogItem } from '@/services/system';
import { getRequestErrorMessage } from '@/utils/requestError';

const OperationLogsPage: React.FC = () => {
  const columns: ProColumns<OperationLogItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '用户 ID', dataIndex: 'userId', width: 100, search: false },
    { title: '模块', dataIndex: 'module', width: 120, search: false },
    { title: '动作', dataIndex: 'action', width: 120, search: false },
    { title: '目标', dataIndex: 'targetId', search: false, ellipsis: true },
    { title: 'IP', dataIndex: 'requestIp', width: 140, search: false },
    { title: '时间', dataIndex: 'createdAt', width: 180, search: false, valueType: 'dateTime' },
  ];

  return (
    <ProTable<OperationLogItem>
      headerTitle="操作审计日志"
      rowKey="id"
      columns={columns}
      search={false}
      request={async (params) => {
        try {
          const res = await listOperationLogs({
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

export default OperationLogsPage;

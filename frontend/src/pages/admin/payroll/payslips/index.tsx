import React, { useRef } from 'react';
import { Card, Tag } from 'antd';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';

import { getPayslips } from '@/services/payroll';

const PayslipPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const columns = [
    { title: '员工ID', dataIndex: 'employeeId', width: 80 },
    { title: '姓名', dataIndex: 'employeeName' },
    { title: '账期', dataIndex: 'period', width: 100 },
    { title: '应发', dataIndex: 'grossSalary', render: (v: number) => `¥${(v || 0).toFixed(2)}` },
    { title: '实发', dataIndex: 'netSalary', render: (v: number) => `¥${(v || 0).toFixed(2)}` },
    { title: '状态', dataIndex: 'status', render: (v: string) =>
      <Tag color={v === 'distributed' ? 'green' : 'default'}>{v === 'distributed' ? '已发放' : v}</Tag>
    },
  ];

  return (
    <Card title="工资条管理">
      <ProTable rowKey="employeeId" columns={columns}
        request={async (params) => { try {
          const res = await getPayslips({ page: params.current, pageSize: params.pageSize });
          return { data: (res.data as any)?.list || [], total: (res.data as any)?.total || 0, success: true };
        } catch { return { data: [], total: 0, success: false }; }}}
        search={false} actionRef={actionRef as any} toolBarRender={false} />
    </Card>
  );
};
export default PayslipPage;

import React, { useRef, useState } from 'react';
import { Card, Button, Tag, message, Switch, Modal, Input, Space } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import { getMonthlySummary, updateMonthlySummaryLock, generateMonthlySummary, exportMonthlySummary } from '@/services/attendance';

const MonthlySummaryPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [period, setPeriod] = useState(dayjs().format('YYYY-MM'));
  const [locked, setLocked] = useState(false);
  const [lockModalOpen, setLockModalOpen] = useState(false);

  const columns: any[] = [
    { title: '员工ID', dataIndex: 'employeeId', width: 80 },
    { title: '员工姓名', dataIndex: 'employeeName', width: 100 },
    { title: '应出勤', dataIndex: 'shouldAttendDays', width: 80 },
    { title: '实际出勤', dataIndex: 'actualAttendDays', width: 80 },
    { title: '迟到', dataIndex: 'lateCount', width: 60 },
    { title: '早退', dataIndex: 'earlyLeaveCount', width: 60 },
    { title: '旷工', dataIndex: 'absentDays', width: 60 },
    { title: '请假', dataIndex: 'leaveDays', width: 60 },
    { title: '加班(h)', dataIndex: 'overtimeHours', width: 80 },
  ];

  const handleLockToggle = async (checked: boolean) => {
    try {
      await updateMonthlySummaryLock({ period, locked: checked });
      message.success(checked ? '已锁定' : '已解锁');
      setLocked(checked);
      actionRef.current?.reload();
    } catch (err: any) { message.error(err?.message || '操作失败'); }
  };

  const handleGenerate = async () => {
    try {
      await generateMonthlySummary(period);
      message.success('汇总生成成功');
      actionRef.current?.reload();
    } catch (err: any) { message.error(err?.message || '生成失败'); }
  };

  const handleExport = async () => {
    try {
      await exportMonthlySummary(period);
      message.success('导出成功');
    } catch (err: any) { message.error(err?.message || '导出失败'); }
  };

  return (
    <Card title={`月考勤汇总 - ${period}`}
      extra={
        <Space>
          <Button onClick={handleGenerate}>生成汇总</Button>
          <Button icon={<DownloadOutlined />} onClick={handleExport}>导出 Excel</Button>
          <Button type={locked ? 'default' : 'primary'} danger={!locked} onClick={() => setLockModalOpen(true)}>
            {locked ? '已锁定' : '锁定月汇总'}
          </Button>
        </Space>
      }>
      <ProTable<any>
        rowKey="employeeId"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          try {
            const res = await getMonthlySummary({ period, page: current, pageSize });
            if (res.data) setLocked(res.data.locked);
            return { data: res.data?.list || [], total: res.data?.total || 0, success: true };
          } catch { return { data: [], total: 0, success: false }; }
        }}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        search={false}
        actionRef={actionRef as any}
        toolBarRender={false}
      />

      <Modal title="确认操作" open={lockModalOpen} onOk={() => { handleLockToggle(true); setLockModalOpen(false); }}
        onCancel={() => setLockModalOpen(false)}>
        <p>确认锁定 {period} 的考勤数据？锁定后该月数据将不可修改。</p>
        <p>如需解锁请联系相关人员进行操作。</p>
      </Modal>
    </Card>
  );
};

export default MonthlySummaryPage;

import React, { useRef, useState } from 'react';
import { Card, Button, Tag, message, Modal, Input, Select, Space, Progress, Table } from 'antd';
import {
  PlusOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  SendOutlined,
  EyeOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import {
  getBatches,
  createBatch,
  startCalculate,
  submitBatchApprove,
  approveBatch,
  distributeBatch,
  getBatchDetails,
  exportBatchDetails,
} from '@/services/payroll';

const statusColor: Record<string, string> = {
  DRAFT: 'default',
  CALCULATING: 'processing',
  PENDING_CONFIRM: 'warning',
  APPROVING: 'processing',
  APPROVED: 'success',
  DISTRIBUTED: 'success',
  REJECTED: 'error',
};
const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  CALCULATING: '计算中',
  PENDING_CONFIRM: '待确认',
  APPROVING: '审批中',
  APPROVED: '已通过',
  DISTRIBUTED: '已发放',
  REJECTED: '已驳回',
};
const nextProgress: Record<string, number> = {
  DRAFT: 0,
  CALCULATING: 60,
  PENDING_CONFIRM: 100,
  APPROVING: 100,
  APPROVED: 100,
  DISTRIBUTED: 100,
};

const anomalyFlagLabel: Record<string, string> = {
  LEAVE_HIGH: '请假过多',
  OVERTIME_HIGH: '加班过多',
  SALARY_CHANGE_HIGH: '变动大',
  NO_PROFILE: '无档案',
};

const anomalyFlagColor: Record<string, string> = {
  LEAVE_HIGH: 'orange',
  OVERTIME_HIGH: 'orange',
  SALARY_CHANGE_HIGH: 'red',
  NO_PROFILE: 'red',
};

const BatchPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [createOpen, setCreateOpen] = useState(false);
  const [year, setYear] = useState<number>(dayjs().year());
  const [month, setMonth] = useState<number>(dayjs().month() + 1);

  // Detail modal state
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailBatchId, setDetailBatchId] = useState<number | null>(null);
  const [detailData, setDetailData] = useState<API.PayrollDetailVO[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailTotal, setDetailTotal] = useState(0);
  const [detailPage, setDetailPage] = useState(1);
  const [detailPageSize, setDetailPageSize] = useState(20);

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '账期', dataIndex: 'period', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      render: (v: string) => <Tag color={statusColor[v]}>{statusLabel[v]}</Tag>,
    },
    { title: '总人数', dataIndex: 'totalCount', width: 80 },
    { title: '成功', dataIndex: 'successCount', width: 80 },
    {
      title: '异常',
      dataIndex: 'anomalyCount',
      width: 80,
      render: (v: number) => (v > 0 ? <Tag color="red">{v}</Tag> : v),
    },
    {
      title: '进度',
      dataIndex: 'progress',
      width: 150,
      render: (_: any, r: any) => (
        <Progress percent={nextProgress[r.status] || 0} size="small" />
      ),
    },
    {
      title: '操作',
      width: 280,
      render: (_: any, r: any) => (
        <Space>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(r.id)}
          >
            查看详情
          </Button>
          {r.status === 'DRAFT' && (
            <Button
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => handleCalculate(r.id)}
            >
              计算
            </Button>
          )}
          {r.status === 'CALCULATING' && (
            <Button
              size="small"
              icon={<PlayCircleOutlined />}
              onClick={() => handleCalculate(r.id)}
            >
              重试计算
            </Button>
          )}
          {r.status === 'PENDING_CONFIRM' && (
            <Button
              size="small"
              icon={<SendOutlined />}
              onClick={() => handleSubmit(r.id)}
            >
              提交
            </Button>
          )}
          {r.status === 'APPROVING' && (
            <Button
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleApprove(r.id)}
            >
              审批通过
            </Button>
          )}
          {r.status === 'APPROVED' && (
            <Button
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleDistribute(r.id)}
            >
              发放
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const handleCreate = async () => {
    const period = `${year}-${String(month).padStart(2, '0')}`;
    try {
      await createBatch({ period });
      message.success('创建成功');
      setCreateOpen(false);
      setYear(dayjs().year());
      setMonth(dayjs().month() + 1);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message);
    }
  };
  const handleCalculate = async (id: number) => {
    try {
      const res = await startCalculate(id);
      // 兜底：若 umi 未将业务错误抛进 catch（补丁未生效时），本地仍要提示
      if (res && typeof res.code === 'number' && res.code !== 0) {
        const msg = res.message || '请求失败';
        if (
          res.code === 50004 ||
          msg.includes('考勤数据未锁定') ||
          msg.includes('考勤月结')
        ) {
          Modal.warning({
            title: '核算提示',
            content: '当前月考勤数据未锁定，请先完成考勤月结',
            okText: '知道了',
          });
        } else {
          message.error(msg);
        }
        return;
      }
      if (res && res.code === 0) {
        const mode = (res.data as { mode?: string; message?: string } | undefined)?.mode;
        const tip =
          mode === 'async'
            ? '计算任务已提交，请稍后刷新'
            : mode === 'sync-fallback'
              ? '异步不可用或已卡住，已降级同步完成'
              : '计算完成';
        message.success(tip);
        actionRef.current?.reload();
      }
    } catch {
      // 错误已由全局 errorHandler 处理（含考勤未锁定弹窗）
    }
  };
  const handleSubmit = async (id: number) => {
    try {
      await submitBatchApprove(id);
      message.success('已提交审批');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message);
    }
  };
  const handleApprove = async (id: number) => {
    try {
      await approveBatch(id);
      message.success('已审批通过');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message);
    }
  };
  const handleDistribute = async (id: number) => {
    try {
      await distributeBatch(id);
      message.success('已发放');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message);
    }
  };

  // View detail with anomaly highlighting
  const handleViewDetail = async (id: number) => {
    setDetailBatchId(id);
    setDetailOpen(true);
    setDetailPage(1);
    await fetchDetailData(id, 1, detailPageSize);
  };

  const handleExportDetail = async () => {
    if (!detailBatchId) return;
    try {
      await exportBatchDetails(detailBatchId);
      message.success('导出成功');
    } catch (err: any) { message.error(err?.message || '导出失败'); }
  };

  const fetchDetailData = async (id: number, page: number, pageSize: number) => {
    setDetailLoading(true);
    try {
      const res = await getBatchDetails(id, { page, pageSize });
      const data = res.data as any;
      setDetailData(data?.list || []);
      setDetailTotal(data?.total || 0);
    } catch (err: any) {
      message.error(err?.message || '获取批次明细失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleDetailTableChange = (pag: any) => {
    const next = pag.current || 1;
    const size = pag.pageSize || 20;
    setDetailPage(next);
    setDetailPageSize(size);
    if (detailBatchId) {
      fetchDetailData(detailBatchId, next, size);
    }
  };

  // Detail modal columns
  const detailColumns = [
    { title: '员工ID', dataIndex: 'employeeId', width: 80 },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    {
      title: '应发金额',
      dataIndex: 'grossSalary',
      width: 120,
      render: (v: number) => v?.toFixed(2),
    },
    {
      title: '实发金额',
      dataIndex: 'netSalary',
      width: 120,
      render: (v: number) => v?.toFixed(2),
    },
    {
      title: '核算状态',
      dataIndex: 'calcStatus',
      width: 100,
      render: (v: string) => (
        <Tag color={v === 'SUCCESS' ? 'green' : v === 'FAILED' ? 'red' : 'default'}>
          {v === 'SUCCESS' ? '成功' : v === 'FAILED' ? '失败' : v || '待处理'}
        </Tag>
      ),
    },
    {
      title: '异常标记',
      dataIndex: 'anomalyFlags',
      width: 200,
      render: (flags: string[]) => {
        if (!flags || flags.length === 0) return '-';
        return (
          <Space size={4} wrap>
            {flags.map((flag: string) => (
              <Tag
                key={flag}
                color={anomalyFlagColor[flag] || 'red'}
              >
                {anomalyFlagLabel[flag] || flag}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '手动调整',
      dataIndex: 'manualAdjusted',
      width: 90,
      render: (v: boolean) =>
        v ? <Tag color="blue">已调整</Tag> : '-',
    },
  ];

  return (
    <Card
      title="核算批次管理"
      extra={
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateOpen(true)}
        >
          新建批次
        </Button>
      }
    >
      <ProTable
        rowKey="id"
        columns={columns}
        request={async (params) => {
          try {
            const res = await getBatches({
              page: params.current,
              pageSize: params.pageSize,
            });
            return {
              data: (res.data as any)?.list || [],
              total: (res.data as any)?.total || 0,
              success: true,
            };
          } catch {
            return { data: [], total: 0, success: false };
          }
        }}
        search={false}
        actionRef={actionRef as any}
        toolBarRender={false}
      />
      <Modal
        title="新建批次"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => { setCreateOpen(false); setYear(dayjs().year()); setMonth(dayjs().month() + 1); }}
      >
        <Space>
          <Select value={year} onChange={(y) => { setYear(y); if (y === dayjs().year() && month > dayjs().month() + 1) setMonth(dayjs().month() + 1); }} style={{ width: 100 }}
            options={Array.from({ length: 5 }, (_, i) => {
              const y = dayjs().year() - i;
              return { label: `${y}年`, value: y };
            })}
          />
          <Select value={month} onChange={setMonth} style={{ width: 100 }}
            options={Array.from({ length: 12 }, (_, i) => {
              const m = i + 1;
              const isFuture = year === dayjs().year() && m > dayjs().month() + 1;
              return { label: `${m}月`, value: m, disabled: isFuture };
            })}
          />
          <span style={{ color: '#999' }}>→ {year}-{String(month).padStart(2, '0')}</span>
        </Space>
      </Modal>

      {/* Detail modal with anomaly highlighting */}
      <Modal
        title={<Space>批次明细 - #{detailBatchId}<Button size="small" icon={<DownloadOutlined />} onClick={handleExportDetail}>导出 Excel</Button></Space>}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={1000}
      >
        <Table
          rowKey="employeeId"
          columns={detailColumns}
          dataSource={detailData}
          loading={detailLoading}
          pagination={{
            current: detailPage,
            pageSize: detailPageSize,
            total: detailTotal,
            showSizeChanger: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }}
          onChange={handleDetailTableChange}
          rowClassName={(record: API.PayrollDetailVO) =>
            record.anomalyFlags && record.anomalyFlags.length > 0
              ? 'ant-table-row-anomaly'
              : ''
          }
          size="small"
        />
        <style>{`
          .ant-table-row-anomaly {
            background-color: #fff2f0 !important;
          }
          .ant-table-row-anomaly:hover > td {
            background-color: #ffd8d2 !important;
          }
        `}</style>
      </Modal>
    </Card>
  );
};
export default BatchPage;

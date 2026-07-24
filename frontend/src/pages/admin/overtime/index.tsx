/**
 * 加班管理（管理端）
 *
 * 功能：员工 / 状态筛选 + 加班记录列表 + 加班台账
 * UI 与请假列表保持一致
 */
import React, { useRef, useState } from 'react';
import {
  Button,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Timeline,
  Typography,
  message,
} from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  EyeOutlined,
  OrderedListOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';

import { getOvertimeApplications, getOvertimeLedger } from '@/services/attendance';
import { getEmployeeList } from '@/services/employee';
import { fetchInstanceDetail, type ApprovalTimelineItem } from '@/services/workflow';
import {
  localizeTimelineText,
  nodeStateLabel,
  taskStatusLabel,
} from '@/constants/workflow';
import '../leave/leave.less';

type OvertimeRow = API.OvertimeApplicationVO;

type ProgressNode = {
  order: number;
  label: string;
  state: string;
  assigneeName?: string;
  actualAssigneeName?: string;
  taskStatus?: string;
};

type OvertimeFilters = {
  employeeId?: number;
  status?: string;
};

const EMPTY_FILTERS: OvertimeFilters = {
  employeeId: undefined,
  status: undefined,
};

const STATUS_OPTIONS = [
  { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
];

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'orange' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
  CANCELLED: { label: '已撤销', color: 'default' },
};

const RATE_TYPE_LABEL: Record<number, string> = {
  15: '1.5倍(工作日)',
  20: '2.0倍(休息日)',
  30: '3.0倍(节假日)',
};

function statusMeta(code?: string) {
  const key = String(code || '').trim().toUpperCase();
  return STATUS_META[key] || { label: code || '-', color: 'default' };
}

function formatTime(value?: string) {
  if (!value) return '-';
  if (/^\d{1,2}:\d{2}/.test(value)) return value.slice(0, 5);
  const d = dayjs(value);
  return d.isValid() ? d.format('HH:mm') : value;
}

function formatDate(value?: string) {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD') : value;
}

function avatarText(name?: string) {
  const n = (name || '').trim();
  return n ? n.slice(-1) : '?';
}

function nodeAssigneeText(n: ProgressNode) {
  if (n.actualAssigneeName) {
    return `原审批人 ${n.assigneeName || '-'}，已转交 ${n.actualAssigneeName}`;
  }
  if (n.assigneeName) return `审批人：${n.assigneeName}`;
  return '审批人：待指定';
}

const AdminOvertimePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [draft, setDraft] = useState<OvertimeFilters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<OvertimeFilters>(EMPTY_FILTERS);
  const appliedRef = useRef(applied);
  appliedRef.current = applied;

  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);

  // 加班台账
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [ledgerPeriod, setLedgerPeriod] = useState(dayjs().format('YYYY-MM'));
  const [ledgerData, setLedgerData] = useState<any[]>([]);
  const [ledgerLoading, setLedgerLoading] = useState(false);
  const [ledgerTotal, setLedgerTotal] = useState(0);

  // 审批进度
  const [progressOpen, setProgressOpen] = useState(false);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressTitle, setProgressTitle] = useState('');
  const [progressNodes, setProgressNodes] = useState<ProgressNode[]>([]);
  const [progressTimeline, setProgressTimeline] = useState<ApprovalTimelineItem[]>([]);
  const [progressStatus, setProgressStatus] = useState('');
  const [progressCurrent, setProgressCurrent] = useState('');
  const [progressAssignee, setProgressAssignee] = useState('');

  const searchEmployees = async (keyword: string) => {
    if (!keyword?.trim()) {
      setEmpOptions([]);
      return;
    }
    setEmpLoading(true);
    try {
      const res = await getEmployeeList({ keyword, page: 1, pageSize: 20 });
      const list = res.data?.list ?? [];
      setEmpOptions(
        list.map((e) => ({
          label: `${e.name}（${e.empNo}）${e.department ? ` · ${e.department}` : ''}`,
          value: e.employeeId,
        })),
      );
    } catch {
      setEmpOptions([]);
    } finally {
      setEmpLoading(false);
    }
  };

  const reloadWith = (next: OvertimeFilters) => {
    setApplied(next);
    appliedRef.current = next;
    actionRef.current?.reloadAndRest?.() ?? actionRef.current?.reload();
  };

  const handleSearch = () => reloadWith({ ...draft });

  const handleReset = () => {
    setDraft(EMPTY_FILTERS);
    setEmpOptions([]);
    reloadWith(EMPTY_FILTERS);
  };

  const loadLedger = async (page = 1, pageSize = 20) => {
    setLedgerLoading(true);
    try {
      const res = await getOvertimeLedger({ period: ledgerPeriod, page, pageSize });
      setLedgerData((res.data as any)?.list || []);
      setLedgerTotal((res.data as any)?.total || 0);
    } catch (err: any) {
      message.error(err?.message || '加载加班台账失败');
    } finally {
      setLedgerLoading(false);
    }
  };

  const handleViewProgress = async (record: OvertimeRow) => {
    if (!record.instanceId) {
      message.warning('该申请暂无审批实例');
      return;
    }
    setProgressOpen(true);
    setProgressLoading(true);
    setProgressTitle(`${formatDate(record.overtimeDate)} · ${record.hours ?? '-'} 小时`);
    setProgressStatus(record.status);
    setProgressAssignee('');
    setProgressCurrent('');
    try {
      const detail = await fetchInstanceDetail(record.instanceId);
      const nodes = (detail?.nodes ?? []) as ProgressNode[];
      setProgressNodes(nodes);
      setProgressTimeline(detail?.timeline ?? []);
      setProgressCurrent(detail?.currentNodeLabel || '');
      if (detail?.status) setProgressStatus(detail.status);
      const current = nodes.find((n) => n.state === 'current');
      setProgressAssignee(
        current?.actualAssigneeName || current?.assigneeName || '',
      );
    } catch (err: any) {
      message.error(err?.message || '加载审批进度失败');
      setProgressNodes([]);
      setProgressTimeline([]);
    } finally {
      setProgressLoading(false);
    }
  };

  const currentStepIndex = Math.max(
    0,
    progressNodes.findIndex((n) => n.state === 'current'),
  );

  const ledgerColumns = [
    { title: '员工ID', dataIndex: 'employeeId', width: 80 },
    { title: '姓名', dataIndex: 'employeeName', width: 100 },
    { title: '部门', dataIndex: 'departmentName', width: 120 },
    { title: '加班日期', dataIndex: 'ledgerDate', width: 110 },
    { title: '加班时长(h)', dataIndex: 'totalHours', width: 100 },
    {
      title: '倍率',
      dataIndex: 'rateType',
      width: 140,
      render: (v: number) => RATE_TYPE_LABEL[v] || v,
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  ];

  const columns: ProColumns<OvertimeRow>[] = [
    {
      title: '员工',
      dataIndex: 'employeeName',
      width: 140,
      render: (_, record) => (
        <div className="leave-emp">
          <span className="leave-emp__avatar">{avatarText(record.employeeName)}</span>
          <span className="leave-emp__name">{record.employeeName || '-'}</span>
        </div>
      ),
    },
    {
      title: '部门',
      dataIndex: 'department',
      width: 120,
      ellipsis: true,
      render: (_, record) => record.department || '—',
    },
    {
      title: '加班时段',
      dataIndex: 'overtimeDate',
      width: 168,
      render: (_, record) => (
        <div className="leave-range">
          <span className="leave-range__date">{formatDate(record.overtimeDate)}</span>
          <span className="leave-range__time">
            {formatTime(record.startTime)} — {formatTime(record.endTime)}
          </span>
        </div>
      ),
    },
    {
      title: '时长',
      dataIndex: 'hours',
      width: 80,
      align: 'right',
      render: (_, record) => (
        <span className="leave-days">
          {record.hours ?? '-'}
          <span className="leave-days__unit">小时</span>
        </span>
      ),
    },
    {
      title: '原因',
      dataIndex: 'reason',
      ellipsis: true,
      render: (_, record) => (
        <Typography.Text type={record.reason ? undefined : 'secondary'} ellipsis={{ tooltip: record.reason }}>
          {record.reason || '未填写'}
        </Typography.Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_, record) => {
        const meta = statusMeta(record.status);
        return (
          <Tag className="leave-status-tag" color={meta.color}>
            {meta.label}
          </Tag>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleViewProgress(record)}
        >
          进度
        </Button>
      ),
    },
  ];

  return (
    <div className="leave-page">
      <header className="leave-hero">
        <div>
          <h1>加班列表</h1>
          <p>查看全员加班申请，按员工、状态筛选；可查看审批进度与月度台账。</p>
        </div>
        <Button
          icon={<OrderedListOutlined />}
          onClick={() => {
            setLedgerPeriod(dayjs().format('YYYY-MM'));
            setLedgerOpen(true);
            loadLedger();
          }}
        >
          加班台账
        </Button>
      </header>

      <div className="leave-filters">
        <span className="leave-filters__label">员工</span>
        <Select
          showSearch
          allowClear
          placeholder="输入姓名搜索"
          filterOption={false}
          notFoundContent={null}
          loading={empLoading}
          onSearch={searchEmployees}
          onChange={(val) => setDraft((prev) => ({ ...prev, employeeId: val as number | undefined }))}
          value={draft.employeeId}
          options={empOptions}
          style={{ width: 240 }}
        />
        <span className="leave-filters__label">状态</span>
        <Select
          allowClear
          placeholder="全部状态"
          value={draft.status}
          onChange={(val) => setDraft((prev) => ({ ...prev, status: val }))}
          options={STATUS_OPTIONS}
          style={{ width: 140 }}
        />
        <div className="leave-filters__actions">
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            查询
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleReset}>
            重置
          </Button>
        </div>
      </div>

      <div className="leave-table-card">
        <ProTable<OvertimeRow>
          rowKey="id"
          columns={columns}
          actionRef={actionRef}
          search={false}
          options={{ density: true, reload: true, setting: true }}
          cardProps={{ bodyStyle: { padding: 0 } }}
          headerTitle="加班记录"
          request={async (params) => {
            const { current, pageSize } = params;
            const filters = appliedRef.current;
            try {
              const res = await getOvertimeApplications({
                page: current,
                pageSize,
                status: filters.status,
                employeeId: filters.employeeId ?? 0,
              });
              return {
                data: (res.data?.list || []) as OvertimeRow[],
                total: res.data?.total || 0,
                success: true,
              };
            } catch {
              return { data: [], total: 0, success: false };
            }
          }}
          pagination={{
            showSizeChanger: true,
            defaultPageSize: 20,
            showTotal: (total) => `共 ${total} 条`,
            style: { padding: '16px 20px' },
          }}
          scroll={{ x: 980 }}
        />
      </div>

      <Modal
        title={`加班台账 · ${ledgerPeriod}`}
        open={ledgerOpen}
        onCancel={() => setLedgerOpen(false)}
        footer={null}
        width={900}
      >
        <Space style={{ marginBottom: 16 }}>
          <Input
            placeholder="账期 YYYY-MM"
            value={ledgerPeriod}
            onChange={(e) => setLedgerPeriod(e.target.value)}
            style={{ width: 140 }}
          />
          <Button type="primary" onClick={() => loadLedger()}>
            查询
          </Button>
        </Space>
        <Table
          rowKey="id"
          columns={ledgerColumns}
          dataSource={ledgerData}
          loading={ledgerLoading}
          pagination={{
            total: ledgerTotal,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, pageSize) => loadLedger(page, pageSize),
          }}
          size="small"
        />
      </Modal>

      <Drawer
        title={`审批进度 · ${progressTitle}`}
        open={progressOpen}
        onClose={() => setProgressOpen(false)}
        width={480}
        destroyOnClose
      >
        {progressLoading ? (
          <Typography.Text type="secondary">加载中…</Typography.Text>
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              <div style={{ marginBottom: 8 }}>
                状态：
                <Tag className="leave-status-tag" color={statusMeta(progressStatus).color}>
                  {statusMeta(progressStatus).label}
                </Tag>
              </div>
              {progressCurrent ? (
                <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
                  当前节点：{progressCurrent}
                </Typography.Paragraph>
              ) : null}
              {progressAssignee ? (
                <Typography.Paragraph style={{ marginBottom: 0 }}>
                  待审批人：<Typography.Text strong>{progressAssignee}</Typography.Text>
                </Typography.Paragraph>
              ) : null}
            </div>
            {progressNodes.length > 0 ? (
              <Steps
                direction="vertical"
                size="small"
                current={currentStepIndex >= 0 ? currentStepIndex : progressNodes.length}
                items={progressNodes.map((n) => {
                  const st = n.taskStatus
                    ? `${nodeStateLabel(n.state)} · ${taskStatusLabel(n.taskStatus)}`
                    : nodeStateLabel(n.state);
                  return {
                    title: n.label,
                    description: (
                      <Space direction="vertical" size={0}>
                        <Typography.Text type="secondary">{nodeAssigneeText(n)}</Typography.Text>
                        <Typography.Text type="secondary">{st}</Typography.Text>
                      </Space>
                    ),
                    status:
                      n.state === 'done'
                        ? 'finish'
                        : n.state === 'current'
                          ? 'process'
                          : n.state === 'cancelled'
                            ? 'error'
                            : 'wait',
                  };
                })}
              />
            ) : (
              <Typography.Text type="secondary">暂无审批节点信息</Typography.Text>
            )}
            {progressTimeline.length > 0 ? (
              <Timeline
                items={progressTimeline.map((t, i) => ({
                  key: i,
                  children: (
                    <>
                      <Typography.Text>
                        {localizeTimelineText(t.displayText)
                          || `${t.assignee || '-'} · ${localizeTimelineText(t.action || t.node) || '-'}`}
                      </Typography.Text>
                      {t.comment ? (
                        <div>
                          <Typography.Text type="secondary">
                            {localizeTimelineText(t.comment)}
                          </Typography.Text>
                        </div>
                      ) : null}
                      {t.time ? (
                        <div>
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {t.time}
                          </Typography.Text>
                        </div>
                      ) : null}
                    </>
                  ),
                }))}
              />
            ) : (
              <Typography.Text type="secondary">暂无审批记录</Typography.Text>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default AdminOvertimePage;

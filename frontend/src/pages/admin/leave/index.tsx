/**
 * 请假管理（管理端）
 *
 * 功能：员工 / 类型 / 状态筛选 + 请假记录列表；待审批可撤销
 */
import React, { useRef, useState } from 'react';
import {
  Button,
  Popconfirm,
  Select,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';

import { cancelLeave, getLeaveApplications } from '@/services/attendance';
import { getEmployeeList } from '@/services/employee';
import { LEAVE_TYPE_OPTIONS, leaveTypeLabel } from '@/constants/leave';
import './leave.less';

type LeaveRow = API.LeaveApplicationVO;

type LeaveFilters = {
  employeeId?: number;
  leaveType?: string;
  status?: string;
};

const EMPTY_FILTERS: LeaveFilters = {
  employeeId: undefined,
  leaveType: undefined,
  status: undefined,
};

const STATUS_OPTIONS = [
  { label: '待审批', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已撤销', value: 'CANCELLED' },
];

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'orange' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
  CANCELLED: { label: '已撤销', color: 'default' },
};

const TYPE_COLOR: Record<string, string> = {
  ANNUAL: 'blue',
  SICK: 'magenta',
  PERSONAL: 'gold',
  MARRIAGE: 'purple',
  MATERNITY: 'pink',
  BEREAVEMENT: 'default',
  COMP_OFF: 'cyan',
  COMPENSATORY: 'cyan',
};

function formatDateTime(value?: string) {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : value;
}

function avatarText(name?: string) {
  const n = (name || '').trim();
  return n ? n.slice(-1) : '?';
}

const AdminLeavePage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  /** 筛选条草稿（点查询后才生效） */
  const [draft, setDraft] = useState<LeaveFilters>(EMPTY_FILTERS);
  /** 已应用筛选，供表格 request 使用 */
  const [applied, setApplied] = useState<LeaveFilters>(EMPTY_FILTERS);
  const appliedRef = useRef(applied);
  appliedRef.current = applied;

  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);

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

  const reloadWith = (next: LeaveFilters) => {
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

  const handleCancel = async (id: number) => {
    try {
      await cancelLeave(id);
      message.success('已撤销');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '撤销失败');
    }
  };

  const columns: ProColumns<LeaveRow>[] = [
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
      title: '请假类型',
      dataIndex: 'leaveType',
      width: 100,
      render: (_, record) => {
        const code = String(record.leaveType || '').toUpperCase();
        return (
          <Tag className="leave-type-tag" color={TYPE_COLOR[code] || 'processing'}>
            {leaveTypeLabel(record.leaveType)}
          </Tag>
        );
      },
    },
    {
      title: '请假时段',
      dataIndex: 'startTime',
      width: 168,
      render: (_, record) => (
        <div className="leave-range">
          <span className="leave-range__date">{formatDateTime(record.startTime)}</span>
          <span className="leave-range__time">至 {formatDateTime(record.endTime)}</span>
        </div>
      ),
    },
    {
      title: '天数',
      dataIndex: 'leaveDays',
      width: 72,
      align: 'right',
      render: (_, record) => (
        <span className="leave-days">
          {record.leaveDays ?? '-'}
          <span className="leave-days__unit">天</span>
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
        const meta = STATUS_META[record.status || ''] || {
          label: record.status || '-',
          color: 'default',
        };
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
      width: 88,
      fixed: 'right',
      render: (_, record) =>
        record.status === 'PENDING' ? (
          <Popconfirm
            title="确认撤销该请假申请？"
            okText="撤销"
            cancelText="取消"
            onConfirm={() => handleCancel(record.id)}
          >
            <Button type="link" size="small" danger>
              撤销
            </Button>
          </Popconfirm>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
  ];

  return (
    <div className="leave-page">
      <header className="leave-hero">
        <div>
          <h1>请假列表</h1>
          <p>查看全员请假申请，按员工、类型、状态筛选；待审批记录可撤销。</p>
        </div>
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
        <span className="leave-filters__label">类型</span>
        <Select
          allowClear
          placeholder="全部类型"
          value={draft.leaveType}
          onChange={(val) => setDraft((prev) => ({ ...prev, leaveType: val }))}
          options={LEAVE_TYPE_OPTIONS}
          style={{ width: 140 }}
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
        <ProTable<LeaveRow>
          rowKey="id"
          columns={columns}
          actionRef={actionRef}
          search={false}
          options={{ density: true, reload: true, setting: true }}
          cardProps={{ bodyStyle: { padding: 0 } }}
          headerTitle="请假记录"
          request={async (params) => {
            const { current, pageSize } = params;
            const filters = appliedRef.current;
            try {
              const res = await getLeaveApplications({
                page: current,
                pageSize,
                leaveType: filters.leaveType,
                status: filters.status,
                employeeId: filters.employeeId ?? 0,
              });
              return {
                data: (res.data?.list || []) as LeaveRow[],
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
    </div>
  );
};

export default AdminLeavePage;

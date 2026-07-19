import {
  Card,
  Col,
  Descriptions,
  Drawer,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import ApprovalActions from '@/components/ApprovalActions';
import ApprovalTimeline from '@/components/ApprovalTimeline';
import type { ApprovalTimelineNode } from '@/components/ApprovalTimeline';
import ProcessStatusTag from '@/components/ProcessStatusTag';
import { processTypeLabel } from '@/constants/workflow';
import { getEmployeeList } from '@/services/employee';
import {
  fetchMyInstances,
  fetchTaskDetail,
  fetchTaskStats,
  fetchTasks,
  postTaskAction,
  remindTask,
  withdrawInstance,
  type ApprovalTaskDetail,
  type ApprovalTaskItem,
  type ApprovalTaskStats,
} from '@/services/workflow';

type TabKey = 'todo' | 'done' | 'mine';

/**
 * 审批中心：列表 + 详情 Drawer（Timeline + Actions + 催办）
 * 正式离职第一岗：同意前须确认工作交接人。
 */
export default function ApprovalCenterPage() {
  const [tab, setTab] = useState<TabKey>('todo');
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<ApprovalTaskItem[]>([]);
  const [stats, setStats] = useState<ApprovalTaskStats | null>(null);
  const [selected, setSelected] = useState<ApprovalTaskItem | null>(null);
  const [detail, setDetail] = useState<ApprovalTaskDetail | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [acting, setActing] = useState(false);

  const [handoverOpen, setHandoverOpen] = useState(false);
  const [handoverEmployeeId, setHandoverEmployeeId] = useState<number | undefined>();
  const [handoverOptions, setHandoverOptions] = useState<{ label: string; value: number }[]>([]);
  const [handoverLoading, setHandoverLoading] = useState(false);
  const [pendingApproveComment, setPendingApproveComment] = useState<string | undefined>();

  const loadList = useCallback(async (key: TabKey) => {
    setLoading(true);
    try {
      if (key === 'mine') {
        const data = await fetchMyInstances({ page: 1, pageSize: 50 });
        setList(data?.list ?? []);
      } else {
        const status = key === 'todo' ? 'pending' : 'done';
        const data = await fetchTasks({ status, page: 1, pageSize: 50 });
        setList(data?.list ?? []);
      }
      const s = await fetchTaskStats();
      setStats(s ?? null);
    } catch (e) {
      message.error((e as Error)?.message || '加载审批列表失败');
      setList([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadList(tab);
  }, [tab, loadList]);

  const openDetail = async (record: ApprovalTaskItem) => {
    setSelected(record);
    setDrawerOpen(true);
    if (!record.taskId) {
      setDetail(null);
      return;
    }
    try {
      const d = await fetchTaskDetail(record.taskId);
      setDetail(d);
    } catch {
      setDetail(null);
    }
  };

  const biz = detail?.businessDetail ?? {};
  const needHandoverConfirm = biz.needHandoverConfirm === true;
  const resigningEmployeeId =
    typeof biz.employeeId === 'number' ? biz.employeeId : Number(biz.employeeId) || undefined;

  const searchHandover = useCallback(
    async (keyword: string) => {
      if (!keyword || keyword.trim().length < 1) {
        setHandoverOptions([]);
        return;
      }
      setHandoverLoading(true);
      try {
        const res = await getEmployeeList({ keyword: keyword.trim(), page: 1, pageSize: 20 });
        const list = res.data?.list ?? [];
        setHandoverOptions(
          list
            .filter((e) => e.employeeId !== resigningEmployeeId)
            .map((e) => ({
              label: `${e.name} · ${e.department || '未分部门'} · ${e.empNo || '-'}`,
              value: e.employeeId,
            })),
        );
      } catch {
        setHandoverOptions([]);
      } finally {
        setHandoverLoading(false);
      }
    },
    [resigningEmployeeId],
  );

  const doApprove = async (comment?: string, handoverId?: number) => {
    if (!selected?.taskId) return;
    setActing(true);
    try {
      await postTaskAction(selected.taskId, {
        action: 'APPROVE',
        comment,
        handoverEmployeeId: handoverId,
      });
      message.success('已同意');
      setHandoverOpen(false);
      setDrawerOpen(false);
      await loadList(tab);
    } catch (e) {
      message.error((e as Error)?.message || '操作失败');
      throw e;
    } finally {
      setActing(false);
    }
  };

  const columns: ColumnsType<ApprovalTaskItem> = [
    { title: '标题', dataIndex: 'title' },
    {
      title: '类型',
      dataIndex: 'processType',
      width: 120,
      render: (v: string) => processTypeLabel(v),
    },
    { title: '申请人', dataIndex: 'applicantName', width: 120 },
    { title: '当前节点', dataIndex: 'currentNodeLabel', width: 140 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <ProcessStatusTag status={s} />,
    },
    { title: '申请时间', dataIndex: 'createTime', width: 180 },
    { title: '截止', dataIndex: 'dueAt', width: 180 },
  ];

  const timelineNodes: ApprovalTimelineNode[] = (detail?.timeline ?? []).map((t, idx) => ({
    key: String(idx),
    title: t.node || t.action,
    assigneeName: t.assignee,
    comment: t.comment,
    time: t.time,
    displayText: t.displayText,
    status:
      t.action === 'REJECT'
        ? 'error'
        : idx === (detail?.timeline?.length ?? 0) - 1 && selected?.status === 'pending'
          ? 'process'
          : 'finish',
  }));

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        审批中心
      </Typography.Title>

      <Row gutter={16}>
        <Col span={8}>
          <Card>
            <Statistic title="待办" value={stats?.pending ?? 0} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="今日已审" value={stats?.approvedToday ?? 0} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="超时" value={stats?.overdueCount ?? 0} valueStyle={{ color: '#cf1322' }} />
          </Card>
        </Col>
      </Row>

      <Card>
        <Tabs
          activeKey={tab}
          onChange={(k) => {
            setTab(k as TabKey);
            setDrawerOpen(false);
            setSelected(null);
          }}
          items={[
            { key: 'todo', label: '我的待办' },
            { key: 'done', label: '我的已办' },
            { key: 'mine', label: '我发起的' },
          ]}
        />
        <Table
          rowKey={(r) => `${r.instanceId}-${r.taskId}`}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          onRow={(record) => ({
            onClick: () => openDetail(record),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

      <Drawer
        title={selected ? `详情 · ${selected.title}` : '审批详情'}
        width={520}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        {selected ? (
          <Space direction="vertical" size={24} style={{ width: '100%' }}>
            <div>
              <Typography.Text type="secondary">流程类型 </Typography.Text>
              <Typography.Text>{processTypeLabel(selected.processType)}</Typography.Text>
              <div style={{ marginTop: 8 }}>
                <Typography.Text type="secondary">流程状态 </Typography.Text>
                <ProcessStatusTag status={selected.status} />
              </div>
              {selected.dueAt ? (
                <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
                  截止：{selected.dueAt}
                </Typography.Paragraph>
              ) : null}
            </div>

            {detail?.instance?.processType === 'RESIGNATION' || selected.processType === 'RESIGNATION' ? (
              <Descriptions size="small" column={1} bordered title="离职信息">
                <Descriptions.Item label="离职员工">
                  {(biz.employeeName as string) || selected.applicantName}
                  {biz.employeeNo ? ` · ${biz.employeeNo}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="离职日">{(biz.resignationDate as string) || '-'}</Descriptions.Item>
                <Descriptions.Item label="原因">{(biz.reasonCategory as string) || '-'}</Descriptions.Item>
                <Descriptions.Item label="说明">{(biz.reasonDetail as string) || '-'}</Descriptions.Item>
                <Descriptions.Item label="交接人">
                  {biz.handoverEmployeeName
                    ? `${biz.handoverEmployeeName}${biz.handoverEmployeeNo ? ` · ${biz.handoverEmployeeNo}` : ''}`
                    : needHandoverConfirm
                      ? '待部门负责人确认'
                      : '-'}
                </Descriptions.Item>
              </Descriptions>
            ) : null}

            <ApprovalTimeline
              nodes={
                timelineNodes.length > 0
                  ? timelineNodes
                  : [
                      {
                        key: '1',
                        title: '发起申请',
                        assigneeName: selected.applicantName,
                        status: 'finish',
                        time: selected.createTime,
                      },
                      {
                        key: '2',
                        title: selected.currentNodeLabel,
                        status: selected.status === 'pending' ? 'process' : 'finish',
                        time: selected.dueAt,
                      },
                    ]
              }
            />
            <div>
              <Typography.Title level={5}>操作</Typography.Title>
              {needHandoverConfirm && tab === 'todo' ? (
                <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
                  同意前请确认工作交接安排（交接人不能是离职员工本人）。
                </Typography.Paragraph>
              ) : null}
              <ApprovalActions
                loading={acting}
                canAct={tab === 'todo' && selected.status === 'pending'}
                canRemind={tab === 'mine' && selected.status === 'pending' && !!selected.taskId}
                canWithdraw={tab === 'mine' && selected.status === 'pending'}
                onAction={async (action, payload) => {
                  if (action === 'APPROVE' && needHandoverConfirm) {
                    setPendingApproveComment(payload.comment);
                    setHandoverEmployeeId(
                      typeof biz.handoverEmployeeId === 'number'
                        ? biz.handoverEmployeeId
                        : undefined,
                    );
                    setHandoverOptions([]);
                    setHandoverOpen(true);
                    return false;
                  }
                  setActing(true);
                  try {
                    if (action === 'WITHDRAW') {
                      await withdrawInstance(selected.instanceId);
                    } else if (action === 'REMIND' && selected.taskId) {
                      await remindTask(selected.taskId);
                    } else if (selected.taskId) {
                      await postTaskAction(selected.taskId, {
                        action: action as 'APPROVE' | 'REJECT' | 'FORWARD',
                        comment: payload.comment,
                        targetUserId: payload.targetUserId,
                      });
                    }
                    setDrawerOpen(false);
                    await loadList(tab);
                  } catch (e) {
                    message.error((e as Error)?.message || '操作失败');
                    throw e;
                  } finally {
                    setActing(false);
                  }
                }}
              />
            </div>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title="确认工作交接安排"
        open={handoverOpen}
        okText="确认并同意"
        confirmLoading={acting}
        onCancel={() => setHandoverOpen(false)}
        onOk={async () => {
          if (!handoverEmployeeId) {
            message.warning('请选择工作交接人');
            return;
          }
          if (resigningEmployeeId && handoverEmployeeId === resigningEmployeeId) {
            message.warning('工作交接人不能是离职员工本人');
            return;
          }
          await doApprove(pendingApproveComment, handoverEmployeeId);
        }}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          离职员工：{(biz.employeeName as string) || selected?.applicantName || '-'}
        </Typography.Paragraph>
        <Select
          showSearch
          style={{ width: '100%' }}
          placeholder="按姓名 / 部门 / 工号搜索交接人"
          filterOption={false}
          notFoundContent={handoverLoading ? '搜索中…' : '无匹配员工'}
          loading={handoverLoading}
          value={handoverEmployeeId}
          onSearch={searchHandover}
          onChange={(v) => setHandoverEmployeeId(v)}
          options={handoverOptions}
        />
      </Modal>
    </Space>
  );
}

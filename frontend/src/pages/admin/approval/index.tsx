import { Card, Col, Drawer, Row, Space, Statistic, Table, Tabs, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import ApprovalActions from '@/components/ApprovalActions';
import ApprovalTimeline from '@/components/ApprovalTimeline';
import type { ApprovalTimelineNode } from '@/components/ApprovalTimeline';
import ProcessStatusTag from '@/components/ProcessStatusTag';
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

  const columns: ColumnsType<ApprovalTaskItem> = [
    { title: '标题', dataIndex: 'title' },
    { title: '类型', dataIndex: 'processType', width: 160 },
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
              <Typography.Text type="secondary">流程状态 </Typography.Text>
              <ProcessStatusTag status={selected.status} />
              {selected.dueAt ? (
                <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
                  截止：{selected.dueAt}
                </Typography.Paragraph>
              ) : null}
            </div>
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
              <ApprovalActions
                loading={acting}
                canAct={tab === 'todo' && selected.status === 'pending'}
                canRemind={tab === 'mine' && selected.status === 'pending' && !!selected.taskId}
                canWithdraw={tab === 'mine' && selected.status === 'pending'}
                onAction={async (action, payload) => {
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
                  } finally {
                    setActing(false);
                  }
                }}
              />
            </div>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}

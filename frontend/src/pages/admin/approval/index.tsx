import { Card, Space, Table, Tabs, Typography, message } from 'antd';
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
  withdrawInstance,
  type ApprovalTaskDetail,
  type ApprovalTaskItem,
  type ApprovalTaskStats,
} from '@/services/workflow';

type TabKey = 'todo' | 'done' | 'mine';

/**
 * 审批中心：我的待办 / 我的已办 / 我发起的
 */
export default function ApprovalCenterPage() {
  const [tab, setTab] = useState<TabKey>('todo');
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<ApprovalTaskItem[]>([]);
  const [stats, setStats] = useState<ApprovalTaskStats | null>(null);
  const [selected, setSelected] = useState<ApprovalTaskItem | null>(null);
  const [detail, setDetail] = useState<ApprovalTaskDetail | null>(null);

  const loadList = useCallback(async (key: TabKey) => {
    setLoading(true);
    try {
      if (key === 'mine') {
        const data = await fetchMyInstances({ page: 1, pageSize: 50 });
        const rows = data?.list ?? [];
        setList(rows);
        setSelected(rows[0] ?? null);
      } else {
        const status = key === 'todo' ? 'pending' : 'done';
        const data = await fetchTasks({ status, page: 1, pageSize: 50 });
        const rows = data?.list ?? [];
        setList(rows);
        setSelected(rows[0] ?? null);
      }
      const s = await fetchTaskStats();
      setStats(s ?? null);
    } catch (e) {
      message.error((e as Error)?.message || '加载审批列表失败，请确认后端已启动');
      setList([]);
      setSelected(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadList(tab);
  }, [tab, loadList]);

  useEffect(() => {
    if (!selected?.taskId) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const d = await fetchTaskDetail(selected.taskId);
        if (!cancelled) setDetail(d);
      } catch {
        if (!cancelled) setDetail(null);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selected?.taskId]);

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
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        待办 {stats?.pending ?? '-'} · 今日已审 {stats?.approvedToday ?? '-'} · 超时{' '}
        {stats?.overdueCount ?? '-'}
        （开发期请求头 X-User-Id=1002 看待办；入职一审也是 1002）
      </Typography.Paragraph>

      <Card>
        <Tabs
          activeKey={tab}
          onChange={(k) => setTab(k as TabKey)}
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
            onClick: () => setSelected(record),
            style: { cursor: 'pointer' },
          })}
        />
      </Card>

      {selected ? (
        <Card title={`详情 · ${selected.title}`}>
          <Space align="start" size={32} wrap style={{ width: '100%' }}>
            <div style={{ minWidth: 280, flex: 1 }}>
              <Typography.Text type="secondary">流程状态 </Typography.Text>
              <ProcessStatusTag status={selected.status} />
              <div style={{ marginTop: 16 }}>
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
              </div>
            </div>
            <div>
              <Typography.Title level={5}>操作</Typography.Title>
              <ApprovalActions
                canAct={tab === 'todo' && selected.status === 'pending'}
                canWithdraw={tab === 'mine' && selected.status === 'pending'}
                onAction={async (action, payload) => {
                  if (action === 'WITHDRAW') {
                    await withdrawInstance(selected.instanceId);
                  } else if (selected.taskId) {
                    await postTaskAction(selected.taskId, {
                      action: action as 'APPROVE' | 'REJECT' | 'FORWARD',
                      comment: payload.comment,
                      targetUserId: payload.targetUserId,
                    });
                  }
                  await loadList(tab);
                }}
              />
            </div>
          </Space>
        </Card>
      ) : null}
    </Space>
  );
}

import { Card, Space, Table, Tabs, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import ApprovalActions from '@/components/ApprovalActions';
import ApprovalTimeline from '@/components/ApprovalTimeline';
import ProcessStatusTag from '@/components/ProcessStatusTag';
import type { ApprovalTaskItem } from '@/services/workflow';

type TabKey = 'todo' | 'done' | 'mine';

const MOCK_TODO: ApprovalTaskItem[] = [
  {
    taskId: 1001,
    instanceId: 201,
    processType: 'ONBOARDING',
    title: '张三入职审批',
    applicantName: '李 HR',
    applicantDept: '人力资源部',
    currentNodeLabel: '部门负责人审批',
    createTime: '2026-07-14 10:20:00',
    dueAt: '2026-07-16 10:20:00',
    status: 'pending',
  },
  {
    taskId: 1002,
    instanceId: 202,
    processType: 'TRANSFER',
    title: '王五调岗审批',
    applicantName: '李 HR',
    applicantDept: '人力资源部',
    currentNodeLabel: '原部门确认',
    createTime: '2026-07-13 09:00:00',
    dueAt: '2026-07-15 09:00:00',
    status: 'pending',
  },
];

const MOCK_DONE: ApprovalTaskItem[] = [
  {
    taskId: 9001,
    instanceId: 180,
    processType: 'LEAVE',
    title: '赵六请假审批',
    applicantName: '赵六',
    applicantDept: '研发部',
    currentNodeLabel: '已结束',
    createTime: '2026-07-10 11:00:00',
    status: 'approved',
  },
];

const MOCK_MINE: ApprovalTaskItem[] = [
  {
    taskId: 0,
    instanceId: 210,
    processType: 'RESIGNATION_REQUEST',
    title: '我的离职申请',
    applicantName: '当前用户',
    currentNodeLabel: 'HR 备案',
    createTime: '2026-07-12 16:00:00',
    status: 'pending',
  },
];

/**
 * 审批中心（Day1 Mock）三 Tab：我的待办 / 我的已办 / 我发起的
 * 路由未挂载：需自行在 config.ts 增加 /admin/approval
 */
export default function ApprovalCenterPage() {
  const [tab, setTab] = useState<TabKey>('todo');
  const [selected, setSelected] = useState<ApprovalTaskItem | null>(MOCK_TODO[0]);

  const data = useMemo(() => {
    if (tab === 'todo') return MOCK_TODO;
    if (tab === 'done') return MOCK_DONE;
    return MOCK_MINE;
  }, [tab]);

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

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        审批中心
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Day1 Mock 页 · 组件：ProcessStatusTag / ApprovalTimeline / ApprovalActions · API 见 services/workflow.ts
      </Typography.Paragraph>

      <Card>
        <Tabs
          activeKey={tab}
          onChange={(k) => {
            const next = k as TabKey;
            setTab(next);
            const list = next === 'todo' ? MOCK_TODO : next === 'done' ? MOCK_DONE : MOCK_MINE;
            setSelected(list[0] ?? null);
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
          dataSource={data}
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
                  nodes={[
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
                      assigneeName: '当前审批人',
                      status: selected.status === 'pending' ? 'process' : 'finish',
                      time: selected.dueAt,
                    },
                    {
                      key: '3',
                      title: '结束',
                      status: selected.status === 'pending' ? 'wait' : 'finish',
                    },
                  ]}
                />
              </div>
            </div>
            <div>
              <Typography.Title level={5}>操作</Typography.Title>
              <ApprovalActions
                canAct={tab === 'todo' && selected.status === 'pending'}
                canWithdraw={tab === 'mine' && selected.status === 'pending'}
                onAction={async (action) => {
                  console.info('[mock approval action]', action, selected.taskId);
                }}
              />
            </div>
          </Space>
        </Card>
      ) : null}
    </Space>
  );
}

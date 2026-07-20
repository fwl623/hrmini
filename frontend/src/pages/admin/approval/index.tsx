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
import { history } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import ApprovalActions from '@/components/ApprovalActions';
import ApprovalTimeline from '@/components/ApprovalTimeline';
import type { ApprovalTimelineNode } from '@/components/ApprovalTimeline';
import ProcessStatusTag from '@/components/ProcessStatusTag';
import {
  approvalActionLabel,
  employmentTypeLabel,
  localizeTimelineText,
  processTypeLabel,
  resignationReasonLabel,
  resignationTypeLabel,
} from '@/constants/workflow';
import {
  fetchMyInstances,
  fetchTaskDetail,
  fetchInstanceDetail,
  fetchTaskStats,
  fetchTasks,
  postTaskAction,
  remindTask,
  searchHandoverCandidates,
  withdrawInstance,
  type ApprovalTaskDetail,
  type ApprovalTaskItem,
  type ApprovalTaskStats,
} from '@/services/workflow';

type TabKey = 'todo' | 'done' | 'mine';

/**
 * 审批中心：列表 + 详情 Drawer（Timeline + Actions + 催办）
 * - 正式离职第一岗：同意前须确认工作交接人
 * - 员工离职申请（RESIGNATION_REQUEST）同意后：提示是否立即发起正式离职
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
    try {
      // 「我发起的」列表 taskId 为 0，需按 instanceId 拉详情（含 businessDetail）
      if (record.taskId) {
        const d = await fetchTaskDetail(record.taskId);
        setDetail(d);
        return;
      }
      if (record.instanceId) {
        const inst = await fetchInstanceDetail(record.instanceId);
        const mapped: ApprovalTaskDetail = {
          task: {
            id: 0,
            status: inst?.status || record.status,
            currentNodeLabel: inst?.currentNodeLabel || record.currentNodeLabel,
          },
          instance: {
            processType: inst?.processType || record.processType,
            businessNo: String(inst?.instanceId ?? record.instanceId),
            initiator: record.applicantName,
            createdAt: inst?.createdAt || record.createTime,
            status: inst?.status || record.status,
          },
          businessDetail: inst?.businessDetail ?? {},
          timeline: inst?.timeline ?? [],
          actions: [],
        };
        setDetail(mapped);
        return;
      }
      setDetail(null);
    } catch {
      setDetail(null);
    }
  };

  const biz = detail?.businessDetail ?? {};
  const processType = String(
    detail?.instance?.processType || selected?.processType || '',
  ).toUpperCase();
  const bizText = (v: unknown, fallback = '-') =>
    v == null || v === '' ? fallback : String(v);
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
        const list = await searchHandoverCandidates(keyword.trim());
        setHandoverOptions(
          (list ?? [])
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

  const promptStartFormalResignation = (requestId?: number) => {
    if (!requestId || Number.isNaN(requestId)) {
      return;
    }
    Modal.confirm({
      title: '是否立即发起正式离职？',
      content: '员工离职申请已通过。立即发起后将进入：部门负责人确认交接 → HR 终审 → 待离职 → 已离职。',
      okText: '立即发起',
      cancelText: '稍后处理',
      onOk: () => {
        history.push(`/admin/resignation?requestId=${requestId}`);
      },
    });
  };

  const promptResignAfterRegularizationFail = (employeeId?: number) => {
    if (!employeeId || Number.isNaN(employeeId)) {
      return;
    }
    Modal.confirm({
      title: '试用不通过：是否立即发起正式离职？',
      content: '转正评估「不通过」已审批完成。按 PRD 需走辞退/正式离职流程。',
      okText: '去发起离职',
      cancelText: '稍后处理',
      onOk: () => {
        history.push(`/admin/resignation?employeeId=${employeeId}&open=1`);
      },
    });
  };

  const resolveRequestId = () => {
    const fromBiz =
      typeof biz.requestId === 'number'
        ? biz.requestId
        : Number(biz.requestId || biz.businessKey) || 0;
    return fromBiz > 0 ? fromBiz : undefined;
  };

  const isResignationRequest =
    selected?.processType === 'RESIGNATION_REQUEST' ||
    detail?.instance?.processType === 'RESIGNATION_REQUEST';

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
      const requestId = isResignationRequest ? resolveRequestId() : undefined;
      const regFailEmployeeId =
        (selected?.processType === 'REGULARIZATION' ||
          detail?.instance?.processType === 'REGULARIZATION') &&
        String(biz.approvalResult || '').toUpperCase() === 'FAIL' &&
        String(selected?.currentNodeLabel || '').includes('HR')
          ? typeof biz.employeeId === 'number'
            ? biz.employeeId
            : Number(biz.employeeId) || undefined
          : undefined;
      await loadList(tab);
      if (requestId) {
        promptStartFormalResignation(requestId);
      } else if (regFailEmployeeId) {
        promptResignAfterRegularizationFail(regFailEmployeeId);
      }
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
    title: approvalActionLabel(t.node || t.action),
    assigneeName: t.assignee,
    comment: localizeTimelineText(t.comment),
    time: t.time,
    displayText: localizeTimelineText(t.displayText),
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

            {processType === 'ONBOARDING' ? (
              <Descriptions size="small" column={1} bordered title="入职信息">
                <Descriptions.Item label="姓名">{bizText(biz.name, selected.applicantName)}</Descriptions.Item>
                <Descriptions.Item label="性别">
                  {({ MALE: '男', FEMALE: '女' } as Record<string, string>)[String(biz.gender || '')] ||
                    bizText(biz.gender)}
                </Descriptions.Item>
                <Descriptions.Item label="手机号">{bizText(biz.mobile)}</Descriptions.Item>
                <Descriptions.Item label="邮箱">{bizText(biz.email)}</Descriptions.Item>
                <Descriptions.Item label="预计入职日">{bizText(biz.expectedOnboardDate)}</Descriptions.Item>
                <Descriptions.Item label="部门">{bizText(biz.departmentName)}</Descriptions.Item>
                <Descriptions.Item label="职位">{bizText(biz.positionName)}</Descriptions.Item>
                <Descriptions.Item label="用工类型">
                  {employmentTypeLabel(
                    typeof biz.employmentType === 'string' ? biz.employmentType : undefined,
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="试用期月数">{bizText(biz.probationMonths)}</Descriptions.Item>
                <Descriptions.Item label="基本工资">{bizText(biz.baseSalary)}</Descriptions.Item>
                <Descriptions.Item label="直属上级">{bizText(biz.managerName)}</Descriptions.Item>
                <Descriptions.Item label="需 HR 二审">
                  {biz.needSecondApproval === true ? '是' : biz.needSecondApproval === false ? '否' : '-'}
                </Descriptions.Item>
                {biz.rejectReason ? (
                  <Descriptions.Item label="驳回原因">{bizText(biz.rejectReason)}</Descriptions.Item>
                ) : null}
              </Descriptions>
            ) : null}

            {processType === 'TRANSFER' ? (
              <Descriptions size="small" column={1} bordered title="调岗信息">
                <Descriptions.Item label="员工">
                  {bizText(biz.employeeName, selected.applicantName)}
                  {biz.employeeNo ? ` · ${biz.employeeNo}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="原部门">{bizText(biz.fromDepartmentName)}</Descriptions.Item>
                <Descriptions.Item label="新部门">{bizText(biz.newDepartmentName)}</Descriptions.Item>
                <Descriptions.Item label="新职位">{bizText(biz.newPositionName)}</Descriptions.Item>
                <Descriptions.Item label="新职级">{bizText(biz.newJobLevel)}</Descriptions.Item>
                <Descriptions.Item label="新上级">{bizText(biz.newManagerName)}</Descriptions.Item>
                <Descriptions.Item label="调薪">
                  {biz.salaryAdjustment != null ? String(biz.salaryAdjustment) : '不调薪'}
                </Descriptions.Item>
                <Descriptions.Item label="生效日">{bizText(biz.effectiveDate)}</Descriptions.Item>
                <Descriptions.Item label="原因">{bizText(biz.reason)}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'MOBILE_CHANGE' ? (
              <Descriptions size="small" column={1} bordered title="手机号变更">
                <Descriptions.Item label="员工ID">{bizText(biz.employeeId)}</Descriptions.Item>
                <Descriptions.Item label="原手机号">{bizText(biz.oldMobile)}</Descriptions.Item>
                <Descriptions.Item label="新手机号">{bizText(biz.newMobile)}</Descriptions.Item>
                <Descriptions.Item label="原因">{bizText(biz.reason)}</Descriptions.Item>
                <Descriptions.Item label="申请状态">{bizText(biz.status)}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'RESIGNATION' ? (
              <Descriptions size="small" column={1} bordered title="离职信息">
                <Descriptions.Item label="离职员工">
                  {bizText(biz.employeeName, selected.applicantName)}
                  {biz.employeeNo ? ` · ${biz.employeeNo}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="离职日">{bizText(biz.resignationDate)}</Descriptions.Item>
                <Descriptions.Item label="原因">
                  {resignationReasonLabel(
                    typeof biz.reasonCategory === 'string' ? biz.reasonCategory : undefined,
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="说明">{bizText(biz.reasonDetail)}</Descriptions.Item>
                <Descriptions.Item label="交接人">
                  {biz.handoverEmployeeName
                    ? `${biz.handoverEmployeeName}${biz.handoverEmployeeNo ? ` · ${biz.handoverEmployeeNo}` : ''}`
                    : needHandoverConfirm
                      ? '待部门负责人确认'
                      : '-'}
                </Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'RESIGNATION_REQUEST' ? (
              <Descriptions size="small" column={1} bordered title="员工离职申请">
                <Descriptions.Item label="申请人">
                  {bizText(biz.employeeName, selected.applicantName)}
                  {biz.employeeNo ? ` · ${biz.employeeNo}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="期望离职日">{bizText(biz.expectedResignDate)}</Descriptions.Item>
                <Descriptions.Item label="原因">
                  {resignationReasonLabel(
                    typeof biz.reasonCategory === 'string' ? biz.reasonCategory : undefined,
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="类型">
                  {resignationTypeLabel(
                    typeof biz.resignationType === 'string' ? biz.resignationType : undefined,
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="说明">{bizText(biz.reasonDetail)}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'REGULARIZATION' ? (
              <Descriptions size="small" column={1} bordered title="转正信息">
                <Descriptions.Item label="员工">
                  {bizText(biz.employeeName, selected.applicantName)}
                  {biz.employeeNo ? ` · ${biz.employeeNo}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="试用起止">
                  {bizText(biz.probationStartDate)} ~ {bizText(biz.probationEndDate)}
                </Descriptions.Item>
                <Descriptions.Item label="表现评价">{bizText(biz.performanceEvaluation)}</Descriptions.Item>
                <Descriptions.Item label="评估结果">
                  {({ PASS: '通过', EXTEND: '延长试用', FAIL: '不通过' } as Record<string, string>)[
                    String(biz.approvalResult || '')
                  ] || bizText(biz.approvalResult)}
                </Descriptions.Item>
                <Descriptions.Item label="延长月数">
                  {biz.extendMonths != null ? String(biz.extendMonths) : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="转正后基本工资">
                  {biz.salaryAdjustment != null ? String(biz.salaryAdjustment) : '不调薪'}
                </Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'LEAVE' ? (
              <Descriptions size="small" column={1} bordered title="请假信息">
                <Descriptions.Item label="请假类型">
                  {(
                    {
                      ANNUAL: '年假',
                      SICK: '病假',
                      PERSONAL: '事假',
                      MARRIAGE: '婚假',
                      MATERNITY: '产假',
                      BEREAVEMENT: '丧假',
                      COMP_OFF: '调休',
                    } as Record<string, string>
                  )[String(biz.leaveType || '')] || bizText(biz.leaveType)}
                </Descriptions.Item>
                <Descriptions.Item label="天数">
                  {biz.days != null ? `${biz.days} 天` : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="摘要">{bizText(biz.businessSummary)}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'OVERTIME' ? (
              <Descriptions size="small" column={1} bordered title="加班信息">
                <Descriptions.Item label="加班日期">{bizText(biz.overtimeDate)}</Descriptions.Item>
                <Descriptions.Item label="加班时长">
                  {biz.hours != null ? `${biz.hours} 小时` : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="摘要">{bizText(biz.businessSummary)}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'MAKEUP' || processType === 'PUNCH_FIX' ? (
              <Descriptions size="small" column={1} bordered title="补卡信息">
                <Descriptions.Item label="补卡日期">{bizText(biz.makeupDate)}</Descriptions.Item>
                <Descriptions.Item label="补卡类型">
                  {biz.punchType === 'IN' ? '上班卡' : biz.punchType === 'OUT' ? '下班卡' : bizText(biz.punchType)}
                </Descriptions.Item>
                <Descriptions.Item label="摘要">{bizText(biz.businessSummary)}</Descriptions.Item>
              </Descriptions>
            ) : null}

            {processType === 'PAYROLL' || processType === 'PAYROLL_BATCH' ? (
              <Descriptions size="small" column={1} bordered title="薪资核算">
                <Descriptions.Item label="标题">{bizText(biz.title, selected.title)}</Descriptions.Item>
                <Descriptions.Item label="业务单号">{bizText(biz.businessKey)}</Descriptions.Item>
                <Descriptions.Item label="摘要">{bizText(biz.businessSummary)}</Descriptions.Item>
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
                    const requestId =
                      action === 'APPROVE' && isResignationRequest
                        ? resolveRequestId()
                        : undefined;
                    const regFailEmployeeId =
                      action === 'APPROVE' &&
                      (selected.processType === 'REGULARIZATION' ||
                        detail?.instance?.processType === 'REGULARIZATION') &&
                      String(biz.approvalResult || '').toUpperCase() === 'FAIL' &&
                      String(selected.currentNodeLabel || '').includes('HR')
                        ? typeof biz.employeeId === 'number'
                          ? biz.employeeId
                          : Number(biz.employeeId) || undefined
                        : undefined;
                    setDrawerOpen(false);
                    await loadList(tab);
                    if (requestId) {
                      promptStartFormalResignation(requestId);
                    } else if (regFailEmployeeId) {
                      promptResignAfterRegularizationFail(regFailEmployeeId);
                    }
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

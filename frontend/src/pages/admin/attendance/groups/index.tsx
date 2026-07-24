import React, { useRef, useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Modal,
  Form,
  Input,
  Select,
  TreeSelect,
  TimePicker,
  InputNumber,
  Space,
  Popconfirm,
  Tag,
  message,
  Alert,
  Typography,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, TeamOutlined } from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

import { createGroup, getGroups, getGroupDetail, updateGroup, deleteGroup } from '@/services/attendance';
import { getDeptTree } from '@/services/org';
import { getEmployeeList } from '@/services/employee';

/** 班次类型下拉选项 */
const SHIFT_TYPE_OPTIONS = [
  { label: '固定班次', value: 'FIXED' },
  { label: '弹性班次', value: 'FLEXIBLE' },
  { label: '排班制', value: 'SCHEDULE' },
];

/** 将树节点及选中项展开为自身+全部子孙 id（选中父部门时覆盖子部门员工） */
function expandDeptIds(tree: any[], selected: number[]): number[] {
  if (!selected?.length) return [];
  const selectedSet = new Set(selected);
  const result = new Set<number>();
  const addSubtree = (node: any) => {
    if (node?.id == null) return;
    result.add(node.id);
    (node.children || []).forEach(addSubtree);
  };
  const walk = (nodes: any[]) => {
    for (const n of nodes || []) {
      if (selectedSet.has(n.id)) addSubtree(n);
      else if (n.children?.length) walk(n.children);
    }
  };
  walk(tree);
  return [...result];
}

const AttendanceGroupPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = React.useState(false);
  const [editingGroup, setEditingGroup] = React.useState<any>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = React.useState(false);
  const [deptTree, setDeptTree] = useState<any[]>([]);
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);
  const shiftType = Form.useWatch('shiftType', form);
  const departmentIds = Form.useWatch('departmentIds', form) as number[] | undefined;

  useEffect(() => {
    getDeptTree().then((res: any) => setDeptTree(res.data || [])).catch(() => {});
  }, []);

  /** 按已选部门加载可选员工（含子孙部门） */
  const loadEmployeesByDepts = useCallback(async (deptIds: number[]) => {
    const expanded = expandDeptIds(deptTree, deptIds || []);
    if (!expanded.length) {
      setEmpOptions([]);
      return [] as number[];
    }
    setEmpLoading(true);
    try {
      const res = await getEmployeeList({
        page: 1,
        pageSize: 500,
        departmentIds: expanded.join(','),
        employmentStatus: 'probation,regular,pending_resign',
      });
      const list = res.data?.list || [];
      const options = list.map((e: any) => ({
        label: `${e.name} (${e.empNo || e.employeeId})`,
        value: e.employeeId as number,
      }));
      setEmpOptions(options);
      return options.map((o) => o.value);
    } catch {
      setEmpOptions([]);
      message.warning('该部门员工列表加载失败');
      return [] as number[];
    } finally {
      setEmpLoading(false);
    }
  }, [deptTree]);

  useEffect(() => {
    if (!modalOpen) return;
    const ids = departmentIds || [];
    if (!ids.length) {
      setEmpOptions([]);
      form.setFieldValue('employeeIds', []);
      return;
    }
    void loadEmployeesByDepts(ids).then((allowedIds) => {
      const allowed = new Set(allowedIds);
      const current: number[] = form.getFieldValue('employeeIds') || [];
      if (!current.length) return;
      const next = current.filter((id) => allowed.has(id));
      if (next.length !== current.length) {
        form.setFieldValue('employeeIds', next);
      }
    });
  }, [departmentIds, modalOpen, loadEmployeesByDepts, form]);

  // ---------- 表格列定义 ----------
  const columns: any[] = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '考勤组名称', dataIndex: 'name', width: 150 },
    {
      title: '班次类型',
      dataIndex: 'shiftType',
      width: 100,
      render: (val: string) => {
        const map: Record<string, string> = { FIXED: '固定', FLEXIBLE: '弹性', SCHEDULE: '排班' };
        return <Tag>{map[val] || val}</Tag>;
      },
    },
    { title: '上班时间', dataIndex: 'workStartTime', width: 100 },
    { title: '下班时间', dataIndex: 'workEndTime', width: 100 },
    { title: '午休', render: (_: any, r: any) => `${r.lunchStartTime ?? '-'} ~ ${r.lunchEndTime ?? '-'}`, width: 140 },
    { title: '迟到阈值(min)', dataIndex: 'lateThresholdMinutes', width: 120 },
    { title: '早退阈值(min)', dataIndex: 'earlyLeaveThresholdMinutes', width: 120 },
    {
      title: '成员',
      dataIndex: 'memberCount',
      width: 70,
      render: (count: number) => (
        <span><TeamOutlined style={{ marginRight: 4 }} />{count ?? 0}</span>
      ),
    },
    {
      title: '操作',
      width: 120,
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除？有关联员工将拒绝" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ---------- 事件处理 ----------

  const handleAdd = () => {
    setEditingGroup(null);
    form.resetFields();
    form.setFieldsValue({ lateThreshold: 15, earlyLeaveThreshold: 15, employeeIds: [] });
    setEmpOptions([]);
    setModalOpen(true);
  };

  const handleEdit = async (record: any) => {
    setEditingGroup(record);
    try {
      const res = await getGroupDetail(record.id);
      const detail = res.data;
      const scope = detail?.applicableScope || { departmentIds: [], positionIds: [], employeeIds: [] };
      form.setFieldsValue({
        name: detail?.name,
        shiftType: detail?.shiftType,
        onDuty: detail?.workStartTime ? dayjs(detail.workStartTime, 'HH:mm') : undefined,
        offDuty: detail?.workEndTime ? dayjs(detail.workEndTime, 'HH:mm') : undefined,
        restStart: detail?.lunchStartTime ? dayjs(detail.lunchStartTime, 'HH:mm') : undefined,
        restEnd: detail?.lunchEndTime ? dayjs(detail.lunchEndTime, 'HH:mm') : undefined,
        lateThreshold: detail?.lateThresholdMinutes ?? 15,
        earlyLeaveThreshold: detail?.earlyLeaveThresholdMinutes ?? 15,
        flexStartEarliest: detail?.flexStartEarliest ? dayjs(detail.flexStartEarliest, 'HH:mm') : undefined,
        flexStartLatest: detail?.flexStartLatest ? dayjs(detail.flexStartLatest, 'HH:mm') : undefined,
        departmentIds: scope.departmentIds || [],
        employeeIds: scope.employeeIds || [],
      });
    } catch (err) {
      message.error('获取考勤组详情失败');
      return;
    }
    setModalOpen(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteGroup(id);
      message.success('删除成功');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);

      const rawDeptIds: number[] = values.departmentIds || [];
      const expandedDeptIds = expandDeptIds(deptTree, rawDeptIds);
      const employeeIds: number[] = values.employeeIds || [];

      if (!expandedDeptIds.length && !employeeIds.length) {
        message.error('请至少选择适用部门');
        return;
      }

      const applicableScope = {
        departmentIds: expandedDeptIds.length ? expandedDeptIds : rawDeptIds,
        positionIds: [],
        employeeIds,
      };

      const payload: any = {
        name: values.name,
        shiftType: values.shiftType,
        restStart: values.restStart?.format('HH:mm') || null,
        restEnd: values.restEnd?.format('HH:mm') || null,
        applicableScope,
      };

      if (values.shiftType === 'FLEXIBLE') {
        payload.flexibleRange = {
          earliest: values.flexStartEarliest?.format('HH:mm'),
          latest: values.flexStartLatest?.format('HH:mm'),
        };
        payload.offDuty = values.offDuty?.format('HH:mm');
        payload.lateThreshold = values.lateThreshold ?? 15;
        payload.earlyLeaveThreshold = values.earlyLeaveThreshold ?? 15;
      } else if (values.shiftType === 'SCHEDULE') {
        payload.onDuty = values.onDuty?.format('HH:mm');
        payload.offDuty = values.offDuty?.format('HH:mm');
      } else {
        payload.onDuty = values.onDuty?.format('HH:mm');
        payload.offDuty = values.offDuty?.format('HH:mm');
        payload.lateThreshold = values.lateThreshold ?? 15;
        payload.earlyLeaveThreshold = values.earlyLeaveThreshold ?? 15;
      }

      if (editingGroup) {
        await updateGroup(editingGroup.id, payload as any);
        message.success('更新成功');
      } else {
        await createGroup(payload as any);
        message.success('创建成功');
      }

      setModalOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const hasDept = !!(departmentIds && departmentIds.length);

  return (
    <Card
      title="考勤组管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建考勤组
        </Button>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="适用部门必选；适用员工可选——不选则该部门全部在职员工入组，选了则仅所选员工。"
      />
      <ProTable<any>
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          try {
            const res = await getGroups({ page: current, size: pageSize });
            return {
              data: res.data?.list || [],
              total: res.data?.total || 0,
              success: true,
            };
          } catch {
            return { data: [], total: 0, success: false };
          }
        }}
        pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
        search={false}
        actionRef={actionRef as any}
        toolBarRender={false}
      />

      <Modal
        title={editingGroup ? '编辑考勤组' : '新建考勤组'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ shiftType: 'FIXED' }}>
          <Form.Item name="name" label="考勤组名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={64} placeholder="如：后端部门考勤" />
          </Form.Item>
          <Form.Item name="shiftType" label="班次类型" rules={[{ required: true }]}>
            <Select options={SHIFT_TYPE_OPTIONS} />
          </Form.Item>

          {shiftType === 'FLEXIBLE' && (
            <>
              <Space style={{ display: 'flex' }} align="start">
                <Form.Item name="flexStartEarliest" label="弹性最早" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
                <Form.Item name="flexStartLatest" label="弹性最晚" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
                <Form.Item name="offDuty" label="下班时间" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
              </Space>
              <Space style={{ display: 'flex' }} align="start">
                <Form.Item name="lateThreshold" label="迟到阈值(min)" initialValue={15}>
                  <InputNumber min={0} max={120} />
                </Form.Item>
                <Form.Item name="earlyLeaveThreshold" label="早退阈值(min)" initialValue={15}>
                  <InputNumber min={0} max={120} />
                </Form.Item>
              </Space>
            </>
          )}

          {(shiftType === 'FIXED' || shiftType === 'SCHEDULE' || !shiftType) && (
            <>
              <Space style={{ display: 'flex' }} align="start" wrap>
                <Form.Item name="onDuty" label="上班时间" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
                <Form.Item name="offDuty" label="下班时间" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
                <Form.Item name="restStart" label="午休开始">
                  <TimePicker format="HH:mm" />
                </Form.Item>
                <Form.Item name="restEnd" label="午休结束">
                  <TimePicker format="HH:mm" />
                </Form.Item>
              </Space>
              <Space style={{ display: 'flex' }} align="start">
                <Form.Item name="lateThreshold" label="迟到阈值(min)" initialValue={15}>
                  <InputNumber min={0} max={120} />
                </Form.Item>
                <Form.Item name="earlyLeaveThreshold" label="早退阈值(min)" initialValue={15}>
                  <InputNumber min={0} max={120} />
                </Form.Item>
              </Space>
            </>
          )}

          <Form.Item
            name="departmentIds"
            label="适用部门"
            rules={[{ required: true, message: '请选择适用部门' }]}
          >
            <TreeSelect
              treeData={deptTree}
              fieldNames={{ label: 'name', value: 'id', children: 'children' }}
              treeCheckable
              showCheckedStrategy={TreeSelect.SHOW_PARENT}
              placeholder="选择适用部门"
              allowClear
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            name="employeeIds"
            label="适用员工"
            extra={
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {hasDept
                  ? '可选。不选 = 所选部门全部在职员工；选了 = 仅名单内员工（只能从该部门选）。'
                  : '请先选择适用部门'}
              </Typography.Text>
            }
          >
            <Select
              mode="multiple"
              placeholder={hasDept ? '不选则默认该部门全部员工' : '请先选择适用部门'}
              allowClear
              showSearch
              disabled={!hasDept}
              loading={empLoading}
              optionFilterProp="label"
              options={empOptions}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AttendanceGroupPage;

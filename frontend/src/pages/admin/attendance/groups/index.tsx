import React, { useRef, useState, useEffect } from 'react';
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

const AttendanceGroupPage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [modalOpen, setModalOpen] = React.useState(false);
  const [editingGroup, setEditingGroup] = React.useState<any>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = React.useState(false);
  const [deptTree, setDeptTree] = useState<any[]>([]);
  const [empList, setEmpList] = useState<any[]>([]);
  const shiftType = Form.useWatch('shiftType', form);

  useEffect(() => {
    getDeptTree().then((res: any) => setDeptTree(res.data || [])).catch(() => {});
    getEmployeeList({ page: 1, pageSize: 200 }).then((res: any) => {
      setEmpList(res.data?.list || []);
    }).catch((err) => {
      console.error('加载员工列表失败', err);
      message.warning('员工列表加载失败，无法选择适用员工');
    });
  }, []);

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
    form.setFieldsValue({ lateThreshold: 15, earlyLeaveThreshold: 15 });
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

      // 组装适用范围
      const applicableScope = {
        departmentIds: values.departmentIds || [],
        positionIds: [],
        employeeIds: values.employeeIds || [],
      };

      // 构造 API 请求数据（按班次类型组装）
      const payload: any = {
        name: values.name,
        shiftType: values.shiftType,
        restStart: values.restStart?.format('HH:mm') || null,
        restEnd: values.restEnd?.format('HH:mm') || null,
        applicableScope,
      };

      if (values.shiftType === 'FLEXIBLE') {
        // 弹性班次：只需要弹性范围 + 下班时间
        payload.flexibleRange = {
          earliest: values.flexStartEarliest?.format('HH:mm'),
          latest: values.flexStartLatest?.format('HH:mm'),
        };
        payload.offDuty = values.offDuty?.format('HH:mm');
        payload.lateThreshold = values.lateThreshold ?? 15;
        payload.earlyLeaveThreshold = values.earlyLeaveThreshold ?? 15;
      } else if (values.shiftType === 'SCHEDULE') {
        // 排班制当前等同于固定班次处理
        payload.onDuty = values.onDuty?.format('HH:mm');
        payload.offDuty = values.offDuty?.format('HH:mm');
      } else {
        // FIXED：标准固定班
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

  // ---------- 渲染 ----------
  return (
    <Card
      title="考勤组管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建考勤组
        </Button>
      }
    >
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

      {/* 新增/编辑弹窗 */}
      <Modal
        title={editingGroup ? '编辑考勤组' : '新建考勤组'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        width={720}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="考勤组名称" rules={[{ required: true, min: 2, max: 20 }]}>
            <Input placeholder="2-20 字符" />
          </Form.Item>
          <Form.Item name="shiftType" label="班次类型" rules={[{ required: true }]}>
            <Select options={SHIFT_TYPE_OPTIONS} placeholder="请选择班次类型" />
          </Form.Item>

          {/* ── 排班制提示 ── */}
          {shiftType === 'SCHEDULE' && (
            <Alert
              type="warning"
              showIcon
              message="排班制开发中，暂等同于固定班次处理"
              style={{ marginBottom: 16 }}
            />
          )}

          {/* ── FIXED 固定班次 / SCHEDULE 排班制 ── */}
          {(shiftType === 'FIXED' || shiftType === 'SCHEDULE') && (
            <>
              <Space style={{ display: 'flex' }} align="start">
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
              {shiftType === 'FIXED' && (
                <Space style={{ display: 'flex' }} align="start">
                  <Form.Item name="lateThreshold" label="迟到阈值(min)" initialValue={15}>
                    <InputNumber min={0} max={120} />
                  </Form.Item>
                  <Form.Item name="earlyLeaveThreshold" label="早退阈值(min)" initialValue={15}>
                    <InputNumber min={0} max={120} />
                  </Form.Item>
                </Space>
              )}
            </>
          )}

          {/* ── FLEXIBLE 弹性班次 ── */}
          {shiftType === 'FLEXIBLE' && (
            <>
              <Space style={{ display: 'flex' }} align="start">
                <Form.Item name="flexStartEarliest" label="弹性最早打卡" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
                <Form.Item name="flexStartLatest" label="弹性最晚打卡" rules={[{ required: true }]}>
                  <TimePicker format="HH:mm" />
                </Form.Item>
              </Space>
              <Space style={{ display: 'flex' }} align="start">
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

          {/* ── 适用范围 ── */}
          <Form.Item name="departmentIds" label="适用部门">
            <TreeSelect
              treeData={deptTree}
              fieldNames={{ label: 'name', value: 'id' }}
              treeCheckable
              showCheckedStrategy="SHOW_PARENT"
              placeholder="选择适用部门"
              allowClear
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item name="employeeIds" label="适用员工">
            <Select
              mode="multiple"
              placeholder="搜索并选择员工"
              allowClear
              showSearch
              filterOption={(input, option) =>
                (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
              }
              options={empList.map((e: any) => ({
                label: `${e.name} (${e.empNo || e.employeeId})`,
                value: e.employeeId,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AttendanceGroupPage;

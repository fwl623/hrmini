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
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
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

  useEffect(() => {
    getDeptTree().then((res: any) => setDeptTree(res.data || [])).catch(() => {});
    getEmployeeList({ page: 1, pageSize: 200 }).then((res: any) => {
      setEmpList(res.data?.list || []);
    }).catch(() => {});
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
    const scope = record.applicableScope || { departmentIds: [], positionIds: [], employeeIds: [] };
    form.setFieldsValue({
      name: record.name,
      shiftType: record.shiftType,
      onDuty: record.workStartTime ? dayjs(record.workStartTime, 'HH:mm') : undefined,
      offDuty: record.workEndTime ? dayjs(record.workEndTime, 'HH:mm') : undefined,
      restStart: record.lunchStartTime ? dayjs(record.lunchStartTime, 'HH:mm') : undefined,
      restEnd: record.lunchEndTime ? dayjs(record.lunchEndTime, 'HH:mm') : undefined,
      lateThreshold: record.lateThresholdMinutes ?? 15,
      earlyLeaveThreshold: record.earlyLeaveThresholdMinutes ?? 15,
      departmentIds: scope.departmentIds || [],
      employeeIds: scope.employeeIds || [],
    });
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

      // 构造 API 请求数据（字段名映射：表单 → API 契约）
      const payload: any = {
        name: values.name,
        shiftType: values.shiftType,
        onDuty: values.onDuty?.format('HH:mm'),
        offDuty: values.offDuty?.format('HH:mm'),
        restStart: values.restStart?.format('HH:mm') || null,
        restEnd: values.restEnd?.format('HH:mm') || null,
        lateThreshold: values.lateThreshold ?? 15,
        earlyLeaveThreshold: values.earlyLeaveThreshold ?? 15,
        applicableScope,
      };

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
            <Select options={SHIFT_TYPE_OPTIONS} />
          </Form.Item>
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
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="lateThreshold" label="迟到阈值(min)" initialValue={15}>
              <InputNumber min={0} max={120} />
            </Form.Item>
            <Form.Item name="earlyLeaveThreshold" label="早退阈值(min)" initialValue={15}>
              <InputNumber min={0} max={120} />
            </Form.Item>
          </Space>
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

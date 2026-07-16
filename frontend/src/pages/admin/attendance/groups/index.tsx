import React, { useState, useRef } from 'react';
import {
  Card,
  Button,
  Modal,
  Form,
  Input,
  Select,
  TimePicker,
  InputNumber,
  Space,
  Popconfirm,
  Tag,
  message,
  Typography,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ActionType } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import dayjs from 'dayjs';

/** Mock 考勤组数据（后端未就绪时用于开发和演示） */
const MOCK_GROUPS = [
  {
    id: 1,
    name: '标准工时',
    shiftType: 'FIXED',            // 固定班次
    workStartTime: '09:00',        // 上班时间
    workEndTime: '18:00',          // 下班时间
    lunchStartTime: '12:00',       // 午休开始
    lunchEndTime: '13:00',         // 午休结束
    lateThresholdMinutes: 15,      // 迟到容忍分钟数
    earlyLeaveThresholdMinutes: 15, // 早退容忍分钟数
    // 适用范围：按部门/岗位/员工三个维度控制
    applicableScope: { departmentIds: [1, 2], positionIds: [], employeeIds: [] },
  },
  {
    id: 2,
    name: '弹性工时',
    shiftType: 'FLEXIBLE',         // 弹性班次
    workStartTime: '08:30',
    workEndTime: '17:30',
    lunchStartTime: '12:00',
    lunchEndTime: '13:00',
    lateThresholdMinutes: 30,
    earlyLeaveThresholdMinutes: 30,
    applicableScope: { departmentIds: [3], positionIds: [1], employeeIds: [] },
  },
];

/** 班次类型下拉选项 */
const SHIFT_TYPE_OPTIONS = [
  { label: '固定班次', value: 'FIXED' },
  { label: '弹性班次', value: 'FLEXIBLE' },
  { label: '排班制', value: 'SCHEDULE' },
];

const AttendanceGroupPage: React.FC = () => {
  // ---------- 页面状态 ----------
  const [data, setData] = useState(MOCK_GROUPS);     // 考勤组列表数据
  const [modalOpen, setModalOpen] = useState(false);  // 弹窗是否打开
  const [editingGroup, setEditingGroup] = useState<any>(null); // 正在编辑的考勤组（null 表示新增）
  const [form] = Form.useForm();                     // 弹窗内表单实例
  const actionRef = useRef<ActionType>();             // ProTable 的引用（用于刷新等操作）

  // ---------- 表格列定义 ----------
  const columns: any[] = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '考勤组名称', dataIndex: 'name', width: 150 },
    {
      title: '班次类型',
      dataIndex: 'shiftType',
      width: 100,
      // render 将枚举值转为中文 Tag 展示
      render: (val: string) => {
        const map: Record<string, string> = { FIXED: '固定', FLEXIBLE: '弹性', SCHEDULE: '排班' };
        return <Tag>{map[val] || val}</Tag>;
      },
    },
    { title: '上班时间', dataIndex: 'workStartTime', width: 100 },
    { title: '下班时间', dataIndex: 'workEndTime', width: 100 },
    // 午休时间段：若未设置则显示短横线
    { title: '午休', render: (_, r) => `${r.lunchStartTime ?? '-'} ~ ${r.lunchEndTime ?? '-'}`, width: 140 },
    { title: '迟到阈值(min)', dataIndex: 'lateThresholdMinutes', width: 120 },
    { title: '早退阈值(min)', dataIndex: 'earlyLeaveThresholdMinutes', width: 120 },
    {
      title: '操作',
      width: 120,
      // 每行提供"编辑"和"删除"两个操作按钮
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          {/* 删除前二次确认，避免误删 */}
          <Popconfirm
            title="确认删除？有关联员工将拒绝"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ---------- 事件处理函数 ----------

  /** 新增：清空表单，打开弹窗 */
  const handleAdd = () => {
    setEditingGroup(null);       // 标记为新增模式
    form.resetFields();          // 重置表单字段
    setModalOpen(true);
  };

  /** 编辑：填充已有数据到表单，打开弹窗 */
  const handleEdit = (record: any) => {
    setEditingGroup(record);
    // 将字符串时间转为 dayjs 对象，TimePicker 才能正确回显
    form.setFieldsValue({
      ...record,
      workStartTime: dayjs(record.workStartTime, 'HH:mm'),
      workEndTime: dayjs(record.workEndTime, 'HH:mm'),
      lunchStartTime: record.lunchStartTime ? dayjs(record.lunchStartTime, 'HH:mm') : undefined,
      lunchEndTime: record.lunchEndTime ? dayjs(record.lunchEndTime, 'HH:mm') : undefined,
    });
    setModalOpen(true);
  };

  /** 删除：从列表中移除指定 ID 的记录 */
  const handleDelete = (id: number) => {
    setData((prev) => prev.filter((item) => item.id !== id));
    message.success('已删除');
  };

  /** 保存（新增/编辑统一入口）：校验表单 → 构造提交数据 → 更新列表 */
  const handleSave = async () => {
    const values = await form.validateFields();                     // 触发校验
    // 将 dayjs 时间格式化为 "HH:mm" 字符串再存入
    const payload = {
      ...values,
      workStartTime: values.workStartTime?.format('HH:mm'),
      workEndTime: values.workEndTime?.format('HH:mm'),
      lunchStartTime: values.lunchStartTime?.format('HH:mm') || null,
      lunchEndTime: values.lunchEndTime?.format('HH:mm') || null,
    };

    if (editingGroup) {
      // 编辑模式：更新已有记录
      setData((prev) =>
        prev.map((item) => (item.id === editingGroup.id ? { ...item, ...payload } : item)),
      );
      message.success('更新成功');
    } else {
      // 新增模式：追加新记录（使用当前时间戳作为临时 ID）
      setData((prev) => [...prev, { id: Date.now(), ...payload }]);
      message.success('创建成功');
    }
    setModalOpen(false); // 关闭弹窗
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
      {/* 表格区域：使用 ProTable 展示考勤组列表 */}
      <ProTable<any>
        rowKey="id"
        columns={columns}
        dataSource={data}
        pagination={false}
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
        width={720}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="考勤组名称" rules={[{ required: true, min: 2, max: 20 }]}>
            <Input placeholder="2-20 字符" />
          </Form.Item>
          <Form.Item name="shiftType" label="班次类型" rules={[{ required: true }]}>
            <Select options={SHIFT_TYPE_OPTIONS} />
          </Form.Item>
          {/* 时间设置行：上班、下班、午休开始/结束 */}
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="workStartTime" label="上班时间" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" />
            </Form.Item>
            <Form.Item name="workEndTime" label="下班时间" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" />
            </Form.Item>
            <Form.Item name="lunchStartTime" label="午休开始">
              <TimePicker format="HH:mm" />
            </Form.Item>
            <Form.Item name="lunchEndTime" label="午休结束">
              <TimePicker format="HH:mm" />
            </Form.Item>
          </Space>
          {/* 迟到/早退阈值设置行 */}
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="lateThresholdMinutes" label="迟到阈值(min)" initialValue={15}>
              <InputNumber min={0} max={120} />
            </Form.Item>
            <Form.Item name="earlyLeaveThresholdMinutes" label="早退阈值(min)" initialValue={15}>
              <InputNumber min={0} max={120} />
            </Form.Item>
          </Space>
          <Form.Item name="applicableScope" label="适用范围">
            <Input.TextArea
              placeholder='{"departmentIds": [1,2], "positionIds": [], "employeeIds": []}'
              rows={2}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default AttendanceGroupPage;

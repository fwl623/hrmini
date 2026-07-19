import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useAccess } from '@umijs/max';
import { useCallback, useEffect, useState } from 'react';
import {
  createResignation,
  fetchResignationStats,
  fetchResignations,
  type ResignationItem,
  type ResignationStats,
} from '@/services/lifecycle';
import { getEmployeeList } from '@/services/employee';

/**
 * HR/管理员离职管理：仅发起正式离职（PRD §5.4）
 * 工作交接人由部门负责人在审批中心确认，本页不采集。
 */
export default function AdminResignationPage() {
  const access = useAccess();
  const [resignations, setResignations] = useState<ResignationItem[]>([]);
  const [stats, setStats] = useState<ResignationStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [empOptions, setEmpOptions] = useState<{ label: string; value: number }[]>([]);
  const [empLoading, setEmpLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, st] = await Promise.all([
        fetchResignations({ page: 1, pageSize: 50 }),
        fetchResignationStats(),
      ]);
      setResignations(list?.list ?? []);
      setStats(st ?? null);
    } catch (e) {
      message.error((e as Error)?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const searchEmployees = useCallback(async (keyword: string) => {
    if (!keyword || keyword.trim().length < 1) {
      setEmpOptions([]);
      return;
    }
    setEmpLoading(true);
    try {
      const res = await getEmployeeList({ keyword: keyword.trim(), page: 1, pageSize: 20 });
      const list = res.data?.list ?? [];
      setEmpOptions(
        list.map((e) => ({
          label: `${e.name} · ${e.department || '未分部门'} · ${e.empNo || '-'}`,
          value: e.employeeId,
        })),
      );
    } catch {
      setEmpOptions([]);
    } finally {
      setEmpLoading(false);
    }
  }, []);

  const resignCols: ColumnsType<ResignationItem> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '员工', dataIndex: 'employeeName' },
    { title: '离职日', dataIndex: 'resignationDate', width: 120 },
    { title: '原因', dataIndex: 'reasonCategory', width: 100 },
    { title: '类型', dataIndex: 'resignationType', width: 120 },
    { title: '状态', dataIndex: 'status', width: 140 },
    { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          离职管理
        </Typography.Title>
        {access.canManageResignation && (
          <Button
            type="primary"
            danger
            onClick={() => {
              form.resetFields();
              setEmpOptions([]);
              form.setFieldsValue({
                resignationDate: dayjs().add(14, 'day'),
                reasonCategory: 'VOLUNTARY',
                resignationType: 'resignation',
              });
              setOpen(true);
            }}
          >
            发起离职申请
          </Button>
        )}
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        正式离职审批中 {stats?.approving ?? '-'} · 待离职 {stats?.pendingResign ?? '-'} · 本月已离职{' '}
        {stats?.resignedThisMonth ?? '-'}
        <br />
        员工线下协商后由 HR 发起；部门负责人在审批中心确认交接安排后同意，再交 HR 终审。
      </Typography.Paragraph>
      <Card loading={loading}>
        <Table rowKey="id" columns={resignCols} dataSource={resignations} pagination={false} />
      </Card>

      <Modal
        title="发起离职申请"
        open={open}
        okText="提交申请"
        onCancel={() => setOpen(false)}
        onOk={async () => {
          try {
            const v = await form.validateFields();
            await createResignation({
              employeeId: v.employeeId,
              resignationDate: v.resignationDate.format('YYYY-MM-DD'),
              reasonCategory: v.reasonCategory,
              resignationType: v.resignationType,
              reasonDetail: v.reasonDetail,
            });
            message.success('已发起正式离职，等待部门负责人在审批中心确认交接并审批');
            setOpen(false);
            load();
          } catch (e) {
            if ((e as { errorFields?: unknown })?.errorFields) return;
            message.error((e as Error)?.message || '提交失败');
          }
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="employeeId"
            label="离职员工"
            rules={[{ required: true, message: '请选择离职员工' }]}
            extra="按姓名 / 部门 / 工号搜索"
          >
            <Select
              showSearch
              placeholder="输入姓名、部门或工号搜索"
              filterOption={false}
              notFoundContent={empLoading ? '搜索中…' : '无匹配员工'}
              loading={empLoading}
              onSearch={searchEmployees}
              options={empOptions}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            name="resignationDate"
            label="离职日期"
            rules={[
              { required: true, message: '请选择离职日期' },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve();
                  if (value.startOf('day').isBefore(dayjs().startOf('day'))) {
                    return Promise.reject(new Error('离职日须 ≥ 今天'));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <DatePicker style={{ width: '100%' }} disabledDate={(d) => !!d && d < dayjs().startOf('day')} />
          </Form.Item>
          <Form.Item name="reasonCategory" label="离职原因" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'VOLUNTARY', label: '主动' },
                { value: 'INVOLUNTARY', label: '被动' },
                { value: 'NEGOTIATED', label: '协商' },
              ]}
            />
          </Form.Item>
          <Form.Item name="resignationType" label="离职类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'resignation', label: '辞职' },
                { value: 'dismissal', label: '辞退' },
                { value: 'contract_expiry', label: '合同到期不续签' },
                { value: 'other', label: '其他' },
              ]}
            />
          </Form.Item>
          <Form.Item name="reasonDetail" label="详细说明" rules={[{ required: true, message: '请填写详细说明' }]}>
            <Input.TextArea rows={3} placeholder="详细离职说明" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}

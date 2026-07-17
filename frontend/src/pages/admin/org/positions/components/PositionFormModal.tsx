import { CheckOutlined } from '@ant-design/icons';
import { Col, Form, Input, Modal, Row, Select, Switch, TreeSelect, Typography } from 'antd';
import React, { useEffect, useMemo } from 'react';
import type {
  CreatePositionParams,
  DeptTreeNode,
  PositionSequence,
  PositionVO,
} from '@/services/org';
import { SEQUENCE_META, SEQUENCE_RANK_MAP, gradeOptionsOf } from '../constants';

const { TextArea } = Input;

export type PositionFormMode = 'create' | 'edit';

export interface PositionFormModalProps {
  open: boolean;
  mode: PositionFormMode;
  loading?: boolean;
  current?: PositionVO | null;
  tree: DeptTreeNode[];
  onCancel: () => void;
  onSubmit: (values: CreatePositionParams) => void;
}

type FormValues = Omit<CreatePositionParams, 'departmentId'> & {
  departmentIdSelect?: number | null;
};

function buildDeptTreeData(
  nodes: DeptTreeNode[],
): { value: number; title: string; children?: ReturnType<typeof buildDeptTreeData> }[] {
  return nodes.map((n) => ({
    value: n.id,
    title: `${n.name}（${n.code}）`,
    children: n.children?.length ? buildDeptTreeData(n.children) : undefined,
  }));
}

function buildDeptSelectData(tree: DeptTreeNode[]) {
  return [
    { value: 0, title: '全公司通用' },
    ...buildDeptTreeData(tree),
  ];
}

/** 受控序列卡片组，对接 Form.Item value/onChange */
const SequenceSelector: React.FC<{
  value?: PositionSequence;
  onChange?: (v: PositionSequence) => void;
}> = ({ value, onChange }) => (
  <Row gutter={12}>
    {(['M', 'P', 'S'] as PositionSequence[]).map((code) => {
      const meta = SEQUENCE_META[code];
      const selected = value === code;
      return (
        <Col span={8} key={code}>
          <div
            role="button"
            tabIndex={0}
            onClick={() => onChange?.(code)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onChange?.(code);
              }
            }}
            style={{
              position: 'relative',
              padding: '14px 12px',
              borderRadius: 8,
              border: selected ? `2px solid ${meta.color}` : '1px solid #f0f0f0',
              background: selected ? meta.bgSoft : '#fafafa',
              cursor: 'pointer',
              textAlign: 'center',
              transition: 'border-color 0.15s, background 0.15s',
            }}
          >
            {selected && (
              <CheckOutlined
                style={{
                  position: 'absolute',
                  top: 8,
                  right: 8,
                  color: meta.color,
                  fontSize: 12,
                }}
              />
            )}
            <div
              style={{
                width: 36,
                height: 36,
                margin: '0 auto 8px',
                borderRadius: '50%',
                background: meta.color,
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: 16,
              }}
            >
              {code}
            </div>
            <Typography.Text strong style={{ fontSize: 13 }}>
              {meta.shortLabel}
            </Typography.Text>
            <div>
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                {meta.gradeRange}
              </Typography.Text>
            </div>
          </div>
        </Col>
      );
    })}
  </Row>
);

const PositionFormModal: React.FC<PositionFormModalProps> = ({
  open,
  mode,
  loading,
  current,
  tree,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm<FormValues>();
  const sequence = Form.useWatch('sequenceCode', form) as PositionSequence | undefined;

  const deptTreeData = useMemo(() => buildDeptSelectData(tree), [tree]);
  const gradeOptions = useMemo(
    () => gradeOptionsOf(sequence).map((g) => ({ value: g, label: g })),
    [sequence],
  );

  useEffect(() => {
    if (!open) return;
    if (mode === 'edit' && current) {
      form.setFieldsValue({
        name: current.name,
        sequenceCode: current.sequenceCode,
        departmentIdSelect: current.departmentId ?? 0,
        gradeMin: current.gradeMin,
        gradeMax: current.gradeMax,
        defaultProbationMonths: current.defaultProbationMonths ?? 6,
        isStandard: current.isStandard ?? true,
        description: current.description ?? undefined,
      });
    } else {
      form.setFieldsValue({
        name: undefined,
        sequenceCode: undefined,
        departmentIdSelect: 0,
        gradeMin: undefined,
        gradeMax: undefined,
        defaultProbationMonths: 6,
        isStandard: true,
        description: undefined,
      });
    }
  }, [open, mode, current, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const ranks = SEQUENCE_RANK_MAP[values.sequenceCode];
    const minIdx = ranks.indexOf(values.gradeMin);
    const maxIdx = ranks.indexOf(values.gradeMax);
    if (minIdx < 0 || maxIdx < 0) {
      form.setFields([
        { name: 'gradeMin', errors: ['请选择合法职级'] },
        { name: 'gradeMax', errors: ['请选择合法职级'] },
      ]);
      return;
    }
    if (minIdx > maxIdx) {
      form.setFields([{ name: 'gradeMax', errors: ['最大职级不能低于最小职级'] }]);
      return;
    }

    const deptSelect = values.departmentIdSelect;
    const payload: CreatePositionParams = {
      name: values.name.trim(),
      sequenceCode: values.sequenceCode,
      departmentId: !deptSelect || deptSelect === 0 ? null : deptSelect,
      gradeMin: values.gradeMin,
      gradeMax: values.gradeMax,
      defaultProbationMonths: values.defaultProbationMonths,
      isStandard: values.isStandard ?? true,
      description: values.description?.trim() || undefined,
    };
    onSubmit(payload);
  };

  return (
    <Modal
      title={mode === 'edit' ? '编辑职位' : '新增职位'}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={loading}
      destroyOnClose
      okText="保存"
      width={560}
    >
      <Form
        form={form}
        layout="vertical"
        preserve={false}
        onValuesChange={(changed) => {
          if ('sequenceCode' in changed) {
            form.setFieldsValue({ gradeMin: undefined, gradeMax: undefined });
          }
        }}
      >
        <Form.Item
          name="name"
          label="职位名称"
          rules={[
            { required: true, message: '请输入职位名称' },
            { max: 64, message: '不超过 64 字符' },
          ]}
        >
          <Input placeholder="如: Java开发工程师" maxLength={64} />
        </Form.Item>

        <Form.Item
          name="sequenceCode"
          label="职位序列"
          rules={[{ required: true, message: '请选择职位序列' }]}
        >
          <SequenceSelector />
        </Form.Item>

        <Form.Item
          name="departmentIdSelect"
          label="所属部门"
          initialValue={0}
          normalize={(v) => (v == null ? 0 : v)}
        >
          <TreeSelect
            placeholder="全公司通用"
            treeData={deptTreeData}
            treeDefaultExpandAll
            showSearch
            treeNodeFilterProp="title"
            style={{ width: '100%' }}
            allowClear
          />
        </Form.Item>

        <Form.Item label="职级范围" required style={{ marginBottom: 0 }}>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="gradeMin"
                rules={[{ required: true, message: '请选择最小职级' }]}
              >
                <Select placeholder="请选择" options={gradeOptions} disabled={!sequence} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="gradeMax"
                rules={[{ required: true, message: '请选择最大职级' }]}
              >
                <Select placeholder="请选择" options={gradeOptions} disabled={!sequence} />
              </Form.Item>
            </Col>
          </Row>
        </Form.Item>

        <Form.Item
          name="defaultProbationMonths"
          label="默认试用期"
          rules={[{ required: true, message: '请选择默认试用期' }]}
        >
          <Select
            options={[
              { value: 1, label: '1个月' },
              { value: 2, label: '2个月' },
              { value: 3, label: '3个月' },
              { value: 4, label: '4个月' },
              { value: 5, label: '5个月' },
              { value: 6, label: '6个月' },
              { value: 7, label: '7个月' },
              { value: 8, label: '8个月' },
              { value: 9, label: '9个月' },
              { value: 10, label: '10个月' },
              { value: 11, label: '11个月' },
              { value: 12, label: '12个月' },
            ]}
          />
        </Form.Item>

        <Form.Item
          name="isStandard"
          label="是否标准职位"
          valuePropName="checked"
          extra="非标准职位入职时可能触发 HR 二审"
          initialValue={true}
        >
          <Switch checkedChildren="标准" unCheckedChildren="非标准" />
        </Form.Item>

        <Form.Item
          name="description"
          label="职位描述"
          rules={[{ max: 512, message: '不超过 512 字符' }]}
        >
          <TextArea rows={3} placeholder="请输入岗位职责说明..." maxLength={512} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default PositionFormModal;

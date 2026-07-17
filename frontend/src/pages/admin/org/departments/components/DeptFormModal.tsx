import { Form, Input, InputNumber, Modal, Select, TreeSelect } from 'antd';
import React, { useEffect, useMemo } from 'react';
import type { CreateDeptParams, DeptTreeNode } from '@/services/org';

const { TextArea } = Input;

export type DeptFormMode = 'createRoot' | 'createChild' | 'edit';

export interface DeptFormModalProps {
  open: boolean;
  mode: DeptFormMode;
  loading?: boolean;
  /** 编辑或新增子部门时的当前部门 */
  current?: DeptTreeNode | null;
  tree: DeptTreeNode[];
  onCancel: () => void;
  onSubmit: (values: CreateDeptParams) => void;
}

function buildParentTreeData(
  nodes: DeptTreeNode[],
  excludeIds: Set<number>,
): { value: number; title: string; disabled?: boolean; children?: ReturnType<typeof buildParentTreeData> }[] {
  return nodes.map((n) => {
    const disabled = excludeIds.has(n.id);
    return {
      value: n.id,
      title: `${n.name}（${n.code}）`,
      disabled,
      children: n.children?.length
        ? buildParentTreeData(n.children, excludeIds)
        : undefined,
    };
  });
}

/** 收集某节点自身及全部子孙 id */
export function collectSelfAndDescendantIds(node: DeptTreeNode): Set<number> {
  const ids = new Set<number>();
  const walk = (n: DeptTreeNode) => {
    ids.add(n.id);
    n.children?.forEach(walk);
  };
  walk(node);
  return ids;
}

const DeptFormModal: React.FC<DeptFormModalProps> = ({
  open,
  mode,
  loading,
  current,
  tree,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm<CreateDeptParams>();

  const title =
    mode === 'createRoot' ? '新增根部门' : mode === 'createChild' ? '新增子部门' : '编辑部门';

  const excludeIds = useMemo(() => {
    if (mode === 'edit' && current) {
      return collectSelfAndDescendantIds(current);
    }
    return new Set<number>();
  }, [mode, current]);

  const parentTreeData = useMemo(() => buildParentTreeData(tree, excludeIds), [tree, excludeIds]);

  /** 有在职人数时禁止改编码（与后端 30006 对齐） */
  const deptCodeLocked = mode === 'edit' && (current?.headcount ?? 0) > 0;

  useEffect(() => {
    if (!open) return;
    if (mode === 'edit' && current) {
      form.setFieldsValue({
        name: current.name,
        deptCode: current.code,
        parentId: current.parentId ?? undefined,
        headEmployeeId: current.headEmployeeId ?? undefined,
        sortOrder: current.sortOrder ?? 0,
        description: current.description ?? undefined,
      });
    } else if (mode === 'createChild' && current) {
      form.setFieldsValue({
        name: undefined,
        deptCode: undefined,
        parentId: current.id,
        headEmployeeId: undefined,
        sortOrder: 0,
        description: undefined,
      });
    } else {
      form.setFieldsValue({
        name: undefined,
        deptCode: undefined,
        parentId: undefined,
        headEmployeeId: undefined,
        sortOrder: 0,
        description: undefined,
      });
    }
  }, [open, mode, current, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: CreateDeptParams = {
      name: values.name.trim(),
      deptCode: values.deptCode.trim().toUpperCase(),
      parentId: mode === 'createRoot' ? null : values.parentId ?? null,
      headEmployeeId: values.headEmployeeId ?? null,
      sortOrder: values.sortOrder ?? 0,
      description: values.description?.trim() || undefined,
    };
    onSubmit(payload);
  };

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={loading}
      destroyOnClose
      okText="保存"
      width={520}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="name"
          label="部门名称"
          rules={[
            { required: true, message: '请输入部门名称' },
            { max: 64, message: '不超过 64 字符' },
          ]}
        >
          <Input placeholder="如：技术中心" maxLength={64} />
        </Form.Item>
        <Form.Item
          name="deptCode"
          label="部门编码"
          extra={
            deptCodeLocked
              ? '部门下仍有在职员工，编码不可修改（与工号规则绑定）'
              : '2 位字母或数字，用于工号生成，全公司唯一'
          }
          rules={[
            { required: true, message: '请输入部门编码' },
            { pattern: /^[A-Za-z0-9]{2}$/, message: '须为 2 位字母或数字' },
          ]}
        >
          <Input
            placeholder="如：JS"
            maxLength={2}
            disabled={deptCodeLocked}
            style={{ textTransform: 'uppercase' }}
          />
        </Form.Item>
        {mode !== 'createRoot' && (
          <Form.Item
            name="parentId"
            label="上级部门"
            rules={mode === 'createChild' ? [{ required: true, message: '请选择上级部门' }] : undefined}
            extra={mode === 'edit' ? '清空则变为根部门；不可选自身及子孙' : undefined}
          >
            {mode === 'createChild' ? (
              <Select
                disabled
                options={
                  current
                    ? [{ value: current.id, label: `${current.name}（${current.code}）` }]
                    : []
                }
              />
            ) : (
              <TreeSelect
                allowClear
                placeholder="空表示根部门"
                treeData={parentTreeData}
                treeDefaultExpandAll
                showSearch
                treeNodeFilterProp="title"
                style={{ width: '100%' }}
              />
            )}
          </Form.Item>
        )}
        <Form.Item
          name="headEmployeeId"
          label="部门负责人"
          extra="填写在职员工 ID（employeeId）；可选。人员搜索选择器待员工模块提供轻量检索接口后再接"
        >
          <InputNumber placeholder="员工 ID" style={{ width: '100%' }} min={1} precision={0} />
        </Form.Item>
        <Form.Item
          name="sortOrder"
          label="排序序号"
          rules={[{ required: true, message: '请输入排序序号' }]}
        >
          <InputNumber style={{ width: '100%' }} min={0} precision={0} />
        </Form.Item>
        <Form.Item
          name="description"
          label="部门描述"
          rules={[{ max: 256, message: '不超过 256 字符' }]}
        >
          <TextArea rows={3} placeholder="职能说明" maxLength={256} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default DeptFormModal;

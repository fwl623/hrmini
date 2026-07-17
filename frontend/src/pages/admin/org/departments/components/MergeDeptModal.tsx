import { Alert, Form, Modal, TreeSelect } from 'antd';
import React, { useEffect, useMemo } from 'react';
import type { DeptTreeNode } from '@/services/org';
import { collectSelfAndDescendantIds } from './DeptFormModal';

export interface MergeDeptModalProps {
  open: boolean;
  loading?: boolean;
  source: DeptTreeNode | null;
  tree: DeptTreeNode[];
  onCancel: () => void;
  onSubmit: (targetDepartmentId: number) => void;
}

function buildTargetTree(
  nodes: DeptTreeNode[],
  excludeIds: Set<number>,
): { value: number; title: string; disabled?: boolean; children?: ReturnType<typeof buildTargetTree> }[] {
  return nodes
    .filter((n) => !excludeIds.has(n.id) || (n.children && n.children.length > 0))
    .map((n) => {
      const disabled = excludeIds.has(n.id);
      const children = n.children?.length ? buildTargetTree(n.children, excludeIds) : undefined;
      return {
        value: n.id,
        title: `${n.name}（${n.code}）`,
        disabled,
        children,
      };
    })
    .filter((n) => !n.disabled || (n.children && n.children.length > 0));
}

const MergeDeptModal: React.FC<MergeDeptModalProps> = ({
  open,
  loading,
  source,
  tree,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm<{ targetDepartmentId: number }>();

  const excludeIds = useMemo(
    () => (source ? collectSelfAndDescendantIds(source) : new Set<number>()),
    [source],
  );

  const targetTreeData = useMemo(() => buildTargetTree(tree, excludeIds), [tree, excludeIds]);

  useEffect(() => {
    if (open) {
      form.resetFields();
    }
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    onSubmit(values.targetDepartmentId);
  };

  return (
    <Modal
      title="合并部门"
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={loading}
      destroyOnClose
      okText="确认合并"
      okButtonProps={{ danger: true }}
      width={480}
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message={`将「${source?.name ?? ''}」合并到目标部门`}
        description="源部门下的员工与子部门将转移到目标部门，源部门随后逻辑删除。此操作不可轻易撤销，请确认。"
      />
      <Form form={form} layout="vertical">
        <Form.Item
          name="targetDepartmentId"
          label="目标部门"
          rules={[{ required: true, message: '请选择目标部门' }]}
        >
          <TreeSelect
            placeholder="选择合并目标（不可选自身及子孙）"
            treeData={targetTreeData}
            treeDefaultExpandAll
            showSearch
            treeNodeFilterProp="title"
            style={{ width: '100%' }}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default MergeDeptModal;

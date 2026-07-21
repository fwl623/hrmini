import { Form, Input, InputNumber, Modal, Select, TreeSelect, message } from 'antd';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { CreateDeptParams, DeptTreeNode } from '@/services/org';
import { getEmployeeDetail, getEmployeeList, type EmployeeItem } from '@/services/employee';

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

type HeadOption = { value: number; label: string };

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

function toHeadOption(e: Pick<EmployeeItem, 'employeeId' | 'name' | 'empNo'>): HeadOption {
  return {
    value: e.employeeId,
    label: `${e.name}${e.empNo ? `（${e.empNo}）` : ''}`,
  };
}

function findDeptInTree(nodes: DeptTreeNode[], id: number): DeptTreeNode | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    if (n.children?.length) {
      const found = findDeptInTree(n.children, id);
      if (found) return found;
    }
  }
  return null;
}

/** 沿父链找第一个有负责人的部门（与入职审批上溯一致） */
function resolveAncestorHead(
  deptId: number | null | undefined,
  tree: DeptTreeNode[],
): { employeeId: number; name?: string } | null {
  if (deptId == null) return null;
  const flat = new Map<number, DeptTreeNode>();
  const walk = (list: DeptTreeNode[]) => {
    for (const n of list) {
      flat.set(n.id, n);
      if (n.children?.length) walk(n.children);
    }
  };
  walk(tree);
  const visited = new Set<number>();
  let cur: number | null | undefined = deptId;
  while (cur != null && !visited.has(cur)) {
    visited.add(cur);
    const node = flat.get(cur);
    if (!node) break;
    if (node.headEmployeeId) {
      return { employeeId: node.headEmployeeId, name: node.manager ?? undefined };
    }
    cur = node.parentId;
  }
  return null;
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
  const [headOptions, setHeadOptions] = useState<HeadOption[]>([]);
  const [headSearching, setHeadSearching] = useState(false);
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  const ensureHeadOption = async (employeeId?: number | null, fallbackName?: string) => {
    if (!employeeId) return;
    setHeadOptions((prev) => {
      if (prev.some((o) => o.value === employeeId)) return prev;
      return [
        ...prev,
        {
          value: employeeId,
          label: fallbackName ? `${fallbackName}` : `员工#${employeeId}`,
        },
      ];
    });
    try {
      const res = await getEmployeeDetail(employeeId);
      const d = res.data;
      if (!d) return;
      const opt = toHeadOption({
        employeeId: d.employeeId,
        name: d.name,
        empNo: d.empNo,
      });
      setHeadOptions((prev) => {
        const others = prev.filter((o) => o.value !== employeeId);
        return [opt, ...others];
      });
    } catch {
      // 无详情权限时保留姓名兜底
    }
  };

  const searchHeads = (keyword?: string) => {
    if (searchTimer.current) clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(async () => {
      setHeadSearching(true);
      try {
        const res = await getEmployeeList({
          keyword: keyword?.trim() || undefined,
          employmentStatus: 'probation,regular',
          page: 1,
          pageSize: 30,
        });
        const list = (res.data?.list ?? []) as EmployeeItem[];
        setHeadOptions((prev) => {
          const selected = form.getFieldValue('headEmployeeId') as number | undefined;
          const map = new Map<number, HeadOption>();
          for (const e of list) {
            map.set(e.employeeId, toHeadOption(e));
          }
          // 保留当前已选项，避免搜索后选中项消失
          if (selected != null) {
            const keep = prev.find((o) => o.value === selected);
            if (keep) map.set(keep.value, keep);
          }
          return [...map.values()];
        });
      } catch {
        message.warning('员工搜索失败，请确认有花名册查看权限');
      } finally {
        setHeadSearching(false);
      }
    }, 300);
  };

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
      void ensureHeadOption(current.headEmployeeId, current.manager ?? undefined);
      searchHeads();
    } else if (mode === 'createChild' && current) {
      // 空部门可先继承上级负责人，避免入职审批无人可派；仍可改选其他人工号
      const inherited =
        current.headEmployeeId != null
          ? { employeeId: current.headEmployeeId, name: current.manager ?? undefined }
          : resolveAncestorHead(current.id, tree);
      form.setFieldsValue({
        name: undefined,
        deptCode: undefined,
        parentId: current.id,
        headEmployeeId: inherited?.employeeId,
        sortOrder: 0,
        description: undefined,
      });
      void ensureHeadOption(inherited?.employeeId, inherited?.name);
      searchHeads();
    } else {
      form.setFieldsValue({
        name: undefined,
        deptCode: undefined,
        parentId: undefined,
        headEmployeeId: undefined,
        sortOrder: 0,
        description: undefined,
      });
      setHeadOptions([]);
      searchHeads();
    }
    return () => {
      if (searchTimer.current) clearTimeout(searchTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅弹窗打开时初始化
  }, [open, mode, current, form, tree]);

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
      cancelText="取消"
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
                onChange={(parentId) => {
                  // 编辑时改挂上级且本部门尚无负责人：提示可继承新上级负责人
                  const head = form.getFieldValue('headEmployeeId');
                  if (head != null || parentId == null) return;
                  const parent = findDeptInTree(tree, parentId as number);
                  const inherited =
                    parent?.headEmployeeId != null
                      ? { employeeId: parent.headEmployeeId, name: parent.manager ?? undefined }
                      : resolveAncestorHead(parentId as number, tree);
                  if (inherited) {
                    form.setFieldValue('headEmployeeId', inherited.employeeId);
                    void ensureHeadOption(inherited.employeeId, inherited.name);
                  }
                }}
              />
            )}
          </Form.Item>
        )}
        <Form.Item
          name="headEmployeeId"
          label="部门负责人"
          extra="按姓名/工号搜索，可选任意在职员工（不必属于本部门）。指定后将自动授予「部门主管」角色以便审批；卸任且不再负责其他部门时回收自动授予的角色。"
        >
          <Select
            allowClear
            showSearch
            filterOption={false}
            loading={headSearching}
            placeholder="输入工号或姓名搜索"
            options={headOptions}
            onSearch={searchHeads}
            onDropdownVisibleChange={(visible) => {
              if (visible && headOptions.length === 0) searchHeads();
            }}
          />
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

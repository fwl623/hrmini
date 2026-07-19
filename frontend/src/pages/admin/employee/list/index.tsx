/**
 * 员工花名册列表页
 *
 * PRD §4.2.2 高级搜索：关键词 / 部门树多选 / 职位多选 / 在职状态多选 / 职级多选 / 入职日期范围
 * PRD §4.2.3 列表操作：详情 / 编辑 / 更多（调岗、离职，按在职状态与权限）
 */
import React, { useEffect, useMemo, useState } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { Button, Dropdown, Space, Tag, TreeSelect, message } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import { history, useAccess, useNavigate } from '@umijs/max';
import { getEmployeeList } from '@/services/employee';
import type { EmployeeItem } from '@/services/employee';
import { getDeptTree, listPositions } from '@/services/org';
import type { DeptTreeNode } from '@/services/org';
import { SEQUENCE_RANK_MAP } from '@/pages/admin/org/positions/constants';

const STATUS_ENUM: Record<string, { text: string; color: string }> = {
  probation: { text: '试用期', color: 'blue' },
  regular: { text: '正式', color: 'green' },
  pending_resign: { text: '待离职', color: 'orange' },
  resigned: { text: '已离职', color: 'default' },
};

/** 可发起调岗/离职的在职状态 */
const ACTIVE_STATUSES = new Set(['probation', 'regular']);

type TreeOption = {
  title: string;
  value: number;
  key: string;
  children?: TreeOption[];
};

function toDeptTreeOptions(nodes: DeptTreeNode[]): TreeOption[] {
  return nodes.map((n) => ({
    title: n.name,
    value: n.id,
    key: `dept-${n.id}`,
    children: n.children?.length ? toDeptTreeOptions(n.children) : undefined,
  }));
}

function joinIds(value: unknown): string {
  if (Array.isArray(value)) {
    return value.filter((v) => v !== undefined && v !== null && v !== '').join(',');
  }
  if (value === undefined || value === null || value === '') return '';
  return String(value);
}

const GRADE_OPTIONS = [
  ...SEQUENCE_RANK_MAP.M,
  ...SEQUENCE_RANK_MAP.P,
  ...SEQUENCE_RANK_MAP.S,
].map((g) => ({ label: g, value: g }));

const EmployeeListPage: React.FC = () => {
  const navigate = useNavigate();
  const access = useAccess();
  const [deptTreeOptions, setDeptTreeOptions] = useState<TreeOption[]>([]);
  const [positionOptions, setPositionOptions] = useState<{ label: string; value: number }[]>([]);

  useEffect(() => {
    (async () => {
      try {
        const [deptRes, posRes] = await Promise.all([
          getDeptTree(),
          listPositions({ page: 1, pageSize: 200 }),
        ]);
        setDeptTreeOptions(toDeptTreeOptions(deptRes.data ?? []));
        const list = posRes.data?.list ?? [];
        setPositionOptions(list.map((p) => ({ label: p.name, value: p.id })));
      } catch {
        message.warning('部门/职位筛选项加载失败，可稍后刷新重试');
      }
    })();
  }, []);

  const columns: ProColumns<EmployeeItem>[] = useMemo(
    () => [
      {
        title: '关键词',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: {
          placeholder: '姓名 / 工号 / 手机号',
          allowClear: true,
        },
      },
      {
        title: '部门',
        dataIndex: 'departmentIds',
        hideInTable: true,
        renderFormItem: () => (
          <TreeSelect
            treeData={deptTreeOptions}
            treeCheckable
            showCheckedStrategy={TreeSelect.SHOW_PARENT}
            placeholder="部门树多选"
            allowClear
            maxTagCount="responsive"
            style={{ width: '100%' }}
            treeDefaultExpandAll
          />
        ),
      },
      {
        title: '职位',
        dataIndex: 'positionIds',
        hideInTable: true,
        valueType: 'select',
        fieldProps: {
          mode: 'multiple',
          options: positionOptions,
          placeholder: '职位多选',
          allowClear: true,
          maxTagCount: 'responsive',
          showSearch: true,
          optionFilterProp: 'label',
        },
      },
      {
        title: '在职状态',
        dataIndex: 'employmentStatus',
        hideInTable: true,
        valueType: 'select',
        fieldProps: {
          mode: 'multiple',
          options: Object.entries(STATUS_ENUM).map(([value, meta]) => ({
            label: meta.text,
            value,
          })),
          placeholder: '状态多选',
          allowClear: true,
          maxTagCount: 'responsive',
        },
      },
      {
        title: '职级',
        dataIndex: 'gradeLevels',
        hideInTable: true,
        valueType: 'select',
        fieldProps: {
          mode: 'multiple',
          options: GRADE_OPTIONS,
          placeholder: '职级多选',
          allowClear: true,
          maxTagCount: 'responsive',
          showSearch: true,
        },
      },
      {
        title: '入职日期',
        dataIndex: 'hireDateRange',
        hideInTable: true,
        valueType: 'dateRange',
        search: {
          transform: (value) => {
            if (value && Array.isArray(value) && value.length === 2) {
              return { hireDateFrom: value[0], hireDateTo: value[1] };
            }
            return {};
          },
        },
      },
      {
        title: '工号',
        dataIndex: 'empNo',
        width: 120,
        copyable: true,
        search: false,
      },
      {
        title: '姓名',
        dataIndex: 'name',
        width: 100,
        search: false,
        render: (_: unknown, record: EmployeeItem) => (
          <a onClick={() => navigate(`/admin/employee/${record.employeeId}`)}>{record.name}</a>
        ),
      },
      {
        title: '部门',
        dataIndex: 'department',
        width: 120,
        search: false,
        ellipsis: true,
      },
      {
        title: '职位',
        dataIndex: 'position',
        width: 150,
        search: false,
        ellipsis: true,
      },
      {
        title: '职级',
        dataIndex: 'grade',
        width: 80,
        search: false,
      },
      {
        title: '在职状态',
        dataIndex: 'employmentStatus',
        width: 100,
        search: false,
        render: (_: unknown, record: EmployeeItem) => {
          const item = STATUS_ENUM[record.employmentStatus];
          return <Tag color={item?.color}>{item?.text || record.employmentStatus}</Tag>;
        },
      },
      {
        title: '入职日期',
        dataIndex: 'hireDate',
        width: 120,
        search: false,
      },
      {
        title: '操作',
        width: 220,
        hideInSearch: true,
        fixed: 'right',
        render: (_: unknown, record: EmployeeItem) => {
          const canMore = ACTIVE_STATUSES.has(record.employmentStatus);
          const moreItems = [
            access.canManageWorkflow
              ? {
                  key: 'transfer',
                  label: '调岗',
                  onClick: () =>
                    history.push(
                      `/admin/transfers?employeeId=${record.employeeId}&open=1`,
                    ),
                }
              : null,
            access.canManageResignation
              ? {
                  key: 'resign',
                  label: '离职',
                  danger: true,
                  onClick: () =>
                    history.push(
                      `/admin/resignation?employeeId=${record.employeeId}&name=${encodeURIComponent(record.name)}&open=1`,
                    ),
                }
              : null,
          ].filter(Boolean) as { key: string; label: string; danger?: boolean; onClick: () => void }[];

          return (
            <Space>
              <Button
                type="link"
                size="small"
                onClick={() => navigate(`/admin/employee/${record.employeeId}`)}
              >
                详情
              </Button>
              <Button
                type="link"
                size="small"
                onClick={() => navigate(`/admin/employee/${record.employeeId}/edit`)}
              >
                编辑
              </Button>
              {canMore && moreItems.length > 0 ? (
                <Dropdown menu={{ items: moreItems }}>
                  <Button type="link" size="small">
                    更多 <DownOutlined />
                  </Button>
                </Dropdown>
              ) : null}
            </Space>
          );
        },
      },
    ],
    [access.canManageResignation, access.canManageWorkflow, deptTreeOptions, navigate, positionOptions],
  );

  return (
    <ProTable<EmployeeItem>
      headerTitle="员工花名册"
      rowKey="employeeId"
      search={{
        labelWidth: 'auto',
        defaultCollapsed: false,
      }}
      scroll={{ x: 1100 }}
      request={async (params) => {
        const { current, pageSize, ...formValues } = params;
        const res = await getEmployeeList({
          page: current,
          pageSize,
          keyword: (formValues.keyword as string) || '',
          departmentIds: joinIds(formValues.departmentIds),
          positionIds: joinIds(formValues.positionIds),
          employmentStatus: joinIds(formValues.employmentStatus),
          gradeLevels: joinIds(formValues.gradeLevels),
          hireDateFrom: (formValues as { hireDateFrom?: string }).hireDateFrom || '',
          hireDateTo: (formValues as { hireDateTo?: string }).hireDateTo || '',
        });
        return {
          data: res.data?.list || [],
          success: res.code === 0,
          total: res.data?.total || 0,
        };
      }}
      locale={{ emptyText: '暂无匹配的员工信息' }}
      columns={columns}
      pagination={{
        showSizeChanger: true,
        pageSizeOptions: ['10', '20', '50', '100'],
        defaultPageSize: 20,
      }}
    />
  );
};

export default EmployeeListPage;

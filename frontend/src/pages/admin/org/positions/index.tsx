/**
 * 职位管理：序列统计卡 + Tabs 筛选 + 表格 + 职级对照表
 * 对齐原型；挂载于 AdminLayout `/admin/org/positions`
 */
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  Button,
  Card,
  Col,
  Empty,
  Modal,
  Row,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createPosition,
  deletePosition,
  getDeptTree,
  listPositions,
  updatePosition,
  type CreatePositionParams,
  type DeptTreeNode,
  type PositionSequence,
  type PositionVO,
} from '@/services/org';
import { getRequestErrorMessage } from '@/utils/requestError';
import PositionFormModal, { type PositionFormMode } from './components/PositionFormModal';
import { GRADE_REFERENCE, SEQUENCE_META } from './constants';

function flattenDept(nodes: DeptTreeNode[], map = new Map<number, string>()) {
  for (const n of nodes) {
    map.set(n.id, n.name);
    if (n.children?.length) flattenDept(n.children, map);
  }
  return map;
}

function formatGradeRange(min?: string, max?: string) {
  if (!min && !max) return '—';
  if (min && max && min === max) return min;
  if (min && max) return `${min}-${max}`;
  return min || max || '—';
}

const SequenceBadge: React.FC<{ code?: string }> = ({ code }) => {
  const seq = (code || '') as PositionSequence;
  const meta = SEQUENCE_META[seq];
  if (!meta) return <Tag>{code || '—'}</Tag>;
  return (
    <Tag
      style={{
        margin: 0,
        borderRadius: 12,
        border: 'none',
        background: meta.color,
        color: '#fff',
        minWidth: 28,
        textAlign: 'center',
        fontWeight: 600,
      }}
    >
      {seq}
    </Tag>
  );
};

const SequenceStatCard: React.FC<{
  code: PositionSequence;
  count: number;
  loading?: boolean;
}> = ({ code, count, loading }) => {
  const meta = SEQUENCE_META[code];
  return (
    <Card
      size="small"
      loading={loading}
      styles={{
        body: {
          background: meta.bgSoft,
          borderRadius: 8,
          minHeight: 110,
        },
      }}
      style={{ borderColor: `${meta.color}33` }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <Tag color={meta.color} style={{ border: 'none', color: '#fff', marginBottom: 8 }}>
          {meta.shortLabel}
        </Tag>
        <Typography.Title level={2} style={{ margin: 0, color: meta.color, lineHeight: 1 }}>
          {count}
        </Typography.Title>
      </div>
      <Typography.Text strong>{meta.label}</Typography.Text>
      <div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          职级范围: {meta.gradeRange}
        </Typography.Text>
      </div>
      <div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {meta.description}
        </Typography.Text>
      </div>
    </Card>
  );
};

const GradeReferencePanel: React.FC = () => (
  <Card title="职位序列职级对照表" size="small">
    <Row gutter={16}>
      {(['M', 'P', 'S'] as PositionSequence[]).map((code) => {
        const meta = SEQUENCE_META[code];
        const rows = GRADE_REFERENCE[code];
        return (
          <Col xs={24} md={8} key={code} style={{ marginBottom: 12 }}>
            <div
              style={{
                borderRadius: 8,
                border: `1px solid ${meta.color}33`,
                overflow: 'hidden',
                background: '#fff',
              }}
            >
              <div
                style={{
                  padding: '10px 14px',
                  background: meta.bgSoft,
                  borderBottom: `1px solid ${meta.color}22`,
                }}
              >
                <Typography.Text strong style={{ color: meta.color }}>
                  {meta.label}（{code}序列）
                </Typography.Text>
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    职级范围: {meta.gradeRange}
                  </Typography.Text>
                </div>
              </div>
              <div style={{ padding: '8px 0' }}>
                {rows.map((row) => (
                  <div
                    key={row.grade}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      padding: '8px 14px',
                    }}
                  >
                    <Tag color={meta.color} style={{ margin: 0, color: '#fff', border: 'none' }}>
                      {row.grade}
                    </Tag>
                    <Typography.Text>{row.title}</Typography.Text>
                  </div>
                ))}
              </div>
            </div>
          </Col>
        );
      })}
    </Row>
  </Card>
);

const PositionsPage: React.FC = () => {
  const access = useAccess();
  const canEdit = access.canEditPosition;

  const [loading, setLoading] = useState(false);
  const [statsLoading, setStatsLoading] = useState(false);
  const [list, setList] = useState<PositionVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sequenceTab, setSequenceTab] = useState<string>('ALL');

  const [stats, setStats] = useState<Record<PositionSequence, number>>({
    M: 0,
    P: 0,
    S: 0,
  });

  const [deptTree, setDeptTree] = useState<DeptTreeNode[]>([]);
  const deptNameById = useMemo(() => flattenDept(deptTree), [deptTree]);

  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<PositionFormMode>('create');
  const [formLoading, setFormLoading] = useState(false);
  const [editing, setEditing] = useState<PositionVO | null>(null);

  const loadDeptTree = useCallback(async () => {
    try {
      const res = await getDeptTree();
      setDeptTree(res.data ?? []);
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '加载部门树失败'));
      }
    }
  }, []);

  /** 统计卡：按序列各查一次 total，不造专用 stats 接口 */
  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      const [m, p, s] = await Promise.all([
        listPositions({ page: 1, pageSize: 1, sequenceCode: 'M' }),
        listPositions({ page: 1, pageSize: 1, sequenceCode: 'P' }),
        listPositions({ page: 1, pageSize: 1, sequenceCode: 'S' }),
      ]);
      setStats({
        M: m.data?.total ?? 0,
        P: p.data?.total ?? 0,
        S: s.data?.total ?? 0,
      });
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '加载序列统计失败'));
      }
    } finally {
      setStatsLoading(false);
    }
  }, []);

  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listPositions({
        page,
        pageSize,
        sequenceCode: sequenceTab === 'ALL' ? undefined : (sequenceTab as PositionSequence),
      });
      setList(res.data?.list ?? []);
      setTotal(res.data?.total ?? 0);
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '加载职位列表失败'));
      }
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, sequenceTab]);

  useEffect(() => {
    void loadDeptTree();
    void loadStats();
  }, [loadDeptTree, loadStats]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  const refreshAll = async () => {
    await Promise.all([loadList(), loadStats(), loadDeptTree()]);
  };

  const openCreate = () => {
    setFormMode('create');
    setEditing(null);
    setFormOpen(true);
  };

  const openEdit = (record: PositionVO) => {
    setFormMode('edit');
    setEditing(record);
    setFormOpen(true);
  };

  const handleFormSubmit = async (values: CreatePositionParams) => {
    setFormLoading(true);
    try {
      if (formMode === 'edit' && editing) {
        await updatePosition(editing.id, values);
        message.success('职位已更新');
      } else {
        await createPosition(values);
        message.success('职位已创建');
      }
      setFormOpen(false);
      setEditing(null);
      await Promise.all([loadList(), loadStats()]);
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '保存失败'));
      }
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = (record: PositionVO) => {
    if (!canEdit) return;
    Modal.confirm({
      title: `确认删除「${record.name}」？`,
      content: '若仍有在职员工引用该职位，删除将被拒绝。删除后不可恢复（逻辑删除）。',
      okText: '删除',
      okType: 'danger',
      onOk: async () => {
        try {
          await deletePosition(record.id);
          message.success('已删除');
          await Promise.all([loadList(), loadStats()]);
        } catch (e) {
          if (!(e instanceof Error && e.name === 'BizError')) {
            message.error(getRequestErrorMessage(e, '删除失败'));
          }
        }
      },
    });
  };

  const columns: ColumnsType<PositionVO> = [
    {
      title: '职位名称',
      dataIndex: 'name',
      ellipsis: true,
    },
    {
      title: '序列',
      dataIndex: 'sequenceCode',
      width: 80,
      render: (code: string) => <SequenceBadge code={code} />,
    },
    {
      title: '所属部门',
      dataIndex: 'departmentId',
      width: 140,
      render: (id?: number | null) => {
        if (id == null) {
          return <Typography.Text type="secondary">全公司通用</Typography.Text>;
        }
        return deptNameById.get(id) ?? `部门#${id}`;
      },
    },
    {
      title: '职级范围',
      width: 120,
      render: (_, row) => formatGradeRange(row.gradeMin, row.gradeMax),
    },
    {
      title: '试用期',
      dataIndex: 'defaultProbationMonths',
      width: 100,
      render: (m?: number) => (m != null ? `${m}个月` : '—'),
    },
    {
      title: '归属人数',
      dataIndex: 'activeCount',
      width: 100,
      tooltip: '含试用/正式/待离职，不含已离职',
      render: (n?: number) => (n != null ? n : 0),
    },
    {
      title: '操作',
      width: 140,
      fixed: 'right',
      render: (_, record) =>
        canEdit ? (
          <Space>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Button type="link" size="small" danger onClick={() => handleDelete(record)}>
              删除
            </Button>
          </Space>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
  ];

  const pagination: TablePaginationConfig = {
    current: page,
    pageSize,
    total,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50', '100'],
    showTotal: (t) => `共 ${t} 条`,
    onChange: (p, ps) => {
      setPage(p);
      setPageSize(ps);
    },
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            职位管理
          </Typography.Title>
          <Typography.Text type="secondary">管理公司职位体系与序列职级</Typography.Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => void refreshAll()}>
            刷新
          </Button>
          {canEdit && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新增职位
            </Button>
          )}
        </Space>
      </div>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        {(['M', 'P', 'S'] as PositionSequence[]).map((code) => (
          <Col xs={24} sm={8} key={code} style={{ marginBottom: 8 }}>
            <SequenceStatCard code={code} count={stats[code]} loading={statsLoading} />
          </Col>
        ))}
      </Row>

      <Card styles={{ body: { paddingTop: 8 } }} style={{ marginBottom: 16 }}>
        <Tabs
          activeKey={sequenceTab}
          onChange={(key) => {
            setSequenceTab(key);
            setPage(1);
          }}
          items={[
            { key: 'ALL', label: '全部' },
            { key: 'M', label: '管理序列' },
            { key: 'P', label: '专业序列' },
            { key: 'S', label: '支持序列' },
          ]}
        />
        <Table<PositionVO>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={list}
          pagination={pagination}
          scroll={{ x: 900 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={sequenceTab === 'ALL' ? '暂无职位，请先新增' : '该序列暂无职位'}
              />
            ),
          }}
        />
      </Card>

      <GradeReferencePanel />

      <PositionFormModal
        open={formOpen}
        mode={formMode}
        loading={formLoading}
        current={editing}
        tree={deptTree}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onSubmit={handleFormSubmit}
      />
    </div>
  );
};

export default PositionsPage;

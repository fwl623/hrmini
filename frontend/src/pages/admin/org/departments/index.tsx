/**
 * 部门管理：左侧树 + 右侧详情
 * 对齐原型；挂载于 AdminLayout `/admin/org/departments`
 */
import {
  DeleteOutlined,
  EditOutlined,
  MergeCellsOutlined,
  PlusOutlined,
  ReloadOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useAccess } from '@umijs/max';
import {
  Badge,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Input,
  Row,
  Space,
  Spin,
  Typography,
  message,
  Modal,
} from 'antd';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  createDept,
  deleteDept,
  getDeptCanDelete,
  getDeptHeadcount,
  getDeptTree,
  mergeDept,
  updateDept,
  type CreateDeptParams,
  type DeptTreeNode,
} from '@/services/org';
import { getRequestErrorMessage } from '@/utils/requestError';
import DeptFormModal, { type DeptFormMode } from './components/DeptFormModal';
import MergeDeptModal from './components/MergeDeptModal';

const MAX_DEPT_LEVEL = 5;

function flattenTree(nodes: DeptTreeNode[], map = new Map<number, DeptTreeNode>()) {
  for (const n of nodes) {
    map.set(n.id, n);
    if (n.children?.length) flattenTree(n.children, map);
  }
  return map;
}

function findParentName(node: DeptTreeNode | null, byId: Map<number, DeptTreeNode>): string {
  if (!node?.parentId) return '—（根部门）';
  return byId.get(node.parentId)?.name ?? `ID ${node.parentId}`;
}

/** 按名称/编码过滤树（命中节点保留祖先链） */
function filterTree(nodes: DeptTreeNode[], keyword: string): DeptTreeNode[] {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return nodes;

  const walk = (list: DeptTreeNode[]): DeptTreeNode[] => {
    const result: DeptTreeNode[] = [];
    for (const n of list) {
      const children = n.children?.length ? walk(n.children) : [];
      const hit =
        n.name.toLowerCase().includes(kw) || n.code.toLowerCase().includes(kw);
      if (hit || children.length) {
        result.push({ ...n, children });
      }
    }
    return result;
  };
  return walk(nodes);
}

const DeptNodeCard: React.FC<{
  node: DeptTreeNode;
  depth: number;
  selectedId?: number;
  onSelect: (node: DeptTreeNode) => void;
}> = ({ node, depth, selectedId, onSelect }) => {
  const selected = selectedId === node.id;
  return (
    <div style={{ marginLeft: depth === 0 ? 0 : 16 }}>
      <div
        role="button"
        tabIndex={0}
        onClick={() => onSelect(node)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onSelect(node);
          }
        }}
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 12px',
          marginBottom: 8,
          borderRadius: 8,
          border: selected ? '1px solid #1677ff' : '1px solid #f0f0f0',
          background: selected ? '#f0f5ff' : '#fff',
          cursor: 'pointer',
          transition: 'background 0.15s, border-color 0.15s',
        }}
      >
        <div style={{ minWidth: 0, flex: 1 }}>
          <div>
            <Typography.Text strong ellipsis>
              {node.name}
            </Typography.Text>
            <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
              {node.code}
            </Typography.Text>
          </div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {node.manager || '暂无负责人'}
          </Typography.Text>
        </div>
        <Badge
          count={`${node.headcountIncludingSub ?? 0}人`}
          style={{ backgroundColor: '#1677ff' }}
          overflowCount={99999}
        />
      </div>
      {node.children?.map((child) => (
        <DeptNodeCard
          key={child.id}
          node={child}
          depth={depth + 1}
          selectedId={selectedId}
          onSelect={onSelect}
        />
      ))}
    </div>
  );
};

const DepartmentsPage: React.FC = () => {
  const access = useAccess();
  const canEdit = access.canEditDept;

  const [loading, setLoading] = useState(false);
  const [tree, setTree] = useState<DeptTreeNode[]>([]);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number>();
  const [detailHeadcount, setDetailHeadcount] = useState<number>();

  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<DeptFormMode>('createRoot');
  const [formLoading, setFormLoading] = useState(false);

  const [mergeOpen, setMergeOpen] = useState(false);
  const [mergeLoading, setMergeLoading] = useState(false);

  const byId = useMemo(() => flattenTree(tree), [tree]);
  const selected = selectedId != null ? byId.get(selectedId) ?? null : null;
  const filteredTree = useMemo(() => filterTree(tree, keyword), [tree, keyword]);
  const children = selected?.children ?? [];

  /** 树结构可推导 parentId，兜底旧缓存缺字段 */
  const selectedWithParent = useMemo(() => {
    if (!selected) return null;
    if (selected.parentId != null || !selectedId) return selected;
    for (const [, node] of byId) {
      if (node.children?.some((c) => c.id === selected.id)) {
        return { ...selected, parentId: node.id };
      }
    }
    return { ...selected, parentId: null };
  }, [selected, selectedId, byId]);

  const loadTree = useCallback(async (keepSelection = true) => {
    setLoading(true);
    try {
      const res = await getDeptTree();
      const data = res.data ?? [];
      setTree(data);
      if (keepSelection && selectedId != null) {
        const map = flattenTree(data);
        if (!map.has(selectedId)) {
          setSelectedId(undefined);
          setDetailHeadcount(undefined);
        }
      }
    } catch (e) {
      // BizError 已由全局 errorHandler 提示
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '加载部门树失败'));
      }
    } finally {
      setLoading(false);
    }
  }, [selectedId]);

  useEffect(() => {
    void loadTree(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅挂载加载
  }, []);

  useEffect(() => {
    if (selectedId == null) {
      setDetailHeadcount(undefined);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const res = await getDeptHeadcount(selectedId);
        if (!cancelled) {
          setDetailHeadcount(res.data?.headcountIncludingSub);
        }
      } catch {
        if (!cancelled) {
          const node = byId.get(selectedId);
          setDetailHeadcount(node?.headcountIncludingSub);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedId, byId]);

  const openCreateRoot = () => {
    setFormMode('createRoot');
    setFormOpen(true);
  };

  const openCreateChild = () => {
    if (!selectedWithParent) return;
    if ((selectedWithParent.level ?? 1) >= MAX_DEPT_LEVEL) {
      message.warning(`部门层级最多 ${MAX_DEPT_LEVEL} 级，无法再新增子部门`);
      return;
    }
    setFormMode('createChild');
    setFormOpen(true);
  };

  const openEdit = () => {
    if (!selectedWithParent) return;
    setFormMode('edit');
    setFormOpen(true);
  };

  const handleFormSubmit = async (values: CreateDeptParams) => {
    setFormLoading(true);
    try {
      if (formMode === 'edit' && selectedWithParent) {
        await updateDept(selectedWithParent.id, values);
        message.success('部门已更新');
      } else {
        const res = await createDept(values);
        message.success('部门已创建');
        if (res.data?.id) {
          setSelectedId(res.data.id);
        }
      }
      setFormOpen(false);
      await loadTree(true);
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '保存失败'));
      }
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedWithParent || !canEdit) return;
    try {
      const check = await getDeptCanDelete(selectedWithParent.id);
      const vo = check.data;
      if (!vo?.canDelete) {
        Modal.warning({
          title: '无法删除',
          content: vo?.reason || '部门下仍有子部门或员工，请先合并或转移后再删除。',
        });
        return;
      }
      Modal.confirm({
        title: `确认删除「${selectedWithParent.name}」？`,
        content: '删除后不可恢复（逻辑删除）。请确认该部门已无人员与子部门。',
        okText: '删除',
        okType: 'danger',
        onOk: async () => {
          try {
            await deleteDept(selectedWithParent.id);
            message.success('已删除');
            setSelectedId(undefined);
            await loadTree(false);
          } catch (e) {
            if (!(e instanceof Error && e.name === 'BizError')) {
              message.error(getRequestErrorMessage(e, '删除失败'));
            }
          }
        },
      });
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '删除校验失败'));
      }
    }
  };

  const handleMerge = async (targetDepartmentId: number) => {
    if (!selectedWithParent) return;
    setMergeLoading(true);
    try {
      await mergeDept(selectedWithParent.id, targetDepartmentId);
      message.success('合并成功');
      setMergeOpen(false);
      setSelectedId(targetDepartmentId);
      await loadTree(true);
    } catch (e) {
      if (!(e instanceof Error && e.name === 'BizError')) {
        message.error(getRequestErrorMessage(e, '合并失败'));
      }
    } finally {
      setMergeLoading(false);
    }
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          部门管理
        </Typography.Title>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={() => loadTree(true)}>
            刷新
          </Button>
          {canEdit && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateRoot}>
              新增根部门
            </Button>
          )}
        </Space>
      </div>

      <Row gutter={16}>
        <Col xs={24} lg={10} xl={9}>
          <Card
            size="small"
            title="组织树"
            styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflow: 'auto' } }}
          >
            <Input.Search
              allowClear
              placeholder="搜索部门名称或编码"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              style={{ marginBottom: 12 }}
            />
            <Spin spinning={loading}>
              {filteredTree.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={keyword ? '无匹配部门' : '暂无部门，请先新增根部门'}
                />
              ) : (
                filteredTree.map((n) => (
                  <DeptNodeCard
                    key={n.id}
                    node={n}
                    depth={0}
                    selectedId={selectedId}
                    onSelect={(node) => setSelectedId(node.id)}
                  />
                ))
              )}
            </Spin>
          </Card>
        </Col>

        <Col xs={24} lg={14} xl={15}>
          {!selectedWithParent ? (
            <Card styles={{ body: { minHeight: 360 } }}>
              <Empty
                description="请从左侧选择一个部门查看详情"
                style={{ marginTop: 80 }}
              />
            </Card>
          ) : (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Card
                title={
                  <Space>
                    <TeamOutlined />
                    <span>{selectedWithParent.name}</span>
                    <Typography.Text type="secondary" style={{ fontWeight: 400, fontSize: 13 }}>
                      编码: {selectedWithParent.code}
                    </Typography.Text>
                  </Space>
                }
                extra={
                  canEdit ? (
                    <Space wrap>
                      <Button size="small" icon={<PlusOutlined />} onClick={openCreateChild}>
                        新增子部门
                      </Button>
                      <Button size="small" icon={<EditOutlined />} onClick={openEdit}>
                        编辑
                      </Button>
                      <Button
                        size="small"
                        icon={<MergeCellsOutlined />}
                        onClick={() => setMergeOpen(true)}
                      >
                        合并
                      </Button>
                      <Button
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={handleDelete}
                      >
                        删除
                      </Button>
                    </Space>
                  ) : null
                }
              >
                <Descriptions column={{ xs: 1, sm: 2 }} size="small">
                  <Descriptions.Item label="部门名称">{selectedWithParent.name}</Descriptions.Item>
                  <Descriptions.Item label="部门编码">{selectedWithParent.code}</Descriptions.Item>
                  <Descriptions.Item label="上级部门">
                    {findParentName(selectedWithParent, byId)}
                  </Descriptions.Item>
                  <Descriptions.Item label="部门负责人">
                    {selectedWithParent.manager || '—'}
                    {selectedWithParent.headEmployeeId != null && (
                      <Typography.Text type="secondary" style={{ marginLeft: 8 }}>
                        (ID: {selectedWithParent.headEmployeeId})
                      </Typography.Text>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="排序序号">
                    {selectedWithParent.sortOrder ?? 0}
                  </Descriptions.Item>
                  <Descriptions.Item label="层级">
                    {selectedWithParent.level ?? '—'} / {MAX_DEPT_LEVEL}
                  </Descriptions.Item>
                  <Descriptions.Item label="部门描述" span={2}>
                    {selectedWithParent.description || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="在职人数（含下属）">
                    <Typography.Text style={{ color: '#1677ff', fontWeight: 600 }}>
                      {detailHeadcount ?? selectedWithParent.headcountIncludingSub ?? 0} 人
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ marginLeft: 12 }}>
                      本部门直属 {selectedWithParent.headcount ?? 0} 人
                    </Typography.Text>
                  </Descriptions.Item>
                </Descriptions>
              </Card>

              <Card title={`直属子部门 ${children.length} 个`}>
                {children.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无直属子部门" />
                ) : (
                  <Row gutter={[12, 12]}>
                    {children.map((child) => (
                      <Col key={child.id} xs={24} sm={12} md={8}>
                        <Card
                          size="small"
                          hoverable
                          onClick={() => setSelectedId(child.id)}
                          styles={{ body: { padding: 12 } }}
                        >
                          <div
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              gap: 8,
                              alignItems: 'flex-start',
                            }}
                          >
                            <div style={{ minWidth: 0 }}>
                              <Typography.Text strong ellipsis>
                                {child.name}
                              </Typography.Text>
                              <div>
                                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                  {child.manager || '暂无负责人'}
                                </Typography.Text>
                              </div>
                            </div>
                            <Badge
                              count={`${child.headcountIncludingSub ?? 0}人`}
                              style={{ backgroundColor: '#1677ff' }}
                              overflowCount={99999}
                            />
                          </div>
                        </Card>
                      </Col>
                    ))}
                  </Row>
                )}
              </Card>
            </Space>
          )}
        </Col>
      </Row>

      <DeptFormModal
        open={formOpen}
        mode={formMode}
        loading={formLoading}
        current={selectedWithParent}
        tree={tree}
        onCancel={() => setFormOpen(false)}
        onSubmit={handleFormSubmit}
      />

      <MergeDeptModal
        open={mergeOpen}
        loading={mergeLoading}
        source={selectedWithParent}
        tree={tree}
        onCancel={() => setMergeOpen(false)}
        onSubmit={handleMerge}
      />
    </div>
  );
};

export default DepartmentsPage;

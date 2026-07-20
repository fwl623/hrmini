import {
  CloudUploadOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import {
  Button,
  Card,
  Col,
  message,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Switch,
  Tag,
  Typography,
  Upload,
} from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  deleteKnowledgeDoc,
  listKnowledgeDocs,
  setKnowledgeEnabled,
  uploadKnowledgeDoc,
  type AiKnowledgeDoc,
} from '@/services/ai';
import '@/components/AiAssistant/ai.less';

const { Text } = Typography;

const statusTag = (status?: string) => {
  const s = (status || '').toUpperCase();
  if (s === 'READY') return <Tag color="success" icon={<CheckCircleOutlined />}>已就绪</Tag>;
  if (s === 'FAILED') return <Tag color="error" icon={<CloseCircleOutlined />}>失败</Tag>;
  if (s === 'PENDING') return <Tag color="processing">处理中</Tag>;
  return <Tag>{status || '-'}</Tag>;
};

const KnowledgePage: React.FC = () => {
  const [list, setList] = useState<AiKnowledgeDoc[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listKnowledgeDocs();
      if (res.code === 0) setList(res.data ?? []);
      else message.error(res.message || '加载失败');
    } catch (e) {
      message.error((e as Error).message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const stats = useMemo(() => {
    const total = list.length;
    const ready = list.filter((d) => d.status === 'READY').length;
    const enabled = list.filter((d) => d.enabled).length;
    const chunks = list.reduce((sum, d) => sum + (d.chunkCount || 0), 0);
    return { total, ready, enabled, chunks };
  }, [list]);

  const columns: ProColumns<AiKnowledgeDoc>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (_, row) => (
        <Space>
          <FileTextOutlined style={{ color: '#1677ff' }} />
          <span>{row.title}</span>
        </Space>
      ),
    },
    { title: '文件名', dataIndex: 'fileName', ellipsis: true, copyable: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (_, row) => statusTag(row.status),
    },
    { title: '分块数', dataIndex: 'chunkCount', width: 90 },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 90,
      render: (_, row) => (
        <Switch
          checked={!!row.enabled}
          onChange={async (checked) => {
            try {
              await setKnowledgeEnabled(row.id, checked);
              message.success(checked ? '已启用' : '已停用');
              void load();
            } catch (e) {
              message.error((e as Error).message || '操作失败');
            }
          }}
        />
      ),
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (_, row) => row.errorMessage || <Text type="secondary">-</Text>,
    },
    {
      title: '操作',
      width: 100,
      valueType: 'option',
      render: (_, row) => [
        <Popconfirm
          key="del"
          title="确认删除该文档及向量？"
          onConfirm={async () => {
            try {
              await deleteKnowledgeDoc(row.id);
              message.success('已删除');
              void load();
            } catch (e) {
              message.error((e as Error).message || '删除失败');
            }
          }}
        >
          <Button danger type="link">
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer
      className="ai-page"
      header={{
        title: '知识库管理',
      }}
    >
      <div className="ai-hero">
        <Typography.Title level={3} className="ai-hero-title">
          <RobotOutlined style={{ marginRight: 10 }} />
          小R 的知识库
        </Typography.Title>
        <div className="ai-hero-desc">支持 txt / md / pdf，上传后即可用于智能问答引用。</div>
        <div className="ai-hero-tags">
          <Space wrap>
            <Tag color="geekblue">制度文档</Tag>
            <Tag color="cyan">智能检索</Tag>
          </Space>
        </div>
      </div>

      <Row gutter={[16, 16]} className="ai-knowledge-stat" style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card bordered>
            <Statistic title="文档总数" value={stats.total} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card bordered>
            <Statistic title="已就绪" value={stats.ready} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card bordered>
            <Statistic title="已启用" value={stats.enabled} valueStyle={{ color: '#0958d9' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card bordered>
            <Statistic title="分块总量" value={stats.chunks} valueStyle={{ color: '#4096ff' }} />
          </Card>
        </Col>
      </Row>

      <div className="ai-knowledge-table">
        <ProTable<AiKnowledgeDoc>
          rowKey="id"
          search={false}
          options={{ reload: () => void load() }}
          loading={loading}
          columns={columns}
          dataSource={list}
          pagination={{ pageSize: 10 }}
          toolBarRender={() => [
            <Upload
              key="upload"
              accept=".txt,.md,.pdf"
              showUploadList={false}
              customRequest={async ({ file, onSuccess, onError }) => {
                setUploading(true);
                try {
                  const res = await uploadKnowledgeDoc(file as File);
                  if (res.code !== 0) throw new Error(res.message || '上传失败');
                  message.success('上传并入库成功');
                  onSuccess?.(res);
                  void load();
                } catch (e) {
                  message.error((e as Error).message || '上传失败');
                  onError?.(e as Error);
                } finally {
                  setUploading(false);
                }
              }}
            >
              <Button type="primary" icon={<CloudUploadOutlined />} loading={uploading}>
                上传制度文档
              </Button>
            </Upload>,
          ]}
          headerTitle="文档列表"
        />
      </div>
    </PageContainer>
  );
};

export default KnowledgePage;

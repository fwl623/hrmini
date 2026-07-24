/**
 * AI 运行时参数（知识库管理权限）
 */
import { PageContainer } from '@ant-design/pro-components';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd';
import React, { useEffect, useState } from 'react';
import { getAiSettings, updateAiSettings, type AiSettings } from '@/services/ai';

const { Text, Paragraph } = Typography;

const AiSettingsPage: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [meta, setMeta] = useState<Pick<
    AiSettings,
    'embeddingModel' | 'embeddingDimensions' | 'apiKeyConfigured'
  > | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await getAiSettings();
      if (res.code !== 0 || !res.data) {
        message.error(res.message || '加载失败');
        return;
      }
      const d = res.data;
      form.setFieldsValue({
        chunkSize: d.chunkSize,
        chunkOverlap: d.chunkOverlap,
        topK: d.topK,
        temperature: d.temperature,
        chatModel: d.chatModel,
        enableThinking: d.enableThinking,
      });
      setMeta({
        embeddingModel: d.embeddingModel,
        embeddingDimensions: d.embeddingDimensions,
        apiKeyConfigured: d.apiKeyConfigured,
      });
    } catch (e) {
      message.error((e as Error).message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const res = await updateAiSettings(values);
      if (res.code === 0 && res.data) {
        message.success('已保存，立即生效');
        form.setFieldsValue(res.data);
        setMeta({
          embeddingModel: res.data.embeddingModel,
          embeddingDimensions: res.data.embeddingDimensions,
          apiKeyConfigured: res.data.apiKeyConfigured,
        });
      } else {
        message.error(res.message || '保存失败');
      }
    } catch (e) {
      if ((e as { errorFields?: unknown })?.errorFields) return;
      message.error((e as Error).message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <PageContainer title="AI 参数设置" subTitle="需具备知识库管理权限">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="分块大小 / 重叠仅影响之后新上传的知识库文档；已入库文档不会自动重切。检索条数与温度保存后立即影响对话。"
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Card title="可调参数" loading={loading}>
            <Form form={form} layout="vertical" requiredMark={false}>
              <Row gutter={16}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="分块大小（字符）"
                    name="chunkSize"
                    rules={[{ required: true, message: '必填' }]}
                    extra="建议 300～1200，过大降低检索精度"
                  >
                    <InputNumber min={100} max={4000} step={50} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="分块重叠"
                    name="chunkOverlap"
                    rules={[{ required: true, message: '必填' }]}
                    extra="需小于分块大小，保持上下文连贯"
                  >
                    <InputNumber min={0} max={2000} step={10} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="RAG 检索条数 topK"
                    name="topK"
                    rules={[{ required: true, message: '必填' }]}
                    extra="每次问答从知识库取回的片段数"
                  >
                    <InputNumber min={1} max={20} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="对话温度 temperature"
                    name="temperature"
                    rules={[{ required: true, message: '必填' }]}
                    extra="越低越稳妥，越高越发散（0～2）"
                  >
                    <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item label="对话模型" name="chatModel" rules={[{ required: true }]}>
                    <Input placeholder="如 deepseek-v4-flash" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="开启思考模式"
                    name="enableThinking"
                    valuePropName="checked"
                    extra="部分模型支持；开启可能更慢"
                  >
                    <Switch checkedChildren="开" unCheckedChildren="关" />
                  </Form.Item>
                </Col>
              </Row>
              <Space>
                <Button type="primary" loading={saving} onClick={() => void handleSave()}>
                  保存
                </Button>
                <Button onClick={() => void load()} disabled={loading || saving}>
                  刷新
                </Button>
              </Space>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="只读状态" loading={loading}>
            <Paragraph type="secondary" style={{ marginBottom: 12 }}>
              以下项在配置文件中维护，页面不可改。
            </Paragraph>
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <div>
                <Text type="secondary">API Key</Text>
                <div>
                  {meta?.apiKeyConfigured ? (
                    <Tag color="success">已配置</Tag>
                  ) : (
                    <Tag color="error">未配置</Tag>
                  )}
                </div>
              </div>
              <div>
                <Text type="secondary">向量模型</Text>
                <div>
                  <Text strong>{meta?.embeddingModel || '-'}</Text>
                </div>
              </div>
              <div>
                <Text type="secondary">向量维度</Text>
                <div>
                  <Text strong>{meta?.embeddingDimensions ?? '-'}</Text>
                </div>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </PageContainer>
  );
};

export default AiSettingsPage;

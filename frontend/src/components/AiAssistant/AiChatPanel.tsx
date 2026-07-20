import {
  BookOutlined,
  RobotOutlined,
  SendOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import {
  Avatar,
  Badge,
  Button,
  Empty,
  Input,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import {
  fetchAiCapabilities,
  streamAiChat,
  type AiAction,
  type AiCitation,
  type AiQuickPrompt,
} from '@/services/ai';
import './ai.less';

const { TextArea } = Input;
const { Text, Paragraph, Title } = Typography;

type ChatMsg = {
  role: 'user' | 'assistant';
  content: string;
  citations?: AiCitation[];
  actions?: AiAction[];
};

type Props = {
  /** 进入页面时恢复悬浮球显示 */
  restoreFloatBall?: boolean;
  dense?: boolean;
};

const FLOAT_HIDDEN_KEY = 'hrms.ai.float.hidden';

const AiChatPanel: React.FC<Props> = ({ restoreFloatBall = false, dense = false }) => {
  const [messages, setMessages] = useState<ChatMsg[]>([
    {
      role: 'assistant',
      content:
        '你好，我是助理小R。可以问制度政策，或说「我要请假」「打开花名册」让我帮你指路。',
    },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [quickPrompts, setQuickPrompts] = useState<AiQuickPrompt[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (restoreFloatBall) {
      localStorage.removeItem(FLOAT_HIDDEN_KEY);
      window.dispatchEvent(new Event('hrms-ai-float-restore'));
    }
    fetchAiCapabilities()
      .then((res) => {
        if (res?.code === 0 && res.data) {
          setQuickPrompts(res.data.quickPrompts ?? []);
        }
      })
      .catch(() => undefined);
  }, [restoreFloatBall]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, loading]);

  const send = async (text: string) => {
    const content = text.trim();
    if (!content || loading) return;
    setInput('');
    setMessages((prev) => [...prev, { role: 'user', content }]);
    setLoading(true);
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    let assistant = '';
    setMessages((prev) => [...prev, { role: 'assistant', content: '' }]);

    try {
      await streamAiChat(
        content,
        {
          onDelta: (delta) => {
            assistant += delta;
            const snapshot = assistant;
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              if (last?.role === 'assistant') {
                next[next.length - 1] = { ...last, content: snapshot };
              }
              return next;
            });
          },
          onFinal: ({ citations, actions }) => {
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              if (last?.role === 'assistant') {
                next[next.length - 1] = {
                  ...last,
                  content: last.content || assistant,
                  citations,
                  actions,
                };
              }
              return next;
            });
          },
          onError: (msg) => {
            setMessages((prev) => {
              const next = [...prev];
              const last = next[next.length - 1];
              if (last?.role === 'assistant') {
                next[next.length - 1] = { ...last, content: msg || '对话失败' };
              }
              return next;
            });
          },
        },
        controller.signal,
      );
      if (!assistant) {
        setMessages((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          if (last?.role === 'assistant' && !last.content?.trim()) {
            next[next.length - 1] = {
              ...last,
              content:
                '未能获取回复。若尚未配置百炼 API Key，请在 application-dev.yml 设置 hrms.ai.api-key 或环境变量 DASHSCOPE_API_KEY 后重启后端。',
            };
          }
          return next;
        });
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') {
        const tip =
          (e as Error).message ||
          '对话失败。请检查是否已配置 DASHSCOPE_API_KEY（或 hrms.ai.api-key）并重启后端。';
        setMessages((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          if (last?.role === 'assistant') {
            next[next.length - 1] = { ...last, content: tip };
          }
          return next;
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={`ai-chat-shell${dense ? ' is-dense' : ''}`}>
      {!dense && (
        <div className="ai-chat-header">
          <Badge dot color="#52c41a" offset={[-2, 42]}>
            <Avatar
              size={44}
              style={{ background: 'linear-gradient(135deg,#1677ff,#69b1ff)' }}
              icon={<RobotOutlined />}
            />
          </Badge>
          <div className="ai-chat-header-meta">
            <Title level={4} style={{ margin: 0, color: '#fff' }}>
              助理小R
            </Title>
            <Text style={{ color: 'rgba(255,255,255,0.85)' }}>有问题随时问我</Text>
          </div>
          <Tag icon={<ThunderboltOutlined />} style={{ border: 'none' }}>
            在线
          </Tag>
        </div>
      )}

      <div ref={listRef} className="ai-chat-list">
        {messages.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="开始提问吧" />
        ) : (
          messages.map((m, idx) => (
            <div key={idx} className={`ai-msg-row${m.role === 'user' ? ' is-user' : ''}`}>
              <Avatar
                size={36}
                style={
                  m.role === 'user'
                    ? { background: '#91caff', color: '#0958d9' }
                    : { background: 'linear-gradient(135deg,#1677ff,#69b1ff)' }
                }
                icon={m.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
              />
              <div className={`ai-bubble${m.role === 'user' ? ' is-user' : ' is-assistant'}`}>
                <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap', color: 'inherit' }}>
                  {m.content || (loading && idx === messages.length - 1 ? '正在思考…' : '')}
                </Paragraph>
                {!!m.citations?.length && (
                  <div className="ai-cite">
                    <BookOutlined style={{ marginRight: 6 }} />
                    来源：{m.citations.map((c) => c.title).join('、')}
                  </div>
                )}
                {!!m.actions?.length && (
                  <Space wrap style={{ marginTop: 10 }}>
                    {m.actions.map((a) => (
                      <Button
                        key={a.route}
                        size="small"
                        type="primary"
                        ghost={m.role !== 'user'}
                        onClick={() => history.push(a.route)}
                      >
                        {a.label}
                      </Button>
                    ))}
                  </Space>
                )}
              </div>
            </div>
          ))
        )}
        {loading && (
          <div style={{ textAlign: 'center', paddingBottom: 8 }}>
            <Spin size="small" tip="生成中" />
          </div>
        )}
      </div>

      {!!quickPrompts.length && (
        <div className="ai-quick">
          <Space wrap size={[8, 8]}>
            {quickPrompts.slice(0, 8).map((q) => (
              <Button key={q.intent} size="small" onClick={() => send(q.prompt)}>
                {q.prompt}
              </Button>
            ))}
          </Space>
        </div>
      )}

      <div className="ai-composer">
        <Space.Compact style={{ width: '100%' }}>
          <TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="试着问：请假怎么申请？公司薪资全量在哪看？"
            autoSize={{ minRows: 1, maxRows: 4 }}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                void send(input);
              }
            }}
            disabled={loading}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={() => void send(input)}
            loading={loading}
          >
            发送
          </Button>
        </Space.Compact>
      </div>
    </div>
  );
};

export default AiChatPanel;
export { FLOAT_HIDDEN_KEY };

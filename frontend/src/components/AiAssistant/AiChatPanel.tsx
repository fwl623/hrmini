import {
  BookOutlined,
  ClearOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { Avatar, Button, Empty, Input, Popconfirm, Space, Spin, Typography } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import {
  fetchAiCapabilities,
  streamAiChat,
  type AiAction,
  type AiQuickPrompt,
} from '@/services/ai';
import { useAiChatStore } from '@/stores/aiChatStore';
import AiApprovalTodoCard from './AiApprovalTodoCard';
import AiDataStatsCard from './AiDataStatsCard';
import AiEmployeeListCard from './AiEmployeeListCard';
import AiLeaveFormCard from './AiLeaveFormCard';
import AiOvertimeFormCard from './AiOvertimeFormCard';
import AiOwlAvatar from './AiOwlAvatar';
import './ai.less';

const { TextArea } = Input;
const { Text, Paragraph, Title } = Typography;

type Props = {
  /** 进入页面时恢复悬浮球显示 */
  restoreFloatBall?: boolean;
  dense?: boolean;
};

const FLOAT_HIDDEN_KEY = 'hrms.ai.float.hidden';

function actionKey(a: AiAction, idx: number) {
  return `${a.type || 'NAVIGATE'}-${a.intent || ''}-${a.formId || a.route || ''}-${idx}`;
}

/** 管理端个人中心与门户路由对齐 */
function resolveBizRoute(route?: string) {
  if (!route) return route;
  if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
    if (route === '/portal/leave') return '/admin/personal/leave';
    if (route === '/portal/overtime') return '/admin/personal/overtime';
    if (route === '/portal/attendance') return '/admin/personal/attendance';
    if (route === '/portal/payslips') return '/admin/personal/payslips';
    if (route === '/portal/resignation') return '/admin/personal/resignation';
    if (route === '/admin/approval') return '/admin/approval';
  }
  return route;
}

const AiChatPanel: React.FC<Props> = ({ restoreFloatBall = false, dense = false }) => {
  const messages = useAiChatStore((s) => s.messages);
  const setMessages = useAiChatStore((s) => s.setMessages);
  const clearMessages = useAiChatStore((s) => s.clearMessages);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [quickPrompts, setQuickPrompts] = useState<AiQuickPrompt[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (restoreFloatBall) {
      localStorage.removeItem(FLOAT_HIDDEN_KEY);
      const t = window.setTimeout(() => {
        window.dispatchEvent(new Event('hrms-ai-float-restore'));
      }, 50);
      return () => window.clearTimeout(t);
    }
    return undefined;
  }, [restoreFloatBall]);

  useEffect(() => {
    fetchAiCapabilities()
      .then((res) => {
        if (res?.code === 0 && res.data) {
          setQuickPrompts(res.data.quickPrompts ?? []);
        }
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, loading]);

  const appendAssistantTip = (tip: string) => {
    setMessages((prev) => [...prev, { role: 'assistant', content: tip }]);
  };

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

  const renderActions = (actions: AiAction[] | undefined) => {
    if (!actions?.length) return null;
    const leaveForms = actions.filter((a) => a.type === 'FORM_SUBMIT' && a.formId === 'leave_apply');
    const overtimeForms = actions.filter(
      (a) => a.type === 'FORM_SUBMIT' && a.formId === 'overtime_apply',
    );
    const todoLists = actions.filter(
      (a) => a.type === 'TASK_LIST' && a.formId === 'approval_todo_list',
    );
    const employeeLists = actions.filter(
      (a) => a.type === 'INFO_LIST' && a.formId === 'employee_roster',
    );
    const dataCards = actions.filter((a) => a.type === 'DATA_CARD');
    const navs = actions.filter(
      (a) =>
        a.type !== 'FORM_SUBMIT' &&
        a.type !== 'TASK_LIST' &&
        a.type !== 'INFO_LIST' &&
        a.type !== 'DATA_CARD',
    );
    const go = (route?: string) => {
      const r = resolveBizRoute(route);
      if (r) history.push(r);
    };
    return (
      <div className="ai-bubble-actions">
        {todoLists.map((a, i) => (
          <AiApprovalTodoCard
            key={actionKey(a, i)}
            action={a}
            disabled={loading}
            onSuccess={appendAssistantTip}
            onNavigate={go}
          />
        ))}
        {employeeLists.map((a, i) => (
          <AiEmployeeListCard key={actionKey(a, i + 5)} action={a} onNavigate={go} />
        ))}
        {dataCards.map((a, i) => (
          <AiDataStatsCard key={actionKey(a, i + 8)} action={a} onNavigate={go} />
        ))}
        {leaveForms.map((a, i) => (
          <AiLeaveFormCard
            key={actionKey(a, i + 10)}
            action={a}
            disabled={loading}
            onSuccess={appendAssistantTip}
            onNavigate={go}
          />
        ))}
        {overtimeForms.map((a, i) => (
          <AiOvertimeFormCard
            key={actionKey(a, i + 20)}
            action={a}
            disabled={loading}
            onSuccess={appendAssistantTip}
            onNavigate={go}
          />
        ))}
        {!!navs.length && (
          <Space wrap>
            {navs.map((a, i) => (
              <Button
                key={actionKey(a, i + 50)}
                size="small"
                type="primary"
                ghost
                disabled={!a.route}
                onClick={() => go(a.route)}
              >
                {a.label}
              </Button>
            ))}
          </Space>
        )}
      </div>
    );
  };

  return (
    <div className={`ai-chat-shell${dense ? ' is-dense' : ''}`}>
      {!dense && (
        <header className="ai-chat-header">
          <div className="ai-chat-header-left">
            <span className="ai-chat-header-avatar">
              <AiOwlAvatar size={42} />
              <span className="ai-online-dot" aria-hidden />
            </span>
            <div className="ai-chat-header-meta">
              <Title level={5} className="ai-chat-header-title">
                助理小R
              </Title>
              <Text className="ai-chat-header-sub">制度问答 · 业务指路 · 聊天办事</Text>
            </div>
          </div>
          <Space size={8}>
            <Popconfirm
              title="清空当前会话？"
              description="清空后不可恢复"
              okText="清空"
              cancelText="取消"
              onConfirm={() => {
                abortRef.current?.abort();
                clearMessages();
                setLoading(false);
              }}
            >
              <Button size="small" icon={<ClearOutlined />} disabled={loading}>
                清空
              </Button>
            </Popconfirm>
            <span className="ai-online-pill">
              <i />
              在线
            </span>
          </Space>
        </header>
      )}

      {dense && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '4px 8px 0' }}>
          <Popconfirm
            title="清空当前会话？"
            okText="清空"
            cancelText="取消"
            onConfirm={() => {
              abortRef.current?.abort();
              clearMessages();
              setLoading(false);
            }}
          >
            <Button size="small" type="text" icon={<ClearOutlined />} disabled={loading}>
              清空
            </Button>
          </Popconfirm>
        </div>
      )}

      <div ref={listRef} className="ai-chat-list">
        <div className="ai-chat-list-glow" aria-hidden />
        {messages.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="开始提问吧" />
        ) : (
          messages.map((m, idx) => (
            <div key={idx} className={`ai-msg-row${m.role === 'user' ? ' is-user' : ''}`}>
              {m.role === 'user' ? (
                <Avatar size={34} className="ai-msg-user-avatar" icon={<UserOutlined />} />
              ) : (
                <span className="ai-msg-owl-avatar">
                  <AiOwlAvatar size={34} />
                </span>
              )}
              <div className={`ai-bubble${m.role === 'user' ? ' is-user' : ' is-assistant'}`}>
                <Paragraph className="ai-bubble-text">
                  {m.content || (loading && idx === messages.length - 1 ? '正在思考…' : '')}
                </Paragraph>
                {!!m.citations?.length && (
                  <div className="ai-cite">
                    <BookOutlined style={{ marginRight: 6 }} />
                    来源：{m.citations.map((c) => c.title).join('、')}
                  </div>
                )}
                {m.role === 'assistant' ? renderActions(m.actions) : null}
              </div>
            </div>
          ))
        )}
        {loading && (
          <div className="ai-loading-hint">
            <Spin size="small" />
            <span>生成中</span>
          </div>
        )}
      </div>

      <footer className="ai-composer-wrap">
        {!!quickPrompts.length && (
          <div className="ai-quick">
            {quickPrompts.slice(0, 6).map((q) => (
              <button
                key={q.intent}
                type="button"
                className="ai-quick-chip"
                disabled={loading}
                onClick={() => send(q.prompt)}
              >
                {q.prompt}
              </button>
            ))}
          </div>
        )}

        <div className="ai-composer">
          <TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="问制度、找入口，或说「我要请假」…"
            autoSize={{ minRows: 1, maxRows: 4 }}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                void send(input);
              }
            }}
            disabled={loading}
            bordered={false}
          />
          <Button
            type="primary"
            className="ai-send-btn"
            icon={<SendOutlined />}
            onClick={() => void send(input)}
            loading={loading}
            disabled={!input.trim() && !loading}
          >
            发送
          </Button>
        </div>
        <Text type="secondary" className="ai-composer-hint">
          Enter 发送 · Shift+Enter 换行 · 可直接说「我要请假」在聊天里办理
        </Text>
      </footer>
    </div>
  );
};

export default AiChatPanel;
export { FLOAT_HIDDEN_KEY };

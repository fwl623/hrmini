import { create } from 'zustand';
import type { AiAction, AiCitation } from '@/services/ai';

export type AiChatMsg = {
  role: 'user' | 'assistant';
  content: string;
  citations?: AiCitation[];
  actions?: AiAction[];
};

const WELCOME: AiChatMsg = {
  role: 'assistant',
  content:
    '你好，我是助理小R。可以说「我要请假」「我的待审批」，有花名册权限还可说「后端部门的员工名单」「各部门人数」。',
};

const MAX_MESSAGES = 80;

interface AiChatState {
  messages: AiChatMsg[];
  setMessages: (updater: AiChatMsg[] | ((prev: AiChatMsg[]) => AiChatMsg[])) => void;
  clearMessages: () => void;
}

/** 仅内存：同一次打开站点内换页保留；刷新/关页即清空，不写 localStorage */
export const useAiChatStore = create<AiChatState>((set, get) => ({
  messages: [WELCOME],
  setMessages: (updater) => {
    const prev = get().messages;
    const next = typeof updater === 'function' ? updater(prev) : updater;
    const trimmed = next.length > MAX_MESSAGES ? next.slice(next.length - MAX_MESSAGES) : next;
    set({ messages: trimmed });
  },
  clearMessages: () => set({ messages: [{ ...WELCOME }] }),
}));

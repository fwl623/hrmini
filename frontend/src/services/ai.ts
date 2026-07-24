/**
 * AI 智能助理 API
 */
import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';
import { getAccessToken } from '@/utils/token';

export interface AiQuickPrompt {
  intent: string;
  label: string;
  prompt: string;
}

export interface AiFormFieldOption {
  value: string;
  label: string;
}

export interface AiFormField {
  name: string;
  label: string;
  fieldType: 'select' | 'datetime' | 'number' | 'textarea' | 'text' | 'date' | string;
  required?: boolean;
  options?: AiFormFieldOption[];
}

export interface AiApprovalTaskItem {
  taskId: number;
  instanceId?: number;
  processType?: string;
  title?: string;
  applicantName?: string;
  currentNodeLabel?: string;
  createTime?: string;
  businessSummary?: string;
}

export interface AiEmployeeItem {
  employeeId?: number;
  empNo?: string;
  name?: string;
  department?: string;
  position?: string;
  grade?: string;
  employmentStatus?: string;
}

export interface AiStatItem {
  label: string;
  value: number | string;
}

export interface AiActionMeta {
  title?: string;
  total?: number;
  deptName?: string;
  hint?: string;
}

export interface AiAction {
  type?: 'NAVIGATE' | 'FORM_SUBMIT' | 'TASK_LIST' | 'INFO_LIST' | 'DATA_CARD' | string;
  label: string;
  route?: string;
  intent?: string;
  formId?: string;
  formSchema?: AiFormField[];
  prefill?: Record<string, unknown>;
  submitApi?: { method: string; path: string };
  tasks?: AiApprovalTaskItem[];
  items?: AiEmployeeItem[];
  stats?: AiStatItem[];
  meta?: AiActionMeta;
  hint?: string;
}

export interface AiCitation {
  docId: number;
  title: string;
}

export interface AiCapabilities {
  quickPrompts: AiQuickPrompt[];
  actions: AiAction[];
}

export interface AiKnowledgeDoc {
  id: number;
  title: string;
  fileName: string;
  status: string;
  enabled: boolean;
  chunkCount?: number;
  errorMessage?: string;
  createdAt?: string;
  updatedAt?: string;
}

export async function fetchAiCapabilities() {
  return request<API.Result<AiCapabilities>>(`${API_BASE}/ai/capabilities`, { method: 'GET' });
}

export async function listKnowledgeDocs() {
  return request<API.Result<AiKnowledgeDoc[]>>(`${API_BASE}/ai/knowledge/docs`, { method: 'GET' });
}

export async function uploadKnowledgeDoc(file: File, title?: string) {
  const form = new FormData();
  form.append('file', file);
  if (title) form.append('title', title);
  return request<API.Result<AiKnowledgeDoc>>(`${API_BASE}/ai/knowledge/docs`, {
    method: 'POST',
    data: form,
  });
}

export async function setKnowledgeEnabled(id: number, enabled: boolean) {
  return request<API.Result<void>>(`${API_BASE}/ai/knowledge/docs/${id}/enabled`, {
    method: 'PUT',
    params: { enabled },
  });
}

export async function deleteKnowledgeDoc(id: number) {
  return request<API.Result<{ deleted: boolean }>>(`${API_BASE}/ai/knowledge/docs/${id}`, {
    method: 'DELETE',
  });
}

export interface AiSettings {
  chunkSize: number;
  chunkOverlap: number;
  topK: number;
  temperature: number;
  chatModel: string;
  enableThinking: boolean;
  embeddingModel: string;
  embeddingDimensions: number;
  apiKeyConfigured: boolean;
}

export type AiSettingsUpdate = Partial<{
  chunkSize: number;
  chunkOverlap: number;
  topK: number;
  temperature: number;
  chatModel: string;
  enableThinking: boolean;
}>;

export async function getAiSettings() {
  return request<API.Result<AiSettings>>(`${API_BASE}/ai/settings`, { method: 'GET' });
}

export async function updateAiSettings(data: AiSettingsUpdate) {
  return request<API.Result<AiSettings>>(`${API_BASE}/ai/settings`, {
    method: 'PUT',
    data,
  });
}

export type StreamHandlers = {
  onDelta?: (text: string) => void;
  onFinal?: (payload: { citations: AiCitation[]; actions: AiAction[] }) => void;
  onError?: (message: string) => void;
  onDone?: () => void;
};

/** SSE 流式对话（fetch + ReadableStream） */
export async function streamAiChat(message: string, handlers: StreamHandlers, signal?: AbortSignal) {
  const token = getAccessToken();
  const res = await fetch(`${API_BASE}/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message }),
    signal,
  });
  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `HTTP ${res.status}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split('\n');
    buffer = parts.pop() ?? '';
    for (const line of parts) {
      const trimmed = line.trim();
      if (!trimmed.startsWith('data:')) continue;
      const data = trimmed.slice(5).trim();
      if (!data) continue;
      try {
        const event = JSON.parse(data) as {
          type: string;
          content?: string;
          message?: string;
          citations?: AiCitation[];
          actions?: AiAction[];
        };
        if (event.type === 'delta' && event.content) {
          handlers.onDelta?.(event.content);
        } else if (event.type === 'final') {
          handlers.onFinal?.({
            citations: event.citations ?? [],
            actions: event.actions ?? [],
          });
        } else if (event.type === 'error') {
          handlers.onError?.(event.message || '对话失败');
        } else if (event.type === 'done') {
          handlers.onDone?.();
        }
      } catch {
        // ignore malformed chunk
      }
    }
  }
  handlers.onDone?.();
}

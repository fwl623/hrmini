/**
 * 聊天内员工名单（只读 INFO_LIST）
 */
import { Button, Tag } from 'antd';
import React from 'react';
import type { AiAction } from '@/services/ai';

type Props = {
  action: AiAction;
  onNavigate?: (route: string) => void;
};

const STATUS_LABEL: Record<string, string> = {
  probation: '试用',
  regular: '正式',
  pending_resign: '待离职',
  resigned: '已离职',
  '10': '试用',
  '20': '正式',
  '30': '待离职',
  '40': '已离职',
};

const AiEmployeeListCard: React.FC<Props> = ({ action, onNavigate }) => {
  const items = action.items ?? [];
  const total = action.meta?.total ?? items.length;
  const title = action.meta?.title || action.label || '员工名单';
  const hint = action.meta?.hint;
  const empty = items.length === 0;

  return (
    <div className="ai-biz-card ai-info-card" onClick={(e) => e.stopPropagation()}>
      <div className="ai-biz-card__title">{title}</div>
      <div className="ai-biz-card__hint">
        {hint || (empty ? '暂无数据' : `共 ${total} 人，下方展示前 ${items.length} 条`)}
      </div>
      {!empty && (
        <div className="ai-info-list">
          {items.map((row, idx) => (
            <div key={row.employeeId ?? `${row.empNo}-${idx}`} className="ai-info-item">
              <div className="ai-info-item__head">
                <span className="ai-info-item__title">{row.name || '—'}</span>
                {row.employmentStatus ? (
                  <Tag>{STATUS_LABEL[row.employmentStatus] || row.employmentStatus}</Tag>
                ) : null}
              </div>
              <div className="ai-info-item__meta">
                {[row.empNo, row.department, row.position, row.grade].filter(Boolean).join(' · ')}
              </div>
            </div>
          ))}
        </div>
      )}
      {action.route ? (
        <Button
          type="link"
          size="small"
          style={{ paddingLeft: 0, marginTop: 4 }}
          onClick={() => onNavigate?.(action.route!)}
        >
          打开花名册
        </Button>
      ) : null}
    </div>
  );
};

export default AiEmployeeListCard;

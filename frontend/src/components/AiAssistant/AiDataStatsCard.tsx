/**
 * 聊天内统计卡片（只读 DATA_CARD：部门人数 / 假期余额等）
 */
import { Button } from 'antd';
import React from 'react';
import type { AiAction } from '@/services/ai';

type Props = {
  action: AiAction;
  onNavigate?: (route: string) => void;
};

const AiDataStatsCard: React.FC<Props> = ({ action, onNavigate }) => {
  const stats = action.stats ?? [];
  const hint = action.hint || action.meta?.hint;

  return (
    <div className="ai-biz-card ai-stats-card" onClick={(e) => e.stopPropagation()}>
      <div className="ai-biz-card__title">{action.label || '统计'}</div>
      {hint ? <div className="ai-biz-card__hint">{hint}</div> : null}
      {!!stats.length && (
        <div className="ai-stats-grid">
          {stats.map((s, i) => (
            <div key={`${s.label}-${i}`} className="ai-stats-cell">
              <div className="ai-stats-cell__value">{s.value}</div>
              <div className="ai-stats-cell__label">{s.label}</div>
            </div>
          ))}
        </div>
      )}
      {!stats.length && !hint ? (
        <div className="ai-biz-card__hint">暂无统计数据</div>
      ) : null}
      {action.route ? (
        <Button
          type="link"
          size="small"
          style={{ paddingLeft: 0, marginTop: 8 }}
          onClick={() => onNavigate?.(action.route!)}
        >
          查看详情
        </Button>
      ) : null}
    </div>
  );
};

export default AiDataStatsCard;

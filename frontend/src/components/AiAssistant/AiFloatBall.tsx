import { CloseOutlined, CustomerServiceOutlined, RobotOutlined } from '@ant-design/icons';
import { Avatar, Drawer, Space, Typography } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import AiChatPanel, { FLOAT_HIDDEN_KEY } from './AiChatPanel';
import './ai.less';

const { Text } = Typography;

const POS_KEY = 'hrms.ai.float.pos';

type Pos = { x: number; y: number };

function loadPos(): Pos {
  try {
    const raw = localStorage.getItem(POS_KEY);
    if (raw) {
      const p = JSON.parse(raw) as Pos;
      if (typeof p.x === 'number' && typeof p.y === 'number') return p;
    }
  } catch {
    // ignore
  }
  return { x: window.innerWidth - 88, y: window.innerHeight - 120 };
}

/**
 * 可拖动悬浮球：悬停 1.5s 出关闭按钮；关闭写入 localStorage。
 */
const AiFloatBall: React.FC = () => {
  const [hidden, setHidden] = useState(() => localStorage.getItem(FLOAT_HIDDEN_KEY) === '1');
  const [pos, setPos] = useState<Pos>(() => loadPos());
  const [open, setOpen] = useState(false);
  const [showClose, setShowClose] = useState(false);
  const dragRef = useRef<{
    dragging: boolean;
    moved: boolean;
    startX: number;
    startY: number;
    origX: number;
    origY: number;
  } | null>(null);
  const hoverTimer = useRef<number | null>(null);

  const syncHidden = useCallback(() => {
    setHidden(localStorage.getItem(FLOAT_HIDDEN_KEY) === '1');
  }, []);

  useEffect(() => {
    window.addEventListener('hrms-ai-float-restore', syncHidden);
    return () => window.removeEventListener('hrms-ai-float-restore', syncHidden);
  }, [syncHidden]);

  useEffect(() => {
    const onMove = (e: PointerEvent) => {
      const d = dragRef.current;
      if (!d?.dragging) return;
      const dx = e.clientX - d.startX;
      const dy = e.clientY - d.startY;
      if (Math.abs(dx) + Math.abs(dy) > 5) d.moved = true;
      const next = {
        x: Math.min(Math.max(8, d.origX + dx), window.innerWidth - 64),
        y: Math.min(Math.max(8, d.origY + dy), window.innerHeight - 64),
      };
      setPos(next);
    };
    const onUp = () => {
      const d = dragRef.current;
      if (!d?.dragging) return;
      d.dragging = false;
      setPos((p) => {
        localStorage.setItem(POS_KEY, JSON.stringify(p));
        return p;
      });
      window.setTimeout(() => {
        if (dragRef.current) dragRef.current.moved = false;
      }, 0);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
  }, []);

  if (hidden) return null;

  return (
    <>
      <div
        className="ai-float-ball"
        style={{ left: pos.x, top: pos.y }}
        onPointerDown={(e) => {
          dragRef.current = {
            dragging: true,
            moved: false,
            startX: e.clientX,
            startY: e.clientY,
            origX: pos.x,
            origY: pos.y,
          };
        }}
        onClick={() => {
          if (dragRef.current?.moved) return;
          setOpen(true);
        }}
        onMouseEnter={() => {
          hoverTimer.current = window.setTimeout(() => setShowClose(true), 1500);
        }}
        onMouseLeave={() => {
          if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
          setShowClose(false);
        }}
        title="助理小R"
      >
        <CustomerServiceOutlined style={{ fontSize: 26 }} />
        {showClose && (
          <button
            type="button"
            className="ai-float-close"
            aria-label="关闭悬浮球"
            onClick={(e) => {
              e.stopPropagation();
              localStorage.setItem(FLOAT_HIDDEN_KEY, '1');
              setHidden(true);
              setShowClose(false);
            }}
          >
            <CloseOutlined style={{ fontSize: 10 }} />
          </button>
        )}
      </div>

      <Drawer
        title={
          <Space>
            <Avatar
              size={28}
              style={{ background: 'linear-gradient(135deg,#1677ff,#69b1ff)' }}
              icon={<RobotOutlined />}
            />
            <span>助理小R</span>
          </Space>
        }
        open={open}
        onClose={() => setOpen(false)}
        width={440}
        destroyOnClose={false}
        styles={{
          header: {
            background: 'linear-gradient(90deg, #e6f4ff, #fff)',
            borderBottom: '1px solid #bae0ff',
          },
          body: { padding: 10, height: 'calc(100% - 55px)', background: '#f7fbff' },
        }}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 8, padding: '0 4px' }}>
          拖拽左下角悬浮球可调整位置 · 悬停 1.5 秒可关闭
        </Text>
        <div style={{ height: 'calc(100% - 28px)' }}>
          <AiChatPanel dense />
        </div>
      </Drawer>
    </>
  );
};

export default AiFloatBall;

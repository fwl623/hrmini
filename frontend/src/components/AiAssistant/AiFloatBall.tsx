import { CloseOutlined } from '@ant-design/icons';
import { Drawer, Space, Typography } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import AiChatPanel, { FLOAT_HIDDEN_KEY } from './AiChatPanel';
import AiOwlAvatar from './AiOwlAvatar';
import AiPetOwl, { type PetMood } from './AiPetOwl';
import './ai.less';

const { Text } = Typography;

const POS_KEY = 'hrms.ai.float.pos';
const PET_VERSION_KEY = 'hrms.ai.float.petVersion';
/** 升版本会强制重新显示一次（解决被关闭后看不见） */
const PET_VERSION = 'owl-pet-v3';

type Pos = { x: number; y: number };

function forceShowPetIfNewVersion() {
  try {
    if (localStorage.getItem(PET_VERSION_KEY) !== PET_VERSION) {
      localStorage.removeItem(FLOAT_HIDDEN_KEY);
      localStorage.setItem(PET_VERSION_KEY, PET_VERSION);
    }
  } catch {
    // ignore
  }
}

function clampPos(p: Pos): Pos {
  const w = typeof window !== 'undefined' ? window.innerWidth : 1200;
  const h = typeof window !== 'undefined' ? window.innerHeight : 800;
  return {
    x: Math.min(Math.max(8, p.x), Math.max(8, w - 100)),
    y: Math.min(Math.max(8, p.y), Math.max(8, h - 120)),
  };
}

function loadPos(): Pos {
  try {
    const raw = localStorage.getItem(POS_KEY);
    if (raw) {
      const p = JSON.parse(raw) as Pos;
      if (typeof p.x === 'number' && typeof p.y === 'number' && Number.isFinite(p.x) && Number.isFinite(p.y)) {
        return clampPos(p);
      }
    }
  } catch {
    // ignore
  }
  const w = typeof window !== 'undefined' ? window.innerWidth : 1200;
  const h = typeof window !== 'undefined' ? window.innerHeight : 800;
  return clampPos({ x: w - 108, y: h - 150 });
}

function readHidden(): boolean {
  forceShowPetIfNewVersion();
  try {
    return localStorage.getItem(FLOAT_HIDDEN_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * 助理小R 桌宠：渲染到 document.body，避免被后台 Layout 裁剪；
 * 可拖拽、眼神跟随、点击打开对话。
 */
const AiFloatBall: React.FC = () => {
  const [hidden, setHidden] = useState(readHidden);
  const [pos, setPos] = useState<Pos>(loadPos);
  const [open, setOpen] = useState(false);
  const [showClose, setShowClose] = useState(false);
  const [mood, setMood] = useState<PetMood>('idle');
  const [tip, setTip] = useState<string | null>(null);
  const [mounted, setMounted] = useState(false);
  const dragRef = useRef<{
    dragging: boolean;
    moved: boolean;
    startX: number;
    startY: number;
    origX: number;
    origY: number;
  } | null>(null);
  const hoverTimer = useRef<number | null>(null);
  const tipTimer = useRef<number | null>(null);
  const clickCount = useRef(0);

  const syncHidden = useCallback(() => {
    setHidden(localStorage.getItem(FLOAT_HIDDEN_KEY) === '1');
  }, []);

  const flashTip = useCallback((text: string) => {
    setTip(text);
    if (tipTimer.current) window.clearTimeout(tipTimer.current);
    tipTimer.current = window.setTimeout(() => setTip(null), 2200);
  }, []);

  useEffect(() => {
    setMounted(true);
    // 挂载时再强制同步一次：聊天页 restore 事件可能早于本组件监听
    forceShowPetIfNewVersion();
    syncHidden();
    setPos(loadPos());
  }, [syncHidden]);

  useEffect(() => {
    window.addEventListener('hrms-ai-float-restore', syncHidden);
    const onResize = () => setPos((p) => clampPos(p));
    window.addEventListener('resize', onResize);
    return () => {
      window.removeEventListener('hrms-ai-float-restore', syncHidden);
      window.removeEventListener('resize', onResize);
    };
  }, [syncHidden]);

  useEffect(() => {
    setMood(open ? 'listen' : 'idle');
  }, [open]);

  useEffect(() => {
    const onMove = (e: PointerEvent) => {
      const d = dragRef.current;
      if (!d?.dragging) return;
      const dx = e.clientX - d.startX;
      const dy = e.clientY - d.startY;
      if (Math.abs(dx) + Math.abs(dy) > 5) d.moved = true;
      setPos(
        clampPos({
          x: d.origX + dx,
          y: d.origY + dy,
        }),
      );
    };
    const onUp = () => {
      const d = dragRef.current;
      if (!d?.dragging) return;
      d.dragging = false;
      setPos((p) => {
        const next = clampPos(p);
        localStorage.setItem(POS_KEY, JSON.stringify(next));
        return next;
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

  if (!mounted || hidden) return null;

  const node = (
    <>
      <div
        className={`ai-pet ${mood === 'happy' ? 'ai-pet--happy' : ''} ${open ? 'ai-pet--listen' : ''}`}
        style={{ left: pos.x, top: pos.y }}
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture?.(e.pointerId);
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
          clickCount.current += 1;
          if (clickCount.current >= 3) {
            clickCount.current = 0;
            setMood('happy');
            flashTip('咕咕～被戳开心了！');
            window.setTimeout(() => setMood(open ? 'listen' : 'idle'), 900);
            return;
          }
          window.setTimeout(() => {
            clickCount.current = 0;
          }, 600);
          setOpen(true);
        }}
        onMouseEnter={() => {
          hoverTimer.current = window.setTimeout(() => setShowClose(true), 1200);
          if (!open) flashTip('点我问问小R～');
        }}
        onMouseLeave={() => {
          if (hoverTimer.current) window.clearTimeout(hoverTimer.current);
          setShowClose(false);
        }}
        title="助理小R · 拖我移动 · 点我聊天"
      >
        <div className="ai-pet-glow" aria-hidden />
        <AiPetOwl className="ai-pet-owl" mood={mood} />
        {tip && <div className="ai-pet-bubble">{tip}</div>}
        {showClose && (
          <button
            type="button"
            className="ai-float-close"
            aria-label="关闭桌宠"
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
            <AiOwlAvatar size={28} />
            <span>助理小R</span>
          </Space>
        }
        open={open}
        onClose={() => setOpen(false)}
        width={440}
        destroyOnClose={false}
        getContainer={() => document.body}
        styles={{
          header: {
            background: 'rgba(255,255,255,0.92)',
            borderBottom: '1px solid rgba(215,228,251,0.85)',
          },
          body: {
            padding: 12,
            height: 'calc(100% - 55px)',
            background: 'linear-gradient(180deg, #f4f7fb 0%, #f8fafc 100%)',
          },
        }}
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 8, padding: '0 4px', fontSize: 12 }}>
          拖拽换位 · 连点三次会开心 · 悬停可关闭
        </Text>
        <div style={{ height: 'calc(100% - 28px)' }}>
          <AiChatPanel dense />
        </div>
      </Drawer>
    </>
  );

  return createPortal(node, document.body);
};

export default AiFloatBall;

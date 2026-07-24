import React, { useEffect, useRef, useState } from 'react';

export type PetMood = 'idle' | 'happy' | 'listen';

type PupilOffset = { x: number; y: number };

type Props = {
  mood?: PetMood;
  /** 瞳孔最大偏移 */
  maxOffset?: number;
  className?: string;
};

/**
 * 助理小R 桌宠猫头鹰：蓝白卡通，双眼跟随鼠标；mood 控制轻互动表情。
 */
const AiPetOwl: React.FC<Props> = ({ mood = 'idle', maxOffset = 5.5, className }) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const [left, setLeft] = useState<PupilOffset>({ x: 0, y: 0 });
  const [right, setRight] = useState<PupilOffset>({ x: 0, y: 0 });

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      const svg = svgRef.current;
      if (!svg) return;

      const updateEye = (cx: number, cy: number): PupilOffset => {
        const pt = svg.createSVGPoint();
        pt.x = e.clientX;
        pt.y = e.clientY;
        const ctm = svg.getScreenCTM();
        if (!ctm) return { x: 0, y: 0 };
        const local = pt.matrixTransform(ctm.inverse());
        const dx = local.x - cx;
        const dy = local.y - cy;
        const dist = Math.hypot(dx, dy) || 1;
        const scale = Math.min(maxOffset / dist, 1);
        return { x: dx * scale, y: dy * scale };
      };

      setLeft(updateEye(70, 78));
      setRight(updateEye(130, 78));
    };

    window.addEventListener('mousemove', onMove, { passive: true });
    return () => window.removeEventListener('mousemove', onMove);
  }, [maxOffset]);

  const cheekOpacity = mood === 'happy' ? 0.9 : mood === 'listen' ? 0.55 : 0.35;
  const wingTilt = mood === 'happy' ? 8 : mood === 'listen' ? 3 : 0;

  return (
    <svg
      ref={svgRef}
      className={className}
      viewBox="0 0 200 170"
      role="img"
      aria-label="助理小R"
    >
      <ellipse cx="100" cy="118" rx="54" ry="42" fill="#2B6BFF" stroke="#143CB8" strokeWidth="5" />
      <ellipse cx="100" cy="124" rx="30" ry="24" fill="#FFFFFF" />

      <ellipse
        cx="46"
        cy="118"
        rx="18"
        ry="30"
        fill="#4D86FF"
        stroke="#143CB8"
        strokeWidth="4"
        transform={`rotate(${-16 - wingTilt} 46 118)`}
      />
      <ellipse
        cx="154"
        cy="118"
        rx="18"
        ry="30"
        fill="#4D86FF"
        stroke="#143CB8"
        strokeWidth="4"
        transform={`rotate(${16 + wingTilt} 154 118)`}
      />

      <circle cx="100" cy="72" r="52" fill="#2B6BFF" stroke="#143CB8" strokeWidth="5" />
      <path
        d="M52 34 L66 62 L42 58 Z"
        fill="#2B6BFF"
        stroke="#143CB8"
        strokeWidth="4"
        strokeLinejoin="round"
      />
      <path
        d="M148 34 L134 62 L158 58 Z"
        fill="#2B6BFF"
        stroke="#143CB8"
        strokeWidth="4"
        strokeLinejoin="round"
      />

      <circle cx="70" cy="78" r="22" fill="#FFFFFF" stroke="#143CB8" strokeWidth="4" />
      <circle cx="130" cy="78" r="22" fill="#FFFFFF" stroke="#143CB8" strokeWidth="4" />

      <g transform={`translate(${left.x} ${left.y})`}>
        <circle cx="70" cy="78" r="9.5" fill="#1A1A1A" />
        {mood === 'listen' && <circle cx="70" cy="78" r="3.5" fill="#4096FF" />}
      </g>
      <g transform={`translate(${right.x} ${right.y})`}>
        <circle cx="130" cy="78" r="9.5" fill="#1A1A1A" />
        {mood === 'listen' && <circle cx="130" cy="78" r="3.5" fill="#4096FF" />}
      </g>

      <ellipse cx="52" cy="98" rx="8" ry="5" fill="#FF8FAB" opacity={cheekOpacity} />
      <ellipse cx="148" cy="98" rx="8" ry="5" fill="#FF8FAB" opacity={cheekOpacity} />

      <path
        d="M100 88 L90 102 L110 102 Z"
        fill="#FFB020"
        stroke="#E08A00"
        strokeWidth="2"
        strokeLinejoin="round"
      />

      <ellipse cx="82" cy="158" rx="14" ry="6" fill="#FF9F1A" stroke="#E08A00" strokeWidth="2.5" />
      <ellipse cx="118" cy="158" rx="14" ry="6" fill="#FF9F1A" stroke="#E08A00" strokeWidth="2.5" />
    </svg>
  );
};

export default AiPetOwl;

import React, { useEffect, useRef, useState } from 'react';

type PupilOffset = { x: number; y: number };

const MAX_OFFSET = 6.5;

/**
 * 蓝白扁平滑稽猫头鹰：瞳孔跟随鼠标；造型贴近参考稿（圆头、大眼、站在登录框上）。
 */
const TrackingOwl: React.FC = () => {
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
        const scale = Math.min(MAX_OFFSET / dist, 1);
        return { x: dx * scale, y: dy * scale };
      };

      setLeft(updateEye(70, 78));
      setRight(updateEye(130, 78));
    };

    window.addEventListener('mousemove', onMove, { passive: true });
    return () => window.removeEventListener('mousemove', onMove);
  }, []);

  return (
    <svg
      ref={svgRef}
      className="hrms-login-owl"
      viewBox="0 0 200 170"
      role="img"
      aria-label="登录猫头鹰"
    >
      {/* 身体 */}
      <ellipse cx="100" cy="118" rx="54" ry="42" fill="#2B6BFF" stroke="#143CB8" strokeWidth="5" />
      {/* 肚皮 */}
      <ellipse cx="100" cy="124" rx="30" ry="24" fill="#FFFFFF" />

      {/* 翅膀 */}
      <ellipse
        cx="46"
        cy="118"
        rx="18"
        ry="30"
        fill="#4D86FF"
        stroke="#143CB8"
        strokeWidth="4"
        transform="rotate(-16 46 118)"
      />
      <ellipse
        cx="154"
        cy="118"
        rx="18"
        ry="30"
        fill="#4D86FF"
        stroke="#143CB8"
        strokeWidth="4"
        transform="rotate(16 154 118)"
      />

      {/* 头 */}
      <circle cx="100" cy="72" r="52" fill="#2B6BFF" stroke="#143CB8" strokeWidth="5" />

      {/* 耳羽 */}
      <path d="M52 34 L66 62 L42 58 Z" fill="#2B6BFF" stroke="#143CB8" strokeWidth="4" strokeLinejoin="round" />
      <path d="M148 34 L134 62 L158 58 Z" fill="#2B6BFF" stroke="#143CB8" strokeWidth="4" strokeLinejoin="round" />

      {/* 大眼白 */}
      <circle cx="70" cy="78" r="22" fill="#FFFFFF" stroke="#143CB8" strokeWidth="4" />
      <circle cx="130" cy="78" r="22" fill="#FFFFFF" stroke="#143CB8" strokeWidth="4" />

      {/* 瞳孔 */}
      <g transform={`translate(${left.x} ${left.y})`}>
        <circle cx="70" cy="78" r="9.5" fill="#1A1A1A" />
      </g>
      <g transform={`translate(${right.x} ${right.y})`}>
        <circle cx="130" cy="78" r="9.5" fill="#1A1A1A" />
      </g>

      {/* 喙 */}
      <path d="M100 88 L90 102 L110 102 Z" fill="#FFB020" stroke="#E08A00" strokeWidth="2" strokeLinejoin="round" />

      {/* 脚（压在登录框上沿） */}
      <ellipse cx="82" cy="158" rx="16" ry="7" fill="#FF9F1A" stroke="#E08A00" strokeWidth="2.5" />
      <ellipse cx="118" cy="158" rx="16" ry="7" fill="#FF9F1A" stroke="#E08A00" strokeWidth="2.5" />
    </svg>
  );
};

export default TrackingOwl;

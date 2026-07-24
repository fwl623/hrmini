import React from 'react';

type Props = {
  size?: number;
  className?: string;
};

/**
 * 助理小R 头像（猫头鹰头部，用于对话页 / Drawer，无鼠标追踪以保持轻量）。
 */
const AiOwlAvatar: React.FC<Props> = ({ size = 36, className }) => (
  <span
    className={`ai-owl-avatar${className ? ` ${className}` : ''}`}
    style={{ width: size, height: size }}
  >
    <svg viewBox="0 0 100 100" width="100%" height="100%" aria-hidden>
      <defs>
        <linearGradient id="aiOwlFace" x1="20" y1="10" x2="80" y2="90" gradientUnits="userSpaceOnUse">
          <stop stopColor="#4B8BFF" />
          <stop offset="1" stopColor="#1E5AE8" />
        </linearGradient>
      </defs>
      <circle cx="50" cy="50" r="48" fill="url(#aiOwlFace)" />
      <path d="M20 22 L33 40 L12 36 Z" fill="#0F3BB0" />
      <path d="M80 22 L67 40 L88 36 Z" fill="#0F3BB0" />
      <circle cx="34" cy="50" r="15.5" fill="#FFFFFF" />
      <circle cx="66" cy="50" r="15.5" fill="#FFFFFF" />
      <circle cx="34" cy="51.5" r="6.2" fill="#1A1A1A" />
      <circle cx="66" cy="51.5" r="6.2" fill="#1A1A1A" />
      <circle cx="31.5" cy="48.5" r="2" fill="#FFFFFF" opacity="0.95" />
      <circle cx="63.5" cy="48.5" r="2" fill="#FFFFFF" opacity="0.95" />
      <path d="M50 55 L45 64.5 L55 64.5 Z" fill="#FFB020" />
      <ellipse cx="22" cy="64" rx="5.5" ry="3.8" fill="#FF8FAB" opacity="0.7" />
      <ellipse cx="78" cy="64" rx="5.5" ry="3.8" fill="#FF8FAB" opacity="0.7" />
    </svg>
  </span>
);

export default AiOwlAvatar;

import type { PositionSequence } from '@/services/org';

/** 序列 → 合法职级（对齐 GradeRangeValidator） */
export const SEQUENCE_RANK_MAP: Record<PositionSequence, string[]> = {
  M: ['M1', 'M2', 'M3', 'M4', 'M5'],
  P: ['P1', 'P2', 'P3', 'P4', 'P5', 'P6', 'P7', 'P8', 'P9', 'P10'],
  S: ['S1', 'S2', 'S3', 'S4', 'S5'],
};

export const SEQUENCE_META: Record<
  PositionSequence,
  {
    label: string;
    shortLabel: string;
    color: string;
    bgSoft: string;
    gradeRange: string;
    description: string;
  }
> = {
  M: {
    label: '管理序列',
    shortLabel: 'M 序列',
    color: '#722ed1',
    bgSoft: '#f9f0ff',
    gradeRange: 'M1 ~ M5',
    description: '负责团队管理与业务决策',
  },
  P: {
    label: '专业序列',
    shortLabel: 'P 序列',
    color: '#1677ff',
    bgSoft: '#e6f4ff',
    gradeRange: 'P1 ~ P10',
    description: '专注技术与专业能力发展',
  },
  S: {
    label: '支持序列',
    shortLabel: 'S 序列',
    color: '#52c41a',
    bgSoft: '#f6ffed',
    gradeRange: 'S1 ~ S5',
    description: '提供职能支持与服务保障',
  },
};

/** 底部职级对照表（静态，对齐原型） */
export const GRADE_REFERENCE: Record<
  PositionSequence,
  { grade: string; title: string }[]
> = {
  M: [
    { grade: 'M1', title: '主管' },
    { grade: 'M2', title: '经理' },
    { grade: 'M3', title: '总监' },
    { grade: 'M4', title: '高级总监' },
    { grade: 'M5', title: 'VP' },
  ],
  P: [
    { grade: 'P3', title: '初级' },
    { grade: 'P5', title: '中级' },
    { grade: 'P7', title: '高级' },
    { grade: 'P9', title: '专家' },
    { grade: 'P10', title: '资深专家' },
  ],
  S: [
    { grade: 'S1', title: '助理' },
    { grade: 'S2', title: '专员' },
    { grade: 'S3', title: '高级专员' },
    { grade: 'S4', title: '主管' },
    { grade: 'S5', title: '经理' },
  ],
};

export function gradeOptionsOf(sequence?: PositionSequence | null): string[] {
  if (!sequence) return [];
  return SEQUENCE_RANK_MAP[sequence] ?? [];
}

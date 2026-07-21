/**
 * 人力资源数据概览 API
 */
import { request } from '@umijs/max';

const API_BASE = '/api/v1/analytics';

export interface HeadcountTrendPoint {
  date: string;
  hires: number;
  resignations: number;
}

export interface DeptDistItem {
  deptName: string;
  headcount: number;
}

export interface WorkflowThroughputItem {
  processType: string;
  label: string;
  submitted: number;
  approved: number;
}

export interface CostTrendItem {
  period: string;
  netTotal: number;
}

export interface KpiRow {
  metric: string;
  value: string;
  changeRate?: number | null;
  trend: string;
}

export interface AnalyticsOverview {
  from: string;
  to: string;
  headcountTrend: HeadcountTrendPoint[];
  deptDistribution: DeptDistItem[];
  workflowThroughput: WorkflowThroughputItem[];
  costTrend: CostTrendItem[];
  kpiTable: KpiRow[];
}

export async function getAnalyticsOverview(params: { from: string; to: string }) {
  return request<API.Result<AnalyticsOverview>>(`${API_BASE}/overview`, {
    method: 'GET',
    params,
  });
}

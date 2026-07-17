/**
 * 工作台 API
 */
import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';

export interface WorkbenchSummary {
  totalEmployees: number;
  newHiresThisMonth: number;
  pendingApprovals: number;
  attendanceAnomalies: number;
  todayPunchRate?: number | null;
  departmentStats?: { deptName: string; headcount: number }[];
  visitTrend?: { date: string; count: number }[];
  recentOperations?: {
    id: number;
    userId: number;
    module: string;
    action: string;
    targetId?: string;
    createdAt?: string;
  }[];
}

export async function getWorkbenchSummary() {
  return request<API.Result<WorkbenchSummary>>(`${API_BASE}/workbench/summary`, {
    method: 'GET',
  });
}

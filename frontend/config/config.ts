import { defineConfig } from '@umijs/max';

/**
 * Umi Max 工程配置（对齐前端系分 v1.8）
 * - 管理端 /admin/*
 * - 员工门户 /portal/*
 * - API 代理 /api → 后端
 * 子路由须用相对 path，不可再写绝对 `/admin/...`（否则 RR6 白屏）
 */
export default defineConfig({
  title: 'HRMS',
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  layout: false,
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: './login', layout: false },
    {
      path: '/admin',
      component: '@/layouts/AdminLayout',
      wrappers: ['@/wrappers/auth'],
      routes: [
        { path: '', redirect: '/admin/workbench' },
        { path: 'workbench', component: './admin/workbench' },

        // 组织管理（Day 3）
        {
          path: 'org/departments',
          component: './admin/org/departments',
          access: 'canViewDept',
        },
        {
          path: 'org/positions',
          component: './admin/org/positions',
          access: 'canViewPosition',
        },

        // 员工管理
        { path: 'employee/list', component: './admin/employee/list' },

        // 入转调离（成员 C）
        { path: 'onboarding', component: './admin/onboarding' },
        { path: 'regularization', component: './admin/regularization' },
        { path: 'transfers', component: './admin/transfers' },
        { path: 'resignation', component: './admin/resignation' },
        { path: 'approval', component: './admin/approval' },
        { path: 'delegation', component: './admin/delegation' },

        // 考勤管理
        { path: 'attendance/groups', component: './admin/attendance/groups' },
        { path: 'attendance/punch', component: './admin/attendance/punch' },
        { path: 'attendance/records', component: './admin/attendance/records' },
        { path: 'attendance/holidays', component: './admin/attendance/holidays' },
        { path: 'attendance/summary', component: './admin/attendance/summary' },
        { path: 'attendance/statistics', component: './admin/attendance/statistics' },

        // 请假加班管理
        { path: 'leave/list', component: './admin/leave' },
        { path: 'overtime/list', component: './admin/overtime' },

        // 薪资管理（SYS_ADMIN 不可见）
        { path: 'payroll/schemes', component: './admin/payroll/schemes', access: 'canViewPayroll' },
        { path: 'payroll/batches', component: './admin/payroll/batches', access: 'canViewPayroll' },
        { path: 'payroll/payslips', component: './admin/payroll/payslips', access: 'canViewPayroll' },
        { path: 'payroll/cost-report', component: './admin/payroll/cost-report', access: 'canViewPayroll' },

        // 系统设置（Day 4，仅 SYS_ADMIN）
        { path: 'system/users', component: './admin/system/users', access: 'canManageSystem' },
        { path: 'system/roles', component: './admin/system/roles', access: 'canManageSystem' },
        {
          path: 'system/operation-logs',
          component: './admin/system/operation-logs',
          access: 'canManageSystem',
        },
        {
          path: 'system/login-logs',
          component: './admin/system/login-logs',
          access: 'canManageSystem',
        },
      ],
    },
    {
      path: '/portal',
      component: '@/layouts/PortalLayout',
      wrappers: ['@/wrappers/auth'],
      routes: [
        { path: '', redirect: '/portal/profile' },
        { path: 'profile', component: './portal/profile' },
        { path: 'attendance', component: './portal/attendance' },
        { path: 'leave', component: './portal/leave' },
        { path: 'overtime', component: './portal/overtime' },
        { path: 'payslips', component: './portal/payslips', access: 'canViewPayroll' },
        { path: 'resignation', component: './portal/resignation' },
        { path: 'security', component: './portal/security' },
      ],
    },
  ],
  npmClient: 'npm',
  proxy: {
    '/api': {
      target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});

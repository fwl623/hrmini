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

        // 考勤管理
        { path: 'attendance/groups', component: './admin/attendance/groups' },

        // 薪资管理（SYS_ADMIN 不可见）
        { path: 'payroll/schemes', component: './admin/payroll/schemes', access: 'canViewPayroll' },

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
        { path: 'leave', component: './portal/profile' },
        { path: 'overtime', component: './portal/profile' },
        { path: 'payslips', component: './portal/profile', access: 'canViewPayroll' },
        { path: 'resignation', component: './portal/profile' },
        { path: 'security', component: './portal/profile' },
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

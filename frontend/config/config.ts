import { defineConfig } from '@umijs/max';

/**
 * Umi Max 工程配置（对齐前端系分 v1.8）
 * - 管理端 /admin/*
 * - 员工门户 /portal/*
 * - API 代理 /api → 后端
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
        { path: '/admin', redirect: '/admin/workbench' },
        { path: '/admin/workbench', component: './admin/workbench' },

        // 组织管理（Day 3）
        { path: '/admin/org/departments', component: './admin/workbench' },
        { path: '/admin/org/positions', component: './admin/workbench' },

        // 员工管理
        { path: '/admin/employee/list', component: './admin/employee/list' },

        // 考勤管理
        { path: '/admin/attendance/groups', component: './admin/attendance/groups' },

        // 薪资管理
        { path: '/admin/payroll/schemes', component: './admin/payroll/schemes' },

        // 系统设置（Day 4 占位）
        { path: '/admin/system/users', component: './admin/workbench' },
      ],
    },
    {
      path: '/portal',
      component: '@/layouts/PortalLayout',
      wrappers: ['@/wrappers/auth'],
      routes: [
        { path: '/portal', redirect: '/portal/profile' },
        { path: '/portal/profile', component: './portal/profile' },
        { path: '/portal/attendance', component: './portal/attendance' },
        { path: '/portal/leave', component: './portal/profile' },
        { path: '/portal/overtime', component: './portal/profile' },
        { path: '/portal/payslips', component: './portal/profile' },
        { path: '/portal/resignation', component: './portal/profile' },
        { path: '/portal/security', component: './portal/profile' },
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

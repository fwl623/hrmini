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
      routes: [
        { path: '/admin', redirect: '/admin/workbench' },
        { path: '/admin/workbench', component: './admin/workbench' },

        // 考勤管理
        { path: '/admin/attendance/groups', component: './admin/attendance/groups' },

        // 薪资管理
        { path: '/admin/payroll/schemes', component: './admin/payroll/schemes' },
      ],
    },
    {
      path: '/portal',
      component: '@/layouts/PortalLayout',
      routes: [
        { path: '/portal', redirect: '/portal/profile' },
        { path: '/portal/profile', component: './portal/profile' },

        // 员工考勤
        { path: '/portal/attendance', component: './portal/attendance' },
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

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
      ],
    },
    {
      path: '/portal',
      component: '@/layouts/PortalLayout',
      routes: [
        { path: '/portal', redirect: '/portal/profile' },
        { path: '/portal/profile', component: './portal/profile' },
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

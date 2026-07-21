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
    { path: '/', component: './home-redirect', layout: false },
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

        // 员工管理（详情/编辑须在 list 之后；:id/edit 须在 :id 之前）
        { path: 'employee/list', component: './admin/employee/list', access: 'canViewEmployee' },
        {
          path: 'employee/mobile-change',
          component: './admin/employee/mobile-change',
          access: 'canManageMobileChange',
        },
        { path: 'employee/:id/edit', component: './admin/employee/edit', access: 'canViewEmployee' },
        { path: 'employee/:id', component: './admin/employee/detail', access: 'canViewEmployee' },

        // 入转调离（成员 C）；FINANCE 不可进
        { path: 'onboarding', component: './admin/onboarding', access: 'canManageWorkflow' },
        {
          path: 'regularization',
          component: './admin/regularization',
          access: 'canManageWorkflow',
        },
        { path: 'transfers', component: './admin/transfers', access: 'canManageWorkflow' },
        { path: 'resignation', component: './admin/resignation', access: 'canManageResignation' },
        { path: 'approval', component: './admin/approval', access: 'canApprove' },
        { path: 'delegation', component: './admin/delegation', access: 'canApprove' },

        // 考勤管理
        {
          path: 'attendance/groups',
          component: './admin/attendance/groups',
          access: 'canManageAttendance',
        },
        {
          path: 'attendance/punch',
          component: './admin/attendance/punch',
          access: 'canManageAttendance',
        },
        {
          path: 'attendance/records',
          component: './admin/attendance/records',
          access: 'canManageAttendance',
        },
        {
          path: 'attendance/holidays',
          component: './admin/attendance/holidays',
          access: 'canManageAttendance',
        },
        {
          path: 'attendance/summary',
          component: './admin/attendance/summary',
          access: 'canManageAttendance',
        },
        {
          path: 'attendance/statistics',
          component: './admin/attendance/statistics',
          access: 'canManageAttendance',
        },

        // 请假加班管理
        { path: 'leave/list', component: './admin/leave', access: 'canManageAttendance' },
        { path: 'overtime/list', component: './admin/overtime', access: 'canManageAttendance' },

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

        // 个人中心（复用门户页，仍留在管理后台布局内）
        { path: 'personal/profile', component: './portal/profile' },
        { path: 'personal/attendance', component: './portal/attendance' },
        { path: 'personal/leave', component: './portal/leave' },
        { path: 'personal/overtime', component: './portal/overtime' },
        {
          path: 'personal/payslips',
          component: './portal/payslips',
          access: 'canViewOwnPayslip',
        },
        { path: 'personal/resignation', component: './portal/resignation' },
        { path: 'personal/security', component: './portal/security' },

        // AI 智能助理
        { path: 'ai/chat', component: './admin/ai/chat', access: 'canUseAiAssistant' },
        {
          path: 'ai/knowledge',
          component: './admin/ai/knowledge',
          access: 'canManageAiKnowledge',
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
        { path: 'payslips', component: './portal/payslips', access: 'canViewOwnPayslip' },
        { path: 'resignation', component: './portal/resignation' },
        { path: 'security', component: './portal/security' },
        { path: 'ai/chat', component: './portal/ai/chat', access: 'canUseAiAssistant' },
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

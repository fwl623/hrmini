import { IdcardOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, history, useAccess, useLocation, useModel } from '@umijs/max';
import { Dropdown, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import React, { useMemo } from 'react';
import { forceLogout } from '@/utils/authSession';
import './layout.css';

const { Header, Sider, Content } = Layout;

type MenuItem = NonNullable<Required<MenuProps>['items']>[number];

/** 扩展 Antd Menu：自定义 accessKey 做权限过滤（非 Antd 标准字段） */
type AccessMenuItem = MenuItem & {
  accessKey?: string;
  children?: AccessMenuItem[];
};

function filterMenu(items: AccessMenuItem[], accessMap: Record<string, boolean>): MenuItem[] {
  return items
    .filter((item) => {
      if (!item || typeof item !== 'object') return false;
      const key = item.accessKey;
      if (!key) return true;
      return accessMap[key] !== false;
    })
    .map((item) => {
      if (!item || typeof item !== 'object') return item;
      if (item.children?.length) {
        const { accessKey: _omit, ...rest } = item;
        return { ...rest, children: filterMenu(item.children, accessMap) } as MenuItem;
      }
      const { accessKey: _omit, ...rest } = item;
      return rest as MenuItem;
    });
}

const AdminLayout: React.FC = () => {
  const location = useLocation();
  const access = useAccess();
  const { initialState } = useModel('@@initialState');
  const username = initialState?.currentUser?.username ?? '用户';

  // 对齐 PRD §2.2：FINANCE 仅工作台+薪资；组织/花名册/审批/考勤均不可见
  const menuAccess: Record<string, boolean> = {
    workbench: true,
    org: access.canViewDept || access.canViewPosition,
    employee: access.canViewEmployee,
    lifecycle: access.canManageWorkflow || access.canHr,
    approval: access.canApprove || access.canManageWorkflow,
    resignation: access.canManageResignation,
    attendance: access.canManageAttendance,
    payroll: access.canViewPayroll,
    system: access.canManageSystem,
  };

  const menuItems: MenuItem[] = useMemo(() => {
    const raw: AccessMenuItem[] = [
      { key: '/admin/workbench', label: '工作台', accessKey: 'workbench' },
      {
        key: '/admin/org',
        label: '组织管理',
        accessKey: 'org',
        children: [
          { key: '/admin/org/departments', label: '部门管理' },
          { key: '/admin/org/positions', label: '职位管理' },
        ],
      },
      {
        key: '/admin/employee',
        label: '员工管理',
        accessKey: 'employee',
        children: [
          { key: '/admin/employee/list', label: '花名册' },
          { key: '/admin/employee/mobile-change', label: '手机号变更' },
        ],
      },
      {
        key: 'group-lifecycle',
        label: '入转调离',
        accessKey: 'lifecycle',
        children: [
          { key: '/admin/onboarding', label: '入职管理' },
          { key: '/admin/regularization', label: '转正管理' },
          { key: '/admin/transfers', label: '调岗管理' },
          { key: '/admin/resignation', label: '离职管理', accessKey: 'resignation' },
        ],
      },
      {
        key: 'group-approval',
        label: '审批管理',
        accessKey: 'approval',
        children: [
          { key: '/admin/approval', label: '审批中心' },
          { key: '/admin/delegation', label: '审批委托' },
        ],
      },
      {
        key: '/admin/attendance',
        label: '考勤管理',
        accessKey: 'attendance',
        children: [
          { key: '/admin/attendance/groups', label: '考勤组管理' },
          { key: '/admin/attendance/punch', label: '打卡中心' },
          { key: '/admin/attendance/records', label: '打卡记录' },
          { key: '/admin/attendance/holidays', label: '法定节假日' },
          { key: '/admin/attendance/summary', label: '月考勤汇总' },
          { key: '/admin/attendance/statistics', label: '考勤统计' },
        ],
      },
      {
        key: '/admin/leave',
        label: '请假管理',
        accessKey: 'attendance',
        children: [{ key: '/admin/leave/list', label: '请假列表' }],
      },
      {
        key: '/admin/overtime',
        label: '加班管理',
        accessKey: 'attendance',
        children: [{ key: '/admin/overtime/list', label: '加班列表' }],
      },
      {
        key: '/admin/payroll',
        label: '薪资管理',
        accessKey: 'payroll',
        children: [
          { key: '/admin/payroll/schemes', label: '账套管理' },
          { key: '/admin/payroll/batches', label: '核算批次' },
          { key: '/admin/payroll/payslips', label: '工资条' },
          { key: '/admin/payroll/cost-report', label: '成本报表' },
        ],
      },
      {
        key: '/admin/system',
        label: '系统设置',
        accessKey: 'system',
        children: [
          { key: '/admin/system/users', label: '用户管理' },
          { key: '/admin/system/roles', label: '角色管理' },
          { key: '/admin/system/operation-logs', label: '操作日志' },
          { key: '/admin/system/login-logs', label: '登录日志' },
        ],
      },
    ];
    return filterMenu(raw, menuAccess);
  }, [access]);

  const openKeys = useMemo(() => {
    const path = location.pathname;
    if (
      path.startsWith('/admin/onboarding') ||
      path.startsWith('/admin/regularization') ||
      path.startsWith('/admin/transfers') ||
      path.startsWith('/admin/resignation')
    ) {
      return ['group-lifecycle'];
    }
    if (path.startsWith('/admin/approval') || path.startsWith('/admin/delegation')) {
      return ['group-approval'];
    }
    const parts = path.split('/').filter(Boolean);
    if (parts.length >= 2) {
      return [`/${parts[0]}/${parts[1]}`];
    }
    return [];
  }, [location.pathname]);

  const handleLogout = async () => {
    await forceLogout();
  };

  const userMenu: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <IdcardOutlined />,
      label: '个人中心',
      onClick: () => history.push('/portal/profile'),
    },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
  ];

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        theme="light"
        width={220}
        className="hrms-sider-scroll"
        style={{ height: '100vh', overflowY: 'auto', overflowX: 'hidden' }}
      >
        <div style={{ padding: 16, fontWeight: 600 }}>HRMS 管理后台</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={openKeys}
          items={menuItems}
          onClick={({ key }) => {
            if (String(key).startsWith('group-')) return;
            history.push(key);
          }}
        />
      </Sider>
      <Layout style={{ height: '100vh', overflow: 'hidden' }}>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexShrink: 0,
          }}
        >
          <Typography.Text>人力资源管理系统</Typography.Text>
          <Dropdown menu={{ items: userMenu }} placement="bottomRight">
            <Space style={{ cursor: 'pointer' }}>
              <UserOutlined />
              <span>{username}</span>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ margin: 24, overflow: 'auto', flex: 1, minHeight: 0 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default AdminLayout;

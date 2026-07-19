import { DashboardOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, history, useAccess, useLocation, useModel } from '@umijs/max';
import { Dropdown, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import React, { useMemo } from 'react';
import { ADMIN_ROLES, type RoleCode } from '@/constants/roles';
import { forceLogout } from '@/utils/authSession';
import './layout.css';

const { Header, Sider, Content } = Layout;

const ALL_PORTAL_MENU: MenuProps['items'] = [
  { key: '/portal/profile', label: '我的档案' },
  { key: '/portal/attendance', label: '考勤打卡' },
  { key: '/portal/leave', label: '我的请假' },
  { key: '/portal/overtime', label: '我的加班' },
  { key: '/portal/payslips', label: '我的薪资' },
  { key: '/portal/security', label: '账号安全' },
];

const PortalLayout: React.FC = () => {
  const location = useLocation();
  const { initialState } = useModel('@@initialState');
  const access = useAccess();
  const username = initialState?.currentUser?.username ?? '员工';

  const portalMenuItems = useMemo(
    () =>
      (ALL_PORTAL_MENU ?? []).filter((item) => {
        if (!item || typeof item !== 'object' || !('key' in item)) return false;
        // 本人工资条：用 canViewOwnPayslip，勿用管理端 canViewPayroll（会拦掉普通员工）
        if (item.key === '/portal/payslips') {
          return access.canViewOwnPayslip;
        }
        return true;
      }),
    [access.canViewOwnPayslip],
  );

  const handleLogout = async () => {
    await forceLogout();
  };

  const roles = initialState?.currentUser?.roles ?? [];
  const hasAdminRole = roles.some((role) => ADMIN_ROLES.includes(role as RoleCode));

  const userMenu: MenuProps['items'] = [
    ...(hasAdminRole
      ? [
          {
            key: 'admin',
            icon: <DashboardOutlined />,
            label: '返回管理后台',
            onClick: () => history.push('/admin/workbench'),
          },
          { type: 'divider' as const },
        ]
      : []),
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
  ];

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        theme="light"
        width={200}
        className="hrms-sider-scroll"
        style={{ height: '100vh', overflowY: 'auto', overflowX: 'hidden' }}
      >
        <div style={{ padding: 16, fontWeight: 600 }}>员工门户</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={portalMenuItems}
          onClick={({ key }) => history.push(key)}
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
          <Typography.Text>员工自助</Typography.Text>
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

export default PortalLayout;

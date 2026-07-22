import { DashboardOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, history, useAccess, useLocation, useModel } from '@umijs/max';
import { Dropdown, Layout, Menu, Space } from 'antd';
import type { MenuProps } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import AiFloatBall from '@/components/AiAssistant/AiFloatBall';
import { ADMIN_ROLES, roleLabel, type RoleCode } from '@/constants/roles';
import { getMyProfile } from '@/services/employee';
import { forceLogout } from '@/utils/authSession';
import './layout.css';

const { Header, Sider, Content } = Layout;

const ALL_PORTAL_MENU: MenuProps['items'] = [
  { key: '/portal/ai/chat', label: '助理小R' },
  { key: '/portal/profile', label: '我的档案' },
  { key: '/portal/attendance', label: '考勤打卡' },
  { key: '/portal/leave', label: '我的请假' },
  { key: '/portal/overtime', label: '我的加班' },
  { key: '/portal/payslips', label: '我的工资条' },
  { key: '/portal/resignation', label: '离职申请' },
  { key: '/portal/security', label: '账号安全' },
];

const PortalLayout: React.FC = () => {
  const location = useLocation();
  const { initialState } = useModel('@@initialState');
  const access = useAccess();
  const username = initialState?.currentUser?.username ?? '员工';
  const roleCode = initialState?.currentUser?.roleCode;
  const identity = roleLabel(roleCode) || '员工';
  const [employeeName, setEmployeeName] = useState('');

  useEffect(() => {
    let cancelled = false;
    const employeeId = initialState?.currentUser?.employeeId;
    if (!employeeId) {
      setEmployeeName('');
      return undefined;
    }
    getMyProfile()
      .then((res) => {
        if (!cancelled && res.code === 0 && res.data?.name) {
          setEmployeeName(res.data.name);
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [initialState?.currentUser?.employeeId]);

  const headerGreeting = employeeName
    ? `你好！${identity} ${employeeName}`
    : `你好！${identity}`;

  const portalMenuItems = useMemo(
    () =>
      (ALL_PORTAL_MENU ?? []).filter((item) => {
        if (!item || typeof item !== 'object' || !('key' in item)) return false;
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
        theme="dark"
        width={220}
        className="hrms-sider-scroll"
        style={{ height: '100vh', overflowY: 'auto', overflowX: 'hidden', background: '#001529' }}
      >
        <div className="hrms-sider-logo">
          <div className="logo-icon">H</div>
          <span className="logo-text">HRMS 个人中心</span>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={portalMenuItems}
          onClick={({ key }) => history.push(key)}
        />
      </Sider>
      <Layout style={{ height: '100vh', overflow: 'hidden' }}>
        <Header className="hrms-header">
          <span className="hrms-header-title">{headerGreeting}</span>
          <Dropdown menu={{ items: userMenu }} placement="bottomRight">
            <Space className="hrms-header-right">
              <span className="hrms-header-avatar">
                <UserOutlined />
              </span>
              <span className="hrms-header-username">{username}</span>
            </Space>
          </Dropdown>
        </Header>
        <Content className="hrms-content">
          <Outlet />
        </Content>
      </Layout>
      <AiFloatBall />
    </Layout>
  );
};

export default PortalLayout;

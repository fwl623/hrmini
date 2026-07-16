import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, history, useLocation, useModel } from '@umijs/max';
import { Dropdown, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import React from 'react';
import { forceLogout } from '@/utils/authSession';

const { Header, Sider, Content } = Layout;

const portalMenuItems: MenuProps['items'] = [
  { key: '/portal/profile', label: '我的档案' },
  { key: '/portal/attendance', label: '考勤打卡' },
  { key: '/portal/leave', label: '我的请假' },
  { key: '/portal/overtime', label: '我的加班' },
  { key: '/portal/payslips', label: '我的薪资' },
  { key: '/portal/resignation', label: '离职申请' },
  { key: '/portal/security', label: '账号安全' },
];

const PortalLayout: React.FC = () => {
  const location = useLocation();
  const { initialState } = useModel('@@initialState');
  const username = initialState?.currentUser?.username ?? '员工';

  const handleLogout = async () => {
    await forceLogout();
  };

  const userMenu: MenuProps['items'] = [
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={200}>
        <div style={{ padding: 16, fontWeight: 600 }}>员工门户</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={portalMenuItems}
          onClick={({ key }) => history.push(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
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
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default PortalLayout;

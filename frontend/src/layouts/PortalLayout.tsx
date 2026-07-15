import { Outlet, history, useLocation } from '@umijs/max';
import { Layout, Menu } from 'antd';
import React from 'react';

const { Header, Sider, Content } = Layout;

/** 员工门户布局骨架 — 菜单 Sprint 1 起按系分 §2.2.13 补全 */

const PortalLayout: React.FC = () => {
  const location = useLocation();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={200}>
        <div style={{ padding: 16, fontWeight: 600 }}>员工门户</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={[
            { key: '/portal/profile', label: '我的档案' },
            { key: '/portal/attendance', label: '考勤打卡' },
          ]}
          onClick={({ key }) => history.push(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px' }}>员工自助</Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default PortalLayout;

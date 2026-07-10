import { Outlet, history, useLocation } from '@umijs/max';
import { Layout, Menu } from 'antd';
import React from 'react';

const { Header, Sider, Content } = Layout;

/** 管理后台布局骨架 — 菜单 Sprint 1 起按系分 §2.3 补全 */
const menuItems = [
  { key: '/admin/workbench', label: '工作台' },
];

const AdminLayout: React.FC = () => {
  const location = useLocation();

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={220}>
        <div style={{ padding: 16, fontWeight: 600 }}>HRMS 管理后台</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => history.push(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 24px' }}>
          人力资源管理系统
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default AdminLayout;

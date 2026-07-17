import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, history, useAccess, useLocation, useModel } from '@umijs/max';
import { Dropdown, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import React, { useMemo } from 'react';
import { forceLogout } from '@/utils/authSession';

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

  const menuAccess: Record<string, boolean> = {
    workbench: true,
    org: access.canManageOrg || access.canViewDept || access.canViewPosition,
    employee: access.canViewEmployee,
    attendance: access.canManageAttendance || access.canHr,
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
        children: [{ key: '/admin/employee/list', label: '花名册' }],
      },
      {
        key: '/admin/attendance',
        label: '考勤管理',
        accessKey: 'attendance',
        children: [{ key: '/admin/attendance/groups', label: '考勤组管理' }],
      },
      {
        key: '/admin/payroll',
        label: '薪资管理',
        accessKey: 'payroll',
        children: [{ key: '/admin/payroll/schemes', label: '账套管理' }],
      },
      {
        key: '/admin/system',
        label: '系统设置',
        accessKey: 'system',
        children: [{ key: '/admin/system/users', label: '用户管理' }],
      },
    ];
    return filterMenu(raw, menuAccess);
  }, [access]);

  const openKeys = useMemo(() => {
    const parts = location.pathname.split('/').filter(Boolean);
    if (parts.length >= 2) {
      return [`/${parts[0]}/${parts[1]}`];
    }
    return [];
  }, [location.pathname]);

  const handleLogout = async () => {
    await forceLogout();
  };

  const userMenu: MenuProps['items'] = [
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={220}>
        <div style={{ padding: 16, fontWeight: 600 }}>HRMS 管理后台</div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={openKeys}
          items={menuItems}
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
          <Typography.Text>人力资源管理系统</Typography.Text>
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

export default AdminLayout;

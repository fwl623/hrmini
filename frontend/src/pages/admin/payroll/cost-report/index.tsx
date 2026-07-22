import React, { useState, useEffect, useCallback } from 'react';
import { Card, Row, Col, Select, TreeSelect, Space, Spin, Empty, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';

import { request } from '@umijs/max';

interface EmployeeDetail {
  employeeId: number;
  employeeName: string;
  positionName: string;
  hireDate: string;
  grossSalary: number;
  netSalary: number;
}

interface DeptSalaryItem {
  deptId: number;
  deptName: string;
  employeeCount: number;
  totalSalary: number;
  totalActualSalary: number;
  hasDetail: boolean;
  employees: EmployeeDetail[];
}

interface DeptReport {
  deptId: number;
  deptName: string;
  period: string;
  totalEmployeeCount: number;
  totalSalary: number;
  totalActualSalary: number;
  children: DeptSalaryItem[];
}

const formatWan = (v: number | null | undefined) => {
  if (v == null || isNaN(v)) return '0.00';
  return (v / 10000).toFixed(2);
};

const CostReportPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [deptTree, setDeptTree] = useState<any[]>([]);
  const [selectedDept, setSelectedDept] = useState<number | undefined>(undefined);
  const [periods, setPeriods] = useState<string[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<string | undefined>(undefined);
  const [report, setReport] = useState<DeptReport | null>(null);
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);

  useEffect(() => {
    loadDeptTree();
    loadPeriods();
  }, []);

  const loadDeptTree = async () => {
    try {
      const res = await request('/api/v1/departments/tree', {
        method: 'GET',
        skipErrorHandler: true,
      });
      const tree = (res as any)?.data || [];
      if (tree.length > 0) {
        setDeptTree(tree);
        setSelectedDept(tree[0].id);
        return;
      }
    } catch {
      // 部门树无权限（如财务角色），使用静态部门数据
    }
    setDeptTree([
      { id: 1, name: '数字马力', children: [
        { id: 10, name: '技术部' }, { id: 11, name: '人力资源部' },
        { id: 12, name: '财务部' }, { id: 13, name: '市场部' }, { id: 14, name: '销售部' },
      ]}
    ]);
    setSelectedDept(1);
  };

  const loadPeriods = async () => {
    try {
      const res = await request('/api/v1/payroll/cost-report/available-periods');
      const list = (res as any)?.data || [];
      setPeriods(list);
      if (list.length > 0 && !selectedPeriod) {
        setSelectedPeriod(list[0]);
      }
    } catch {
      setPeriods([]);
    }
  };

  const fetchData = async (deptId: number, period: string) => {
    setLoading(true);
    try {
      const res = await request(
        `/api/v1/payroll/cost-report/department-salary?deptId=${deptId}&period=${period}`,
      );
      const data = (res as any)?.data as DeptReport | undefined;
      setReport(data || null);
    } catch {
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  // 切换筛选条件刷新表格时，所有行默认收起
  useEffect(() => {
    if (selectedDept && selectedPeriod) {
      setExpandedRowKeys([]);
      fetchData(selectedDept, selectedPeriod);
    }
  }, [selectedDept, selectedPeriod]);

  // 数据刷新后重置展开状态
  useEffect(() => {
    setExpandedRowKeys([]);
  }, [report]);

  // 切换展开/收起
  const toggleExpand = useCallback((deptId: number) => {
    setExpandedRowKeys((prev) =>
      prev.includes(deptId)
        ? prev.filter((id) => id !== deptId)
        : [...prev, deptId],
    );
  }, []);

  // ==================== 表格列定义 ====================

  const deptColumns: ColumnsType<DeptSalaryItem> = [
    {
      title: '部门名称',
      dataIndex: 'deptName',
      key: 'deptName',
    },
    {
      title: '在职人数',
      dataIndex: 'employeeCount',
      key: 'employeeCount',
      width: 100,
      align: 'center',
    },
    {
      title: '应发总额',
      dataIndex: 'totalSalary',
      key: 'totalSalary',
      width: 140,
      align: 'right',
      render: (v: number) => `${formatWan(v)}万`,
    },
    {
      title: '实发总额',
      dataIndex: 'totalActualSalary',
      key: 'totalActualSalary',
      width: 140,
      align: 'right',
      render: (v: number) => `${formatWan(v)}万`,
    },
    {
      title: '操作',
      key: 'action',
      width: 130,
      align: 'center',
      render: (_: any, record: DeptSalaryItem) => {
        if (!record.hasDetail) {
          return <Tag color="default">无明细</Tag>;
        }
        const isExpanded = expandedRowKeys.includes(record.deptId);
        return (
          <a
            style={{ color: '#1677ff', cursor: 'pointer' }}
            onClick={() => toggleExpand(record.deptId)}
          >
            {isExpanded ? '收起明细' : '展开明细'}
          </a>
        );
      },
    },
  ];

  const empColumns: ColumnsType<EmployeeDetail> = [
    {
      title: '员工姓名',
      dataIndex: 'employeeName',
      key: 'employeeName',
      width: 120,
    },
    {
      title: '岗位',
      dataIndex: 'positionName',
      key: 'positionName',
      width: 150,
    },
    {
      title: '入职日期',
      dataIndex: 'hireDate',
      key: 'hireDate',
      width: 120,
      align: 'center',
    },
    {
      title: '应发工资',
      dataIndex: 'grossSalary',
      key: 'grossSalary',
      width: 120,
      align: 'right',
      render: (v: number) => `${formatWan(v)}万`,
    },
    {
      title: '实发工资',
      dataIndex: 'netSalary',
      key: 'netSalary',
      width: 120,
      align: 'right',
      render: (v: number) => `${formatWan(v)}万`,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      align: 'center',
      render: (_: any, record: EmployeeDetail) => (
        <a
          onClick={() => {
            window.open(
              `/admin/payroll/payslips?month=${selectedPeriod}&employeeId=${record.employeeId}`,
              '_blank',
            );
          }}
        >
          查看工资条
        </a>
      ),
    },
  ];

  // ==================== 展开渲染 ====================

  const expandedRowRender = (record: DeptSalaryItem) => {
    if (!record.employees || record.employees.length === 0) {
      return <Empty description="暂无员工明细" />;
    }
    return (
      <Table<EmployeeDetail>
        columns={empColumns}
        dataSource={record.employees}
        rowKey="employeeId"
        pagination={false}
        size="small"
        bordered={false}
        style={{ margin: 0 }}
      />
    );
  };

  return (
    <Card title="成本报表">
      <Space style={{ marginBottom: 24 }} wrap>
        <TreeSelect
          treeData={deptTree}
          placeholder="选择部门"
          allowClear
          style={{ width: 240 }}
          value={selectedDept}
          onChange={(val) => setSelectedDept(val)}
          treeDefaultExpandAll
          fieldNames={{ label: 'name', value: 'id' }}
        />
        <Select
          placeholder="选择核算月份"
          style={{ width: 150 }}
          value={selectedPeriod}
          onChange={(val) => setSelectedPeriod(val)}
          options={periods.map((p) => ({ label: p, value: p }))}
          notFoundContent="暂无核算数据"
        />
      </Space>

      {/* 统计卡片 */}
      {report && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={8}>
            <Card size="small">
              <div style={{ textAlign: 'center' }}>
                <div style={{ color: '#999', fontSize: 13 }}>员工总数</div>
                <div style={{ fontSize: 24, fontWeight: 600, color: '#1677ff' }}>
                  {report.totalEmployeeCount}
                </div>
              </div>
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small">
              <div style={{ textAlign: 'center' }}>
                <div style={{ color: '#999', fontSize: 13 }}>应发总额</div>
                <div style={{ fontSize: 24, fontWeight: 600, color: '#52c41a' }}>
                  {formatWan(report.totalSalary)}万
                </div>
              </div>
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small">
              <div style={{ textAlign: 'center' }}>
                <div style={{ color: '#999', fontSize: 13 }}>实发总额</div>
                <div style={{ fontSize: 24, fontWeight: 600, color: '#faad14' }}>
                  {formatWan(report.totalActualSalary)}万
                </div>
              </div>
            </Card>
          </Col>
        </Row>
      )}

      {/* 部门薪资汇总列表 */}
      <Spin spinning={loading}>
        <Card title="部门薪资汇总" size="small">
          {report && report.children && report.children.length > 0 ? (
            <Table<DeptSalaryItem>
              columns={deptColumns}
              dataSource={report.children}
              rowKey="deptId"
              pagination={false}
              expandable={{
                expandedRowRender,
                expandedRowKeys,
                rowExpandable: (record) => record.hasDetail,
                // 左侧 +/- 图标仅展示状态，不绑定点击事件
                expandIcon: ({ expanded }) => (
                  <span style={{ cursor: 'default', userSelect: 'none' }}>
                    {expanded ? '−' : '+'}
                  </span>
                ),
              }}
            />
          ) : (
            <Empty
              description={
                selectedPeriod ? '所选月份暂无薪资核算数据' : '请选择部门和月份'
              }
            />
          )}
        </Card>
      </Spin>
    </Card>
  );
};

export default CostReportPage;

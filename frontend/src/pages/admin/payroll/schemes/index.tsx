import React, { useState } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  DatePicker,
  InputNumber,
  Space,
  Popconfirm,
  Tag,
  message,
  Typography,
  Divider,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, CodeOutlined } from '@ant-design/icons';

// ===================== Mock 数据定义 =====================

/**
 * Mock 账套数据 —— 在实际项目中应由后端 API 返回。
 * 每个账套（Scheme）包含基本信息（名称、生效日期、状态）以及一组工资项目（items）。
 */
const MOCK_SCHEMES = [
  {
    id: 1,
    name: '标准账套',
    effectiveDate: '2026-01-01',
    status: 'ENABLED',                      // ENABLED: 启用, DISABLED: 停用
    items: [
      // 固定项：基本工资，不依赖公式
      { itemCode: 'baseSalary', itemName: '基本工资', itemType: 'FIXED', calcRule: null, sortOrder: 1 },
      // 计算项：绩效工资，基于绩效基数乘以系数
      { itemCode: 'performance', itemName: '绩效工资', itemType: 'VARIABLE', calcRule: '#performanceBase * 0.8', sortOrder: 2 },
      // 计算项：加班费，按国家法定工作日折算（21.75 天/月, 8 小时/天）
      { itemCode: 'overtimePay', itemName: '加班费', itemType: 'VARIABLE', calcRule: '#baseSalary / 21.75 / 8 * #overtimeHours * 1.5', sortOrder: 3 },
      // 考勤扣款：根据考勤记录扣除
      { itemCode: 'attendanceDeduct', itemName: '考勤扣款', itemType: 'ATTENDANCE_DEDUCT', calcRule: null, sortOrder: 4 },
      // 社保扣款：养老保险，基数为 ssBase，个人缴纳比例 8%
      { itemCode: 'ssDeduct', itemName: '养老保险', itemType: 'SS_DEDUCT', baseField: 'ssBase', ratio: 0.08, sortOrder: 5 },
      // 公积金扣款：基数为 hfBase，个人缴纳比例 7%
      { itemCode: 'hfDeduct', itemName: '住房公积金', itemType: 'HF_DEDUCT', baseField: 'hfBase', ratio: 0.07, sortOrder: 6 },
      // 个税：由专项计税模块计算，此处无固定公式
      { itemCode: 'tax', itemName: '个税', itemType: 'TAX', calcRule: null, sortOrder: 7 },
    ],
  },
  {
    id: 2,
    name: '销售账套',
    effectiveDate: '2026-03-01',
    status: 'ENABLED',
    items: [
      { itemCode: 'baseSalary', itemName: '基本工资', itemType: 'FIXED', calcRule: null, sortOrder: 1 },
      // 计算项：销售提成，按销售额的 5% 计算
      { itemCode: 'commission', itemName: '销售提成', itemType: 'VARIABLE', calcRule: '#salesAmount * 0.05', sortOrder: 2 },
    ],
  },
];

/**
 * 工资项目类型选项映射 —— 用于下拉选择框。
 * 枚举值与后端定义一致，后续可通过接口动态获取。
 */
const ITEM_TYPE_OPTIONS = [
  { label: '固定项', value: 'FIXED' },
  { label: '计算项', value: 'VARIABLE' },
  { label: '考勤扣款', value: 'ATTENDANCE_DEDUCT' },
  { label: '养老', value: 'SS_DEDUCT' },
  { label: '公积金', value: 'HF_DEDUCT' },
  { label: '个税', value: 'TAX' },
];

// ===================== 页面主组件 =====================

/**
 * 薪资账套管理页面
 *
 * 功能说明：
 * - 以表格形式展示所有账套及其工资项目
 * - 支持新建、编辑、删除账套
 * - 支持对账套内的工资项目进行动态增删（Modal 内 Form.List）
 * - 计算项展示 SpEL 公式图标，悬浮可查看完整公式
 */
const PayrollSchemePage: React.FC = () => {
  // ===================== 状态管理 =====================

  const [data, setData] = useState(MOCK_SCHEMES);           // 账套列表数据
  const [modalOpen, setModalOpen] = useState(false);         // 新建/编辑弹窗是否可见
  const [editingScheme, setEditingScheme] = useState<any>(null); // 正在编辑的账套（null 表示新增）
  const [form] = Form.useForm();                             // 弹窗内表单实例

  // ===================== 表格列定义 =====================

  const columns: any[] = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '账套名称', dataIndex: 'name', width: 150 },
    { title: '生效日期', dataIndex: 'effectiveDate', width: 120 },
    {
      /* 状态列：根据枚举值渲染不同颜色的 Tag */
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (val: string) =>
        val === 'ENABLED' ? <Tag color="green">启用</Tag> : <Tag color="default">停用</Tag>,
    },
    {
      /* 工资项目列：展示该账套下的所有工资项目，计算项附带 SpEL 公式提示 */
      title: '工资项目',
      width: 300,
      render: (_: any, record: any) => (
        <Space wrap>
          {record.items?.map((item: any) => (
            <Tag key={item.itemCode} color="blue" title={item.calcRule ? `SpEL: ${item.calcRule}` : undefined}>
              {item.itemName}
              {/* 有计算公式时显示公式图标 */}
              {item.calcRule && <CodeOutlined style={{ marginLeft: 4 }} />}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      /* 操作列：编辑 / 删除 */
      title: '操作',
      width: 120,
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          {/* 删除前二次确认，防止误操作 */}
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ===================== 事件处理函数 =====================

  /** 打开新建账套弹窗，清空表单并重置编辑状态 */
  const handleAdd = () => {
    setEditingScheme(null);     // 标记为新增模式
    form.resetFields();         // 清空表单内容
    setModalOpen(true);         // 显示弹窗
  };

  /** 打开编辑账套弹窗，用当前行数据回填表单 */
  const handleEdit = (record: any) => {
    setEditingScheme(record);   // 标记为编辑模式，记录正在编辑的对象
    form.setFieldsValue({
      ...record,
      effectiveDate: undefined, // TODO: 日期选择器格式化，需根据实际日期格式做转换
    });
    setModalOpen(true);
  };

  /** 根据 ID 从列表中移除账套 */
  const handleDelete = (id: number) => {
    // 过滤掉匹配 ID 的项，触发组件重渲染
    setData((prev) => prev.filter((item) => item.id !== id));
    message.success('已删除');
  };

  /**
   * 保存表单（新增 / 编辑统一入口）
   * - 先校验表单字段合法性
   * - 编辑模式：更新已有账套
   * - 新增模式：追加到列表末尾，使用 Date.now() 作为临时 ID
   */
  const handleSave = async () => {
    const values = await form.validateFields();     // 校验并获取表单值
    if (editingScheme) {
      // ---- 编辑模式 ----
      setData((prev) =>
        prev.map((item) => (item.id === editingScheme.id ? { ...item, ...values } : item)),
      );
      message.success('更新成功');
    } else {
      // ---- 新增模式 ----
      setData((prev) => [...prev, { id: Date.now(), ...values, items: [] }]);
      message.success('创建成功');
    }
    setModalOpen(false);    // 关闭弹窗
  };

  // ===================== 页面渲染 =====================

  return (
    <Card
      title="薪资账套管理"
      extra={
        /* 卡片右上角操作区：新建账套按钮 */
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新建账套
        </Button>
      }
    >
      {/* 提示说明：告知用户 SpEL 公式支持的变量 */}
      <Typography.Paragraph type="secondary">
        工资项目支持 SpEL 公式编辑，可用变量：#baseSalary, #performanceBase, #ssBase, #hfBase, #overtimeHours, #salesAmount 等
      </Typography.Paragraph>

      {/* ==================== 账套列表表格 ==================== */}
      <Table rowKey="id" columns={columns} dataSource={data} pagination={false} />

      {/* ==================== 新建 / 编辑弹窗 ==================== */}
      <Modal
        title={editingScheme ? '编辑账套' : '新建账套'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        width={800}
      >
        <Form form={form} layout="vertical">
          {/* 账套名称：必填，长度限制 2-50 字符 */}
          <Form.Item name="name" label="账套名称" rules={[{ required: true, min: 2, max: 50 }]}>
            <Input placeholder="2-50 字符" />
          </Form.Item>

          {/* 生效日期与状态：同行并排展示 */}
          <Space style={{ display: 'flex' }} align="start">
            <Form.Item name="effectiveDate" label="生效日期" rules={[{ required: true }]}>
              <DatePicker />
            </Form.Item>
            <Form.Item name="status" label="状态" initialValue="ENABLED">
              <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
            </Form.Item>
          </Space>

          {/* ==================== 工资项目配置（动态表单列表） ==================== */}
          <Divider>工资项目配置</Divider>

          {/*
           * Form.List 实现动态增删工资项目。
           * fields 数组代表当前所有项目，add/remove 用于添加 / 移除。
           * 每行包含：项目编码、项目名称、项目类型、SpEL 公式（可选）。
           */}
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                {/* 遍历渲染每个工资项目的表单行 */}
                {fields.map(({ key, name, ...restField }) => (
                  <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                    <Form.Item {...restField} name={[name, 'itemCode']} rules={[{ required: true }]}>
                      <Input placeholder="项目编码" style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'itemName']} rules={[{ required: true }]}>
                      <Input placeholder="项目名称" style={{ width: 120 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'itemType']} rules={[{ required: true }]}>
                      <Select options={ITEM_TYPE_OPTIONS} style={{ width: 130 }} />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'calcRule']}>
                      <Input placeholder="SpEL 公式（可选）" style={{ width: 200 }} />
                    </Form.Item>
                    {/* 删除当前工资项目 */}
                    <Button type="link" danger onClick={() => remove(name)}>
                      删除
                    </Button>
                  </Space>
                ))}

                {/* 添加工资项目按钮 */}
                <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                  添加工资项目
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </Card>
  );
};

export default PayrollSchemePage;

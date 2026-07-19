-- 门户「我的薪资」联调种子：孙七(employee_id=104) 近6个月已发放工资条
SET NAMES utf8mb4;

INSERT INTO payroll_batch (id, period, status, total_count, success_count, gross_total, net_total, anomaly_count, attendance_locked, created_by)
VALUES
    (201, '2026-02', 'DISTRIBUTED', 1, 1, 21000.00, 16800.00, 0, 1, 1001),
    (202, '2026-03', 'DISTRIBUTED', 1, 1, 21500.00, 17150.00, 0, 1, 1001),
    (203, '2026-04', 'DISTRIBUTED', 1, 1, 22000.00, 17500.00, 0, 1, 1001),
    (204, '2026-05', 'DISTRIBUTED', 1, 1, 21800.00, 17320.00, 0, 1, 1001),
    (205, '2026-06', 'DISTRIBUTED', 1, 1, 22500.00, 17900.00, 0, 1, 1001),
    (206, '2026-07', 'DISTRIBUTED', 1, 1, 23000.00, 18250.00, 0, 1, 1001)
ON DUPLICATE KEY UPDATE status=VALUES(status), gross_total=VALUES(gross_total), net_total=VALUES(net_total);

INSERT INTO payroll_detail (batch_id, employee_id, calc_status, gross_salary, net_salary, detail_json, prev_net_salary, manual_adjusted)
VALUES
    (201, 104, 'SUCCESS', 21000.00, 16800.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 3000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 660, 'type', 'DEDUCTION')
     ), NULL, 0),
    (202, 104, 'SUCCESS', 21500.00, 17150.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 3500, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 810, 'type', 'DEDUCTION')
     ), 16800.00, 0),
    (203, 104, 'SUCCESS', 22000.00, 17500.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 4000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 960, 'type', 'DEDUCTION')
     ), 17150.00, 0),
    (204, 104, 'SUCCESS', 21800.00, 17320.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 3800, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 940, 'type', 'DEDUCTION')
     ), 17500.00, 0),
    (205, 104, 'SUCCESS', 22500.00, 17900.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 4500, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 1060, 'type', 'DEDUCTION')
     ), 17320.00, 0),
    (206, 104, 'SUCCESS', 23000.00, 18250.00,
     JSON_ARRAY(
       JSON_OBJECT('itemName', '基本工资', 'amount', 18000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '绩效工资', 'amount', 5000, 'type', 'EARNING'),
       JSON_OBJECT('itemName', '社保个人', 'amount', 2100, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '公积金个人', 'amount', 1440, 'type', 'DEDUCTION'),
       JSON_OBJECT('itemName', '个税', 'amount', 1210, 'type', 'DEDUCTION')
     ), 17900.00, 0)
ON DUPLICATE KEY UPDATE gross_salary=VALUES(gross_salary), net_salary=VALUES(net_salary), detail_json=VALUES(detail_json);

ALTER TABLE payroll_batch AUTO_INCREMENT = 300;

-- 联调：补银行卡演示数据
SET NAMES utf8mb4;
INSERT INTO employee_bank (employee_id, bank_account_enc, bank_account_tail, bank_name)
VALUES
    (101, 'ENC:6222021000000001001', '1001', '工商银行'),
    (102, 'ENC:6222021000000001002', '1002', '建设银行'),
    (103, 'ENC:6222021000000001003', '1003', '农业银行'),
    (104, 'ENC:6222021000000001004', '1004', '招商银行'),
    (105, 'ENC:6222021000000001005', '1005', '中国银行'),
    (106, 'ENC:6222021000000001006', '1006', '交通银行'),
    (109, 'ENC:6222021000000001009', '1009', '工商银行')
ON DUPLICATE KEY UPDATE bank_name=VALUES(bank_name), bank_account_tail=VALUES(bank_account_tail), bank_account_enc=VALUES(bank_account_enc);

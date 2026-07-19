-- 修复开户行/账套中文（须 utf8mb4）
SET NAMES utf8mb4;

UPDATE payroll_scheme SET name = '标准月薪账套' WHERE id = 1;

UPDATE employee_bank SET bank_name = '工商银行', bank_account_enc = 'ENC:6222021000000001001', bank_account_tail = '1001' WHERE employee_id = 101;
UPDATE employee_bank SET bank_name = '建设银行', bank_account_enc = 'ENC:6222021000000001002', bank_account_tail = '1002' WHERE employee_id = 102;
UPDATE employee_bank SET bank_name = '农业银行', bank_account_enc = 'ENC:6222021000000001003', bank_account_tail = '1003' WHERE employee_id = 103;
UPDATE employee_bank SET bank_name = '招商银行', bank_account_enc = 'ENC:6222021000000001004', bank_account_tail = '1004' WHERE employee_id = 104;
UPDATE employee_bank SET bank_name = '中国银行', bank_account_enc = 'ENC:6222021000000001005', bank_account_tail = '1005' WHERE employee_id = 105;
UPDATE employee_bank SET bank_name = '交通银行', bank_account_enc = 'ENC:6222021000000001006', bank_account_tail = '1006' WHERE employee_id = 106;
UPDATE employee_bank SET bank_name = '工商银行', bank_account_enc = 'ENC:6222021000000001009', bank_account_tail = '1009' WHERE employee_id = 109;

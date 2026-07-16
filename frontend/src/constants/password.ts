/** 与后端 AuthServiceImpl.PASSWORD_PATTERN 一致 */
export const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

export const PASSWORD_RULE_MESSAGE = '至少 8 位，且包含大写字母、小写字母和数字';

package com.company.hrms.module.ai.capability;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.security.LoginUser;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 意图 → 路由 → 权限门控（与前端 access 对齐）。
 */
@Component
public class CapabilityRegistry {

    @Getter
    public static class Capability {
        private final String intent;
        private final String label;
        private final String route;
        private final String accessKey;
        private final List<String> keywords;
        private final String quickPrompt;

        public Capability(String intent, String label, String route, String accessKey,
                          List<String> keywords, String quickPrompt) {
            this.intent = intent;
            this.label = label;
            this.route = route;
            this.accessKey = accessKey;
            this.keywords = keywords;
            this.quickPrompt = quickPrompt;
        }
    }

    private final List<Capability> all = List.of(
            new Capability("leave", "请假申请", "/portal/leave", "portal",
                    List.of("请假", "年假", "事假", "调休"), "我要请假"),
            new Capability("overtime", "加班申请", "/portal/overtime", "portal",
                    List.of("加班"), "我要申请加班"),
            new Capability("attendance", "考勤打卡", "/portal/attendance", "portal",
                    List.of("打卡", "考勤"), "怎么打卡"),
            new Capability("payslip", "我的工资条", "/portal/payslips", "canViewOwnPayslip",
                    List.of("工资条", "查工资", "我的工资", "工资怎么查", "本人薪资"), "怎么查我的工资"),
            new Capability("resignation", "离职申请", "/portal/resignation", "portal",
                    List.of("离职申请", "我要离职"), "我想申请离职"),
            new Capability("approval", "审批中心", "/admin/approval", "canApprove",
                    List.of("审批中心", "去审批", "待办审批"), "打开审批中心"),
            new Capability("employee", "花名册", "/admin/employee/list", "canViewEmployee",
                    List.of("花名册", "员工列表"), "打开花名册"),
            new Capability("onboarding", "入职管理", "/admin/onboarding", "canManageWorkflow",
                    List.of("入职"), "打开入职管理"),
            new Capability("attendanceGroup", "考勤组管理", "/admin/attendance/groups", "canManageAttendance",
                    List.of("考勤组"), "打开考勤组"),
            // 管理端薪资全量：HR_STAFF / FINANCE* 可进；SYS_ADMIN 禁入（PRD）
            new Capability("payrollScheme", "账套管理", "/admin/payroll/schemes", "canViewPayroll",
                    List.of("账套", "薪资全量", "工资全量", "公司薪资", "所有人薪资", "薪资管理", "看薪资"),
                    "我想看公司薪资全量"),
            new Capability("payrollBatch", "核算批次", "/admin/payroll/batches", "canViewPayroll",
                    List.of("核算", "算薪", "薪资核算"), "打开核算批次")
    );

    public List<Capability> listAllowed(LoginUser user) {
        List<Capability> result = new ArrayList<>();
        for (Capability c : all) {
            if (hasAccess(user, c.getAccessKey())) {
                result.add(c);
            }
        }
        return result;
    }

    public List<Capability> detectIntents(String question, LoginUser user) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        String q = question.toLowerCase(Locale.ROOT);
        List<Capability> matched = new ArrayList<>();
        List<Capability> deniedHints = new ArrayList<>();
        for (Capability c : all) {
            boolean hit = c.getKeywords().stream().anyMatch(k -> q.contains(k.toLowerCase(Locale.ROOT)));
            if (!hit) {
                continue;
            }
            if (hasAccess(user, c.getAccessKey())) {
                matched.add(c);
            } else {
                deniedHints.add(c);
            }
        }
        // 无权限命中也返回到 denied 供文案使用：通过临时字段不方便，调用方再查
        if (matched.isEmpty() && !deniedHints.isEmpty()) {
            return deniedHints; // 调用方需再验权限，无权限则不推 actions
        }
        return matched;
    }

    public boolean hasAccess(LoginUser user, String accessKey) {
        if (user == null || accessKey == null) {
            return false;
        }
        boolean isSysAdmin = user.hasRole(RoleCode.SYS_ADMIN.name());
        boolean isHr = isSysAdmin || user.hasRole(RoleCode.HR_STAFF.name());
        boolean isFinance = user.hasRole(RoleCode.FINANCE.name());
        boolean isFinanceManager = user.hasRole(RoleCode.FINANCE_MANAGER.name());
        boolean isFinanceFamily = isFinance || isFinanceManager;
        boolean isManager = user.hasRole(RoleCode.DEPT_MANAGER.name());

        return switch (accessKey) {
            case "portal" -> true;
            case "canViewOwnPayslip" ->
                    isSysAdmin
                            || user.hasRole(RoleCode.EMPLOYEE.name())
                            || isHr
                            || isFinanceFamily
                            || isManager;
            case "canApprove" ->
                    isHr || isManager || isFinanceManager
                            || user.hasPermission("approval:handle")
                            || user.hasPermission("menu:workflow");
            case "canViewEmployee" ->
                    !isFinanceFamily && (isHr || isManager || user.hasPermission("menu:employee"));
            case "canManageWorkflow" ->
                    !isFinanceFamily && (isHr || isManager
                            || user.hasPermission("workflow:manage")
                            || user.hasPermission("menu:workflow")
                            || user.hasPermission("approval:handle"));
            case "canManageAttendance" ->
                    !isFinanceFamily && (isHr || user.hasPermission("attendance:manage")
                            || user.hasPermission("menu:attendance"));
            case "canViewPayroll" ->
                    !isSysAdmin && (isHr || isFinanceFamily || user.hasPermission("payroll:view"));
            default -> false;
        };
    }
}

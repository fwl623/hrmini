package com.company.hrms.module.ai.capability;



import com.company.hrms.common.enums.RoleCode;

import com.company.hrms.common.security.LoginUser;

import lombok.Getter;

import org.springframework.stereotype.Component;



import java.util.ArrayList;

import java.util.List;

import java.util.Locale;



/**

 * 能力注册表：关键词意图检测 → 前端路由 / 办事表单 / 待办列表 / 查数卡片 → 角色权限门控。

 */

@Component

public class CapabilityRegistry {



    public static final String ACTION_NAVIGATE = "NAVIGATE";

    public static final String ACTION_FORM_SUBMIT = "FORM_SUBMIT";

    public static final String ACTION_TASK_LIST = "TASK_LIST";

    public static final String ACTION_INFO_LIST = "INFO_LIST";

    public static final String ACTION_DATA_CARD = "DATA_CARD";



    @Getter

    public static class Capability {

        private final String intent;

        private final String label;

        private final String route;

        private final String accessKey;

        private final String actionType;

        private final List<String> keywords;

        private final String quickPrompt;



        public Capability(String intent, String label, String route, String accessKey,

                          String actionType, List<String> keywords, String quickPrompt) {

            this.intent = intent;

            this.label = label;

            this.route = route;

            this.accessKey = accessKey;

            this.actionType = actionType == null ? ACTION_NAVIGATE : actionType;

            this.keywords = keywords;

            this.quickPrompt = quickPrompt;

        }



        public boolean isFormSubmit() {

            return ACTION_FORM_SUBMIT.equals(actionType);

        }



        public boolean isTaskList() {

            return ACTION_TASK_LIST.equals(actionType);

        }



        public boolean isInfoList() {

            return ACTION_INFO_LIST.equals(actionType);

        }



        public boolean isDataCard() {

            return ACTION_DATA_CARD.equals(actionType);

        }

    }



    private final List<Capability> all = List.of(

            // 查额度必须排在请假申请前，且关键词与「申请请假」分开，避免「年假还剩几天」误出表单
            new Capability("leave_balance", "我的假期余额", "/portal/leave", "portal", ACTION_DATA_CARD,
                    List.of("年假还剩", "调休还剩", "假期余额", "年假余额", "调休余额",
                            "剩余年假", "剩余调休", "年假额度", "假期额度", "还剩几天", "还有几天",
                            "年假多少", "调休多少", "查年假", "查调休", "我的年假", "我的调休",
                            "年假剩余", "调休剩余", "假还剩", "余额还剩"),
                    "我的年假还剩几天"),

            new Capability("leave", "请假申请", "/portal/leave", "portal", ACTION_FORM_SUBMIT,
                    List.of("请假", "请个假", "我要请假", "申请请假", "请年假", "请事假", "请病假", "请调休",
                            "生病", "生病了", "不舒服", "感冒", "发烧", "身体不适"),
                    "我要请假"),

            new Capability("overtime", "加班申请", "/portal/overtime", "portal", ACTION_FORM_SUBMIT,

                    List.of("加班", "申请加班"), "我要申请加班"),

            new Capability("attendance", "考勤打卡", "/portal/attendance", "portal", ACTION_NAVIGATE,

                    List.of("打卡", "考勤"), "怎么打卡"),

            new Capability("payslip", "我的工资条", "/portal/payslips", "canViewOwnPayslip", ACTION_NAVIGATE,

                    List.of("工资条", "查工资", "我的工资", "工资怎么查", "本人薪资"), "怎么查我的工资"),

            new Capability("resignation", "离职申请", "/portal/resignation", "portal", ACTION_NAVIGATE,

                    List.of("离职申请", "我要离职"), "我想申请离职"),

            new Capability("approval_todo", "我的待审批", "/admin/approval", "canApprove", ACTION_TASK_LIST,

                    List.of("待审批", "今日待办", "我的待办", "今日待审批", "审批一下", "看看待办"), "我的待审批"),

            new Capability("approval", "审批中心", "/admin/approval", "canApprove", ACTION_NAVIGATE,

                    List.of("审批中心", "去审批", "打开审批"), "打开审批中心"),

            // 查数类优先于单纯「打开花名册」

            new Capability("employee_list", "部门员工名单", "/admin/employee/list", "canViewEmployee", ACTION_INFO_LIST,
                    List.of("员工名单", "部门员工", "的员工", "都有谁", "花名册里",
                            "哪些员工", "有哪些员工", "有哪些人", "员工有谁", "谁在", "哪些人"),
                    "查部门员工"),

            new Capability("employee_search", "查员工", "/admin/employee/list", "canViewEmployee", ACTION_INFO_LIST,

                    List.of("查一下", "找一下", "查员工", "工号"), "查一下员工"),

            new Capability("my_team", "我的团队", "/admin/employee/list", "canViewEmployee", ACTION_INFO_LIST,

                    List.of("我的团队", "本部门名单", "我部门"), "我的团队"),

            new Capability("dept_headcount", "部门人数", "/admin/org/departments", "canViewEmployee", ACTION_DATA_CARD,

                    List.of("有多少人", "部门人数", "多少人"), "后端有多少人"),

            new Capability("org_overview", "各部门人数", "/admin/org/departments", "canViewEmployee", ACTION_DATA_CARD,

                    List.of("各部门人数", "公司人数", "人力概况", "各部门有多少"), "各部门人数"),

            new Capability("employee", "花名册", "/admin/employee/list", "canViewEmployee", ACTION_NAVIGATE,

                    List.of("花名册", "员工列表", "打开花名册"), "打开花名册"),

            new Capability("onboarding", "入职管理", "/admin/onboarding", "canManageWorkflow", ACTION_NAVIGATE,

                    List.of("入职"), "打开入职管理"),

            new Capability("attendanceGroup", "考勤组管理", "/admin/attendance/groups", "canManageAttendance", ACTION_NAVIGATE,

                    List.of("考勤组"), "打开考勤组"),

            new Capability("payrollScheme", "账套管理", "/admin/payroll/schemes", "canViewPayroll", ACTION_NAVIGATE,

                    List.of("账套", "薪资全量", "工资全量", "公司薪资", "所有人薪资", "薪资管理", "看薪资"),

                    "我想看公司薪资全量"),

            new Capability("payrollBatch", "核算批次", "/admin/payroll/batches", "canViewPayroll", ACTION_NAVIGATE,

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

    public Capability findByIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return null;
        }
        for (Capability c : all) {
            if (intent.equals(c.getIntent())) {
                return c;
            }
        }
        return null;
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

        // 查数意图命中时，去掉泛化的「打开花名册」NAVIGATE，避免重复入口

        boolean hasRosterQuery = matched.stream().anyMatch(c ->

                c.isInfoList() || "dept_headcount".equals(c.getIntent()) || "org_overview".equals(c.getIntent()));

        if (hasRosterQuery) {

            matched.removeIf(c -> "employee".equals(c.getIntent()));

        }

        // 查假期余额时去掉请假表单，避免「年假还剩几天」误出申请卡
        boolean hasLeaveBalance = matched.stream().anyMatch(c -> "leave_balance".equals(c.getIntent()));
        if (hasLeaveBalance) {
            matched.removeIf(c -> "leave".equals(c.getIntent()));
        }

        // 「各部门人数」优先于单部门「有多少人」

        boolean hasOverview = matched.stream().anyMatch(c -> "org_overview".equals(c.getIntent()));

        if (hasOverview) {

            matched.removeIf(c -> "dept_headcount".equals(c.getIntent()));

        }

        if (matched.isEmpty() && !deniedHints.isEmpty()) {

            return deniedHints;

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



    /** 是否可下发办事提交表单（需绑定员工档案）。 */

    public boolean canSubmitBizForm(LoginUser user) {

        return user != null && user.getEmployeeId() != null;

    }

}


/// <reference types="@umijs/max" />

declare namespace API {
  /** 统一响应结构 */
  interface Result<T = unknown> {
    code: number;
    message: string;
    data: T;
    traceId?: string;
    timestamp?: number;
  }

  interface LoginRequest {
    username: string;
    password: string;
  }

  interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    mustChangePassword: boolean;
  }

  interface RefreshTokenRequest {
    refreshToken: string;
  }

  interface ProfileResponse {
    userId: number;
    employeeId?: number;
    username: string;
    roles: string[];
    permissions: string[];
    dataScope: string;
    mustChangePassword: boolean;
    passwordExpiredAt?: string;
  }

  interface ChangePasswordRequest {
    oldPassword: string;
    newPassword: string;
    confirmPassword: string;
  }

  interface CurrentUser {
    userId: number;
    employeeId?: number;
    username: string;
    roles: string[];
    roleCode: string;
    permissions: string[];
    dataScope: string;
    mustChangePassword: boolean;
    passwordExpiredAt?: string;
  }

  interface InitialState {
    currentUser?: CurrentUser;
    fetchUserInfo?: () => Promise<CurrentUser | undefined>;
  }

  /** 分页结构 */
  interface Page<T> {
    list: T[];
    total: number;
    page: number;
    pageSize: number;
  }

  // ========== 考勤组 ==========

  interface AttendanceGroupDTO {
    name: string;
    shiftType: 'FIXED' | 'FLEXIBLE' | 'SCHEDULE';
    onDuty: string;
    offDuty: string;
    restStart?: string;
    restEnd?: string;
    lateThreshold?: number;
    earlyLeaveThreshold?: number;
    applicableScope: {
      departmentIds?: number[];
      positionIds?: number[];
      employeeIds?: number[];
    };
    ipWhitelist?: string[];
    gpsRange?: { lat: number; lng: number; radiusM: number };
  }

  interface AttendanceGroupVO {
    id: number;
    name: string;
    shiftType: string;
    workStartTime: string;
    workEndTime: string;
    lunchStartTime?: string;
    lunchEndTime?: string;
    lateThresholdMinutes: number;
    earlyLeaveThresholdMinutes: number;
    deleted: number;
    createdAt: string;
    updatedAt: string;
  }

  interface WorkdayConfigVO {
    dayOfWeek: number;
    isWorkday: boolean;
  }

  type WorkdayConfigDTO = WorkdayConfigVO;

  interface HolidayVO {
    id: number;
    holidayDate: string;
    name: string;
  }

  // ========== 打卡 ==========

  interface TodayPunchVO {
    clockedCount: number;
    totalCount: number;
    lateCount: number;
    earlyLeaveCount: number;
    absentCount: number;
  }

  interface PunchRecordVO {
    employeeId: number;
    employeeName: string;
    departmentName: string;
    punchDate: string;
    clockInTime: string;
    clockInStatus: string;
    clockOutTime: string;
    clockOutStatus: string;
    source: string;
    clientIp: string;
    gpsJson: string | null;
  }

  // ========== 请假 ==========

  interface LeaveBalanceVO {
    leaveType: string;
    balance: number;
  }

  interface LeaveApplicationVO {
    id: number;
    employeeName: string;
    leaveType: string;
    startTime: string;
    endTime: string;
    leaveDays: number;
    reason: string;
    status: string;
  }

  interface LeaveApplicationDTO {
    leaveType: string;
    startTime: string;
    endTime: string;
    days: number;
    reason: string;
    handoverEmployeeId?: number;
    attachment?: string;
  }

  // ========== 加班 ==========

  interface OvertimeApplicationVO {
    id: number;
    employeeName: string;
    overtimeDate: string;
    hours: number;
    status: string;
  }

  interface OvertimeApplicationDTO {
    overtimeDate: string;
    startTime: string;
    endTime: string;
    reason: string;
  }

  // ========== 考勤日历 ==========

  interface AttendanceCalendarDay {
    date: string;
    dayStatus: string;
    clockInTime?: string;
    clockOutTime?: string;
  }

  interface AttendanceCalendarVO {
    year: number;
    month: number;
    days: AttendanceCalendarDay[];
  }

  // ========== 月考勤汇总 ==========

  interface MonthlySummaryVO {
    list: MonthlySummaryItem[];
    total: number;
    locked: boolean;
  }

  interface MonthlySummaryItem {
    employeeId: number;
    employeeName: string;
    period: string;
    shouldAttendDays: number;
    actualAttendDays: number;
    lateCount: number;
    earlyLeaveCount: number;
    absentDays: number;
    leaveDays: number;
    overtimeHours: number;
  }

  // ========== 薪资 ==========

  interface PayrollSchemeDTO {
    name: string;
    scope: { departmentIds?: number[]; positionIds?: number[]; jobLevels?: string[] };
    effectiveDate: string;
    status?: string;
    items: PayrollSchemeItemDTO[];
  }

  interface PayrollSchemeItemDTO {
    itemCode: string;
    itemName: string;
    itemType: string;
    calcRule?: string;
    baseField?: string;
    ratio?: number;
    sortOrder?: number;
  }

  interface PayrollSchemeVO {
    id: number;
    name: string;
    effectiveDate: string;
    status: string;
    items: PayrollSchemeItemDTO[];
  }

  interface PayrollBatchVO {
    id: number;
    period: string;
    status: string;
    totalCount: number;
    successCount: number;
    grossTotal: number;
    netTotal: number;
  }

  interface PayrollBatchDetailVO {
    id: number;
    period: string;
    status: string;
    totalCount: number;
    successCount: number;
    anomalyCount: number;
    progress: number;
  }

  interface PayrollDetailVO {
    employeeId: number;
    employeeName: string;
    grossSalary: number;
    netSalary: number;
    calcStatus: string;
    anomalyFlags: string[];
    manualAdjusted: boolean;
  }

  interface PayslipVO {
    employeeId: number;
    employeeName: string;
    period: string;
    grossSalary: number;
    netSalary: number;
    status: string;
  }

  interface PayslipDetailVO {
    period: string;
    employee: { name: string; employeeNo: string; department: string };
    earnings: { name: string; amount: number }[];
    grossSalary: number;
    deductions: { name: string; amount: number }[];
    totalDeduction: number;
    netSalary: number;
  }

  interface PayslipTrendVO {
    period: string;
    netSalary: number;
  }

  interface CostReportVO {
    trend: { period: string; grossTotal: number; netTotal: number }[];
    deptDistribution: { deptName: string; grossTotal: number; netTotal: number }[];
  }
}

package com.company.hrms.module.attendance.controller;

import com.company.hrms.attendance.entity.AttendanceGroup;
import com.company.hrms.attendance.entity.HolidayCalendar;
import com.company.hrms.attendance.entity.WorkdayConfig;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.attendance.dto.AttendanceGroupCreateDTO;
import com.company.hrms.module.attendance.dto.AttendanceGroupVO;
import com.company.hrms.module.attendance.dto.HolidayCreateDTO;
import com.company.hrms.module.attendance.dto.HolidayUpdateDTO;
import com.company.hrms.module.attendance.dto.WorkdayConfigDTO;
import com.company.hrms.module.attendance.service.AttendanceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 考勤组管理 Controller
 *
 * 涵盖考勤组 CRUD、工作日配置、节假日管理
 */
@RestController
@RequiredArgsConstructor
public class AttendanceGroupController {

    private final AttendanceGroupService attendanceGroupService;

    // ========== 考勤组管理 ==========

    /**
     * 考勤组分页列表
     * GET /api/v1/attendance/groups?page=1&size=20
     */
    @GetMapping("/attendance/groups")
    public Result<PageResult<AttendanceGroup>> list(PageParam pageParam) {
        return Result.success(attendanceGroupService.page(pageParam));
    }

    /**
     * 查询考勤组详情
     * GET /api/v1/attendance/groups/{id}
     */
    @GetMapping("/attendance/groups/{id}")
    public Result<AttendanceGroupVO> detail(@PathVariable Long id) {
        return Result.success(attendanceGroupService.getById(id));
    }

    /**
     * 创建考勤组
     * POST /api/v1/attendance/groups
     */
    @PostMapping("/attendance/groups")
    public Result<Map<String, Long>> create(@RequestBody AttendanceGroupCreateDTO dto) {
        Long id = attendanceGroupService.create(dto);
        return Result.success(Map.of("id", id));
    }

    /**
     * 更新考勤组
     * PUT /api/v1/attendance/groups/{id}
     */
    @PutMapping("/attendance/groups/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AttendanceGroupCreateDTO dto) {
        attendanceGroupService.update(id, dto);
        return Result.success();
    }

    /**
     * 删除考勤组
     * DELETE /api/v1/attendance/groups/{id}
     */
    @DeleteMapping("/attendance/groups/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attendanceGroupService.delete(id);
        return Result.success();
    }

    // ========== 工作日配置 ==========

    /**
     * 查询工作日配置
     * GET /api/v1/attendance/workdays
     */
    @GetMapping("/attendance/workdays")
    public Result<List<WorkdayConfig>> getWorkdays() {
        return Result.success(attendanceGroupService.getWorkdays());
    }

    /**
     * 更新工作日配置（全量覆盖）
     * PUT /api/v1/attendance/workdays
     */
    @PutMapping("/attendance/workdays")
    public Result<Void> updateWorkdays(@RequestBody List<WorkdayConfigDTO> list) {
        attendanceGroupService.updateWorkdays(list);
        return Result.success();
    }

    // ========== 节假日管理 ==========

    /**
     * 节假日分页列表
     * GET /api/v1/attendance/holidays?page=1&size=20
     */
    @GetMapping("/attendance/holidays")
    public Result<PageResult<HolidayCalendar>> listHolidays(PageParam pageParam) {
        return Result.success(attendanceGroupService.pageHolidays(pageParam));
    }

    /**
     * 新增节假日
     * POST /api/v1/attendance/holidays
     */
    @PostMapping("/attendance/holidays")
    public Result<Map<String, Long>> createHoliday(@RequestBody HolidayCreateDTO dto) {
        Long id = attendanceGroupService.createHoliday(dto);
        return Result.success(Map.of("id", id));
    }

    /**
     * 更新节假日
     * PUT /api/v1/attendance/holidays/{id}
     */
    @PutMapping("/attendance/holidays/{id}")
    public Result<Void> updateHoliday(@PathVariable Long id, @RequestBody HolidayUpdateDTO dto) {
        attendanceGroupService.updateHoliday(id, dto);
        return Result.success();
    }

    /**
     * 删除节假日
     * DELETE /api/v1/attendance/holidays/{id}
     */
    @DeleteMapping("/attendance/holidays/{id}")
    public Result<Void> deleteHoliday(@PathVariable Long id) {
        attendanceGroupService.deleteHoliday(id);
        return Result.success();
    }
}

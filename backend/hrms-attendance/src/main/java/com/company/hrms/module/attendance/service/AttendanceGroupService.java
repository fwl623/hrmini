package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.attendance.entity.AttendanceGroup;
import com.company.hrms.attendance.entity.AttendanceGroupMember;
import com.company.hrms.attendance.entity.AttendanceGroupScope;
import com.company.hrms.attendance.entity.HolidayCalendar;
import com.company.hrms.attendance.entity.WorkdayConfig;
import com.company.hrms.attendance.mapper.AttendanceGroupMapper;
import com.company.hrms.attendance.mapper.AttendanceGroupMemberMapper;
import com.company.hrms.attendance.mapper.AttendanceGroupScopeMapper;
import com.company.hrms.attendance.mapper.HolidayCalendarMapper;
import com.company.hrms.attendance.mapper.WorkdayConfigMapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.attendance.dto.ApplicableScopeDTO;
import com.company.hrms.module.attendance.dto.AttendanceGroupCreateDTO;
import com.company.hrms.module.attendance.dto.AttendanceGroupVO;
import com.company.hrms.module.attendance.dto.HolidayCreateDTO;
import com.company.hrms.module.attendance.dto.HolidayUpdateDTO;
import com.company.hrms.module.attendance.dto.WorkdayConfigDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 考勤组管理 Service
 * 涵盖考勤组 CRUD、适用范围物化、工作日配置、节假日管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceGroupService {

    private final AttendanceGroupMapper attendanceGroupMapper;
    private final AttendanceGroupScopeMapper attendanceGroupScopeMapper;
    private final AttendanceGroupMemberMapper attendanceGroupMemberMapper;
    private final WorkdayConfigMapper workdayConfigMapper;
    private final HolidayCalendarMapper holidayCalendarMapper;
    private final ObjectMapper objectMapper;

    // ========== 考勤组 CRUD ==========

    /**
     * 考勤组分页列表
     */
    public PageResult<AttendanceGroup> page(PageParam pageParam) {
        IPage<AttendanceGroup> page = attendanceGroupMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()),
                new LambdaQueryWrapper<AttendanceGroup>()
                        .eq(AttendanceGroup::getDeleted, 0)
                        .orderByDesc(AttendanceGroup::getCreatedAt)
        );
        // 填充每个组的成员数
        page.getRecords().forEach(group ->
                group.setMemberCount(attendanceGroupMapper.countMemberByGroupId(group.getId())));
        return PageResult.of(page.getRecords(), page.getTotal(), pageParam);
    }

    /**
     * 考勤组详情（含适用范围、成员数）
     */
    public AttendanceGroupVO getById(Long id) {
        AttendanceGroup group = attendanceGroupMapper.selectById(id);
        if (group == null || group.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "考勤组不存在");
        }
        List<AttendanceGroupScope> scopes = attendanceGroupScopeMapper.selectByGroupId(id);
        int memberCount = attendanceGroupMapper.countMemberByGroupId(id);
        return AttendanceGroupVO.from(group, scopes, memberCount);
    }

    /**
     * 创建考勤组
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(AttendanceGroupCreateDTO dto) {
        // 校验名称唯一性
        if (attendanceGroupMapper.countByName(dto.getName()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "考勤组名称已存在");
        }

        AttendanceGroup group = toEntity(dto);
        attendanceGroupMapper.insert(group);
        Long groupId = group.getId();

        // 物化适用范围
        if (dto.getApplicableScope() != null) {
            materializeScope(groupId, dto.getApplicableScope());
        }

        log.info("创建考勤组: id={}, name={}", groupId, dto.getName());
        return groupId;
    }

    /**
     * 更新考勤组
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AttendanceGroupCreateDTO dto) {
        AttendanceGroup existing = attendanceGroupMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "考勤组不存在");
        }

        // 校验名称唯一性（排除自身）
        if (!dto.getName().equals(existing.getName()) && attendanceGroupMapper.countByName(dto.getName()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "考勤组名称已存在");
        }

        AttendanceGroup group = toEntity(dto);
        group.setId(id);
        group.setCreatedAt(existing.getCreatedAt());
        attendanceGroupMapper.updateById(group);

        // 重新物化适用范围
        if (dto.getApplicableScope() != null) {
            materializeScope(id, dto.getApplicableScope());
        }

        log.info("更新考勤组: id={}, name={}", id, dto.getName());
    }

    /**
     * 删除考勤组（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AttendanceGroup group = attendanceGroupMapper.selectById(id);
        if (group == null || group.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "考勤组不存在");
        }

        // 校验无关联员工
        int memberCount = attendanceGroupMapper.countMemberByGroupId(id);
        if (memberCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请先移除关联员工");
        }

        group.setDeleted(1);
        attendanceGroupMapper.updateById(group);
        log.info("删除考勤组: id={}, name={}", id, group.getName());
    }

    // ========== 工作日配置 ==========

    /**
     * 查询工作日配置（全部 7 条）
     */
    public List<WorkdayConfig> getWorkdays() {
        return workdayConfigMapper.selectList(null);
    }

    /**
     * 全量覆盖工作日配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkdays(List<WorkdayConfigDTO> list) {
        // 先删后插
        workdayConfigMapper.delete(null);
        for (WorkdayConfigDTO dto : list) {
            WorkdayConfig config = new WorkdayConfig();
            config.setDayOfWeek(dto.getDayOfWeek());
            config.setIsWorkday(dto.getIsWorkday() ? 1 : 0);
            workdayConfigMapper.insert(config);
        }
        log.info("更新工作日配置: {} 条", list.size());
    }

    // ========== 节假日管理 ==========

    /**
     * 节假日分页列表
     */
    public PageResult<HolidayCalendar> pageHolidays(PageParam pageParam) {
        IPage<HolidayCalendar> page = holidayCalendarMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()),
                new LambdaQueryWrapper<HolidayCalendar>()
                        .orderByDesc(HolidayCalendar::getHolidayDate)
        );
        return PageResult.of(page.getRecords(), page.getTotal(), pageParam);
    }

    /**
     * 新增节假日
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createHoliday(HolidayCreateDTO dto) {
        HolidayCalendar holiday = new HolidayCalendar();
        holiday.setHolidayDate(LocalDate.parse(dto.getHolidayDate()));
        holiday.setName(dto.getName());
        holidayCalendarMapper.insert(holiday);
        return holiday.getId();
    }

    /**
     * 更新节假日
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateHoliday(Long id, HolidayUpdateDTO dto) {
        HolidayCalendar holiday = holidayCalendarMapper.selectById(id);
        if (holiday == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "节假日不存在");
        }
        holiday.setHolidayDate(LocalDate.parse(dto.getHolidayDate()));
        holiday.setName(dto.getName());
        holidayCalendarMapper.updateById(holiday);
    }

    /**
     * 删除节假日
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteHoliday(Long id) {
        if (holidayCalendarMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "节假日不存在");
        }
        holidayCalendarMapper.deleteById(id);
    }

    // ========== 私有方法 ==========

    /**
     * 物化员工-考勤组映射
     * 先删后插，保证数据一致性
     * TODO: 对接员工服务 Feign 接口，根据 departmentIds/positionIds 解析实际员工列表
     */
    private void materializeScope(Long groupId, ApplicableScopeDTO scope) {
        // 1. 删除旧 scope 和 member
        attendanceGroupScopeMapper.deleteByGroupId(groupId);
        attendanceGroupMemberMapper.deleteByGroupId(groupId);

        // 2. 如果指定了员工，先清理这些员工在其他考勤组的成员关系（实现换组）
        if (scope.getEmployeeIds() != null && !scope.getEmployeeIds().isEmpty()) {
            attendanceGroupMemberMapper.deleteByEmployeeIds(scope.getEmployeeIds());
        }

        // 3. 插入 scope 记录
        List<AttendanceGroupScope> scopes = new ArrayList<>();

        if (scope.getDepartmentIds() != null) {
            for (Long deptId : scope.getDepartmentIds()) {
                AttendanceGroupScope s = new AttendanceGroupScope();
                s.setGroupId(groupId);
                s.setScopeType("DEPARTMENT");
                s.setScopeId(deptId);
                scopes.add(s);
            }
        }

        if (scope.getPositionIds() != null) {
            for (Long posId : scope.getPositionIds()) {
                AttendanceGroupScope s = new AttendanceGroupScope();
                s.setGroupId(groupId);
                s.setScopeType("POSITION");
                s.setScopeId(posId);
                scopes.add(s);
            }
        }

        if (scope.getEmployeeIds() != null) {
            for (Long empId : scope.getEmployeeIds()) {
                AttendanceGroupScope s = new AttendanceGroupScope();
                s.setGroupId(groupId);
                s.setScopeType("EMPLOYEE");
                s.setScopeId(empId);
                scopes.add(s);
            }
        }

        if (!scopes.isEmpty()) {
            scopes.forEach(attendanceGroupScopeMapper::insert);
        }

        // 4. 物化 member（当前仅直接指定的 employeeIds）
        // TODO: 后续通过 Feign 调用员工服务，根据 departmentIds/positionIds 解析员工 ID 并合并
        if (scope.getEmployeeIds() != null && !scope.getEmployeeIds().isEmpty()) {
            List<AttendanceGroupMember> members = scope.getEmployeeIds().stream()
                    .map(empId -> {
                        AttendanceGroupMember m = new AttendanceGroupMember();
                        m.setGroupId(groupId);
                        m.setEmployeeId(empId);
                        return m;
                    })
                    .collect(Collectors.toList());
            attendanceGroupMemberMapper.batchInsert(members);
        }

        log.info("物化考勤组范围: groupId={}, scopes={}, members={}",
                groupId, scopes.size(), scope.getEmployeeIds() != null ? scope.getEmployeeIds().size() : 0);
    }

    /**
     * DTO → Entity 转换
     */
    private AttendanceGroup toEntity(AttendanceGroupCreateDTO dto) {
        AttendanceGroup group = new AttendanceGroup();
        group.setName(dto.getName());
        group.setShiftType(dto.getShiftType() != null ? dto.getShiftType().toUpperCase() : "FIXED");
        group.setWorkStartTime(dto.getOnDuty() != null ? LocalTime.parse(dto.getOnDuty(), DateTimeFormatter.ofPattern("HH:mm")) : null);
        group.setWorkEndTime(dto.getOffDuty() != null ? LocalTime.parse(dto.getOffDuty(), DateTimeFormatter.ofPattern("HH:mm")) : null);
        group.setLunchStartTime(dto.getRestStart() != null ? LocalTime.parse(dto.getRestStart(), DateTimeFormatter.ofPattern("HH:mm")) : null);
        group.setLunchEndTime(dto.getRestEnd() != null ? LocalTime.parse(dto.getRestEnd(), DateTimeFormatter.ofPattern("HH:mm")) : null);

        if (dto.getFlexibleRange() != null) {
            group.setFlexStartEarliest(dto.getFlexibleRange().getEarliest() != null
                    ? LocalTime.parse(dto.getFlexibleRange().getEarliest(), DateTimeFormatter.ofPattern("HH:mm")) : null);
            group.setFlexStartLatest(dto.getFlexibleRange().getLatest() != null
                    ? LocalTime.parse(dto.getFlexibleRange().getLatest(), DateTimeFormatter.ofPattern("HH:mm")) : null);
        }

        group.setLateThresholdMinutes(dto.getLateThreshold() != null ? dto.getLateThreshold() : 15);
        group.setEarlyLeaveThresholdMinutes(dto.getEarlyLeaveThreshold() != null ? dto.getEarlyLeaveThreshold() : 15);

        // IP 白名单序列化
        if (dto.getIpWhitelist() != null && !dto.getIpWhitelist().isEmpty()) {
            try {
                group.setIpWhitelistJson(objectMapper.writeValueAsString(dto.getIpWhitelist()));
            } catch (JsonProcessingException e) {
                log.warn("IP白名单序列化失败", e);
            }
        }

        // GPS 范围序列化
        if (dto.getGpsRange() != null) {
            try {
                group.setGpsRangeJson(objectMapper.writeValueAsString(dto.getGpsRange()));
            } catch (JsonProcessingException e) {
                log.warn("GPS范围序列化失败", e);
            }
        }

        group.setDeleted(0);
        return group;
    }
}

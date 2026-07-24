package com.company.hrms.module.org.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.org.dto.CreatePositionRequest;
import com.company.hrms.module.org.dto.PositionHeadcountRow;
import com.company.hrms.module.org.dto.PositionVO;
import com.company.hrms.module.org.dto.UpdatePositionRequest;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.entity.Position;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.org.mapper.OrgEmployeeMapper;
import com.company.hrms.module.org.mapper.PositionMapper;
import com.company.hrms.module.org.service.GradeRangeValidator;
import com.company.hrms.module.org.service.OrgAccessGuard;
import com.company.hrms.module.org.service.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 职位服务：序列 M/P/S 与职级范围校验、按部门筛选、在岗人数。
 * 读/写分别走 {@link OrgAccessGuard#requirePositionRead()} / {@link OrgAccessGuard#requirePositionWrite()}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionMapper positionMapper;
    private final DepartmentMapper departmentMapper;
    private final OrgEmployeeMapper orgEmployeeMapper;

    @Override
    public PageResult<PositionVO> list(int page, int pageSize, Long departmentId, String sequenceCode) {
        OrgAccessGuard.requirePositionRead();
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 100);

        LambdaQueryWrapper<Position> qw = new LambdaQueryWrapper<>();
        if (departmentId != null) {
            qw.eq(Position::getDepartmentId, departmentId);
        }
        if (StringUtils.hasText(sequenceCode)) {
            qw.eq(Position::getSequence, sequenceCode.trim().toUpperCase());
        }
        qw.orderByDesc(Position::getId);

        Page<Position> mpPage = positionMapper.selectPage(new Page<>(p, size), qw);
        Map<Long, Integer> countByPosition = loadPositionCounts();
        List<PositionVO> list = mpPage.getRecords().stream()
                .map(pos -> toVo(pos, countByPosition.getOrDefault(pos.getId(), 0)))
                .collect(Collectors.toList());
        return PageResult.of(list, mpPage.getTotal(), p, size);
    }

    @Override
    public PositionVO getById(Long id) {
        OrgAccessGuard.requirePositionRead();
        Position position = requirePosition(id);
        return toVo(position, orgEmployeeMapper.countByPositionId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Long> create(CreatePositionRequest request) {
        OrgAccessGuard.requirePositionWrite();
        String seq = request.getSequenceCode().trim().toUpperCase();
        String gradeMin = request.getGradeMin().trim().toUpperCase();
        String gradeMax = request.getGradeMax().trim().toUpperCase();
        GradeRangeValidator.validate(seq, gradeMin, gradeMax);
        validateDepartment(request.getDepartmentId());

        Position position = new Position();
        applyRequest(position, request.getName(), seq, request.getDepartmentId(),
                gradeMin, gradeMax, request.getDefaultProbationMonths(),
                request.getIsStandard(), request.getDescription());
        positionMapper.insert(position);
        log.info("创建职位 id={}, name={}", position.getId(), position.getName());
        return Map.of("id", position.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdatePositionRequest request) {
        OrgAccessGuard.requirePositionWrite();
        Position position = requirePosition(id);
        String seq = request.getSequenceCode().trim().toUpperCase();
        String gradeMin = request.getGradeMin().trim().toUpperCase();
        String gradeMax = request.getGradeMax().trim().toUpperCase();
        GradeRangeValidator.validate(seq, gradeMin, gradeMax);
        validateDepartment(request.getDepartmentId());
        assertExistingGradesInRange(id, seq, gradeMin, gradeMax);

        applyRequest(position, request.getName(), seq, request.getDepartmentId(),
                gradeMin, gradeMax, request.getDefaultProbationMonths(),
                request.getIsStandard(), request.getDescription());
        positionMapper.updateById(position);
        log.info("更新职位 id={}", id);
    }

    private void assertExistingGradesInRange(Long positionId, String seq, String gradeMin, String gradeMax) {
        for (String grade : orgEmployeeMapper.selectGradesByPositionId(positionId)) {
            if (!GradeRangeValidator.inRange(seq, grade, gradeMin, gradeMax)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "存在在岗员工职级 " + grade + " 不在新范围内，无法收窄");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrgAccessGuard.requirePositionWrite();
        requirePosition(id);
        int used = orgEmployeeMapper.countByPositionId(id);
        if (used > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "职位仍被在职员工引用，无法删除");
        }
        positionMapper.deleteById(id);
        log.info("删除职位 id={}", id);
    }

    private Map<Long, Integer> loadPositionCounts() {
        Map<Long, Integer> map = new HashMap<>();
        for (PositionHeadcountRow row : orgEmployeeMapper.countBelongingGroupByPosition()) {
            if (row.getPositionId() != null && row.getCnt() != null) {
                map.put(row.getPositionId(), row.getCnt());
            }
        }
        return map;
    }

    private void applyRequest(Position position, String name, String sequenceCode, Long departmentId,
                              String gradeMin, String gradeMax, Integer probationMonths,
                              Boolean isStandard, String description) {
        position.setName(name.trim());
        position.setSequence(sequenceCode);
        position.setDepartmentId(departmentId);
        position.setRankMin(gradeMin);
        position.setRankMax(gradeMax);
        position.setDefaultProbationMonths(probationMonths == null ? 3 : probationMonths);
        position.setIsStandard(Boolean.TRUE.equals(isStandard) ? 1 : 0);
        position.setDescription(description);
    }

    private void validateDepartment(Long departmentId) {
        if (departmentId == null) {
            return;
        }
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "所属部门不存在");
        }
    }

    private Position requirePosition(Long id) {
        Position position = positionMapper.selectById(id);
        if (position == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "职位不存在");
        }
        return position;
    }

    private PositionVO toVo(Position position, int activeCount) {
        PositionVO vo = new PositionVO();
        vo.setId(position.getId());
        vo.setName(position.getName());
        vo.setSequenceCode(position.getSequence());
        vo.setDepartmentId(position.getDepartmentId());
        vo.setGradeMin(position.getRankMin());
        vo.setGradeMax(position.getRankMax());
        vo.setDefaultProbationMonths(position.getDefaultProbationMonths());
        vo.setIsStandard(position.getIsStandard() != null && position.getIsStandard() == 1);
        vo.setDescription(position.getDescription());
        vo.setActiveCount(activeCount);
        return vo;
    }
}

package com.company.hrms.module.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.payroll.entity.PayrollScheme;
import com.company.hrms.payroll.entity.PayrollSchemeItem;
import com.company.hrms.payroll.entity.PayrollSchemeScope;
import com.company.hrms.payroll.mapper.PayrollSchemeItemMapper;
import com.company.hrms.payroll.mapper.PayrollSchemeMapper;
import com.company.hrms.payroll.mapper.PayrollSchemeScopeMapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.module.payroll.dto.SchemeCreateDTO;
import com.company.hrms.module.payroll.dto.SchemeItemDTO;
import com.company.hrms.module.payroll.dto.SchemeScopeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemeService {

    private final PayrollSchemeMapper schemeMapper;
    private final PayrollSchemeItemMapper schemeItemMapper;
    private final PayrollSchemeScopeMapper schemeScopeMapper;

    public List<PayrollScheme> list() {
        List<PayrollScheme> schemes = schemeMapper.selectList(new LambdaQueryWrapper<PayrollScheme>()
                .eq(PayrollScheme::getDeleted, 0)
                .orderByAsc(PayrollScheme::getId));
        // 填充每个账套的工资项目
        for (PayrollScheme scheme : schemes) {
            scheme.setItems(schemeItemMapper.selectBySchemeId(scheme.getId()));
        }
        return schemes;
    }

    public PayrollScheme getById(Long id) {
        PayrollScheme scheme = schemeMapper.selectById(id);
        if (scheme == null || scheme.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "账套不存在");
        }
        return scheme;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SchemeCreateDTO dto) {
        PayrollScheme scheme = new PayrollScheme();
        scheme.setName(dto.getName());
        scheme.setDescription(dto.getDescription());
        scheme.setEffectiveDate(LocalDate.parse(dto.getEffectiveDate()));
        scheme.setStatus(dto.getStatus() != null ? dto.getStatus().toLowerCase() : "enabled");
        scheme.setDeleted(0);
        schemeMapper.insert(scheme);

        Long schemeId = scheme.getId();

        // 校验并插入工资项目
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "工资项目至少需要1项");
        }
        // 校验 itemCode 在账套内唯一
        Set<String> itemCodes = new HashSet<>();
        for (SchemeItemDTO item : dto.getItems()) {
            if (!itemCodes.add(item.getItemCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "工资项目编码重复: " + item.getItemCode());
            }
            String type = item.getItemType() != null ? item.getItemType().toUpperCase() : "FIXED";
            switch (type) {
                case "VARIABLE":
                    if (item.getCalcRule() == null || item.getCalcRule().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID,
                                "变动项目(" + item.getItemCode() + ")必须填写计算规则(calcRule)");
                    }
                    break;
                case "SS_DEDUCT":
                case "HF_DEDUCT":
                    if (item.getBaseField() == null || item.getBaseField().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID,
                                "社保/公积金项目(" + item.getItemCode() + ")必须填写基数类型(baseField)");
                    }
                    if (item.getRatio() == null || item.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID,
                                "社保/公积金项目(" + item.getItemCode() + ")必须填写比例(ratio)");
                    }
                    break;
            }
        }
        for (SchemeItemDTO item : dto.getItems()) {
            PayrollSchemeItem entity = new PayrollSchemeItem();
            entity.setSchemeId(schemeId);
            entity.setItemCode(item.getItemCode());
            entity.setItemName(item.getItemName());
            entity.setItemType(item.getItemType() != null ? item.getItemType().toUpperCase() : "FIXED");
            entity.setCalcRule(item.getCalcRule());
            entity.setBaseField(item.getBaseField());
            entity.setRatio(item.getRatio());
            entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            schemeItemMapper.insert(entity);
        }

        // 插入适用范围
        if (dto.getScope() != null) {
            insertScope(schemeId, dto.getScope());
        }

        log.info("创建账套: id={}, name={}", schemeId, dto.getName());
        return schemeId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SchemeCreateDTO dto) {
        PayrollScheme scheme = schemeMapper.selectById(id);
        if (scheme == null || scheme.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "账套不存在");
        }
        scheme.setName(dto.getName());
        scheme.setDescription(dto.getDescription());
        scheme.setEffectiveDate(LocalDate.parse(dto.getEffectiveDate()));
        scheme.setStatus(dto.getStatus() != null ? dto.getStatus().toLowerCase() : "enabled");
        schemeMapper.updateById(scheme);

        // 校验并先删后插工资项目
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "工资项目至少需要1项");
        }
        // 校验 itemCode 在账套内唯一
        Set<String> itemCodes = new HashSet<>();
        for (SchemeItemDTO item : dto.getItems()) {
            if (!itemCodes.add(item.getItemCode())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "工资项目编码重复: " + item.getItemCode());
            }
            String type = item.getItemType() != null ? item.getItemType().toUpperCase() : "FIXED";
            switch (type) {
                case "VARIABLE":
                    if (item.getCalcRule() == null || item.getCalcRule().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID,
                                "变动项目(" + item.getItemCode() + ")必须填写计算规则(calcRule)");
                    }
                    break;
                case "SS_DEDUCT":
                case "HF_DEDUCT":
                    if (item.getBaseField() == null || item.getBaseField().isBlank()) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID,
                                "社保/公积金项目(" + item.getItemCode() + ")必须填写基数类型(baseField)");
                    }
                    if (item.getRatio() == null || item.getRatio().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessException(ErrorCode.PARAM_INVALID,
                                "社保/公积金项目(" + item.getItemCode() + ")必须填写比例(ratio)");
                    }
                    break;
            }
        }
        schemeItemMapper.delete(new LambdaQueryWrapper<PayrollSchemeItem>().eq(PayrollSchemeItem::getSchemeId, id));
        for (SchemeItemDTO item : dto.getItems()) {
                PayrollSchemeItem entity = new PayrollSchemeItem();
                entity.setSchemeId(id);
                entity.setItemCode(item.getItemCode());
                entity.setItemName(item.getItemName());
                entity.setItemType(item.getItemType() != null ? item.getItemType().toUpperCase() : "FIXED");
                entity.setCalcRule(item.getCalcRule());
                entity.setBaseField(item.getBaseField());
                entity.setRatio(item.getRatio());
                entity.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                schemeItemMapper.insert(entity);
            }

        // 先删后插适用范围
        schemeScopeMapper.delete(new LambdaQueryWrapper<PayrollSchemeScope>().eq(PayrollSchemeScope::getSchemeId, id));
        if (dto.getScope() != null) {
            insertScope(id, dto.getScope());
        }

        log.info("更新账套: id={}, name={}", id, dto.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PayrollScheme scheme = schemeMapper.selectById(id);
        if (scheme == null || scheme.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "账套不存在");
        }
        scheme.setDeleted(1);
        schemeMapper.updateById(scheme);
        log.info("删除账套: id={}", id);
    }

    private void insertScope(Long schemeId, SchemeScopeDTO scope) {
        if (scope.getDepartmentIds() != null) {
            for (Long deptId : scope.getDepartmentIds()) {
                PayrollSchemeScope s = new PayrollSchemeScope();
                s.setSchemeId(schemeId);
                s.setScopeType("DEPARTMENT");
                s.setScopeId(String.valueOf(deptId));
                schemeScopeMapper.insert(s);
            }
        }
        if (scope.getPositionIds() != null) {
            for (Long posId : scope.getPositionIds()) {
                PayrollSchemeScope s = new PayrollSchemeScope();
                s.setSchemeId(schemeId);
                s.setScopeType("POSITION");
                s.setScopeId(String.valueOf(posId));
                schemeScopeMapper.insert(s);
            }
        }
        if (scope.getJobLevels() != null) {
            for (String level : scope.getJobLevels()) {
                PayrollSchemeScope s = new PayrollSchemeScope();
                s.setSchemeId(schemeId);
                s.setScopeType("JOB_LEVEL");
                s.setScopeId(level);
                schemeScopeMapper.insert(s);
            }
        }
    }
}

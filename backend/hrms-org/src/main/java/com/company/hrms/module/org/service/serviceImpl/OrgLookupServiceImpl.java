package com.company.hrms.module.org.service.serviceImpl;

import com.company.hrms.common.org.DeptBriefDTO;
import com.company.hrms.common.org.OrgLookupService;
import com.company.hrms.module.org.dto.DeptHeadcountVO;
import com.company.hrms.module.org.dto.DeptTreeNodeVO;
import com.company.hrms.module.org.service.DeptService;
import com.company.hrms.module.org.service.OrgAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 组织只读 SPI：供 AI 查数卡片按名解析部门、拉取人数。
 */
@Service
@RequiredArgsConstructor
public class OrgLookupServiceImpl implements OrgLookupService {

    private final DeptService deptService;

    @Override
    public List<DeptBriefDTO> resolveDepartmentsByNameHint(String hint, int limit) {
        OrgAccessGuard.requireDeptRead();
        String normalized = normalizeHint(hint);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        int cap = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 20));
        List<DeptTreeNodeVO> flat = flatten(deptService.getTree());
        String q = normalized.toLowerCase(Locale.ROOT);

        List<Scored> scored = new ArrayList<>();
        for (DeptTreeNodeVO n : flat) {
            if (n == null || n.getId() == null || !StringUtils.hasText(n.getName())) {
                continue;
            }
            String name = n.getName().trim();
            String nameNorm = normalizeHint(name).toLowerCase(Locale.ROOT);
            String nameLower = name.toLowerCase(Locale.ROOT);
            int score = 0;
            if (nameLower.equals(q) || nameNorm.equals(q)) {
                score = 100;
            } else if (nameLower.startsWith(q) || nameNorm.startsWith(q)) {
                score = 80;
            } else if (nameLower.contains(q) || nameNorm.contains(q)) {
                score = 60;
            } else if (q.contains(nameNorm) && nameNorm.length() >= 2) {
                score = 40;
            }
            if (score > 0) {
                scored.add(new Scored(score, toBrief(n)));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.dto().getName(), Comparator.nullsLast(String::compareTo)));
        return scored.stream().limit(cap).map(Scored::dto).collect(Collectors.toList());
    }

    @Override
    public List<DeptBriefDTO> listTopDepartmentHeadcounts(int limit) {
        OrgAccessGuard.requireDeptRead();
        int cap = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 30));
        List<DeptTreeNodeVO> flat = flatten(deptService.getTree());
        return flat.stream()
                .filter(n -> n != null && n.getId() != null)
                .map(this::toBrief)
                .sorted(Comparator.comparingInt((DeptBriefDTO d) ->
                                d.getHeadcount() == null ? 0 : d.getHeadcount())
                        .reversed()
                        .thenComparing(DeptBriefDTO::getName, Comparator.nullsLast(String::compareTo)))
                .limit(cap)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<DeptBriefDTO> getDepartmentHeadcount(long departmentId) {
        OrgAccessGuard.requireDeptRead();
        DeptHeadcountVO vo = deptService.headcount(departmentId);
        if (vo == null) {
            return Optional.empty();
        }
        DeptBriefDTO dto = new DeptBriefDTO();
        dto.setDepartmentId(vo.getDepartmentId());
        dto.setHeadcount(vo.getHeadcount());
        dto.setHeadcountIncludingSub(vo.getHeadcountIncludingSub());
        // 名称从树补齐
        flatten(deptService.getTree()).stream()
                .filter(n -> n.getId() != null && n.getId().equals(departmentId))
                .findFirst()
                .ifPresent(n -> dto.setName(n.getName()));
        return Optional.of(dto);
    }

    private static String normalizeHint(String hint) {
        if (hint == null) {
            return "";
        }
        String s = hint.trim();
        s = s.replaceAll("(?i)^(查看|看看|我要看|打开|查询)+", "");
        s = s.replaceAll("(的员工名单|员工名单|的名单|名单|的员工|员工|的人|有多少人|多少人|人数)$", "");
        s = s.replaceAll("(部门|部|组)$", "");
        return s.trim();
    }

    private static List<DeptTreeNodeVO> flatten(List<DeptTreeNodeVO> tree) {
        List<DeptTreeNodeVO> out = new ArrayList<>();
        if (tree == null) {
            return out;
        }
        for (DeptTreeNodeVO n : tree) {
            walk(n, out);
        }
        return out;
    }

    private static void walk(DeptTreeNodeVO n, List<DeptTreeNodeVO> out) {
        if (n == null) {
            return;
        }
        out.add(n);
        if (n.getChildren() != null) {
            for (DeptTreeNodeVO c : n.getChildren()) {
                walk(c, out);
            }
        }
    }

    private DeptBriefDTO toBrief(DeptTreeNodeVO n) {
        DeptBriefDTO dto = new DeptBriefDTO();
        dto.setDepartmentId(n.getId());
        dto.setName(n.getName());
        dto.setHeadcount(n.getHeadcount());
        dto.setHeadcountIncludingSub(n.getHeadcountIncludingSub());
        return dto;
    }

    private record Scored(int score, DeptBriefDTO dto) {
    }
}

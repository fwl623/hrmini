package com.company.hrms.module.org.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.module.auth.constant.AuthRedisKeys;
import com.company.hrms.module.org.constant.OrgRedisKeys;
import com.company.hrms.module.org.dto.CreateDeptRequest;
import com.company.hrms.module.org.dto.DeptCanDeleteVO;
import com.company.hrms.module.org.dto.DeptHeadcountRow;
import com.company.hrms.module.org.dto.DeptHeadcountVO;
import com.company.hrms.module.org.dto.DeptTreeNodeVO;
import com.company.hrms.module.org.dto.EmpIdNameRow;
import com.company.hrms.module.org.dto.MergeDeptRequest;
import com.company.hrms.module.org.dto.UpdateDeptRequest;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.org.mapper.OrgEmployeeMapper;
import com.company.hrms.module.org.mapper.PositionMapper;
import com.company.hrms.module.org.service.DeptService;
import com.company.hrms.module.org.service.OrgAccessGuard;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private static final int MAX_LEVEL = 5;
    private static final long TREE_CACHE_SECONDS = 300L;

    private final DepartmentMapper departmentMapper;
    private final OrgEmployeeMapper orgEmployeeMapper;
    private final PositionMapper positionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<DeptTreeNodeVO> getTree() {
        OrgAccessGuard.requireDeptRead();
        String cached = redisTemplate.opsForValue().get(OrgRedisKeys.DEPT_TREE);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ex) {
                log.warn("部门树缓存反序列化失败，回源查询: {}", ex.getMessage());
            }
        }
        List<DeptTreeNodeVO> tree = buildTree();
        try {
            redisTemplate.opsForValue().set(
                    OrgRedisKeys.DEPT_TREE,
                    objectMapper.writeValueAsString(tree),
                    TREE_CACHE_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("部门树缓存写入失败: {}", ex.getMessage());
        }
        return tree;
    }

    @Override
    public DeptTreeNodeVO getById(Long id) {
        OrgAccessGuard.requireDeptRead();
        Department d = requireDept(id);
        DeptTreeNodeVO vo = new DeptTreeNodeVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        vo.setParentId(d.getParentId());
        vo.setLevel(d.getLevel());
        vo.setHeadEmployeeId(d.getHeadEmployeeId());
        vo.setDescription(d.getDescription());
        vo.setSortOrder(d.getSortOrder());
        vo.setHeadcount(orgEmployeeMapper.countActiveByDeptId(id));
        vo.setHeadcountIncludingSub(orgEmployeeMapper.countActiveByPathPrefix(d.getPath()));
        if (d.getHeadEmployeeId() != null) {
            vo.setManager(orgEmployeeMapper.selectNameById(d.getHeadEmployeeId()));
        }
        return vo;
    }

    @Override
    public DeptHeadcountVO headcount(Long id) {
        OrgAccessGuard.requireDeptRead();
        Department dept = requireDept(id);
        int direct = orgEmployeeMapper.countActiveByDeptId(id);
        int including = orgEmployeeMapper.countActiveByPathPrefix(dept.getPath());
        return new DeptHeadcountVO(id, direct, including);
    }

    @Override
    public DeptCanDeleteVO canDelete(Long id) {
        OrgAccessGuard.requireDeptRead();
        requireDept(id);
        int children = departmentMapper.countChildren(id);
        // 含待离职(30)，不含已离职(40)
        int employees = orgEmployeeMapper.countBelongingByDeptId(id);
        int positions = positionMapper.countByDepartmentId(id);
        boolean can = children == 0 && employees == 0 && positions == 0;
        String reason = null;
        if (!can) {
            if (children > 0) {
                reason = "存在子部门，请先合并或调整";
            } else if (employees > 0) {
                reason = "部门下仍有员工（含待离职），请先转移或合并";
            } else {
                reason = "部门下仍有职位，请先调整职位所属或合并";
            }
        }
        return new DeptCanDeleteVO(can, children > 0, employees, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Long> create(CreateDeptRequest request) {
        OrgAccessGuard.requireDeptWrite();
        String code = request.getDeptCode().toUpperCase();
        assertCodeUnique(code, null);
        validateHeadEmployee(request.getHeadEmployeeId());

        Department parent = null;
        int level = 1;
        String parentPath = "/";
        if (request.getParentId() != null) {
            parent = requireDept(request.getParentId());
            level = parent.getLevel() + 1;
            parentPath = parent.getPath();
        }
        if (level > MAX_LEVEL) {
            throw new BusinessException(ErrorCode.DEPT_LEVEL_EXCEEDED);
        }

        Department dept = new Department();
        dept.setName(request.getName().trim());
        dept.setCode(code);
        dept.setParentId(request.getParentId());
        dept.setLevel(level);
        dept.setPath("/"); // 占位，插入后回写
        dept.setHeadEmployeeId(request.getHeadEmployeeId());
        dept.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        dept.setDescription(request.getDescription());
        departmentMapper.insert(dept);

        String path = parentPath + dept.getId() + "/";
        dept.setPath(path);
        departmentMapper.updateById(dept);

        evictTreeCache();
        if (request.getHeadEmployeeId() != null) {
            invalidateHeadPerms(null, request.getHeadEmployeeId());
        }
        log.info("创建部门 id={}, code={}, level={}", dept.getId(), code, level);
        return Map.of("id", dept.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UpdateDeptRequest request) {
        OrgAccessGuard.requireDeptWrite();
        Department dept = requireDept(id);
        String code = request.getDeptCode().toUpperCase();
        if (!code.equalsIgnoreCase(dept.getCode())
                && orgEmployeeMapper.countBelongingByDeptId(id) > 0) {
            throw new BusinessException(ErrorCode.DEPT_CODE_LOCKED);
        }
        assertCodeUnique(code, id);
        validateHeadEmployee(request.getHeadEmployeeId());

        Long oldHeadId = dept.getHeadEmployeeId();
        Long newHeadId = request.getHeadEmployeeId();
        Long newParentId = request.getParentId();
        boolean parentChanged = !Objects.equals(dept.getParentId(), newParentId);

        if (parentChanged) {
            moveDepartment(dept, newParentId);
        }

        dept.setName(request.getName().trim());
        dept.setCode(code);
        dept.setHeadEmployeeId(newHeadId);
        dept.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        dept.setDescription(request.getDescription());
        departmentMapper.updateById(dept);

        evictTreeCache();
        if (!Objects.equals(oldHeadId, newHeadId)) {
            // DataScope 当前按 LoginUser.deptId（员工所属部门）生效，不读 head_employee_id；
            // 仍清新旧负责人权限缓存，避免角色/展示层缓存与负责人字段脱节。
            invalidateHeadPerms(oldHeadId, newHeadId);
        }
        log.info("更新部门 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrgAccessGuard.requireDeptWrite();
        Department dept = requireDept(id);
        int children = departmentMapper.countChildren(id);
        int employees = orgEmployeeMapper.countBelongingByDeptId(id);
        int positions = positionMapper.countByDepartmentId(id);
        if (children > 0 || employees > 0 || positions > 0) {
            throw new BusinessException(ErrorCode.DEPT_NOT_EMPTY);
        }
        freeDeptCode(dept);
        departmentMapper.deleteById(id);
        evictTreeCache();
        log.info("删除部门 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void merge(Long id, MergeDeptRequest request) {
        OrgAccessGuard.requireDeptWrite();
        Department source = requireDept(id);
        Long targetId = request.getTargetDepartmentId();
        if (Objects.equals(id, targetId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能合并到自身");
        }
        Department target = requireDept(targetId);
        if (target.getPath().startsWith(source.getPath())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不能合并到本部门的下级部门");
        }

        // 转移员工前先取 userId 以便清权限缓存。
        // 含已离职(40)：一并改挂目标部门，避免源部门删除后 department_id 悬空。
        List<Long> userIds = orgEmployeeMapper.selectUserIdsByDeptId(id);
        orgEmployeeMapper.transferDepartment(id, targetId);
        // 源部门职位改挂目标，避免指向已删部门
        positionMapper.reassignDepartment(id, targetId);

        List<Department> children = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        for (Department child : children) {
            rehangSubtree(child, target);
        }

        freeDeptCode(source);
        departmentMapper.deleteById(id);
        evictTreeCache();
        invalidateUserPerms(userIds);
        log.info("部门合并 source={} -> target={}", id, targetId);
    }

    private void moveDepartment(Department dept, Long newParentId) {
        if (Objects.equals(dept.getId(), newParentId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "上级部门不能是自身");
        }

        Department newParent = null;
        int newLevel = 1;
        String newParentPath = "/";
        if (newParentId != null) {
            newParent = requireDept(newParentId);
            if (newParent.getPath().startsWith(dept.getPath())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "不能将部门移动到其下级");
            }
            newLevel = newParent.getLevel() + 1;
            newParentPath = newParent.getPath();
        }

        String oldPath = dept.getPath();
        int oldLevel = dept.getLevel();
        int levelDelta = newLevel - oldLevel;

        List<Department> subtree = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().likeRight(Department::getPath, oldPath));
        int maxNewLevel = subtree.stream()
                .mapToInt(d -> d.getLevel() + levelDelta)
                .max()
                .orElse(newLevel);
        if (maxNewLevel > MAX_LEVEL) {
            throw new BusinessException(ErrorCode.DEPT_LEVEL_EXCEEDED);
        }

        String newPath = newParentPath + dept.getId() + "/";
        dept.setParentId(newParentId);
        dept.setLevel(newLevel);
        dept.setPath(newPath);

        for (Department node : subtree) {
            if (Objects.equals(node.getId(), dept.getId())) {
                continue;
            }
            String relative = node.getPath().substring(oldPath.length());
            node.setPath(newPath + relative);
            node.setLevel(node.getLevel() + levelDelta);
            departmentMapper.updateById(node);
        }
    }

    private void rehangSubtree(Department child, Department newParent) {
        String oldPath = child.getPath();
        int oldLevel = child.getLevel();
        int newLevel = newParent.getLevel() + 1;
        int levelDelta = newLevel - oldLevel;
        String newPath = newParent.getPath() + child.getId() + "/";

        List<Department> subtree = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().likeRight(Department::getPath, oldPath));
        int maxNewLevel = subtree.stream()
                .mapToInt(d -> d.getLevel() + levelDelta)
                .max()
                .orElse(newLevel);
        if (maxNewLevel > MAX_LEVEL) {
            throw new BusinessException(ErrorCode.DEPT_LEVEL_EXCEEDED);
        }

        child.setParentId(newParent.getId());
        child.setLevel(newLevel);
        child.setPath(newPath);
        departmentMapper.updateById(child);

        for (Department node : subtree) {
            if (Objects.equals(node.getId(), child.getId())) {
                continue;
            }
            String relative = node.getPath().substring(oldPath.length());
            node.setPath(newPath + relative);
            node.setLevel(node.getLevel() + levelDelta);
            departmentMapper.updateById(node);
        }
    }

    private List<DeptTreeNodeVO> buildTree() {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSortOrder).orderByAsc(Department::getId));

        Map<Long, Integer> directCount = new HashMap<>();
        for (DeptHeadcountRow row : orgEmployeeMapper.countActiveGroupByDept()) {
            if (row.getDeptId() == null || row.getCnt() == null) {
                continue;
            }
            directCount.put(row.getDeptId(), row.getCnt());
        }

        Map<Long, String> pathById = all.stream()
                .collect(Collectors.toMap(Department::getId, Department::getPath, (a, b) -> a));

        List<Long> headIds = all.stream()
                .map(Department::getHeadEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> nameByEmpId = new HashMap<>();
        if (!headIds.isEmpty()) {
            for (EmpIdNameRow row : orgEmployeeMapper.selectNamesByIds(headIds)) {
                if (row.getId() != null) {
                    nameByEmpId.put(row.getId(), row.getName());
                }
            }
        }

        Map<Long, DeptTreeNodeVO> nodes = new HashMap<>();
        for (Department d : all) {
            DeptTreeNodeVO node = new DeptTreeNodeVO();
            node.setId(d.getId());
            node.setName(d.getName());
            node.setCode(d.getCode());
            node.setParentId(d.getParentId());
            node.setLevel(d.getLevel());
            node.setHeadEmployeeId(d.getHeadEmployeeId());
            node.setDescription(d.getDescription());
            node.setSortOrder(d.getSortOrder());
            int headcount = directCount.getOrDefault(d.getId(), 0);
            node.setHeadcount(headcount);
            node.setHeadcountIncludingSub(sumHeadcountUnderPath(d.getPath(), all, pathById, directCount));
            if (d.getHeadEmployeeId() != null) {
                node.setManager(nameByEmpId.get(d.getHeadEmployeeId()));
            }
            nodes.put(d.getId(), node);
        }

        List<DeptTreeNodeVO> roots = new ArrayList<>();
        for (Department d : all) {
            DeptTreeNodeVO node = nodes.get(d.getId());
            if (d.getParentId() == null || !nodes.containsKey(d.getParentId())) {
                roots.add(node);
            } else {
                nodes.get(d.getParentId()).getChildren().add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    /** 本部门 + 子孙部门在职人数（内存汇总，避免 N+1） */
    private int sumHeadcountUnderPath(String path,
                                      List<Department> all,
                                      Map<Long, String> pathById,
                                      Map<Long, Integer> directCount) {
        int sum = 0;
        for (Department d : all) {
            String p = pathById.get(d.getId());
            if (p != null && p.startsWith(path)) {
                sum += directCount.getOrDefault(d.getId(), 0);
            }
        }
        return sum;
    }

    private void sortTree(List<DeptTreeNodeVO> nodes) {
        nodes.sort(Comparator
                .comparing(DeptTreeNodeVO::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DeptTreeNodeVO::getId));
        for (DeptTreeNodeVO n : nodes) {
            if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                sortTree(n.getChildren());
            }
        }
    }

    private Department requireDept(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "部门不存在");
        }
        return dept;
    }

    private void assertCodeUnique(String code, Long excludeId) {
        int cnt = excludeId == null
                ? departmentMapper.countByCode(code)
                : departmentMapper.countByCodeExclude(code, excludeId);
        if (cnt > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "部门编码已存在");
        }
    }

    /**
     * 逻辑删除前释放 uk_code：DDL 唯一索引不区分 deleted。
     * 正常编码为 2 位 [A-Za-z0-9]；用 {@code ~} 前缀占位，避免占用合法编码。
     */
    private void freeDeptCode(Department dept) {
        String freed = "~" + dept.getId();
        if (freed.length() > 8) {
            freed = freed.substring(0, 8);
        }
        dept.setCode(freed);
        departmentMapper.updateById(dept);
    }

    private void validateHeadEmployee(Long headEmployeeId) {
        if (headEmployeeId == null) {
            return;
        }
        if (orgEmployeeMapper.countActiveEmployeeById(headEmployeeId) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "部门负责人不存在或已离职");
        }
    }

    private void evictTreeCache() {
        redisTemplate.delete(OrgRedisKeys.DEPT_TREE);
    }

    private void invalidateUserPerms(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> keys = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(AuthRedisKeys::permissions)
                .collect(Collectors.toList());
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void invalidateHeadPerms(Long oldHeadEmployeeId, Long newHeadEmployeeId) {
        List<Long> userIds = new ArrayList<>();
        if (oldHeadEmployeeId != null) {
            Long uid = orgEmployeeMapper.selectUserIdByEmployeeId(oldHeadEmployeeId);
            if (uid != null) {
                userIds.add(uid);
            }
        }
        if (newHeadEmployeeId != null) {
            Long uid = orgEmployeeMapper.selectUserIdByEmployeeId(newHeadEmployeeId);
            if (uid != null) {
                userIds.add(uid);
            }
        }
        invalidateUserPerms(userIds);
    }
}

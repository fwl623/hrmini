package com.company.hrms.module.org.service;

import com.company.hrms.module.org.dto.CreateDeptRequest;
import com.company.hrms.module.org.dto.DeptCanDeleteVO;
import com.company.hrms.module.org.dto.DeptHeadcountVO;
import com.company.hrms.module.org.dto.DeptTreeNodeVO;
import com.company.hrms.module.org.dto.MergeDeptRequest;
import com.company.hrms.module.org.dto.UpdateDeptRequest;

import java.util.List;
import java.util.Map;

public interface DeptService {

    List<DeptTreeNodeVO> getTree();

    DeptTreeNodeVO getById(Long id);

    DeptHeadcountVO headcount(Long id);

    DeptCanDeleteVO canDelete(Long id);

    Map<String, Long> create(CreateDeptRequest request);

    void update(Long id, UpdateDeptRequest request);

    void delete(Long id);

    void merge(Long id, MergeDeptRequest request);
}

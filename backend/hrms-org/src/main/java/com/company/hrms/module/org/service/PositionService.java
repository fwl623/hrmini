package com.company.hrms.module.org.service;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.org.dto.CreatePositionRequest;
import com.company.hrms.module.org.dto.PositionVO;
import com.company.hrms.module.org.dto.UpdatePositionRequest;

import java.util.Map;

public interface PositionService {

    PageResult<PositionVO> list(int page, int pageSize, Long departmentId, String sequenceCode);

    PositionVO getById(Long id);

    Map<String, Long> create(CreatePositionRequest request);

    void update(Long id, UpdatePositionRequest request);

    void delete(Long id);
}

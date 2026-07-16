package com.company.hrms.module.auth.service;

import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.auth.dto.CreateUserRequest;
import com.company.hrms.module.auth.dto.UpdateUserRequest;
import com.company.hrms.module.auth.dto.UserVO;
import com.company.hrms.module.auth.entity.LoginLog;

public interface SystemUserService {

    PageResult<UserVO> pageUsers(String keyword, PageParam pageParam);

    Long createUser(CreateUserRequest request);

    void updateUser(Long id, UpdateUserRequest request);

    PageResult<LoginLog> pageLoginLogs(PageParam pageParam);
}

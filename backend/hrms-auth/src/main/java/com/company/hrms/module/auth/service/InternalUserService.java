package com.company.hrms.module.auth.service;

import com.company.hrms.module.auth.dto.InternalCreateUserRequest;

public interface InternalUserService {

    Long createUser(InternalCreateUserRequest request);

    void updateStatus(Long userId, Integer status);

    void updateUsername(Long userId, String username);
}

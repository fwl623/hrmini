package com.company.hrms.module.auth.config;

import com.company.hrms.module.auth.constant.AuthRedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;

/**
 * 用户权限码缓存失效（{@code hrms:user:perms:{userId}}）。
 * 角色/用户权限变更后主动 DEL，下次请求从 DB 重建。
 */
@Component
public class PermissionCacheManager {

    private final StringRedisTemplate redisTemplate;

    public PermissionCacheManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(AuthRedisKeys.permissions(userId));
    }

    public void evictAll(Collection<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        for (Long userId : userIds) {
            evict(userId);
        }
    }
}

package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.identity.IdentityDtos;

/**
 * 社交公开资料服务。
 *
 * <p>类职责：根据社交可见性规则读取用户公开资料。
 */
public interface SocialProfileService {

    /**
     * 获取目标用户公开资料。
     *
     * <p>前置条件：currentUserId 为当前登录用户，targetUserId 为待查看用户。
     *
     * <p>后置条件：返回目标用户公开资料；若目标不存在或被黑名单关系阻止，则抛出业务异常。
     */
    IdentityDtos.PublicUserProfile getUserProfile(String currentUserId, String targetUserId);
}

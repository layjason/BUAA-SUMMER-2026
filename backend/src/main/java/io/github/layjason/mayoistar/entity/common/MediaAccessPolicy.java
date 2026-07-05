package io.github.layjason.mayoistar.entity.common;

/**
 * 媒体访问策略。
 */
public enum MediaAccessPolicy {
    publicAccess,
    owner,
    conversationMember,
    teamMember,
    activityOwner,
    adminOnly;
}

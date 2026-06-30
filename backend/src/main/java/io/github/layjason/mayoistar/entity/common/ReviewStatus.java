package io.github.layjason.mayoistar.entity.common;

/**
 * 审核状态，用于后台管理员审核决策。
 */
public enum ReviewStatus {
    pending,
    approved,
    rejected,
    changeRequired;
}

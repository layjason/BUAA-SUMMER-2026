-- ============================================================================
-- V2: 信誉积分变更记录表 + 黑名单自引用约束
-- ============================================================================

CREATE TABLE reputation_records (
    record_id    VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    score_change INTEGER      NOT NULL,
    reason       TEXT         NOT NULL,
    source       VARCHAR(30)  NOT NULL,
    reference_id VARCHAR(36),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reputation_records PRIMARY KEY (record_id)
);

CREATE INDEX idx_reputation_records_user ON reputation_records (user_id);
CREATE UNIQUE INDEX uk_reputation_records_source_reference ON reputation_records (source, reference_id);

ALTER TABLE reputation_records ADD CONSTRAINT ck_reputation_records_source CHECK (source IN ('report', 'admin_manual'));

ALTER TABLE reputation_records ADD CONSTRAINT fk_reputation_records_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- 为 blacklists 表添加自引用检查约束，与 friendships 和 follows 保持一致
ALTER TABLE blacklists ADD CONSTRAINT ck_blacklists_self CHECK (blocker_id != blocked_user_id);

COMMENT ON TABLE reputation_records IS '信誉积分变更记录，记录每次积分变动的原因和关联信息。仅保存审计流水，当前信誉分由举报事实重算。';

COMMENT ON COLUMN reputation_records.record_id IS '记录唯一标识，UUID 格式';
COMMENT ON COLUMN reputation_records.user_id IS '用户 ID，被变更积分的用户';
COMMENT ON COLUMN reputation_records.score_change IS '积分变动量，正数为加分，负数为扣分';
COMMENT ON COLUMN reputation_records.reason IS '变更原因描述';
COMMENT ON COLUMN reputation_records.source IS '变更来源。report — 举报核实后的扣分；admin_manual — 管理员手动调整';
COMMENT ON COLUMN reputation_records.reference_id IS '关联实体 ID，如举报 ID，便于审计追溯';
COMMENT ON COLUMN reputation_records.created_at IS '变更时间，UTC 时区';

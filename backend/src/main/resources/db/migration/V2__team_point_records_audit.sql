-- ============================================================================
-- V9__team_point_records_audit.sql
-- 小队积分变动记录增加来源和引用字段，支持防重与审计追溯
-- 兼容 PostgreSQL 和 H2 (PostgreSQL 模式)
-- ============================================================================

ALTER TABLE team_point_records
    ADD COLUMN source       VARCHAR(30) NOT NULL DEFAULT 'manual',
    ADD COLUMN reference_id VARCHAR(36);

COMMENT ON COLUMN team_point_records.source IS '积分变动来源，如 checkin、no_show、summary_post、manual。不变量：非空';
COMMENT ON COLUMN team_point_records.reference_id IS '关联实体 ID，如报名记录 ID、图文总结 ID。与 source 组合防重';

CREATE UNIQUE INDEX uq_team_point_records_source_ref
    ON team_point_records (source, reference_id) NULLS NOT DISTINCT;

-- ============================================================================
-- V4__add_team_id.sql
-- 为媒体文件添加小队关联
-- 兼容 PostgreSQL 和 H2 (PostgreSQL 模式)
-- activities.team_id 已在 V1 中存在
-- ============================================================================

ALTER TABLE media_files ADD COLUMN team_id VARCHAR(36);
CREATE INDEX idx_media_files_team_id ON media_files(team_id);

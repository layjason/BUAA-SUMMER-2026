-- ============================================================================
-- V6__add_team_media_foreign_keys.sql
-- 为 team_media_files 表补充外键约束
-- 兼容 PostgreSQL 和 H2 (PostgreSQL 模式)
-- ============================================================================

ALTER TABLE team_media_files
    ADD CONSTRAINT fk_team_media_files_team
        FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE CASCADE;

ALTER TABLE team_media_files
    ADD CONSTRAINT fk_team_media_files_media
        FOREIGN KEY (media_id) REFERENCES media_files (media_id) ON DELETE CASCADE;

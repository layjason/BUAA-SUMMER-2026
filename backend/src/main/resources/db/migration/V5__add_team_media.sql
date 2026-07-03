-- ============================================================================
-- V5__add_team_media.sql
-- 为小队添加群文件/相册关联表
-- 兼容 PostgreSQL 和 H2 (PostgreSQL 模式)
-- ============================================================================

CREATE TABLE team_media_files (
    id       UUID        NOT NULL,
    team_id  VARCHAR(36) NOT NULL,
    media_id UUID        NOT NULL,
    CONSTRAINT pk_team_media_files PRIMARY KEY (id)
);

CREATE INDEX idx_team_media_files_team  ON team_media_files (team_id);
CREATE INDEX idx_team_media_files_media ON team_media_files (media_id);
CREATE UNIQUE INDEX uq_team_media_files_pair ON team_media_files (team_id, media_id);

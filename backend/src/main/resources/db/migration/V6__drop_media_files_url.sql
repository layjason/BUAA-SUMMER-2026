-- ============================================================================
-- V6__drop_media_files_url.sql
-- 删除 media_files 表中的历史 url 字段
-- 原因：该字段始终为 null，客户端已统一使用 signedUrl，不再需要长期 url
-- ============================================================================

ALTER TABLE media_files DROP COLUMN IF EXISTS url;

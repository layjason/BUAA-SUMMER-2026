-- ============================================================================
-- V4__add_require_location_check.sql
-- 迷星群聚 (MayoiStar) - 添加活动签到位置校验控制字段
-- ============================================================================

ALTER TABLE activities ADD COLUMN IF NOT EXISTS require_location_check BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN activities.require_location_check IS '是否要求签到用户提供位置信息进行位置校验。true 时签到必须传入 currentLocation，且服务端校验距离。默认 false。';

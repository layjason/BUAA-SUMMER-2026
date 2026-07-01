-- ============================================================================
-- V3__seed_qa_accounts.sql
-- QA API 调试种子账号
-- ============================================================================

-- 说明：
-- 1. 这些账号只用于本地/测试环境的 Yaak API 调试。
-- 2. password_hash 使用 BCrypt(12)，明文密码仅保存在 qa/yaak 的本地环境文件中。
-- 3. 使用固定 UUID 便于 API 测试稳定引用用户标识。

INSERT INTO users (
    user_id,
    email,
    nickname,
    password_hash,
    kind,
    account_status,
    activated_at,
    created_at,
    updated_at
)
SELECT
    '11111111-1111-1111-1111-111111111111',
    'test_user@mayoistar.qa',
    'test_user',
    '$2a$12$HUxVBbqQjJY5.JhW.COwq.qoiR.hsa.balmFFD2BPuyDDJDJbIkV2',
    'personal',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE user_id = '11111111-1111-1111-1111-111111111111'
);

INSERT INTO personal_profiles (
    user_id,
    gender,
    signature,
    interest_tags,
    reputation_score,
    updated_at
)
SELECT
    '11111111-1111-1111-1111-111111111111',
    'unspecified',
    'QA 默认个人用户',
    NULL,
    100,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM personal_profiles WHERE user_id = '11111111-1111-1111-1111-111111111111'
);

INSERT INTO users (
    user_id,
    email,
    nickname,
    password_hash,
    kind,
    account_status,
    activated_at,
    created_at,
    updated_at
)
SELECT
    '22222222-2222-2222-2222-222222222222',
    'test_peer@mayoistar.qa',
    'test_peer',
    '$2a$12$g3nyUsNqI4r05zuYuDLAJudclhYEgucK0ivxZPijRRZrTRAl9hvCe',
    'personal',
    'active',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE user_id = '22222222-2222-2222-2222-222222222222'
);

INSERT INTO personal_profiles (
    user_id,
    gender,
    signature,
    interest_tags,
    reputation_score,
    updated_at
)
SELECT
    '22222222-2222-2222-2222-222222222222',
    'unspecified',
    'QA 默认好友用户',
    NULL,
    100,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM personal_profiles WHERE user_id = '22222222-2222-2222-2222-222222222222'
);

INSERT INTO admins (
    admin_id,
    username,
    password_hash,
    created_at,
    updated_at
)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'admin',
    '$2a$12$E0vHRZGDlx0ZhLSWzGuZc.j6UHQh22z.1XJUpfOV1qwWwsiOcJM/C',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM admins WHERE admin_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
);

INSERT INTO admins (admin_id, username, password_hash, created_at, updated_at, login_attempts)
VALUES (
    'dev-admin-id',
    'testadminyaak',
    '$2a$12$DyrIUrUDF9GemFv/o2XLbuaHJ.Ji38p0a9nqMOHMcSAMTn5WPhTum',
    NOW(),
    NOW(),
    0
) ON CONFLICT DO NOTHING;

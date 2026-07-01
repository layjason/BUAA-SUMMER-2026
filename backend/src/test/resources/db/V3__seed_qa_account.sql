INSERT INTO users (user_id, email, nickname, password_hash, kind, account_status, created_at, updated_at, login_attempts)
VALUES (
    'qa-seed-user-id',
    'qa@mayoi-star.test',
    'qaseeduser',
    '$2b$10$JUJ399Hw/TZlNyMUZk3zk.dvMYtGL8mOAFR00hyqSDaBZ0SHRmkwq',
    'personal',
    'active',
    NOW(),
    NOW(),
    0
) ON CONFLICT DO NOTHING;

-- ============================================================================
-- V1__initial_schema.sql
-- 迷星群聚 (MayoiStar) 初始数据库模式
-- 兼容 PostgreSQL 和 H2 (PostgreSQL 模式)
-- ============================================================================

-- --------------------------------------------------------------------------
-- common - 通用
-- --------------------------------------------------------------------------

CREATE TABLE media_files (
    media_id        VARCHAR(36)  NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(127) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    usage           VARCHAR(50)  NOT NULL,
    storage_path    VARCHAR(500) NOT NULL,
    url             VARCHAR(500),
    uploaded_by     VARCHAR(36)  NOT NULL,
    uploaded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_media_files PRIMARY KEY (media_id)
);

CREATE INDEX idx_media_files_uploaded_by ON media_files (uploaded_by);
CREATE INDEX idx_media_files_usage       ON media_files (usage);

-- --------------------------------------------------------------------------
-- identity - 身份与资料
-- --------------------------------------------------------------------------

CREATE TABLE users (
    user_id         VARCHAR(36)  NOT NULL,
    email           VARCHAR(255),
    username        VARCHAR(50),
    password_hash   VARCHAR(255) NOT NULL,
    kind            VARCHAR(20)  NOT NULL,
    account_status  VARCHAR(20)  NOT NULL,
    activated_at    TIMESTAMP WITH TIME ZONE,
    banned_at       TIMESTAMP WITH TIME ZONE,
    banned_until    TIMESTAMP WITH TIME ZONE,
    ban_reason      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE INDEX idx_users_kind           ON users (kind);
CREATE INDEX idx_users_account_status ON users (account_status);

CREATE TABLE personal_profiles (
    user_id             VARCHAR(36)  NOT NULL,
    nickname            VARCHAR(50)  NOT NULL,
    avatar_media_id     VARCHAR(36),
    gender              VARCHAR(20),
    birthday            VARCHAR(10),
    signature           TEXT,
    interest_tags       TEXT,
    reputation_score    INTEGER      NOT NULL DEFAULT 100,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_personal_profiles PRIMARY KEY (user_id),
    CONSTRAINT uq_personal_profiles_nickname UNIQUE (nickname)
);

CREATE TABLE merchant_profiles (
    user_id                     VARCHAR(36)  NOT NULL,
    merchant_name               VARCHAR(100),
    merchant_nickname           VARCHAR(50),
    avatar_media_id             VARCHAR(36),
    interested_activity_fields  TEXT,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_merchant_profiles PRIMARY KEY (user_id),
    CONSTRAINT uq_merchant_nickname UNIQUE (merchant_nickname)
);

CREATE TABLE qualifications (
    qualification_id   VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    license_media_ids  TEXT,
    submitted_at       TIMESTAMP WITH TIME ZONE,
    reviewed_at        TIMESTAMP WITH TIME ZONE,
    reject_reason      TEXT,
    reviewer_id        VARCHAR(36),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_qualifications PRIMARY KEY (qualification_id)
);

CREATE INDEX idx_qualifications_user_id ON qualifications (user_id);

CREATE TABLE security_tokens (
    token_id    VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    token_type  VARCHAR(20)  NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used        BOOLEAN,
    revoked     BOOLEAN,
    CONSTRAINT pk_security_tokens PRIMARY KEY (token_id)
);

CREATE INDEX idx_security_tokens_user_id  ON security_tokens (user_id);
CREATE INDEX idx_security_tokens_hash     ON security_tokens (token_hash);
CREATE INDEX idx_security_tokens_type     ON security_tokens (token_type);

CREATE TABLE interest_tags (
    tag_id  VARCHAR(36)  NOT NULL,
    name    VARCHAR(30)  NOT NULL,
    CONSTRAINT pk_interest_tags PRIMARY KEY (tag_id),
    CONSTRAINT uq_interest_tags_name UNIQUE (name)
);

-- --------------------------------------------------------------------------
-- activities - 活动
-- --------------------------------------------------------------------------

CREATE TABLE activities (
    activity_id             VARCHAR(36)   NOT NULL,
    organizer_id            VARCHAR(36)   NOT NULL,
    team_id                 VARCHAR(36),
    title                   VARCHAR(200)  NOT NULL,
    tags                    TEXT,
    introduction            TEXT,
    start_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    point_lon               DOUBLE PRECISION,
    point_lat               DOUBLE PRECISION,
    city                    VARCHAR(100),
    address                 VARCHAR(500),
    place_name              VARCHAR(200),
    safety_notice           TEXT,
    capacity                INTEGER       NOT NULL,
    fee_amount              DOUBLE PRECISION,
    fee_description         VARCHAR(500),
    min_age                 INTEGER,
    registration_deadline   TIMESTAMP WITH TIME ZONE,
    review_status           VARCHAR(30)   NOT NULL,
    runtime_status          VARCHAR(30)   NOT NULL,
    manual_review_required  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activities PRIMARY KEY (activity_id)
);

CREATE INDEX idx_activities_organizer_id    ON activities (organizer_id);
CREATE INDEX idx_activities_team_id         ON activities (team_id);
CREATE INDEX idx_activities_review_status   ON activities (review_status);
CREATE INDEX idx_activities_runtime_status  ON activities (runtime_status);
CREATE INDEX idx_activities_start_at        ON activities (start_at);

CREATE TABLE activity_images (
    image_id    VARCHAR(36) NOT NULL,
    activity_id VARCHAR(36) NOT NULL,
    media_id    VARCHAR(36) NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_activity_images PRIMARY KEY (image_id)
);

CREATE INDEX idx_activity_images_activity_id ON activity_images (activity_id);

CREATE TABLE activity_review_records (
    record_id    VARCHAR(36)  NOT NULL,
    activity_id  VARCHAR(36)  NOT NULL,
    result       VARCHAR(30)  NOT NULL,
    reason       TEXT,
    reviewer_id  VARCHAR(36),
    reviewed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activity_review_records PRIMARY KEY (record_id)
);

CREATE INDEX idx_review_records_activity_id ON activity_review_records (activity_id);

CREATE TABLE activity_templates (
    template_id                  VARCHAR(36)  NOT NULL,
    name                         VARCHAR(100) NOT NULL,
    activity_type                VARCHAR(50)  NOT NULL,
    default_tags                 TEXT,
    default_introduction         TEXT,
    default_safety_notice        TEXT,
    default_capacity             INTEGER      NOT NULL,
    default_cover_image_media_id VARCHAR(36),
    CONSTRAINT pk_activity_templates PRIMARY KEY (template_id)
);

CREATE TABLE activity_registrations (
    registration_id        VARCHAR(36)  NOT NULL,
    activity_id            VARCHAR(36)  NOT NULL,
    user_id                VARCHAR(36)  NOT NULL,
    status                 VARCHAR(30)  NOT NULL,
    participant_note       TEXT,
    accepted_safety_notice BOOLEAN      NOT NULL,
    waiting_rank           INTEGER,
    confirmation_deadline  TIMESTAMP WITH TIME ZONE,
    registered_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_in_at          TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_activity_registrations PRIMARY KEY (registration_id)
);

CREATE INDEX idx_registrations_activity_id ON activity_registrations (activity_id);
CREATE INDEX idx_registrations_user_id     ON activity_registrations (user_id);
CREATE INDEX idx_registrations_status      ON activity_registrations (status);
CREATE UNIQUE INDEX uq_registrations_activity_user
    ON activity_registrations (activity_id, user_id);

CREATE TABLE activity_summary_posts (
    summary_id  VARCHAR(36) NOT NULL,
    activity_id VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activity_summary_posts PRIMARY KEY (summary_id)
);

CREATE INDEX idx_summary_posts_activity_id ON activity_summary_posts (activity_id);

CREATE TABLE activity_summary_images (
    image_id   VARCHAR(36) NOT NULL,
    summary_id VARCHAR(36) NOT NULL,
    media_id   VARCHAR(36) NOT NULL,
    tags       TEXT,
    CONSTRAINT pk_activity_summary_images PRIMARY KEY (image_id)
);

CREATE INDEX idx_summary_images_summary_id ON activity_summary_images (summary_id);

CREATE TABLE activity_reviews (
    review_id   VARCHAR(36) NOT NULL,
    activity_id VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    rating      INTEGER     NOT NULL,
    content     TEXT,
    tags        TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activity_reviews PRIMARY KEY (review_id)
);

CREATE INDEX idx_reviews_activity_id ON activity_reviews (activity_id);
CREATE UNIQUE INDEX uq_reviews_activity_user
    ON activity_reviews (activity_id, user_id);

-- --------------------------------------------------------------------------
-- social - 好友社群
-- --------------------------------------------------------------------------

CREATE TABLE friend_requests (
    request_id     VARCHAR(36)  NOT NULL,
    requester_id   VARCHAR(36)  NOT NULL,
    target_user_id VARCHAR(36)  NOT NULL,
    source         VARCHAR(30)  NOT NULL,
    message        TEXT,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_friend_requests PRIMARY KEY (request_id)
);

CREATE INDEX idx_friend_requests_requester ON friend_requests (requester_id);
CREATE INDEX idx_friend_requests_target    ON friend_requests (target_user_id);
CREATE INDEX idx_friend_requests_status    ON friend_requests (status);

CREATE TABLE friendships (
    friendship_id  VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    friend_user_id VARCHAR(36)  NOT NULL,
    source         VARCHAR(30)  NOT NULL,
    remark         VARCHAR(50),
    group_tags     JSON,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_friendships PRIMARY KEY (friendship_id)
);

CREATE INDEX idx_friendships_user ON friendships (user_id);
CREATE UNIQUE INDEX uq_friendships_user_friend
    ON friendships (user_id, friend_user_id);

CREATE TABLE follows (
    follow_id   VARCHAR(36) NOT NULL,
    follower_id VARCHAR(36) NOT NULL,
    followed_id VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (follow_id)
);

CREATE INDEX idx_follows_follower ON follows (follower_id);
CREATE INDEX idx_follows_followed ON follows (followed_id);
CREATE UNIQUE INDEX uq_follows_pair
    ON follows (follower_id, followed_id);

CREATE TABLE blacklists (
    blacklist_id   VARCHAR(36) NOT NULL,
    blocker_id     VARCHAR(36) NOT NULL,
    blocked_user_id VARCHAR(36) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_blacklists PRIMARY KEY (blacklist_id)
);

CREATE INDEX idx_blacklists_blocker  ON blacklists (blocker_id);
CREATE INDEX idx_blacklists_blocked  ON blacklists (blocked_user_id);
CREATE UNIQUE INDEX uq_blacklists_pair
    ON blacklists (blocker_id, blocked_user_id);

CREATE TABLE user_reports (
    report_id       VARCHAR(36)  NOT NULL,
    reporter_user_id VARCHAR(36)  NOT NULL,
    target_user_id  VARCHAR(36)  NOT NULL,
    reason          TEXT         NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    handling_note   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    handled_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_reports PRIMARY KEY (report_id)
);

CREATE INDEX idx_user_reports_reporter ON user_reports (reporter_user_id);
CREATE INDEX idx_user_reports_target   ON user_reports (target_user_id);
CREATE INDEX idx_user_reports_status   ON user_reports (status);

CREATE TABLE teams (
    team_id          VARCHAR(36)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    tags             JSON,
    join_mode        VARCHAR(30)  NOT NULL,
    capacity         INTEGER      NOT NULL,
    description      TEXT,
    avatar_media_id  VARCHAR(36),
    status           VARCHAR(20)  NOT NULL,
    leader_id        VARCHAR(36)  NOT NULL,
    chat_id          VARCHAR(36),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_teams PRIMARY KEY (team_id)
);

CREATE INDEX idx_teams_leader_id ON teams (leader_id);
CREATE INDEX idx_teams_status    ON teams (status);
CREATE INDEX idx_teams_chat_id   ON teams (chat_id);

CREATE TABLE team_members (
    member_id  VARCHAR(36) NOT NULL,
    team_id    VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    role       VARCHAR(20) NOT NULL,
    points     INTEGER     NOT NULL DEFAULT 0,
    joined_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_members PRIMARY KEY (member_id)
);

CREATE INDEX idx_team_members_team_id ON team_members (team_id);
CREATE INDEX idx_team_members_user_id ON team_members (user_id);
CREATE UNIQUE INDEX uq_team_members_pair
    ON team_members (team_id, user_id);

CREATE TABLE team_join_requests (
    request_id VARCHAR(36)  NOT NULL,
    team_id    VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    message    TEXT,
    status     VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_join_requests PRIMARY KEY (request_id)
);

CREATE INDEX idx_team_join_requests_team   ON team_join_requests (team_id);
CREATE INDEX idx_team_join_requests_user   ON team_join_requests (user_id);
CREATE INDEX idx_team_join_requests_status ON team_join_requests (status);

CREATE TABLE team_point_records (
    record_id    VARCHAR(36) NOT NULL,
    team_id      VARCHAR(36) NOT NULL,
    user_id      VARCHAR(36) NOT NULL,
    point_change INTEGER     NOT NULL,
    reason       VARCHAR(50) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_point_records PRIMARY KEY (record_id)
);

CREATE INDEX idx_team_point_records_team ON team_point_records (team_id);
CREATE INDEX idx_team_point_records_user ON team_point_records (user_id);

-- --------------------------------------------------------------------------
-- chat - 即时通讯
-- --------------------------------------------------------------------------

CREATE TABLE conversations (
    conversation_id  VARCHAR(36)  NOT NULL,
    kind             VARCHAR(20)  NOT NULL,
    title            VARCHAR(100),
    avatar_media_id  VARCHAR(36),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (conversation_id)
);

CREATE INDEX idx_conversations_kind ON conversations (kind);

CREATE TABLE conversation_members (
    member_id       VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversation_members PRIMARY KEY (member_id)
);

CREATE INDEX idx_conversation_members_conv ON conversation_members (conversation_id);
CREATE INDEX idx_conversation_members_user ON conversation_members (user_id);
CREATE UNIQUE INDEX uq_conversation_members_pair
    ON conversation_members (conversation_id, user_id);

CREATE TABLE chat_messages (
    message_id          VARCHAR(36)  NOT NULL,
    conversation_id     VARCHAR(36)  NOT NULL,
    sender_id           VARCHAR(36)  NOT NULL,
    kind                VARCHAR(20)  NOT NULL,
    text                TEXT,
    image_media_id      VARCHAR(36),
    location_lon        DOUBLE PRECISION,
    location_lat        DOUBLE PRECISION,
    location_city       VARCHAR(100),
    location_address    VARCHAR(500),
    location_place_name VARCHAR(200),
    mentioned_user_ids  TEXT,
    mention_all         BOOLEAN,
    recalled            BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (message_id)
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id);
CREATE INDEX idx_chat_messages_sender       ON chat_messages (sender_id);
CREATE INDEX idx_chat_messages_sent_at      ON chat_messages (sent_at);

CREATE TABLE team_announcements (
    announcement_id VARCHAR(36)  NOT NULL,
    team_id         VARCHAR(36)  NOT NULL,
    publisher_id    VARCHAR(36)  NOT NULL,
    content         TEXT         NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_announcements PRIMARY KEY (announcement_id)
);

CREATE INDEX idx_team_announcements_team ON team_announcements (team_id);

CREATE TABLE team_polls (
    poll_id    VARCHAR(36)  NOT NULL,
    team_id    VARCHAR(36)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    deadline   TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_polls PRIMARY KEY (poll_id)
);

CREATE INDEX idx_team_polls_team ON team_polls (team_id);

CREATE TABLE poll_options (
    option_id VARCHAR(36)  NOT NULL,
    poll_id   VARCHAR(36)  NOT NULL,
    content   VARCHAR(500) NOT NULL,
    CONSTRAINT pk_poll_options PRIMARY KEY (option_id)
);

CREATE INDEX idx_poll_options_poll ON poll_options (poll_id);

CREATE TABLE poll_votes (
    vote_id   VARCHAR(36) NOT NULL,
    poll_id   VARCHAR(36) NOT NULL,
    option_id VARCHAR(36) NOT NULL,
    user_id   VARCHAR(36) NOT NULL,
    voted_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_poll_votes PRIMARY KEY (vote_id)
);

CREATE INDEX idx_poll_votes_poll   ON poll_votes (poll_id);
CREATE INDEX idx_poll_votes_user   ON poll_votes (user_id);
CREATE UNIQUE INDEX uq_poll_votes_user
    ON poll_votes (poll_id, user_id);

-- --------------------------------------------------------------------------
-- admin - 后台管理
-- --------------------------------------------------------------------------

CREATE TABLE admins (
    admin_id      VARCHAR(36)  NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_admins PRIMARY KEY (admin_id),
    CONSTRAINT uq_admins_username UNIQUE (username)
);

CREATE TABLE ban_records (
    ban_id       VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    operator_id  VARCHAR(36)  NOT NULL,
    reason       TEXT         NOT NULL,
    banned_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    banned_until TIMESTAMP WITH TIME ZONE NOT NULL,
    unbanned_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_ban_records PRIMARY KEY (ban_id)
);

CREATE INDEX idx_ban_records_user_id ON ban_records (user_id);

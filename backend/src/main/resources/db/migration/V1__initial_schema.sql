-- ============================================================================
-- V1__initial_schema.sql
-- 迷星群聚 (MayoiStar) 初始数据库模式
-- 兼容 PostgreSQL 和 H2 (PostgreSQL 模式)
-- ============================================================================

-- --------------------------------------------------------------------------
-- common - 通用
-- --------------------------------------------------------------------------

CREATE TABLE media_files (
    media_id        UUID  NOT NULL,
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

COMMENT ON TABLE media_files IS '媒体文件元数据，记录上传文件的存储信息和用途。业务对象通过 media_id 引用媒体文件，不直接存储文件内容或存储凭据。';

COMMENT ON COLUMN media_files.media_id IS '媒体文件唯一标识，UUID 格式';
COMMENT ON COLUMN media_files.file_name IS '原始上传文件名';
COMMENT ON COLUMN media_files.content_type IS 'MIME 类型，如 image/png、image/jpeg';
COMMENT ON COLUMN media_files.size_bytes IS '文件字节数';
COMMENT ON COLUMN media_files.usage IS '用途分类。avatar / merchantLicense / activityImage / chatImage / teamFile / teamAlbum / summaryImage / activityReviewImage';
COMMENT ON COLUMN media_files.storage_path IS '存储服务中的路径';
COMMENT ON COLUMN media_files.url IS '可访问的公开链接。私有文件可为空，通过鉴权接口获取';
COMMENT ON COLUMN media_files.uploaded_by IS '上传者用户 ID，关联 users 表';
COMMENT ON COLUMN media_files.uploaded_at IS '上传时间，UTC 时区';

-- --------------------------------------------------------------------------
-- identity - 身份与资料
-- --------------------------------------------------------------------------

CREATE TABLE users (
    user_id         VARCHAR(36)  NOT NULL,
    email           VARCHAR(255),
    nickname        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR NOT NULL,
    kind            VARCHAR(20)  NOT NULL,
    account_status  VARCHAR(20)  NOT NULL,
    activated_at    TIMESTAMP WITH TIME ZONE,
    banned_at       TIMESTAMP WITH TIME ZONE,
    banned_until    TIMESTAMP WITH TIME ZONE,
    ban_reason      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    login_attempts       INTEGER NOT NULL DEFAULT 0,
    last_failed_login_at TIMESTAMP WITH TIME ZONE,
    locked_until         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_nickname UNIQUE (nickname)
);

CREATE INDEX idx_users_kind           ON users (kind);
CREATE INDEX idx_users_account_status ON users (account_status);

COMMENT ON TABLE users IS '用户账号，统一存储个人用户和商家两种类型。nickname 全平台唯一，kind 字段区分用户类型，管理员由独立的 admins 表管理。';

COMMENT ON COLUMN users.user_id IS '用户唯一标识，UUID 格式';
COMMENT ON COLUMN users.email IS '邮箱，全平台唯一。个人用户和商家使用邮箱登录';
COMMENT ON COLUMN users.nickname IS '全平台唯一昵称，个人用户和商家共享唯一性约束。不变量：非空，全平台范围内唯一';
COMMENT ON COLUMN users.password_hash IS '密码的加盐哈希，不限长度以兼容任意算法';
COMMENT ON COLUMN users.kind IS '用户类型。personal — 个人用户；merchant — 商家。不变量：非空，值为 personal / merchant 之一';
COMMENT ON COLUMN users.account_status IS '账号状态。不变量：非空，值为 inactive / active / banned 之一';
COMMENT ON COLUMN users.activated_at IS '激活时间，null 表示未激活（邮箱未验证）';
COMMENT ON COLUMN users.banned_at IS '封禁开始时间，null 表示未被封禁';
COMMENT ON COLUMN users.banned_until IS '封禁截止时间，null 表示永久封禁';
COMMENT ON COLUMN users.ban_reason IS '封禁原因说明';
COMMENT ON COLUMN users.created_at IS '注册时间，UTC 时区';
COMMENT ON COLUMN users.updated_at IS '最后更新时间，UTC 时区';
COMMENT ON COLUMN users.login_attempts IS '连续登录失败次数，成功登录后重置';
COMMENT ON COLUMN users.last_failed_login_at IS '最近一次登录失败时间，用于失败窗口计算';
COMMENT ON COLUMN users.locked_until IS '账号锁定截止时间，null 表示未锁定';

CREATE TABLE personal_profiles (
    user_id             VARCHAR(36)  NOT NULL,
    avatar_media_id     UUID,
    gender              VARCHAR(20),
    birthday            VARCHAR(10),
    signature           TEXT,
    interest_tags       JSONB,
    reputation_score    INTEGER      NOT NULL DEFAULT 100,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_personal_profiles PRIMARY KEY (user_id)
);

COMMENT ON TABLE personal_profiles IS '个人用户资料，与 User 一对一关联。昵称已上移至 users 表。兴趣标签以 JSON 数组形式存储。';

COMMENT ON COLUMN personal_profiles.user_id IS '关联 users 表的主键，一对一关系';
COMMENT ON COLUMN personal_profiles.avatar_media_id IS '头像媒体文件 ID，关联 media_files 表';
COMMENT ON COLUMN personal_profiles.gender IS '性别。unspecified — 未指定；female — 女；male — 男；other — 其他';
COMMENT ON COLUMN personal_profiles.birthday IS '生日，格式 YYYY-MM-DD';
COMMENT ON COLUMN personal_profiles.signature IS '个性签名';
COMMENT ON COLUMN personal_profiles.interest_tags IS '兴趣标签，JSONB 数组格式，如 ["篮球","电影"]';
COMMENT ON COLUMN personal_profiles.reputation_score IS '信誉分，默认值 100。后置条件：违规行为会扣分，正常参与活动可恢复';
COMMENT ON COLUMN personal_profiles.updated_at IS '最后更新时间，UTC 时区';

CREATE TABLE merchant_profiles (
    user_id                     VARCHAR(36)  NOT NULL,
    merchant_name               VARCHAR(100),
    avatar_media_id             UUID,
    interested_activity_fields  JSONB,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_merchant_profiles PRIMARY KEY (user_id)
);

COMMENT ON TABLE merchant_profiles IS '商家资料，与 User 一对一关联。merchant_name 为主体名称（如营业执照名称），昵称已上移至 users 表。';

COMMENT ON COLUMN merchant_profiles.user_id IS '关联 users 表的主键，一对一关系';
COMMENT ON COLUMN merchant_profiles.merchant_name IS '商家主体名称（如营业执照上的注册名称），不强制唯一';
COMMENT ON COLUMN merchant_profiles.avatar_media_id IS '头像媒体文件 ID，关联 media_files 表';
COMMENT ON COLUMN merchant_profiles.interested_activity_fields IS '关注的活动领域，JSONB 数组格式';
COMMENT ON COLUMN merchant_profiles.updated_at IS '最后更新时间，UTC 时区';

CREATE TABLE qualifications (
    qualification_id   VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    license_media_ids  JSONB,
    submitted_at       TIMESTAMP WITH TIME ZONE,
    reviewed_at        TIMESTAMP WITH TIME ZONE,
    reject_reason      TEXT,
    reviewer_id        VARCHAR(36),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_qualifications PRIMARY KEY (qualification_id)
);

CREATE INDEX idx_qualifications_user_id ON qualifications (user_id);

COMMENT ON TABLE qualifications IS '商家资质审核记录，与 MerchantProfile 中的商家一对一关联。已通过的资质审核不可覆盖，被驳回后方可重新提交。';

COMMENT ON COLUMN qualifications.qualification_id IS '资质审核记录唯一标识，UUID 格式';
COMMENT ON COLUMN qualifications.user_id IS '关联商家用户 ID';
COMMENT ON COLUMN qualifications.status IS '审核状态。不变量：非空，值为 not_submitted / pending / approved / rejected 之一。前置条件：初始状态为 not_submitted。后置条件：approved 状态下不可再修改或覆盖';
COMMENT ON COLUMN qualifications.license_media_ids IS '营业执照等资质证明媒体文件 ID 列表，JSONB 数组格式';
COMMENT ON COLUMN qualifications.submitted_at IS '提交时间，UTC 时区';
COMMENT ON COLUMN qualifications.reviewed_at IS '审核时间，UTC 时区，null 表示未审核';
COMMENT ON COLUMN qualifications.reject_reason IS '驳回原因，仅在 status 为 REJECTED 时有值';
COMMENT ON COLUMN qualifications.reviewer_id IS '审核管理员 ID，关联 admins 表';
COMMENT ON COLUMN qualifications.created_at IS '记录创建时间，UTC 时区';

CREATE TABLE security_tokens (
    token_id    VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR NOT NULL,
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

COMMENT ON TABLE security_tokens IS '安全令牌，统一存储激活令牌、密码重置令牌和刷新令牌。token_type 区分令牌用途。数据库中仅存储令牌哈希，原始令牌仅在生成时返回一次。';

COMMENT ON COLUMN security_tokens.token_id IS '令牌唯一标识，UUID 格式';
COMMENT ON COLUMN security_tokens.user_id IS '关联用户 ID';
COMMENT ON COLUMN security_tokens.token_hash IS '令牌的加盐哈希，不限长度以兼容任意算法';
COMMENT ON COLUMN security_tokens.token_type IS '令牌类型。activation — 邮箱激活令牌；password_reset — 密码重置令牌；refresh — 刷新令牌。不变量：非空，值为 activation / password_reset / refresh 之一';
COMMENT ON COLUMN security_tokens.expires_at IS '过期时间，UTC 时区。后置条件：超过此时间的令牌即使未标记 used 也不可用';
COMMENT ON COLUMN security_tokens.created_at IS '创建时间，UTC 时区';
COMMENT ON COLUMN security_tokens.used IS '是否已被使用。ACTIVATION 和 PASSWORD_RESET 类型的令牌使用后标记为 true，REFRESH 类型可多次使用';
COMMENT ON COLUMN security_tokens.revoked IS '是否已被吊销。吊销后令牌立即失效，不等过期';

CREATE TABLE interest_tags (
    tag_id  VARCHAR(36)  NOT NULL,
    name    VARCHAR(30)  NOT NULL,
    CONSTRAINT pk_interest_tags PRIMARY KEY (tag_id),
    CONSTRAINT uq_interest_tags_name UNIQUE (name)
);

COMMENT ON TABLE interest_tags IS '系统预定义兴趣标签，供用户在注册和编辑资料时选择。标签名全局唯一。';

COMMENT ON COLUMN interest_tags.tag_id IS '标签唯一标识，UUID 格式';
COMMENT ON COLUMN interest_tags.name IS '标签名称，全局唯一，不可重复。如"篮球"、"摄影"、"编程"';

-- --------------------------------------------------------------------------
-- activities - 活动
-- --------------------------------------------------------------------------

CREATE TABLE activities (
    activity_id             VARCHAR(36)   NOT NULL,
    organizer_id            VARCHAR(36)   NOT NULL,
    team_id                 VARCHAR(36),
    title                   VARCHAR(200)  NOT NULL,
    tags                    JSONB,
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
    fee_amount              DECIMAL(19,4),
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
CREATE INDEX idx_activities_city           ON activities (city);

COMMENT ON TABLE activities IS '活动主体，保存活动的完整信息。地点信息以展平字段存储。review_status 控制审核流程，runtime_status 控制活动生命周期的展示状态。';

COMMENT ON COLUMN activities.activity_id IS '活动唯一标识，UUID 格式';
COMMENT ON COLUMN activities.organizer_id IS '发起人用户 ID，关联 users 表';
COMMENT ON COLUMN activities.team_id IS '关联小队 ID，关联 teams 表。null 表示个人发起而非小队发起';
COMMENT ON COLUMN activities.title IS '活动标题';
COMMENT ON COLUMN activities.tags IS '活动标签，JSONB 数组格式';
COMMENT ON COLUMN activities.introduction IS '活动简介';
COMMENT ON COLUMN activities.start_at IS '活动开始时间，UTC 时区';
COMMENT ON COLUMN activities.end_at IS '活动结束时间，UTC 时区。前置条件：end_at > start_at';
COMMENT ON COLUMN activities.point_lon IS '活动地点经度，WGS84 坐标系';
COMMENT ON COLUMN activities.point_lat IS '活动地点纬度，WGS84 坐标系';
COMMENT ON COLUMN activities.city IS '所在城市';
COMMENT ON COLUMN activities.address IS '详细地址';
COMMENT ON COLUMN activities.place_name IS '地点名称，如场馆名、商场名';
COMMENT ON COLUMN activities.safety_notice IS '安全须知文本';
COMMENT ON COLUMN activities.capacity IS '容量上限，即最大可报名人数';
COMMENT ON COLUMN activities.fee_amount IS '活动费用金额，DECIMAL(19,4)，null 表示免费';
COMMENT ON COLUMN activities.fee_description IS '费用说明，如"包含材料费"';
COMMENT ON COLUMN activities.min_age IS '最低年龄要求，null 表示无限制';
COMMENT ON COLUMN activities.registration_deadline IS '报名截止时间，UTC 时区，null 表示无截止';
COMMENT ON COLUMN activities.review_status IS '审核状态。不变量：非空，值为 draft / pending / approved / rejected / changeRequired 之一。前置条件：创建活动时初始值为 draft';
COMMENT ON COLUMN activities.runtime_status IS '运行状态。不变量：非空。notStarted — 未开始；registering — 报名中；registrationClosed — 报名结束；ongoing — 进行中；ended — 已结束；takenDown — 已下架';
COMMENT ON COLUMN activities.manual_review_required IS '是否需要人工审核，默认 false。为 true 时即使 AI 审核通过也需要管理员确认';
COMMENT ON COLUMN activities.created_at IS '创建时间，UTC 时区';
COMMENT ON COLUMN activities.updated_at IS '最后更新时间，UTC 时区';

CREATE TABLE activity_images (
    image_id    VARCHAR(36) NOT NULL,
    activity_id VARCHAR(36) NOT NULL,
    media_id    UUID NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT pk_activity_images PRIMARY KEY (image_id)
);

CREATE INDEX idx_activity_images_activity_id ON activity_images (activity_id);

COMMENT ON TABLE activity_images IS '活动与媒体文件的关联，按 sort_order 排序展示。';

COMMENT ON COLUMN activity_images.image_id IS '关联记录唯一标识，UUID 格式';
COMMENT ON COLUMN activity_images.activity_id IS '关联活动 ID';
COMMENT ON COLUMN activity_images.media_id IS '关联媒体文件 ID';
COMMENT ON COLUMN activity_images.sort_order IS '排序序号，值小的在前';

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

COMMENT ON TABLE activity_review_records IS '活动审核记录，保存每次审核的结果和原因。AI 自动审核时 reviewer_id 为空，人工审核时记录审核人 ID。';

COMMENT ON COLUMN activity_review_records.record_id IS '审核记录唯一标识，UUID 格式';
COMMENT ON COLUMN activity_review_records.activity_id IS '关联活动 ID';
COMMENT ON COLUMN activity_review_records.result IS '审核结果。不变量：非空，值为 pending / approved / rejected / changeRequired 之一';
COMMENT ON COLUMN activity_review_records.reason IS '审核原因或意见。APPROVED 时可为空，REJECTED 时应当填写';
COMMENT ON COLUMN activity_review_records.reviewer_id IS '审核人 ID。AI 自动审核时为空，人工审核时关联 admins 表';
COMMENT ON COLUMN activity_review_records.reviewed_at IS '审核时间，UTC 时区';

CREATE TABLE activity_templates (
    template_id                  VARCHAR(36)  NOT NULL,
    name                         VARCHAR(100) NOT NULL,
    activity_type                VARCHAR(50)  NOT NULL,
    default_tags                 JSONB,
    default_introduction         TEXT,
    default_safety_notice        TEXT,
    default_capacity             INTEGER      NOT NULL,
    default_cover_image_media_id UUID,
    CONSTRAINT pk_activity_templates PRIMARY KEY (template_id)
);

COMMENT ON TABLE activity_templates IS '活动模板，预设活动信息供用户快速创建活动草稿。';

COMMENT ON COLUMN activity_templates.template_id IS '模板唯一标识，UUID 格式';
COMMENT ON COLUMN activity_templates.name IS '模板名称';
COMMENT ON COLUMN activity_templates.activity_type IS '活动类型';
COMMENT ON COLUMN activity_templates.default_tags IS '默认标签，JSONB 数组格式';
COMMENT ON COLUMN activity_templates.default_introduction IS '默认简介模板文本';
COMMENT ON COLUMN activity_templates.default_safety_notice IS '默认安全须知模板文本';
COMMENT ON COLUMN activity_templates.default_capacity IS '默认容量上限';
COMMENT ON COLUMN activity_templates.default_cover_image_media_id IS '默认封面图媒体文件 ID';

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


CREATE INDEX idx_registrations_user_id     ON activity_registrations (user_id);
CREATE INDEX idx_registrations_status      ON activity_registrations (status);
CREATE UNIQUE INDEX uq_registrations_activity_user
    ON activity_registrations (activity_id, user_id);

COMMENT ON TABLE activity_registrations IS '活动报名记录，跟踪用户的报名、候补、签到状态。一个用户对同一个活动最多只有一条有效报名记录。';

COMMENT ON COLUMN activity_registrations.registration_id IS '报名记录唯一标识，UUID 格式';
COMMENT ON COLUMN activity_registrations.activity_id IS '关联活动 ID';
COMMENT ON COLUMN activity_registrations.user_id IS '报名用户 ID';
COMMENT ON COLUMN activity_registrations.status IS '报名状态。不变量：非空。registered — 已报名；waiting — 候补中；waitingConfirmation — 待确认；canceled — 已取消；checkedIn — 已签到';
COMMENT ON COLUMN activity_registrations.participant_note IS '参与者备注';
COMMENT ON COLUMN activity_registrations.accepted_safety_notice IS '是否已接受安全须知，必须为 true 才可完成报名';
COMMENT ON COLUMN activity_registrations.waiting_rank IS '候补排名。仅在 status 为 WAITING 时有值，越小越靠前';
COMMENT ON COLUMN activity_registrations.confirmation_deadline IS '确认截止时间，UTC 时区。候补转正后需在此时间前确认';
COMMENT ON COLUMN activity_registrations.registered_at IS '报名时间，UTC 时区';
COMMENT ON COLUMN activity_registrations.checked_in_at IS '签到时间，UTC 时区，null 表示未签到';

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

COMMENT ON TABLE activity_summary_posts IS '活动图文总结，活动结束后由发起人发布。';

COMMENT ON COLUMN activity_summary_posts.summary_id IS '总结帖唯一标识，UUID 格式';
COMMENT ON COLUMN activity_summary_posts.activity_id IS '关联活动 ID';
COMMENT ON COLUMN activity_summary_posts.user_id IS '发布者用户 ID';
COMMENT ON COLUMN activity_summary_posts.title IS '总结标题';
COMMENT ON COLUMN activity_summary_posts.content IS '总结正文内容';
COMMENT ON COLUMN activity_summary_posts.created_at IS '发布时间，UTC 时区';

CREATE TABLE activity_summary_images (
    image_id   VARCHAR(36) NOT NULL,
    summary_id VARCHAR(36) NOT NULL,
    media_id   UUID NOT NULL,
    tags       JSONB,
    CONSTRAINT pk_activity_summary_images PRIMARY KEY (image_id)
);

CREATE INDEX idx_summary_images_summary_id ON activity_summary_images (summary_id);

COMMENT ON TABLE activity_summary_images IS '活动总结中的图片及其经人工确认的标签。';

COMMENT ON COLUMN activity_summary_images.image_id IS '关联记录唯一标识，UUID 格式';
COMMENT ON COLUMN activity_summary_images.summary_id IS '关联总结帖 ID';
COMMENT ON COLUMN activity_summary_images.media_id IS '关联媒体文件 ID';
COMMENT ON COLUMN activity_summary_images.tags IS '人工确认的图片标签，JSONB 格式';

CREATE TABLE activity_reviews (
    review_id   VARCHAR(36) NOT NULL,
    activity_id VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    rating      INTEGER     NOT NULL,
    content     TEXT,
    tags        JSONB,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_activity_reviews PRIMARY KEY (review_id)
);


CREATE UNIQUE INDEX uq_reviews_activity_user
    ON activity_reviews (activity_id, user_id);

COMMENT ON TABLE activity_reviews IS '活动评价，参与者对已结束活动的评分和文字评价。一个用户对同一活动只能评价一次。';

COMMENT ON COLUMN activity_reviews.review_id IS '评价唯一标识，UUID 格式';
COMMENT ON COLUMN activity_reviews.activity_id IS '关联活动 ID';
COMMENT ON COLUMN activity_reviews.user_id IS '评价者用户 ID';
COMMENT ON COLUMN activity_reviews.rating IS '评分（整数），范围 1-5';
COMMENT ON COLUMN activity_reviews.content IS '评价内容';
COMMENT ON COLUMN activity_reviews.tags IS '评价标签，JSONB 数组格式';
COMMENT ON COLUMN activity_reviews.created_at IS '评价时间，UTC 时区';

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

COMMENT ON TABLE friend_requests IS '好友申请，记录一方发起的好友申请及其处理状态。';

COMMENT ON COLUMN friend_requests.request_id IS '申请唯一标识，UUID 格式';
COMMENT ON COLUMN friend_requests.requester_id IS '发起者用户 ID';
COMMENT ON COLUMN friend_requests.target_user_id IS '目标用户 ID';
COMMENT ON COLUMN friend_requests.source IS '好友来源。profile — 个人主页；activityParticipants — 活动参与者；team — 小队成员；qrCode — 扫码添加。不变量：非空';
COMMENT ON COLUMN friend_requests.message IS '附言，发起者发送的留言';
COMMENT ON COLUMN friend_requests.status IS '处理状态。不变量：非空，值为 pending / accepted / rejected / canceled 之一。前置条件：创建时初始值为 pending';
COMMENT ON COLUMN friend_requests.created_at IS '申请时间，UTC 时区';

CREATE TABLE friendships (
    friendship_id  VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    friend_user_id VARCHAR(36)  NOT NULL,
    source         VARCHAR(30)  NOT NULL,
    remark         VARCHAR(50),
    group_tags     JSON,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_friendships PRIMARY KEY (friendship_id),
    CONSTRAINT ck_friendships_pair CHECK (user_id != friend_user_id)
);


CREATE UNIQUE INDEX uq_friendships_user_friend
    ON friendships (user_id, friend_user_id);

COMMENT ON TABLE friendships IS '好友关系，每人一条记录，存储当前用户对好友的关系数据。每对好友关系存在两行（A 到 B 和 B 到 A），各自独立存储备注和分组标签。';

COMMENT ON COLUMN friendships.friendship_id IS '好友关系唯一标识，UUID 格式';
COMMENT ON COLUMN friendships.user_id IS '当前用户 ID';
COMMENT ON COLUMN friendships.friend_user_id IS '好友用户 ID';
COMMENT ON COLUMN friendships.source IS '好友来源。manualRequest — 手动申请通过；mutualFollow — 互关自动升级。不变量：非空';
COMMENT ON COLUMN friendships.remark IS '当前用户给好友的备注名';
COMMENT ON COLUMN friendships.group_tags IS '当前用户给好友设置的分组标签，JSON 字符串数组格式';
COMMENT ON COLUMN friendships.created_at IS '成为好友的时间，UTC 时区';

CREATE TABLE follows (
    follow_id   VARCHAR(36) NOT NULL,
    follower_id VARCHAR(36) NOT NULL,
    followed_id VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (follow_id),
    CONSTRAINT ck_follows_self CHECK (follower_id != followed_id)
);


CREATE INDEX idx_follows_followed ON follows (followed_id);
CREATE UNIQUE INDEX uq_follows_pair
    ON follows (follower_id, followed_id);

COMMENT ON TABLE follows IS '关注关系，记录一方单向关注另一方的行为。互相关注时可自动升级为好友关系。';

COMMENT ON COLUMN follows.follow_id IS '关注关系唯一标识，UUID 格式';
COMMENT ON COLUMN follows.follower_id IS '关注者用户 ID，即主动发起关注的一方';
COMMENT ON COLUMN follows.followed_id IS '被关注者用户 ID';
COMMENT ON COLUMN follows.created_at IS '关注时间，UTC 时区';

CREATE TABLE blacklists (
    blacklist_id   VARCHAR(36) NOT NULL,
    blocker_id     VARCHAR(36) NOT NULL,
    blocked_user_id VARCHAR(36) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_blacklists PRIMARY KEY (blacklist_id),
    CONSTRAINT ck_blacklists_self CHECK (blocker_id != blocked_user_id)
);



CREATE INDEX idx_blacklists_blocked  ON blacklists (blocked_user_id);
CREATE UNIQUE INDEX uq_blacklists_pair
    ON blacklists (blocker_id, blocked_user_id);

COMMENT ON TABLE blacklists IS '黑名单，阻止关注、好友申请和小队加入等社交动作。';

COMMENT ON COLUMN blacklists.blacklist_id IS '黑名单记录唯一标识，UUID 格式';
COMMENT ON COLUMN blacklists.blocker_id IS '屏蔽者用户 ID，即设置黑名单的一方';
COMMENT ON COLUMN blacklists.blocked_user_id IS '被屏蔽用户 ID';
COMMENT ON COLUMN blacklists.created_at IS '屏蔽时间，UTC 时区';

CREATE TABLE reports (
    report_id        VARCHAR(36)  NOT NULL,
    reporter_user_id VARCHAR(36)  NOT NULL,
    target_type      VARCHAR(30)  NOT NULL,
    target_id        VARCHAR(36)  NOT NULL,
    reason           TEXT         NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    handling_note    TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    handled_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_reports PRIMARY KEY (report_id)
);

CREATE INDEX idx_reports_reporter ON reports (reporter_user_id);
CREATE INDEX idx_reports_target   ON reports (target_type, target_id);
CREATE INDEX idx_reports_status   ON reports (status);

COMMENT ON TABLE reports IS '举报，由用户提交并进入后台处理流程，目标可以是用户、小队、活动或消息。';

COMMENT ON COLUMN reports.report_id IS '举报唯一标识，UUID 格式';
COMMENT ON COLUMN reports.reporter_user_id IS '举报者用户 ID';
COMMENT ON COLUMN reports.target_type IS '被举报对象类型。不变量：非空，值为 user / team / activity / message 之一';
COMMENT ON COLUMN reports.target_id IS '被举报对象 ID';
COMMENT ON COLUMN reports.reason IS '举报原因';
COMMENT ON COLUMN reports.status IS '处理状态。不变量：非空，值为 pending / processing / resolved / rejected 之一';
COMMENT ON COLUMN reports.handling_note IS '处理备注，管理员填写';
COMMENT ON COLUMN reports.created_at IS '举报时间，UTC 时区';
COMMENT ON COLUMN reports.handled_at IS '处理时间，UTC 时区，null 表示未处理';

CREATE TABLE teams (
    team_id          VARCHAR(36)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    tags             JSON,
    join_mode        VARCHAR(30)  NOT NULL,
    capacity         INTEGER      NOT NULL,
    description      TEXT,
    avatar_media_id  UUID,
    status           VARCHAR(20)  NOT NULL,
    creator_id        VARCHAR(36)  NOT NULL,
    leader_id        VARCHAR(36)  NOT NULL,
    chat_id          VARCHAR(36),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_teams PRIMARY KEY (team_id),
    CONSTRAINT uq_teams_name UNIQUE (name)
);

CREATE INDEX idx_teams_creator_id ON teams (creator_id);
CREATE INDEX idx_teams_leader_id ON teams (leader_id);
CREATE INDEX idx_teams_status    ON teams (status);
CREATE INDEX idx_teams_chat_id   ON teams (chat_id);


COMMENT ON TABLE teams IS '小队，兴趣社交的基本组织单位。小队名称全平台唯一。创建小队时自动生成群聊会话（chat_id）。小队解散或停用后不再出现在发现列表。';

COMMENT ON COLUMN teams.team_id IS '小队唯一标识，UUID 格式';
COMMENT ON COLUMN teams.name IS '小队名称，全平台唯一';
COMMENT ON COLUMN teams.tags IS '小队标签，JSON 字符串数组格式';
COMMENT ON COLUMN teams.join_mode IS '加入模式。publicJoin — 自由加入；approvalRequired — 需审核。不变量：非空，值为 publicJoin / approvalRequired 之一';
COMMENT ON COLUMN teams.capacity IS '人数上限';
COMMENT ON COLUMN teams.description IS '小队简介';
COMMENT ON COLUMN teams.avatar_media_id IS '头像媒体文件 ID';
COMMENT ON COLUMN teams.status IS '小队状态。active — 活跃；dissolved — 已解散；disabled — 已停用。不变量：非空，值为 active / dissolved / disabled 之一';
COMMENT ON COLUMN teams.creator_id IS '小队创建者用户 ID，创建后不随队长转移改变';
COMMENT ON COLUMN teams.leader_id IS '队长用户 ID';
COMMENT ON COLUMN teams.chat_id IS '关联群聊会话 ID，创建小队时自动生成';
COMMENT ON COLUMN teams.created_at IS '创建时间，UTC 时区';
COMMENT ON COLUMN teams.updated_at IS '最后更新时间，UTC 时区';

CREATE TABLE team_members (
    member_id  VARCHAR(36) NOT NULL,
    team_id    VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    role       VARCHAR(20) NOT NULL,
    points     INTEGER     NOT NULL DEFAULT 0,
    joined_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_members PRIMARY KEY (member_id)
);


CREATE INDEX idx_team_members_user_id ON team_members (user_id);
CREATE UNIQUE INDEX uq_team_members_pair
    ON team_members (team_id, user_id);

COMMENT ON TABLE team_members IS '小队成员，记录用户在小队中的角色和积分。';

COMMENT ON COLUMN team_members.member_id IS '成员记录唯一标识，UUID 格式';
COMMENT ON COLUMN team_members.team_id IS '关联小队 ID';
COMMENT ON COLUMN team_members.user_id IS '成员用户 ID';
COMMENT ON COLUMN team_members.role IS '角色。leader — 队长；admin — 管理员；member — 普通成员。不变量：非空，值为 leader / admin / member 之一';
COMMENT ON COLUMN team_members.points IS '成员积分，默认 0';
COMMENT ON COLUMN team_members.joined_at IS '加入时间，UTC 时区';

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

COMMENT ON TABLE team_join_requests IS '入队申请，记录用户申请加入审核制小队的请求。';

COMMENT ON COLUMN team_join_requests.request_id IS '申请唯一标识，UUID 格式';
COMMENT ON COLUMN team_join_requests.team_id IS '关联小队 ID';
COMMENT ON COLUMN team_join_requests.user_id IS '申请人用户 ID';
COMMENT ON COLUMN team_join_requests.message IS '申请附言';
COMMENT ON COLUMN team_join_requests.status IS '处理状态。不变量：非空，值为 pending / accepted / rejected / canceled 之一';
COMMENT ON COLUMN team_join_requests.created_at IS '申请时间，UTC 时区';

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

COMMENT ON TABLE team_point_records IS '小队积分变动记录，记录成员积分增减的历史明细。用于积分榜的计算和审计追溯。';

COMMENT ON COLUMN team_point_records.record_id IS '积分变动记录唯一标识，UUID 格式';
COMMENT ON COLUMN team_point_records.team_id IS '关联小队 ID';
COMMENT ON COLUMN team_point_records.user_id IS '成员用户 ID';
COMMENT ON COLUMN team_point_records.point_change IS '积分变动值。正数为增加，负数为扣减。不变量：非零';
COMMENT ON COLUMN team_point_records.reason IS '变动原因，如"参加活动"、"违纪扣分"';
COMMENT ON COLUMN team_point_records.created_at IS '变动时间，UTC 时区';

CREATE TABLE team_moderation_records (
    record_id   VARCHAR(36) NOT NULL,
    team_id     VARCHAR(36) NOT NULL,
    action      VARCHAR(40) NOT NULL,
    reason      TEXT        NOT NULL,
    operator_id VARCHAR(36) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_moderation_records PRIMARY KEY (record_id)
);

CREATE INDEX idx_team_moderation_records_team ON team_moderation_records (team_id);
CREATE INDEX idx_team_moderation_records_operator ON team_moderation_records (operator_id);

COMMENT ON TABLE team_moderation_records IS '小队后台治理记录，记录停用、恢复等治理动作。';

COMMENT ON COLUMN team_moderation_records.record_id IS '治理记录唯一标识，UUID 格式';
COMMENT ON COLUMN team_moderation_records.team_id IS '被治理小队 ID';
COMMENT ON COLUMN team_moderation_records.action IS '治理动作。不变量：非空，值为 disableTeam / restoreTeam 之一';
COMMENT ON COLUMN team_moderation_records.reason IS '治理原因或说明';
COMMENT ON COLUMN team_moderation_records.operator_id IS '操作管理员 ID';
COMMENT ON COLUMN team_moderation_records.created_at IS '治理时间，UTC 时区';

-- reputation - 信誉积分

CREATE TABLE reputation_records (
    record_id    VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    score_change INTEGER      NOT NULL,
    reason       TEXT         NOT NULL,
    source       VARCHAR(30)  NOT NULL,
    reference_id VARCHAR(36),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_reputation_records PRIMARY KEY (record_id)
);

CREATE INDEX idx_reputation_records_user ON reputation_records (user_id);
CREATE UNIQUE INDEX uk_reputation_records_source_reference ON reputation_records (source, reference_id);
ALTER TABLE reputation_records ADD CONSTRAINT ck_reputation_records_source CHECK (source IN ('report', 'admin_manual'));

COMMENT ON TABLE reputation_records IS '信誉积分变更记录，记录每次积分变动的原因和关联信息。仅保存审计流水，当前信誉分由举报事实重算。';
COMMENT ON COLUMN reputation_records.record_id IS '记录唯一标识，UUID 格式';
COMMENT ON COLUMN reputation_records.user_id IS '用户 ID，被变更积分的用户';
COMMENT ON COLUMN reputation_records.score_change IS '积分变动量，正数为加分，负数为扣分';
COMMENT ON COLUMN reputation_records.reason IS '变更原因描述';
COMMENT ON COLUMN reputation_records.source IS '变更来源。report — 举报核实后的扣分；admin_manual — 管理员手动调整';
COMMENT ON COLUMN reputation_records.reference_id IS '关联实体 ID，如举报 ID，便于审计追溯';
COMMENT ON COLUMN reputation_records.created_at IS '变更时间，UTC 时区';

-- --------------------------------------------------------------------------
-- chat - 即时通讯
-- --------------------------------------------------------------------------

CREATE TABLE conversations (
    conversation_id  VARCHAR(36)  NOT NULL,
    kind             VARCHAR(20)  NOT NULL,
    title            VARCHAR(100),
    avatar_media_id  UUID,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (conversation_id)
);

CREATE INDEX idx_conversations_kind ON conversations (kind);

COMMENT ON TABLE conversations IS '会话，表示一个好友对话或小队群聊。kind 区分好友会话和小队群聊。小队群聊的成员与小队成员同步。';

COMMENT ON COLUMN conversations.conversation_id IS '会话唯一标识，UUID 格式';
COMMENT ON COLUMN conversations.kind IS '会话类型。friend — 好友一对一对话；team — 小队群聊。不变量：非空，值为 friend / team 之一';
COMMENT ON COLUMN conversations.title IS '会话标题。好友会话可为空（由前端计算显示名），小队群聊为小队名称';
COMMENT ON COLUMN conversations.avatar_media_id IS '会话头像媒体文件 ID';
COMMENT ON COLUMN conversations.created_at IS '创建时间，UTC 时区';
COMMENT ON COLUMN conversations.updated_at IS '最后更新时间（最后一条消息的时间），UTC 时区';

CREATE TABLE conversation_members (
    member_id       VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    joined_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_conversation_members PRIMARY KEY (member_id)
);


CREATE INDEX idx_conversation_members_user ON conversation_members (user_id);
CREATE UNIQUE INDEX uq_conversation_members_pair
    ON conversation_members (conversation_id, user_id);

COMMENT ON TABLE conversation_members IS '会话成员，记录用户属于哪些会话。好友会话有 2 个成员，小队群聊的成员与小队成员列表同步。';

COMMENT ON COLUMN conversation_members.member_id IS '成员记录唯一标识，UUID 格式';
COMMENT ON COLUMN conversation_members.conversation_id IS '关联会话 ID';
COMMENT ON COLUMN conversation_members.user_id IS '成员用户 ID';
COMMENT ON COLUMN conversation_members.joined_at IS '加入时间，UTC 时区';

CREATE TABLE chat_messages (
    message_id          VARCHAR(36)  NOT NULL,
    conversation_id     VARCHAR(36)  NOT NULL,
    sender_id           VARCHAR(36)  NOT NULL,
    kind                VARCHAR(20)  NOT NULL,
    text                TEXT,
    image_media_id      UUID,
    location_lon        DOUBLE PRECISION,
    location_lat        DOUBLE PRECISION,
    location_city       VARCHAR(100),
    location_address    VARCHAR(500),
    location_place_name VARCHAR(200),
    mentioned_user_ids  JSONB,
    mention_all         BOOLEAN,
    recalled            BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (message_id)
);

CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id);
CREATE INDEX idx_chat_messages_sender       ON chat_messages (sender_id);
CREATE INDEX idx_chat_messages_sent_at      ON chat_messages (sent_at);

COMMENT ON TABLE chat_messages IS '聊天消息，支持文本、图片和位置共享三种类型。撤回后 recalled 为 true，消息内容保持不动用于审计。mention_all 仅队长和管理员可用。';

COMMENT ON COLUMN chat_messages.message_id IS '消息唯一标识，UUID 格式';
COMMENT ON COLUMN chat_messages.conversation_id IS '关联会话 ID';
COMMENT ON COLUMN chat_messages.sender_id IS '发送者用户 ID';
COMMENT ON COLUMN chat_messages.kind IS '消息类型。text — 文本消息；image — 图片消息；location — 位置共享。不变量：非空，值为 text / image / location 之一';
COMMENT ON COLUMN chat_messages.text IS '文本内容，kind 为 TEXT 时有值，其他类型时为空';
COMMENT ON COLUMN chat_messages.image_media_id IS '图片媒体文件 ID，kind 为 IMAGE 时有值';
COMMENT ON COLUMN chat_messages.location_lon IS '位置经度，kind 为 LOCATION 时有值';
COMMENT ON COLUMN chat_messages.location_lat IS '位置纬度，kind 为 LOCATION 时有值';
COMMENT ON COLUMN chat_messages.location_city IS '位置所在城市，kind 为 LOCATION 时有值';
COMMENT ON COLUMN chat_messages.location_address IS '位置详细地址，kind 为 LOCATION 时有值';
COMMENT ON COLUMN chat_messages.location_place_name IS '位置地点名称，kind 为 LOCATION 时有值';
COMMENT ON COLUMN chat_messages.mentioned_user_ids IS '被 @ 的用户 ID 列表，JSONB 数组格式';
COMMENT ON COLUMN chat_messages.mention_all IS '是否 @全体成员。仅队长和管理员可用';
COMMENT ON COLUMN chat_messages.recalled IS '是否已撤回，默认 false。撤回后消息内容保持不变以备审计';
COMMENT ON COLUMN chat_messages.sent_at IS '发送时间，UTC 时区';

CREATE TABLE message_reads (
    read_id    VARCHAR(36)  NOT NULL,
    message_id VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    status     VARCHAR(10)  NOT NULL,
    read_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_message_reads PRIMARY KEY (read_id),
    CONSTRAINT uq_message_reads_msg_user UNIQUE (message_id, user_id)
);

CREATE INDEX idx_message_reads_message ON message_reads (message_id);
CREATE INDEX idx_message_reads_user       ON message_reads (user_id);

COMMENT ON TABLE message_reads IS '消息读取状态，记录每用户对每条消息的已读/未读状态。同一用户对同一消息只能有一条记录。';

COMMENT ON COLUMN message_reads.read_id IS '读取状态记录唯一标识，UUID 格式';
COMMENT ON COLUMN message_reads.message_id IS '关联消息 ID';
COMMENT ON COLUMN message_reads.user_id IS '用户 ID';
COMMENT ON COLUMN message_reads.status IS '读取状态。unread — 未读；read — 已读。不变量：值为 unread / read 之一';
COMMENT ON COLUMN message_reads.read_at IS '首次阅读时间，null 表示尚未阅读';

CREATE TABLE team_announcements (
    announcement_id VARCHAR(36)  NOT NULL,
    team_id         VARCHAR(36)  NOT NULL,
    publisher_id    VARCHAR(36)  NOT NULL,
    content         TEXT         NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_announcements PRIMARY KEY (announcement_id)
);

CREATE INDEX idx_team_announcements_team ON team_announcements (team_id);

COMMENT ON TABLE team_announcements IS '群公告，由队长或管理员在小队中发布。';

COMMENT ON COLUMN team_announcements.announcement_id IS '公告唯一标识，UUID 格式';
COMMENT ON COLUMN team_announcements.team_id IS '关联小队 ID';
COMMENT ON COLUMN team_announcements.publisher_id IS '发布者用户 ID';
COMMENT ON COLUMN team_announcements.content IS '公告内容';
COMMENT ON COLUMN team_announcements.published_at IS '发布时间，UTC 时区';

CREATE TABLE team_polls (
    poll_id    VARCHAR(36)  NOT NULL,
    team_id    VARCHAR(36)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    deadline   TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_team_polls PRIMARY KEY (poll_id)
);

CREATE INDEX idx_team_polls_team ON team_polls (team_id);

COMMENT ON TABLE team_polls IS '群投票，由小队成员创建。选项至少两个，与 poll_options 一对多关联。';

COMMENT ON COLUMN team_polls.poll_id IS '投票唯一标识，UUID 格式';
COMMENT ON COLUMN team_polls.team_id IS '关联小队 ID';
COMMENT ON COLUMN team_polls.title IS '投票标题';
COMMENT ON COLUMN team_polls.deadline IS '投票截止时间，null 表示无截止时间';
COMMENT ON COLUMN team_polls.created_at IS '创建时间，UTC 时区';

CREATE TABLE poll_options (
    option_id VARCHAR(36)  NOT NULL,
    poll_id   VARCHAR(36)  NOT NULL,
    content   VARCHAR(500) NOT NULL,
    CONSTRAINT pk_poll_options PRIMARY KEY (option_id)
);

CREATE INDEX idx_poll_options_poll ON poll_options (poll_id);

COMMENT ON TABLE poll_options IS '投票选项，属于一个群投票。';

COMMENT ON COLUMN poll_options.option_id IS '选项唯一标识，UUID 格式';
COMMENT ON COLUMN poll_options.poll_id IS '关联投票 ID';
COMMENT ON COLUMN poll_options.content IS '选项文本内容';

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

COMMENT ON TABLE poll_votes IS '投票记录，记录用户在投票中的选项选择。同一用户对同一投票只能保留一个选择（后续投票覆盖前次选择）。';

COMMENT ON COLUMN poll_votes.vote_id IS '投票记录唯一标识，UUID 格式';
COMMENT ON COLUMN poll_votes.poll_id IS '关联投票 ID';
COMMENT ON COLUMN poll_votes.option_id IS '关联选项 ID';
COMMENT ON COLUMN poll_votes.user_id IS '投票用户 ID';
COMMENT ON COLUMN poll_votes.voted_at IS '投票时间，UTC 时区';

-- --------------------------------------------------------------------------
-- admin - 后台管理
-- --------------------------------------------------------------------------

CREATE TABLE admins (
    admin_id      VARCHAR(36)  NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    login_attempts       INTEGER NOT NULL DEFAULT 0,
    last_failed_login_at TIMESTAMP WITH TIME ZONE,
    locked_until         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_admins PRIMARY KEY (admin_id),
    CONSTRAINT uq_admins_username UNIQUE (username)
);

COMMENT ON TABLE admins IS '管理员账号，由系统预置，使用用户名登录。管理员不提供注册接口，账号由后台直接创建。密码只保存加盐哈希。';

COMMENT ON COLUMN admins.admin_id IS '管理员唯一标识，UUID 格式';
COMMENT ON COLUMN admins.username IS '管理员用户名，全平台唯一，用于后台登录';
COMMENT ON COLUMN admins.password_hash IS '密码的加盐哈希，不限长度以兼容任意算法';
COMMENT ON COLUMN admins.created_at IS '创建时间，UTC 时区';
COMMENT ON COLUMN admins.updated_at IS '最后更新时间，UTC 时区';
COMMENT ON COLUMN admins.login_attempts IS '连续登录失败次数，成功登录后重置';
COMMENT ON COLUMN admins.last_failed_login_at IS '最近一次登录失败时间，用于失败窗口计算';
COMMENT ON COLUMN admins.locked_until IS '账号锁定截止时间，null 表示未锁定';

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

COMMENT ON TABLE ban_records IS '封禁记录，记录用户被封禁和解封的历史。每次封禁产生一条记录，解封时更新 unbanned_at 字段。';

COMMENT ON COLUMN ban_records.ban_id IS '封禁记录唯一标识，UUID 格式';
COMMENT ON COLUMN ban_records.user_id IS '被封禁用户 ID';
COMMENT ON COLUMN ban_records.operator_id IS '操作管理员 ID';
COMMENT ON COLUMN ban_records.reason IS '封禁原因';
COMMENT ON COLUMN ban_records.banned_at IS '封禁时间，UTC 时区';
COMMENT ON COLUMN ban_records.banned_until IS '封禁截止时间，UTC 时区。超过此时间后应自动解封';
COMMENT ON COLUMN ban_records.unbanned_at IS '实际解封时间，UTC 时区。null 表示尚未解封（可能仍在封禁期内或永久封禁）';

-- ============================================================================
-- Foreign Key Constraints
-- ============================================================================

-- identity - 身份与资料
ALTER TABLE personal_profiles ADD CONSTRAINT fk_personal_profiles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE personal_profiles ADD CONSTRAINT fk_personal_profiles_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_files(media_id) ON DELETE SET NULL;

ALTER TABLE merchant_profiles ADD CONSTRAINT fk_merchant_profiles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE merchant_profiles ADD CONSTRAINT fk_merchant_profiles_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_files(media_id) ON DELETE SET NULL;

ALTER TABLE qualifications ADD CONSTRAINT fk_qualifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE qualifications ADD CONSTRAINT fk_qualifications_reviewer FOREIGN KEY (reviewer_id) REFERENCES admins(admin_id) ON DELETE SET NULL;

ALTER TABLE security_tokens ADD CONSTRAINT fk_security_tokens_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE media_files ADD CONSTRAINT fk_media_files_uploader FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE RESTRICT;

-- activities - 活动
ALTER TABLE activities ADD CONSTRAINT fk_activities_organizer FOREIGN KEY (organizer_id) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE activities ADD CONSTRAINT fk_activities_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE SET NULL;

ALTER TABLE activity_images ADD CONSTRAINT fk_activity_images_activity FOREIGN KEY (activity_id) REFERENCES activities(activity_id) ON DELETE CASCADE;
ALTER TABLE activity_images ADD CONSTRAINT fk_activity_images_media FOREIGN KEY (media_id) REFERENCES media_files(media_id) ON DELETE RESTRICT;

ALTER TABLE activity_review_records ADD CONSTRAINT fk_review_records_activity FOREIGN KEY (activity_id) REFERENCES activities(activity_id) ON DELETE CASCADE;
ALTER TABLE activity_review_records ADD CONSTRAINT fk_review_records_reviewer FOREIGN KEY (reviewer_id) REFERENCES admins(admin_id) ON DELETE SET NULL;

ALTER TABLE activity_templates ADD CONSTRAINT fk_templates_cover_image FOREIGN KEY (default_cover_image_media_id) REFERENCES media_files(media_id) ON DELETE SET NULL;

ALTER TABLE activity_registrations ADD CONSTRAINT fk_registrations_activity FOREIGN KEY (activity_id) REFERENCES activities(activity_id) ON DELETE CASCADE;
ALTER TABLE activity_registrations ADD CONSTRAINT fk_registrations_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;

ALTER TABLE activity_summary_posts ADD CONSTRAINT fk_summary_posts_activity FOREIGN KEY (activity_id) REFERENCES activities(activity_id) ON DELETE CASCADE;
ALTER TABLE activity_summary_posts ADD CONSTRAINT fk_summary_posts_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;

ALTER TABLE activity_summary_images ADD CONSTRAINT fk_summary_images_post FOREIGN KEY (summary_id) REFERENCES activity_summary_posts(summary_id) ON DELETE CASCADE;
ALTER TABLE activity_summary_images ADD CONSTRAINT fk_summary_images_media FOREIGN KEY (media_id) REFERENCES media_files(media_id) ON DELETE RESTRICT;

ALTER TABLE activity_reviews ADD CONSTRAINT fk_reviews_activity FOREIGN KEY (activity_id) REFERENCES activities(activity_id) ON DELETE CASCADE;
ALTER TABLE activity_reviews ADD CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;

-- social - 好友社群
ALTER TABLE friend_requests ADD CONSTRAINT fk_friend_requests_from FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE friend_requests ADD CONSTRAINT fk_friend_requests_to FOREIGN KEY (target_user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE friendships ADD CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE friendships ADD CONSTRAINT fk_friendships_friend_user FOREIGN KEY (friend_user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE follows ADD CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE follows ADD CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE blacklists ADD CONSTRAINT fk_blacklists_blocker FOREIGN KEY (blocker_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE blacklists ADD CONSTRAINT fk_blacklists_blocked FOREIGN KEY (blocked_user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE reports ADD CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE teams ADD CONSTRAINT fk_teams_creator FOREIGN KEY (creator_id) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE teams ADD CONSTRAINT fk_teams_leader FOREIGN KEY (leader_id) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE teams ADD CONSTRAINT fk_teams_chat FOREIGN KEY (chat_id) REFERENCES conversations(conversation_id) ON DELETE SET NULL;
ALTER TABLE teams ADD CONSTRAINT fk_teams_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_files(media_id) ON DELETE SET NULL;

ALTER TABLE team_members ADD CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE;
ALTER TABLE team_members ADD CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;

ALTER TABLE team_join_requests ADD CONSTRAINT fk_team_join_req_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE;
ALTER TABLE team_join_requests ADD CONSTRAINT fk_team_join_req_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE team_point_records ADD CONSTRAINT fk_team_points_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE;
ALTER TABLE team_point_records ADD CONSTRAINT fk_team_points_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;

ALTER TABLE team_moderation_records ADD CONSTRAINT fk_team_moderation_records_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE;
ALTER TABLE team_moderation_records ADD CONSTRAINT fk_team_moderation_records_operator FOREIGN KEY (operator_id) REFERENCES admins(admin_id) ON DELETE RESTRICT;

ALTER TABLE reputation_records ADD CONSTRAINT fk_reputation_records_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- chat - 即时通讯
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_avatar FOREIGN KEY (avatar_media_id) REFERENCES media_files(media_id) ON DELETE SET NULL;

ALTER TABLE conversation_members ADD CONSTRAINT fk_conv_members_conv FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE;
ALTER TABLE conversation_members ADD CONSTRAINT fk_conv_members_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;

ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_conv FOREIGN KEY (conversation_id) REFERENCES conversations(conversation_id) ON DELETE CASCADE;
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_image FOREIGN KEY (image_media_id) REFERENCES media_files(media_id) ON DELETE SET NULL;

ALTER TABLE message_reads ADD CONSTRAINT fk_message_reads_message FOREIGN KEY (message_id) REFERENCES chat_messages(message_id) ON DELETE CASCADE;
ALTER TABLE message_reads ADD CONSTRAINT fk_message_reads_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE team_announcements ADD CONSTRAINT fk_announcements_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE;
ALTER TABLE team_announcements ADD CONSTRAINT fk_announcements_pub FOREIGN KEY (publisher_id) REFERENCES users(user_id) ON DELETE RESTRICT;

ALTER TABLE team_polls ADD CONSTRAINT fk_team_polls_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE;

ALTER TABLE poll_options ADD CONSTRAINT fk_poll_options_poll FOREIGN KEY (poll_id) REFERENCES team_polls(poll_id) ON DELETE CASCADE;

ALTER TABLE poll_votes ADD CONSTRAINT fk_poll_votes_poll FOREIGN KEY (poll_id) REFERENCES team_polls(poll_id) ON DELETE CASCADE;
ALTER TABLE poll_votes ADD CONSTRAINT fk_poll_votes_option FOREIGN KEY (option_id) REFERENCES poll_options(option_id) ON DELETE CASCADE;
ALTER TABLE poll_votes ADD CONSTRAINT fk_poll_votes_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- admin - 后台管理
ALTER TABLE ban_records ADD CONSTRAINT fk_ban_records_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE ban_records ADD CONSTRAINT fk_ban_records_operator FOREIGN KEY (operator_id) REFERENCES admins(admin_id) ON DELETE RESTRICT;

-- ============================================================================
-- CHECK Constraints (enum validation)
-- ============================================================================

-- identity
ALTER TABLE users ADD CONSTRAINT ck_users_kind CHECK (kind IN ('personal', 'merchant'));
ALTER TABLE users ADD CONSTRAINT ck_users_account_status CHECK (account_status IN ('inactive', 'active', 'banned'));

ALTER TABLE personal_profiles ADD CONSTRAINT ck_personal_profiles_gender CHECK (gender IN ('unspecified', 'female', 'male', 'other'));

ALTER TABLE qualifications ADD CONSTRAINT ck_qualifications_status CHECK (status IN ('not_submitted', 'pending', 'approved', 'rejected'));

ALTER TABLE security_tokens ADD CONSTRAINT ck_security_tokens_token_type CHECK (token_type IN ('activation', 'password_reset', 'refresh'));

-- common
ALTER TABLE media_files ADD CONSTRAINT ck_media_files_usage CHECK (usage IN ('avatar', 'merchantLicense', 'activityImage', 'chatImage', 'teamFile', 'teamAlbum', 'summaryImage', 'activityReviewImage'));

-- activities
ALTER TABLE activities ADD CONSTRAINT ck_activities_review_status CHECK (review_status IN ('draft', 'pending', 'approved', 'rejected', 'changeRequired'));
ALTER TABLE activities ADD CONSTRAINT ck_activities_runtime_status CHECK (runtime_status IN ('notStarted', 'registering', 'registrationClosed', 'ongoing', 'ended', 'takenDown'));

ALTER TABLE activity_review_records ADD CONSTRAINT ck_review_records_result CHECK (result IN ('pending', 'approved', 'rejected', 'changeRequired'));

ALTER TABLE activity_registrations ADD CONSTRAINT ck_registrations_status CHECK (status IN ('registered', 'waiting', 'waitingConfirmation', 'canceled', 'checkedIn'));

-- social
ALTER TABLE friend_requests ADD CONSTRAINT ck_friend_requests_source CHECK (source IN ('profile', 'activityParticipants', 'team', 'qrCode'));
ALTER TABLE friend_requests ADD CONSTRAINT ck_friend_requests_status CHECK (status IN ('pending', 'accepted', 'rejected', 'canceled'));

ALTER TABLE friendships ADD CONSTRAINT ck_friendships_source CHECK (source IN ('manualRequest', 'mutualFollow'));

ALTER TABLE reports ADD CONSTRAINT ck_reports_target_type CHECK (target_type IN ('user', 'team', 'activity', 'message'));
ALTER TABLE reports ADD CONSTRAINT ck_reports_status CHECK (status IN ('pending', 'processing', 'resolved', 'rejected'));

ALTER TABLE teams ADD CONSTRAINT ck_teams_join_mode CHECK (join_mode IN ('publicJoin', 'approvalRequired'));
ALTER TABLE teams ADD CONSTRAINT ck_teams_status CHECK (status IN ('active', 'dissolved', 'disabled'));

ALTER TABLE team_members ADD CONSTRAINT ck_team_members_role CHECK (role IN ('leader', 'admin', 'member'));

ALTER TABLE team_join_requests ADD CONSTRAINT ck_team_join_requests_status CHECK (status IN ('pending', 'accepted', 'rejected', 'canceled'));

ALTER TABLE team_moderation_records ADD CONSTRAINT ck_team_moderation_records_action CHECK (action IN ('disableTeam', 'restoreTeam'));

-- chat
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_kind CHECK (kind IN ('friend', 'team'));

ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_kind CHECK (kind IN ('text', 'image', 'location', 'emoticon'));

ALTER TABLE message_reads ADD CONSTRAINT ck_message_reads_status CHECK (status IN ('unread', 'read'));

-- ============================================================================
-- Missing FK Indexes
-- ============================================================================

CREATE INDEX idx_personal_profiles_avatar ON personal_profiles (avatar_media_id);
CREATE INDEX idx_merchant_profiles_avatar ON merchant_profiles (avatar_media_id);
CREATE INDEX idx_qualifications_reviewer ON qualifications (reviewer_id);
CREATE INDEX idx_activity_images_media ON activity_images (media_id);
CREATE INDEX idx_review_records_reviewer ON activity_review_records (reviewer_id);
CREATE INDEX idx_templates_cover_image ON activity_templates (default_cover_image_media_id);
CREATE INDEX idx_summary_posts_user ON activity_summary_posts (user_id);
CREATE INDEX idx_summary_images_media ON activity_summary_images (media_id);
CREATE INDEX idx_teams_avatar ON teams (avatar_media_id);
CREATE INDEX idx_conversations_avatar ON conversations (avatar_media_id);
CREATE INDEX idx_chat_messages_image ON chat_messages (image_media_id);
CREATE INDEX idx_announcements_publisher ON team_announcements (publisher_id);
CREATE INDEX idx_poll_votes_option ON poll_votes (option_id);
CREATE INDEX idx_ban_records_operator ON ban_records (operator_id);
CREATE INDEX idx_reviews_user ON activity_reviews (user_id);

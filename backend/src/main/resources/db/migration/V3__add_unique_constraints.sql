-- 为好友关系、关注关系、黑名单、会话成员添加业务唯一约束，
-- 防止应用层竞态条件导致的重复记录。
ALTER TABLE friendships
    ADD CONSTRAINT uq_friendships_user_friend UNIQUE (user_id, friend_user_id);

ALTER TABLE follows
    ADD CONSTRAINT uq_follows_follower_followed UNIQUE (follower_id, followed_id);

ALTER TABLE blacklists
    ADD CONSTRAINT uq_blacklists_blocker_blocked UNIQUE (blocker_id, blocked_user_id);

ALTER TABLE conversation_members
    ADD CONSTRAINT uq_conversation_members_conv_user UNIQUE (conversation_id, user_id);

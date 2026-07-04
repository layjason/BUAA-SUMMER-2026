ALTER TABLE activities ADD COLUMN ai_content_review_json TEXT;

COMMENT ON COLUMN activities.ai_content_review_json IS 'AI 内容安全审核结果快照 JSON。前置条件：活动提交审核时由服务端生成；后置条件：活动详情可回显最近一次 AI 审核结果。';

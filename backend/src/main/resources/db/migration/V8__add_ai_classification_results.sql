-- V8__add_ai_classification_results.sql
-- 新增 AI 图片分类结果缓存表（兼容 PostgreSQL 和 H2 PostgreSQL 模式）
-- 注意：H2 不支持 GENERATED ALWAYS AS IDENTITY 语法

CREATE TABLE ai_classification_results (
    media_id UUID NOT NULL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    classified_at TIMESTAMP NOT NULL DEFAULT NOW(),
    task_id UUID,
    CONSTRAINT fk_ai_result_media FOREIGN KEY (media_id) REFERENCES media_files(media_id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_result_task_id ON ai_classification_results(task_id);

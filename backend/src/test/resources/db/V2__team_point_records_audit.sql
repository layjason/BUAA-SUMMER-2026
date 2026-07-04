ALTER TABLE team_point_records
    ADD COLUMN source       VARCHAR(30) NOT NULL DEFAULT 'manual',
    ADD COLUMN reference_id VARCHAR(36);

CREATE UNIQUE INDEX uq_team_point_records_source_ref
    ON team_point_records (source, reference_id) NULLS NOT DISTINCT;

-- =====================================================
-- V2__sync_postgres_schema.sql | Blueprint hardening
-- Ensures PostgreSQL metadata matches the blueprint defaults.
-- =====================================================

CREATE UNIQUE INDEX IF NOT EXISTS user_email_unique_ci
    ON core_user (LOWER(email));

CREATE INDEX IF NOT EXISTS core_collection_user_id_idx
    ON core_collection (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS collection_unique_name_per_user
    ON core_collection (LOWER(name), user_id);

CREATE INDEX IF NOT EXISTS core_lesson_user_id_idx
    ON core_lesson (user_id);

CREATE INDEX IF NOT EXISTS core_pin_panel_id_idx
    ON core_pin (panel_id);

CREATE INDEX IF NOT EXISTS core_pin_collection_id_idx
    ON core_pin (collection_id);

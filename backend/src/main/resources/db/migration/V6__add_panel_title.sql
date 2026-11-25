-- =====================================================
-- V6__add_panel_title.sql
-- Persist panel/section titles (user-provided). Empty means no custom title.
-- =====================================================

ALTER TABLE core_panel
    ADD COLUMN IF NOT EXISTS title VARCHAR(200) NOT NULL DEFAULT '';
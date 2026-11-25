-- =====================================================
-- V5__allow_aesthetic_theme_values.sql
-- Allow additional reader/dashboard theme selections.
-- =====================================================

ALTER TABLE core_usersettings DROP CONSTRAINT IF EXISTS usersettings_theme_valid;
ALTER TABLE core_usersettings
    ADD CONSTRAINT usersettings_theme_valid
    CHECK (theme IN ('light', 'dark', 'sunrise', 'sage', 'cloud', 'dusk', 'cobalt', 'midnight'));

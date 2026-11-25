-- =====================================================
-- V3__add_user_settings_ui_fields.sql | Blueprint compatibility patch
-- Safe for both the new core_* schema and older database layouts.
-- =====================================================

DO $$
BEGIN
    IF to_regclass('public.core_usersettings') IS NOT NULL THEN
        ALTER TABLE core_usersettings
            ADD COLUMN IF NOT EXISTS background VARCHAR(10) NOT NULL DEFAULT 'default',
            ADD COLUMN IF NOT EXISTS timer_preset VARCHAR(10) NOT NULL DEFAULT '25-5';

        UPDATE core_usersettings
        SET background = 'default'
        WHERE background IS NULL OR background = '';

        UPDATE core_usersettings
        SET timer_preset = '25-5'
        WHERE timer_preset IS NULL OR timer_preset = '';
    ELSIF to_regclass('public.user_settings') IS NOT NULL THEN
        ALTER TABLE user_settings
            ADD COLUMN IF NOT EXISTS background VARCHAR(255) NOT NULL DEFAULT 'default',
            ADD COLUMN IF NOT EXISTS timer_preset VARCHAR(255) NOT NULL DEFAULT '25-5';

        UPDATE user_settings
        SET background = 'default'
        WHERE background IS NULL OR background = '';

        UPDATE user_settings
        SET timer_preset = '25-5'
        WHERE timer_preset IS NULL OR timer_preset = '';
    END IF;
END $$;

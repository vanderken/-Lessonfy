-- =====================================================
-- V4__migrate_legacy_tables_to_blueprint.sql
-- Converts legacy Lessonfy table names into the exact blueprint schema.
-- Safe for databases already on the blueprint layout.
-- =====================================================

DO $$
BEGIN
    IF to_regclass('public.users') IS NOT NULL AND to_regclass('public.core_user') IS NULL THEN
        ALTER TABLE users RENAME TO core_user;
    END IF;

    IF to_regclass('public.user_settings') IS NOT NULL AND to_regclass('public.core_usersettings') IS NULL THEN
        ALTER TABLE user_settings RENAME TO core_usersettings;
    END IF;

    IF to_regclass('public.lessons') IS NOT NULL AND to_regclass('public.core_lesson') IS NULL THEN
        ALTER TABLE lessons RENAME TO core_lesson;
    END IF;

    IF to_regclass('public.sections') IS NOT NULL AND to_regclass('public.core_panel') IS NULL THEN
        ALTER TABLE sections RENAME TO core_panel;
    END IF;

    IF to_regclass('public.collections') IS NOT NULL AND to_regclass('public.core_collection') IS NULL THEN
        ALTER TABLE collections RENAME TO core_collection;
    END IF;

    IF to_regclass('public.pins') IS NOT NULL AND to_regclass('public.core_pin') IS NULL THEN
        ALTER TABLE pins RENAME TO core_pin;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.core_user') IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'last_login'
        ) THEN
            ALTER TABLE core_user ADD COLUMN last_login TIMESTAMPTZ;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'is_superuser'
        ) THEN
            ALTER TABLE core_user ADD COLUMN is_superuser BOOLEAN NOT NULL DEFAULT FALSE;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'first_name'
        ) THEN
            ALTER TABLE core_user ADD COLUMN first_name VARCHAR(150) NOT NULL DEFAULT '';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'last_name'
        ) THEN
            ALTER TABLE core_user ADD COLUMN last_name VARCHAR(150) NOT NULL DEFAULT '';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'is_staff'
        ) THEN
            ALTER TABLE core_user ADD COLUMN is_staff BOOLEAN NOT NULL DEFAULT FALSE;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'is_active'
        ) THEN
            ALTER TABLE core_user ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_user' AND column_name = 'date_joined'
        ) THEN
            ALTER TABLE core_user ADD COLUMN date_joined TIMESTAMPTZ NOT NULL DEFAULT NOW();
        END IF;
    END IF;

    IF to_regclass('public.core_usersettings') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_usersettings' AND column_name = 'bionic_reading_enabled'
        ) THEN
            ALTER TABLE core_usersettings RENAME COLUMN bionic_reading_enabled TO bionic_reading;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_usersettings' AND column_name = 'focus_mode_enabled'
        ) THEN
            ALTER TABLE core_usersettings RENAME COLUMN focus_mode_enabled TO focus_mode;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_usersettings' AND column_name = 'sound_type'
        ) THEN
            ALTER TABLE core_usersettings RENAME COLUMN sound_type TO sound;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_usersettings' AND column_name = 'sound_volume'
        ) THEN
            ALTER TABLE core_usersettings RENAME COLUMN sound_volume TO volume;
        END IF;

        ALTER TABLE core_usersettings
            ALTER COLUMN bionic_reading SET DEFAULT TRUE,
            ALTER COLUMN focus_mode SET DEFAULT TRUE,
            ALTER COLUMN font_size SET DEFAULT 19,
            ALTER COLUMN line_spacing SET DEFAULT 1.7,
            ALTER COLUMN volume SET DEFAULT 0.4;

        UPDATE core_usersettings
        SET theme = CASE
            WHEN theme IS NULL OR LOWER(theme) IN ('light', 'default') THEN 'light'
            ELSE 'dark'
        END
        WHERE theme IS NULL OR theme NOT IN ('light', 'dark');

        UPDATE core_usersettings
        SET background = 'default'
        WHERE background IS NULL OR background NOT IN ('default', 'cream', 'mint', 'sky');

        UPDATE core_usersettings
        SET sound = 'off'
        WHERE sound IS NULL OR sound NOT IN ('off', 'brown', 'white', 'rain');

        UPDATE core_usersettings
        SET timer_preset = '25-5'
        WHERE timer_preset IS NULL OR timer_preset NOT IN ('15-3', '25-5', '45-10');

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.core_usersettings'::regclass AND conname = 'usersettings_theme_valid'
        ) THEN
            ALTER TABLE core_usersettings ADD CONSTRAINT usersettings_theme_valid CHECK (theme IN ('light','dark'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.core_usersettings'::regclass AND conname = 'usersettings_background_valid'
        ) THEN
            ALTER TABLE core_usersettings ADD CONSTRAINT usersettings_background_valid CHECK (background IN ('default','cream','mint','sky'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.core_usersettings'::regclass AND conname = 'usersettings_sound_valid'
        ) THEN
            ALTER TABLE core_usersettings ADD CONSTRAINT usersettings_sound_valid CHECK (sound IN ('off','brown','white','rain'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.core_usersettings'::regclass AND conname = 'usersettings_timer_valid'
        ) THEN
            ALTER TABLE core_usersettings ADD CONSTRAINT usersettings_timer_valid CHECK (timer_preset IN ('15-3','25-5','45-10'));
        END IF;
    END IF;

    IF to_regclass('public.core_collection') IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_collection' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE core_collection ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
        END IF;
    END IF;

    IF to_regclass('public.core_lesson') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_lesson' AND column_name = 'subtitle'
        ) THEN
            ALTER TABLE core_lesson ALTER COLUMN subtitle SET DEFAULT '';
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_lesson' AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE core_lesson DROP COLUMN updated_at;
        END IF;
    END IF;

    IF to_regclass('public.core_panel') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_panel' AND column_name = 'content'
        ) THEN
            ALTER TABLE core_panel RENAME COLUMN content TO text;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_panel' AND column_name = 'position'
        ) THEN
            ALTER TABLE core_panel RENAME COLUMN position TO "order";
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_panel' AND column_name = 'is_done'
        ) THEN
            ALTER TABLE core_panel ADD COLUMN is_done BOOLEAN NOT NULL DEFAULT FALSE;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_panel' AND column_name = 'title'
        ) THEN
            ALTER TABLE core_panel DROP COLUMN title;
        END IF;
    END IF;

    IF to_regclass('public.core_pin') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_pin' AND column_name = 'section_id'
        ) THEN
            ALTER TABLE core_pin RENAME COLUMN section_id TO panel_id;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_pin' AND column_name = 'selected_text'
        ) THEN
            ALTER TABLE core_pin RENAME COLUMN selected_text TO text_snippet;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_pin' AND column_name = 'start_position'
        ) THEN
            ALTER TABLE core_pin RENAME COLUMN start_position TO start_offset;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_pin' AND column_name = 'end_position'
        ) THEN
            ALTER TABLE core_pin RENAME COLUMN end_position TO end_offset;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_pin' AND column_name = 'collection_id'
        ) THEN
            ALTER TABLE core_pin ADD COLUMN collection_id BIGINT;
        END IF;

        IF to_regclass('public.pin_collection_assignments') IS NOT NULL THEN
            UPDATE core_pin p
            SET collection_id = sub.collection_id
            FROM (
                SELECT pin_id, MIN(collection_id) AS collection_id
                FROM pin_collection_assignments
                GROUP BY pin_id
            ) AS sub
            WHERE p.collection_id IS NULL AND p.id = sub.pin_id;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'core_pin' AND column_name = 'collection_id'
        ) THEN
            ALTER TABLE core_pin ALTER COLUMN collection_id SET NOT NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'public.core_pin'::regclass AND conname = 'pin_end_after_start'
        ) THEN
            ALTER TABLE core_pin ADD CONSTRAINT pin_end_after_start CHECK (end_offset > start_offset);
        END IF;
    END IF;
END $$;

ALTER TABLE IF EXISTS core_user DROP CONSTRAINT IF EXISTS uk_users_email;

CREATE UNIQUE INDEX IF NOT EXISTS user_email_unique_ci ON core_user (LOWER(email));

CREATE INDEX IF NOT EXISTS core_collection_user_id_idx ON core_collection (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS collection_unique_name_per_user ON core_collection (LOWER(name), user_id);
CREATE INDEX IF NOT EXISTS core_lesson_user_id_idx ON core_lesson (user_id);
CREATE INDEX IF NOT EXISTS core_pin_panel_id_idx ON core_pin (panel_id);
CREATE INDEX IF NOT EXISTS core_pin_collection_id_idx ON core_pin (collection_id);

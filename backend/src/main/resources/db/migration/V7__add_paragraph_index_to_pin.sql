-- Adds the paragraph position of a highlighted text within its panel,
-- so highlights made in the reader can be stored as pins and re-rendered.
ALTER TABLE core_pin ADD COLUMN IF NOT EXISTS paragraph_index INT NOT NULL DEFAULT 0;

-- A highlight that covers a single word can legitimately have start == end,
-- so relax the constraint from strictly-greater to greater-or-equal.
ALTER TABLE core_pin DROP CONSTRAINT IF EXISTS pin_end_after_start;
ALTER TABLE core_pin
    ADD CONSTRAINT pin_end_after_start CHECK (end_offset >= start_offset);

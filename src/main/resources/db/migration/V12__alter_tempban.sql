ALTER TABLE temp_bans ADD COLUMN is_active BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE temp_bans ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE temp_bans ADD COLUMN reason TEXT;
UPDATE temp_bans
SET is_active = false
WHERE discord_id IN (SELECT discord_id FROM users WHERE banned = false);

UPDATE blacklist_entries
SET is_active = false
WHERE discord_id IN (SELECT discord_id FROM users WHERE banned = false);
CREATE TABLE temp_bans (
    id UUID PRIMARY KEY,
    discord_id VARCHAR(255) NOT NULL,
    guild_id VARCHAR(255) NOT NULL,
    unban_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_temp_bans_unban_at ON temp_bans(unban_at);
CREATE INDEX idx_temp_bans_discord_id ON temp_bans(discord_id);
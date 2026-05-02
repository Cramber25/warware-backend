CREATE TABLE blacklist_entries (
    id UUID PRIMARY KEY,
    discord_id VARCHAR(255),
    roblox_id VARCHAR(255),
    roblox_username VARCHAR(255),
    reason TEXT,
    markdown_info TEXT,
    admin_discord_id VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_blacklist_roblox_id ON blacklist_entries(roblox_id);
CREATE INDEX idx_blacklist_discord_id ON blacklist_entries(discord_id);

CREATE TABLE moderation_logs (
    id UUID PRIMARY KEY,
    target_discord_id VARCHAR(255) NOT NULL,
    target_discord_username VARCHAR(255),
    moderator_discord_id VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    reason TEXT,
    duration VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_modlog_target ON moderation_logs(target_discord_id);
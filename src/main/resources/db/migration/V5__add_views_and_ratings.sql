ALTER TABLE assets
    ADD COLUMN view_count INT NOT NULL DEFAULT 0,
    ADD COLUMN average_rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;

CREATE TABLE asset_ratings (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    user_id UUID NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    admin_reply TEXT,
    admin_reply_created_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_asset_ratings_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_ratings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT uq_asset_user_rating UNIQUE (asset_id, user_id)
);

CREATE INDEX idx_asset_ratings_asset_id ON asset_ratings(asset_id);
CREATE INDEX idx_assets_view_count ON assets(view_count DESC);
CREATE INDEX idx_assets_average_rating ON assets(average_rating DESC);
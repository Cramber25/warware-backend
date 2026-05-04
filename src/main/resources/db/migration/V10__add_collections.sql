CREATE TABLE collections (
                             id UUID PRIMARY KEY,
                             name VARCHAR(255) NOT NULL,
                             description TEXT,
                             thumbnail_url VARCHAR(255)
);

CREATE TABLE asset_collections (
                                   asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
                                   collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
                                   PRIMARY KEY (asset_id, collection_id)
);

CREATE INDEX idx_asset_collections_asset_id ON asset_collections(asset_id);
CREATE INDEX idx_asset_collections_collection_id ON asset_collections(collection_id);
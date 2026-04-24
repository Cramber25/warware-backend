CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       discord_id VARCHAR(255) UNIQUE NOT NULL,
                       discord_username VARCHAR(255),
                       discord_avatar_url TEXT,
                       roblox_id VARCHAR(255) UNIQUE,
                       roblox_username VARCHAR(255),
                       roblox_avatar_url TEXT,
                       role VARCHAR(50) NOT NULL DEFAULT 'USER',
                       balance INTEGER NOT NULL DEFAULT 0,
                       banned BOOLEAN NOT NULL DEFAULT FALSE,
                       created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE categories (
                            id UUID PRIMARY KEY,
                            name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE tags (
                      id UUID PRIMARY KEY,
                      name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE store_settings (
                                setting_key VARCHAR(100) PRIMARY KEY,
                                setting_value TEXT NOT NULL,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY,
                            admin_discord_id VARCHAR(255) NOT NULL,
                            action VARCHAR(255) NOT NULL,
                            details TEXT,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE assets (
                        id UUID PRIMARY KEY,
                        creator_id UUID NOT NULL REFERENCES users(id),
                        title VARCHAR(255) NOT NULL,
                        description TEXT,
                        price INTEGER NOT NULL,
                        visibility VARCHAR(50) NOT NULL DEFAULT 'PRIVATE',
                        delivery_type VARCHAR(50) NOT NULL,
                        r2_file_key VARCHAR(500) NOT NULL,
                        thumbnail_url TEXT,
                        gallery_urls TEXT,
                        category_id UUID REFERENCES categories(id),
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE asset_tags (
                            asset_id UUID NOT NULL REFERENCES assets(id),
                            tag_id UUID NOT NULL REFERENCES tags(id),
                            PRIMARY KEY (asset_id, tag_id)
);

CREATE TABLE promo_codes (
                             id UUID PRIMARY KEY,
                             code VARCHAR(50) NOT NULL UNIQUE,
                             discount_percent INTEGER,
                             discount_amount INTEGER,
                             creator_id UUID REFERENCES users(id),
                             target_asset_id UUID REFERENCES assets(id),
                             target_category_id UUID REFERENCES categories(id),
                             target_tag_id UUID REFERENCES tags(id),
                             usage_limit INTEGER,
                             is_per_user BOOLEAN NOT NULL DEFAULT FALSE,
                             is_active BOOLEAN NOT NULL DEFAULT TRUE,
                             created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              user_id UUID NOT NULL REFERENCES users(id),
                              amount INTEGER NOT NULL,
                              transaction_type VARCHAR(50) NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE orders (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id),
                        asset_id UUID NOT NULL REFERENCES assets(id),
                        promo_code_id UUID REFERENCES promo_codes(id),
                        status VARCHAR(50) NOT NULL,
                        downloaded BOOLEAN NOT NULL DEFAULT FALSE,
                        order_type VARCHAR(50) NOT NULL DEFAULT 'PURCHASE',
                        purchase_price INTEGER NOT NULL DEFAULT 0,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE promo_code_usages (
                                   id UUID PRIMARY KEY,
                                   user_id UUID NOT NULL REFERENCES users(id),
                                   promo_code_id UUID NOT NULL REFERENCES promo_codes(id),
                                   created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE tickets (
                         id UUID PRIMARY KEY,
                         order_id UUID NOT NULL REFERENCES orders(id),
                         status VARCHAR(50) NOT NULL,
                         created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ticket_messages (
                                 id UUID PRIMARY KEY,
                                 ticket_id UUID NOT NULL REFERENCES tickets(id),
                                 sender_id UUID NOT NULL REFERENCES users(id),
                                 content TEXT NOT NULL,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_assets_creator_id ON assets(creator_id);
CREATE INDEX idx_assets_category_id ON assets(category_id);
CREATE INDEX idx_asset_tags_asset_id ON asset_tags(asset_id);
CREATE INDEX idx_asset_tags_tag_id ON asset_tags(tag_id);
CREATE INDEX idx_promo_codes_creator_id ON promo_codes(creator_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_asset_id ON orders(asset_id);
CREATE INDEX idx_promo_code_usages_user_id ON promo_code_usages(user_id);
CREATE INDEX idx_promo_code_usages_promo_code_id ON promo_code_usages(promo_code_id);
CREATE INDEX idx_tickets_order_id ON tickets(order_id);
CREATE INDEX idx_ticket_messages_ticket_id ON ticket_messages(ticket_id);
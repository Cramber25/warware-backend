ALTER TABLE users ADD COLUMN email VARCHAR(255);

CREATE TABLE user_login_logs (
                                 id UUID PRIMARY KEY,
                                 user_id UUID NOT NULL REFERENCES users(id),
                                 ip_address VARCHAR(45) NOT NULL,
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_login_logs_user_id ON user_login_logs(user_id);
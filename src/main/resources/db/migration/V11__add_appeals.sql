CREATE TABLE appeals (
                         id UUID PRIMARY KEY,
                         user_id UUID NOT NULL,
                         appeal_type VARCHAR(255) NOT NULL,
                         reference_id UUID,
                         content TEXT NOT NULL,
                         status VARCHAR(255) DEFAULT 'PENDING' NOT NULL,
                         admin_reply TEXT,
                         resolved_by UUID,
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         resolved_at TIMESTAMP WITH TIME ZONE,
                         CONSTRAINT fk_appeal_user FOREIGN KEY (user_id) REFERENCES users(id),
                         CONSTRAINT fk_appeal_admin FOREIGN KEY (resolved_by) REFERENCES users(id)
);

CREATE INDEX idx_appeal_user ON appeals(user_id);
CREATE INDEX idx_appeal_status ON appeals(status);
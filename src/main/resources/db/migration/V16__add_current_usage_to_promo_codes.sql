ALTER TABLE promo_codes ADD COLUMN current_usage INTEGER NOT NULL DEFAULT 0;

UPDATE promo_codes pc
SET current_usage = (
    SELECT CAST(COUNT(*) AS INTEGER)
    FROM promo_code_usages pcu
    WHERE pcu.promo_code_id = pc.id
);
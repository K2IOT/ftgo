-- Phase 02A rolling-deployment migration.
-- Existing rows keep a NULL snapshot rather than receiving an invented address.
-- New application code writes all four columns atomically after authoritative
-- Restaurant menu validation. A later operational backfill may tighten these
-- columns to NOT NULL once all legacy rows have a real source address.
ALTER TABLE orders
    ADD COLUMN pickup_address_street VARCHAR(255) NULL,
    ADD COLUMN pickup_address_city VARCHAR(255) NULL,
    ADD COLUMN pickup_address_state VARCHAR(255) NULL,
    ADD COLUMN pickup_address_zip_code VARCHAR(255) NULL,
    ADD CONSTRAINT chk_orders_pickup_address_complete CHECK (
        (pickup_address_street IS NULL
            AND pickup_address_city IS NULL
            AND pickup_address_state IS NULL
            AND pickup_address_zip_code IS NULL)
        OR
        (pickup_address_street IS NOT NULL
            AND pickup_address_city IS NOT NULL
            AND pickup_address_state IS NOT NULL
            AND pickup_address_zip_code IS NOT NULL)
    );

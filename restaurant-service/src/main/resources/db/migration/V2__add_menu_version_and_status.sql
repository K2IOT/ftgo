ALTER TABLE restaurants
    ADD COLUMN menu_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN accepting_orders BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_restaurant_order_availability
    ON restaurants(enabled, accepting_orders);

CREATE INDEX idx_menu_item_restaurant_lookup
    ON menu_items(restaurant_id, id, available);

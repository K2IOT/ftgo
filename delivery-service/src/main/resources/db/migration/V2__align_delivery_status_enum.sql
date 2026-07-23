ALTER TABLE deliveries
    MODIFY COLUMN status ENUM(
        'PENDING',
        'ASSIGNED',
        'PICKED_UP',
        'DELIVERED'
    ) NOT NULL;

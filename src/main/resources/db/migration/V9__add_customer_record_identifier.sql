
ALTER TABLE customer
    ADD COLUMN logical_load_id BIGINT;

ALTER TABLE customer
    ADD COLUMN record_identifier VARCHAR(50);

ALTER TABLE customer
    ADD CONSTRAINT fk_customer_logical_load
        FOREIGN KEY (logical_load_id)
        REFERENCES logical_load(id);

ALTER TABLE customer
    ADD CONSTRAINT ck_customer_record_identifier
        CHECK (
            record_identifier IS NULL
            OR record_identifier ~ '^REG-[0-9]{6,}$'
        );

ALTER TABLE customer
    ADD CONSTRAINT ck_customer_record_load_pair
        CHECK (
            (logical_load_id IS NULL AND record_identifier IS NULL)
            OR
            (logical_load_id IS NOT NULL AND record_identifier IS NOT NULL)
        );

CREATE UNIQUE INDEX uk_customer_logical_load_record
    ON customer(logical_load_id, record_identifier);

CREATE INDEX idx_customer_logical_load
    ON customer(logical_load_id);

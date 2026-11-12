
CREATE TABLE logical_load (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    load_identifier VARCHAR(50) NOT NULL,
    expected_records BIGINT NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'INCOMPLETA',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT uk_logical_load_identifier
        UNIQUE (load_identifier),

    CONSTRAINT ck_logical_load_expected_records
        CHECK (expected_records > 0),

    CONSTRAINT ck_logical_load_status
        CHECK (status IN ('INCOMPLETA', 'CONCLUIDA'))
);

ALTER TABLE file_processing
    ADD COLUMN logical_load_id BIGINT;

ALTER TABLE file_processing
    ADD COLUMN file_type VARCHAR(20);

ALTER TABLE file_processing
    ADD CONSTRAINT fk_file_processing_logical_load
        FOREIGN KEY (logical_load_id)
        REFERENCES logical_load(id);

ALTER TABLE file_processing
    ADD CONSTRAINT ck_file_processing_file_type
        CHECK (
            file_type IS NULL
            OR file_type IN ('ORIGINAL', 'COMPLEMENTAR')
        );

CREATE INDEX idx_file_processing_logical_load
    ON file_processing(logical_load_id);

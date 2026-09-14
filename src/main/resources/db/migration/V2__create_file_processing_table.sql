CREATE TABLE file_processing (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,

    file_hash CHAR(64),

    status VARCHAR(30) NOT NULL,

    read_count BIGINT NOT NULL DEFAULT 0,
    write_count BIGINT NOT NULL DEFAULT 0,
    skip_count BIGINT NOT NULL DEFAULT 0,

    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,

    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_file_processing_hash UNIQUE (file_hash)
);
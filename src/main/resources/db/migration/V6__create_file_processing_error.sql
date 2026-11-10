
CREATE TABLE file_processing_error (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    file_processing_id BIGINT NOT NULL,

    line_number BIGINT,

    error_stage VARCHAR(20) NOT NULL,

    error_type VARCHAR(255) NOT NULL,

    error_message TEXT NOT NULL,

    raw_content TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_processing_error_file_processing
        FOREIGN KEY (file_processing_id)
        REFERENCES file_processing(id),

    CONSTRAINT ck_processing_error_stage
        CHECK (error_stage IN ('READ', 'PROCESS', 'WRITE'))
);

CREATE INDEX idx_processing_error_file_processing
    ON file_processing_error(file_processing_id);

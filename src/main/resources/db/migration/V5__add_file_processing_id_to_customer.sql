ALTER TABLE customer
ADD COLUMN file_processing_id BIGINT;

ALTER TABLE customer
ADD CONSTRAINT fk_customer_file_processing
    FOREIGN KEY (file_processing_id)
    REFERENCES file_processing(id);

CREATE INDEX idx_customer_file_processing_id
    ON customer(file_processing_id);
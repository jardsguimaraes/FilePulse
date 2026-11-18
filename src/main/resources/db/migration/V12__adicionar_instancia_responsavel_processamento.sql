
ALTER TABLE file_processing
ADD COLUMN instance_id UUID;

CREATE INDEX idx_file_processing_instance_id
ON file_processing (instance_id);

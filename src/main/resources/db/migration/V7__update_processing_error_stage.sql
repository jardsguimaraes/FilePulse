ALTER TABLE file_processing_error
DROP CONSTRAINT ck_processing_error_stage;

ALTER TABLE file_processing_error
ADD CONSTRAINT ck_processing_error_stage
CHECK (error_stage IN ('LEITURA', 'PROCESSAMENTO', 'ESCRITA'));
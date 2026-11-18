
CREATE TABLE filepulse_instance (
    instance_id UUID PRIMARY KEY,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stopped_at TIMESTAMP NULL
);

CREATE INDEX idx_filepulse_instance_heartbeat
ON filepulse_instance (last_heartbeat_at);

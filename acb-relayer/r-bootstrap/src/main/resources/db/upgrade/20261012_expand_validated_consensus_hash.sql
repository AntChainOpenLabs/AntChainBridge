-- Widen VCS hash columns for chains whose raw block hashes exceed 32 bytes.
-- Back up the table and stop Relayer writers before applying this migration.
ALTER TABLE validated_consensus_state
    MODIFY COLUMN hash VARCHAR(128) BINARY NOT NULL,
    MODIFY COLUMN parent_hash VARCHAR(128) BINARY NOT NULL;

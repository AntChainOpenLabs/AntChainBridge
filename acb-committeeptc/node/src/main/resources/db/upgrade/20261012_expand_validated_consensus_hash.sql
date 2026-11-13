-- HISTORICAL ONLY: do not execute for dioxide2 digest storage.
-- See tools/dioxide-consensus-hash/README.md; raw_vcs retains original hashes.
-- Widen VCS hash columns for chains whose raw block hashes exceed 32 bytes.
-- Back up the table and stop Committee node writers before applying this migration.
ALTER TABLE validated_consensus_states
    MODIFY COLUMN hash VARCHAR(128) DEFAULT NULL,
    MODIFY COLUMN parent_hash VARCHAR(128) DEFAULT NULL;

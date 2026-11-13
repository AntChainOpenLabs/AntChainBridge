-- One-time, data-preserving migration for a v1 Dioxide coordinator database.
-- Stop every Dioxide writer before running this file. Back up the three
-- bridge_tx_* tables first. Do not run this script a second time.

CREATE TABLE IF NOT EXISTS bridge_tx_schema_version (
  component VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  schema_version INT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (component)
) ENGINE=InnoDB;

ALTER TABLE bridge_tx_account
  ADD COLUMN allocation_state VARCHAR(24) NOT NULL DEFAULT 'READY' AFTER observed_isn,
  ADD COLUMN active_operation_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER allocation_state,
  ADD COLUMN active_attempt_no INT NULL AFTER active_operation_id,
  ADD COLUMN active_isn BIGINT NULL AFTER active_attempt_no,
  ADD COLUMN last_error VARCHAR(512) NULL AFTER active_isn,
  ADD KEY ix_bridge_tx_account_active (network_id, active_operation_id);

ALTER TABLE bridge_tx_submission
  MODIFY COLUMN state VARCHAR(24) NOT NULL,
  MODIFY COLUMN last_error VARCHAR(512) NULL,
  ADD COLUMN active_attempt_no INT NOT NULL DEFAULT 1 AFTER state;

-- Early v1 installations used a unique ISN index. Later hotfixes already
-- replaced it with ix_bridge_tx_isn. Normalize either layout without assuming
-- which one is present.
SET @drop_old_unique = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='bridge_tx_submission'
           AND index_name='uq_bridge_tx_isn'),
  'ALTER TABLE bridge_tx_submission DROP INDEX uq_bridge_tx_isn',
  'SELECT 1'
);
PREPARE bridge_tx_migration_stmt FROM @drop_old_unique;
EXECUTE bridge_tx_migration_stmt;
DEALLOCATE PREPARE bridge_tx_migration_stmt;

SET @add_v2_isn_index = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='bridge_tx_submission'
           AND index_name='ix_bridge_tx_isn'),
  'SELECT 1',
  'ALTER TABLE bridge_tx_submission ADD KEY ix_bridge_tx_isn (network_id, account, isn)'
);
PREPARE bridge_tx_migration_stmt FROM @add_v2_isn_index;
EXECUTE bridge_tx_migration_stmt;
DEALLOCATE PREPARE bridge_tx_migration_stmt;

CREATE TABLE bridge_tx_attempt (
  network_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  attempt_no INT NOT NULL,
  account VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  isn BIGINT NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  signed_tx MEDIUMBLOB NOT NULL,
  tx_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  state VARCHAR(24) NOT NULL,
  node_error_code INT NULL,
  last_error VARCHAR(512) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (network_id, operation_id, attempt_no),
  KEY ix_bridge_tx_attempt_isn (network_id, account, isn),
  KEY ix_bridge_tx_attempt_hash (network_id, tx_hash),
  KEY ix_bridge_tx_attempt_state (network_id, state)
) ENGINE=InnoDB;

INSERT INTO bridge_tx_attempt(
  network_id, operation_id, attempt_no, account, isn, payload_hash,
  signed_tx, tx_hash, state, last_error, created_at, updated_at
)
SELECT network_id, operation_id, 1, account, isn, payload_hash,
       signed_tx, tx_hash, state, last_error, created_at, updated_at
FROM bridge_tx_submission;

-- A v1 row did not describe an in-flight reservation reliably. Leave accounts
-- READY; the first v2 request reads dx.isn and replaces the old speculative
-- next_isn with the node-authoritative value. Historical rows remain auditable.
UPDATE bridge_tx_account
SET allocation_state='READY', active_operation_id=NULL,
    active_attempt_no=NULL, active_isn=NULL, last_error=NULL;

INSERT INTO bridge_tx_schema_version(component, schema_version)
VALUES ('dioxide-coordinator', 2)
ON DUPLICATE KEY UPDATE schema_version=VALUES(schema_version);

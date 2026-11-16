-- Dioxide transaction coordinator schema v2.
-- Install explicitly in the shared operational database. Never reset it after a
-- process restart or a chain rollback.
CREATE TABLE IF NOT EXISTS bridge_tx_schema_version (
  component VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  schema_version INT NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (component)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bridge_tx_account (
  network_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  checkpoint_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  -- The last node-observed next ISN. Signing and tx.send do not advance it.
  next_isn BIGINT NOT NULL,
  observed_isn BIGINT NOT NULL DEFAULT 0,
  allocation_state VARCHAR(24) NOT NULL DEFAULT 'READY',
  active_operation_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
  active_attempt_no INT NULL,
  active_isn BIGINT NULL,
  last_error VARCHAR(512) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (network_id, account),
  KEY ix_bridge_tx_account_active (network_id, active_operation_id)
) ENGINE=InnoDB;

-- One row represents one stable business operation. signed_tx/tx_hash mirror
-- the latest attempt for compatible inspection; bridge_tx_attempt is the audit log.
CREATE TABLE IF NOT EXISTS bridge_tx_submission (
  network_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  isn BIGINT NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  signed_tx MEDIUMBLOB NOT NULL,
  tx_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  state VARCHAR(24) NOT NULL,
  active_attempt_no INT NOT NULL DEFAULT 1,
  last_error VARCHAR(512) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (network_id, operation_id),
  KEY ix_bridge_tx_isn (network_id, account, isn),
  KEY ix_bridge_tx_hash (network_id, tx_hash),
  KEY ix_bridge_tx_pending (network_id, state)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bridge_tx_attempt (
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

-- Publish the version only after every v2 object exists.
INSERT INTO bridge_tx_schema_version(component, schema_version)
VALUES ('dioxide-coordinator', 2)
ON DUPLICATE KEY UPDATE schema_version=VALUES(schema_version);

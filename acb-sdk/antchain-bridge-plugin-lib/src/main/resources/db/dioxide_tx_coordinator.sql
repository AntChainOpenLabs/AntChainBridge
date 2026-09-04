-- Install explicitly in the shared operational database. Do not reset after rollback.
CREATE TABLE IF NOT EXISTS bridge_tx_account (
  network_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  checkpoint_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  next_isn BIGINT NOT NULL,
  observed_isn BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (network_id, account)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bridge_tx_submission (
  network_id VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  account VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  isn BIGINT NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  signed_tx MEDIUMBLOB NOT NULL,
  tx_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  state VARCHAR(16) NOT NULL,
  last_error VARCHAR(128) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (network_id, operation_id),
  UNIQUE KEY uq_bridge_tx_isn (network_id, account, isn),
  KEY ix_bridge_tx_hash (network_id, tx_hash),
  KEY ix_bridge_tx_pending (network_id, state)
) ENGINE=InnoDB;

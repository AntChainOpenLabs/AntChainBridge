-- Optional explicit upgrade for Dioxide native hashes in the UCP pool.
-- Back up and inspect existing schema/collations before execution. No startup hook runs this script.
-- This does NOT widen validated_consensus_state or committee/monitor tables.
ALTER TABLE ucp_pool
    MODIFY COLUMN blockhash VARCHAR(128) DEFAULT NULL,
    MODIFY COLUMN txhash VARCHAR(128) DEFAULT NULL;

# Durable Dioxide transaction submission

The node's default tx.compose ISN is an observation, not a reservation. Independent unordered
Relayer sessions, plugin instances and Python processes sharing an account must use this same
MySQL coordinator. Thread-local counters and locks are insufficient.

## Installation

Install the SQL in antchain-bridge-plugin-lib/src/main/resources/db/dioxide_tx_coordinator.sql
using a schema administrator. Grant the runtime user only SELECT/INSERT/UPDATE on the two
bridge_tx_ tables. No Relayer core table is changed.

Deploy the SPI, Relayer, Plugin Server and Dioxide plugin together. A missing optional gRPC
submissionId remains backward compatible for other plugins; Dioxide intentionally refuses
unidentified relay submissions instead of falling back to unsafe signing.

Create /etc/antchain-bridge/dioxide-tx.properties and a separate passwordFile, both mode 0600:

    networkId=unique-stable-network-instance
    checkpointHeight=12345
    checkpointHash=verified-finalized-checkpoint-hash
    jdbcUrl=jdbc:mysql://database:3306/bridge?connectTimeout=5000&socketTimeout=30000
    user=bridge_tx
    passwordFile=/etc/antchain-bridge/dioxide-tx.password

The plugin config txCoordinatorConfigFile, JVM property dioxide.tx.config, or environment
DIOXIDE_TX_COORDINATOR_CONFIG may select another protected file. Use the identical network
identifier and checkpoint on every writer, regardless of domain/plugin/RPC endpoint.

Python: pip install this directory, wrap the SDK DioxClient in CoordinatedDioxClient and pass a
stable operation_id for each resumable operation. The wrapper does not monkey-patch unrelated
SDK instances. Stop using direct SDK writes with the same signing account.

Funding and dapp creation also use the coordinator and explicit stage operation IDs. Failed
dapp waits raise an error rather than reporting synchronous success. This release does not
invoke those administrative operations.

## Semantics and recovery

- The allocation key is network + effective sender/delegatee; Ed25519 suffixes normalize.
- A row lock and unique constraint protect ISN allocation. Signing is committed before broadcast.
- Node ISN lag cannot lower the allocated counter; regression below an observed node ISN,
  checkpoint changes, database outages and uint32 exhaustion fail closed.
- Relayer uses UCP + target domain + operation type, not message body or SDP messageId.
- Retrying the same operation returns its hash or broadcasts the same persisted signed bytes.
  An uncertain response never frees the ISN or generates a replacement business transaction.
- Confirmation does not hold the account allocation lock. Legacy SDP query mailboxes have a
  separate cross-process lock spanning query, full finality and read-back.
- Signed payloads are sensitive replay material: do not log, publish or export them in PRs.

SIGNED/UNKNOWN records require reconciliation. BROADCAST indicates a known hash, not execution
success. To recover, use the original operation and arguments. Never truncate tables, decrement
the counter or change the namespace to bypass unresolved records. Network restores require
explicit operator review. A transaction past its lifetime is not automatically replaced.

Back up packages/configuration and the coordinator tables before rollout. Pause submissions,
drain in-flight work, install storage first, then deploy compatible callers. Rollback preserves
all coordination records; do not reopen known-unsafe concurrent old writers.

## Tests

Use an isolated MySQL database. Set Maven isn.test.jdbc for JdbcTransactionCoordinatorTest.
Set ISN_TEST_MYSQL=1 for Python tests; JAVA_PROBE_JAVA and JAVA_PROBE_CLASSPATH enable the
mixed Java/Python process test and recovery from a Java process exiting after signing commit.
The same suite exercises exit before signing commit (rollback), plus 32 shared-mailbox queries
from two Java and two Python processes. Fixture tables are created only in the isolated test DB.
Run DioxideIsnFinalityTest explicitly with -Dexec.skip=true; do not run live BBC tests against
production credentials. Build the Relayer with Java 8 and Dioxide with Java 21.

This change contains no regulatory contract code, contract deployment or historical replay.

## Indexed relay groups

A relay group can contain unrelated business transactions. Preserve the full groupHash:index
reference and inspect only that array member. Cache the original group without mutating it:
Fastjson JSONObject(Map) aliases the input map rather than copying it. The full RPC JSON
receipt test checks two referenced members while ignoring an unrelated failing member.

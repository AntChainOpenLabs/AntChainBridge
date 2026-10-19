# Receipt-local AM event indices

JSON-RPC logIndex counts logs across the entire block. A receipt proof authenticates
one transaction's receipt, whose logs array starts at zero. Using the former as
an index into the latter rejects otherwise valid messages from later transactions.

Both collector paths now record a receipt-local logIndex and an explicit
receiptLogIndex marker, while preserving the original RPC log inside
sendAuthMessageLog. Filter collection emits one message per selected event, not
all receipt events for each filter result.

The verifier first authenticates the original receipt proof against the trusted
consensus receipts root and checks the receipt transaction index.

- Explicit receiptLogIndex: require a consistent, in-range position and a full
  event match. An invalid explicit position never falls back.
- Legacy ledger: require a unique address/topics/data match within that proven
  receipt, and consistent local/global index metadata. Ambiguous identical legacy
  events fail closed rather than choosing the first event.
- The trusted AM address, exact event topic shape, decoded message and full
  event content remain checked. No node/RPC lookup substitutes for proof.

This does not rewrite stored UCPs, re-submit source transactions, change contracts,
or change the existing consensus/light-client validation behavior.

## Offline regression

After preparing the generated ABI classes as described in the plugin README:

```sh
mvn -f acb-sdk/pluginset/ethereum2/offchain-plugin/pom.xml \
  -Dtest=EthereumReceiptLogIndexTest,EthereumCollectorLogIndexTest,EthereumReceiptProofCompatibilityTest,EthereumHcdvsTest test
```

23 tests cover both real collector methods with mocked RPC, multiple transactions
per block, multiple AM events per transaction, legacy proof compatibility,
explicit-index failures, ambiguity, malformed metadata and root/message tampering,
plus the existing consensus fixtures.

Deploy the collector and each committee member's HCDVS plugin together. Keep
backups per component; do not rewrite pending proof records during rollback.

## Deployment regression (2026-09-04)

The corresponding downstream collector/HCDVS fix was deployed together on the
Plugin Server and all four PTC nodes. All 96 original ordinary burst messages
completed, including 93 previously blocked messages. Every original UCP's stored
raw_message fingerprint remained identical; there was no source re-submission or
forced proof state. Four newly collected messages (two ordinary, two regulated)
also completed. Later transactions in those blocks exercised global/local index
pairs 2/0 and 4/1. Target business receipts contained the expected payload once.

These are downstream deployment observations, not a claim that this upstream
branch's artifact was installed. This PR preserves upstream consensus/light-client
changes and adds no regulatory-specific implementation.

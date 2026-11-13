# Dioxide2 consensus-state database hash keys

BTA products `dioxide2` and `dioxide` use lowercase `SHA-256(original hash bytes)` in
`validated_consensus_states.hash` and `parent_hash`. These are database lookup
keys, not replacement consensus hashes. Every other product retains its original
hexadecimal columns. `raw_vcs` is never rewritten; signed encodings, original
hashes, parent verification, and responses remain unchanged.

Both committee-node and monitor-node require a consistent, nonempty BTA product
for a domain. Conflicting registrations or unavailable product lookup fail closed.
Dioxide hash queries accept the original hexadecimal hash, search both digest and
legacy keys, and check the decoded original hash before returning a single row.
Duplicate or inconsistent candidates fail rather than silently selecting a row.
Height/latest queries continue decoding `raw_vcs`.

Native consensus reading for plain `dioxide` does not establish HCDVS or PTC
capability. Both Dioxide products require an actual HCDVS for committee
endorsement; missing verifiers fail explicitly. The historical maintenance CLI
below still selects `dioxide2` records only. Do not assume it converts existing
plain `dioxide` records, or run it automatically during deployment.

## Offline and isolated MySQL tests

Use the existing Java 21 toolchain, with no dependency upgrades or `clean`:

```sh
mvn -o -f acb-committeeptc/pom.xml -pl node,monitor-node \
  -Dtest=DioxideHashRepositoryTest test
```

These new tests use JUnit Jupiter and real MyBatis mappers against H2. Existing
JUnit 4 tests must also be invoked explicitly with JUnitCore when the installed
Surefire provider does not include the vintage engine; zero executed tests are
not acceptance. Relevant legacy classes are `EndorseServiceRepositoryTest`,
`EndorserServiceTest`, and `HCDVSServiceTest`. The latter requires a demo testchain
fixture; never substitute a business-chain SDK integration test.

To test the actual MySQL driver/mappers, explicitly create separate local schemas
named `diox_hash_fixture_*`, then supply a private properties file:

```properties
url=jdbc:mysql://127.0.0.1:3306/diox_hash_fixture_node?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
user=YOUR_LOCAL_TEST_USER
password=YOUR_LOCAL_TEST_PASSWORD
```

Use mode `0600`, do not put credentials in the URL or command line, and do not
commit this file. Run each module independently with its own fixture schema:

```sh
mvn -o -f acb-committeeptc/node/pom.xml \
  -Dtest=DioxideHashRepositoryTest,DioxideHashMigrationTest \
  -Ddiox.hash.test.config=/private/node-fixture.properties test
mvn -o -f acb-committeeptc/monitor-node/pom.xml \
  -Dtest=DioxideHashRepositoryTest \
  -Ddiox.hash.test.config=/private/monitor-fixture.properties test
```

The tests reject fixture URLs outside the local `diox_hash_fixture_*` schemas.
Migration tests create additional isolated schemas and retain them for inspection.
No fixtures are inserted into `committee_node` or `monitor_node`.

## Explicit maintenance CLI

The CLI is `com.alipay.antchain.bridge.ptc.committee.node.dal.tools.DioxideHashMigration`
in the committee-node artifact. It uses JDBC and the original consensus decoder;
it does not initialize Spring, load a chain SDK, sign, or send transactions.
It is not called automatically during node startup. It can operate on either
committee-node or monitor-node schema because their storage formats are the same.

Prepare an ordinary JDBC properties file with `url`, `user`, and `password`, mode
`0600`. Use the exploded committee-node classes plus its Maven runtime classpath:

```sh
umask 077
mvn -o -f acb-committeeptc/node/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=/private/node-classpath.txt
# JAVA_CLASSPATH contains frozen target/classes plus the dependencies above.
java -cp "$JAVA_CLASSPATH" \
  com.alipay.antchain.bridge.ptc.committee.node.dal.tools.DioxideHashMigration \
  audit /private/jdbc.properties committee_node /private/new-audit.journal 250
```

The first argument is `audit`, `apply`, or `verify`. The remaining arguments are
private properties path, database name, **new** journal path, and optional batch
size (1–5000, default 250). Reused journals are refused, never overwritten.
Freeze the maintenance runtime before execution; do not rebuild the classes it
is currently using. The CLI journal is append-only and forced to disk.

- `audit`: scan the full table; validate Dioxide domain, height, CS version,
  original hashes, and allowable original/digest column values; perform no writes.
- `apply`: first audit the entire table; then take BTA shared locks and row locks
  in short transactions, recheck product mapping and raw evidence, and replace
  only the two lookup columns. Preserve timestamps. A batch failure rolls back
  that batch; earlier commits remain recorded. Correct digests are skipped.
- `verify`: require every Dioxide lookup column to equal the digest computed
  directly from `raw_vcs`; non-Dioxide columns must fit 64 characters. No writes.

If submission/commit acknowledgement or journal output is uncertain, stop and
retain every record. Audit with a **new** journal, then explicitly rerun `apply`
only after investigation. It always computes from `raw_vcs`, never from an
already-digested column. A conflicting value is rejected, not overwritten.

## Local conversion and VARCHAR(64)

1. Keep business services and anchors stopped. Record schema/indexes and exact
   per-domain counts. Make private complete dumps of both `bta` and
   `validated_consensus_states`; restore them to new isolated databases and
   compare every row, including `raw_vcs`, timestamps, and original columns.
2. Exercise audit/apply/verify and the DDL in those restored copies first.
3. For each actual local schema, audit, apply, and verify. Compare unchanged
   fields against the restored backup. All non-Dioxide records and all original
   consensus encodings must remain identical.
4. Only after verifying every row has at most 64 characters, explicitly narrow
   `hash` and `parent_hash` to `VARCHAR(64)`, retaining each column's character
   set, collation, nullability and default, as well as every index. Run in strict
   SQL mode. DDL is **not** part of the data transaction and is not auto-rollback.
5. Verify again and rerun apply with a new journal to prove zero further changes.

The historical `20261012_expand_validated_consensus_hash.sql` files are retained
for provenance; **do not run them for this scheme**. No automatic shrinking or
historical-row conversion occurs on application startup.

The local acceptance uses existing `utf8mb4` column collations. It does not change
character sets to match remote `utf8mb3`. A 64-character lowercase hex digest fits
both; remote deployment and any remote data conversion are separate work.

## Rollout boundary

This change does not modify Relayer, plugin JARs, contracts, database indexes,
network checkpoints, transaction hashes, or ISN coordination. The committee PTC
currently advertises state storage; Relayer skips its separate local VCS writes
on that path. A future stateless PTC path needs its own assessment.

Build the executable committee/monitor packages with Java 21 and retain hashes.
If local tables have already been narrowed, install the newly-built node packages
before starting Dioxide listeners: old runtime packages do not implement digest
storage. The local acceptance intentionally leaves runtime JARs and services
untouched. Restore wider columns before considering an old-binary rollback;
never replace `raw_vcs` or replay business messages to compensate.

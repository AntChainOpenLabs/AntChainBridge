# Measurement tools and verification boundaries

This stage depends on the three preceding stages. Latency recording is disabled
by default; platform reporting remains disabled by default. Enabling either is
an explicit operator action. No migration or service is started by these tools.

The Java 21 measurement reactor and Java 8 Relayer metrics were compiled and
tested against source-built staged dependencies in a separate Maven repository.
Python aggregation was checked with 19 synthetic-data tests. Source/destination
Monitor regression harnesses passed 54/32 assertions using an in-process EVM.
Live chains, production databases, finality, real supervision and deployed
plugin compatibility were not exercised in this integration task.

Tests reject missing timestamps, conflicting UCP mappings, corrupt payloads,
duplicate reception and incomplete observation. Submission attempts remain
exclusive and persistent. Query/check modes do not broadcast transactions.
Inspect each adapter's supported workload before running it; offline acceptance
does not establish any particular throughput or latency result.

The historical `tools/paper-experiment` path and Maven artifact names are kept
for command compatibility; the contribution supplies general measurement,
reception verification and Gas regression tools, without result datasets.

# Dioxide verification boundaries

Plain Dioxide retains the upstream V0 trusted-relayer receiving AM. Reading native
consensus data is not a claim of cryptographic proof verification or PTC support.
Both Dioxide products must have a real HCDVS before a committee can endorse their
consensus states or messages. Missing HCDVS is an error; no product-based bypass
or synthetic successful validation is included.

Coordinate every writing process through the same explicit network/checkpoint
and database configuration. Never reset counters after an unknown submission.
Do not run database migrations implicitly during startup. Use isolated fixtures
for tests and preserve existing deployments and original consensus bytes.

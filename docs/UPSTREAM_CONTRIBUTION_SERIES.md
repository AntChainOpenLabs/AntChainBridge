# Upstream contribution series

Target: `AntChainOpenLabs/AntChainBridge:main-research-topic3`.
Initial target snapshot: `37c4310e58a96830ad43a32711c405c06761449a`.
Existing development branches are retained; the series is reorganized against
the upstream tree, not a force-push of that development history.

1. **Fix existing plugin verification and transaction handling** — existing
   FISCO/Ethereum/Mychain verification and context fixes; Dioxide coordinated
   writes, complete receipt handling, and compatible submission-ID transport.
2. **Add optional monitoring infrastructure and interfaces** — optional APIs,
   nodes, reporting and simulation; unsupported legacy calls fail explicitly.
3. **Add monitored multi-chain plugins and optimized contracts** — matched
   contract/client versions, initialization readback and optimized proof handling.
4. **Add latency and gas measurement tools** — minimal applications, durable
   attempts, exact reception checks, metrics and strict offline aggregation.

Only the first draft PR is published initially. Each subsequent PR is rebased
onto the accepted upstream predecessor before publication; cumulative drafts
are not opened against an unchanged upstream base.

No product-based HCDVS bypass is included. Dioxide requires a real verifier for
committee endorsement and otherwise fails explicitly. The ordinary V0 receiving
mode remains available. Runtime credentials, deployed network identities,
operational logs, result datasets and unintegrated prototypes are excluded.

Database scripts are explicit maintenance tools. Contract upgrades and node
rollouts are separate operations; merging this series does not deploy anything.

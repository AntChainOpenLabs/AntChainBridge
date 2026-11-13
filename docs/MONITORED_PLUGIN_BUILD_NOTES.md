# Monitored plugin build and compatibility notes

This stage depends on the optional monitoring interfaces and node infrastructure.
Build Ethereum wrappers from the pinned tool in `tools/ethereum-contracts`.
Generate FISCO wrappers with the matching console SDK v3 generator and both
ECDSA and genuine SM compiler outputs; the generator script does not reuse an
existing checkout's ignored wrappers. Mychain creation/runtime resources are
regenerated with MySolidity 0.8.14-ci, optimizer enabled, runs 200.

Monitor implementation versions and verifier interfaces must be deployed as a
matched set with their clients. Existing deployments must not mix these new
proof-consumer interfaces with old verifier bytecode. This commit performs no
runtime deployment, contract upgrade, schema migration or trust configuration.

Validation used a separate dependency repository with project SNAPSHOTs rebuilt
from source. It retained external/vendor dependencies, including the Mychain
SDK; a completely empty-cache build is not claimed. Ethereum and Mychain
resources were compiled afresh. The FISCO offline test wrappers contain newly
compiled ECDSA bytecode and an explicitly empty SM test fixture, not a disguised
ECDSA-as-SM release. These ignored test wrappers are not committed. A genuine
SM compiler/release build remains unverified. Dioxide's live-chain protocol
binding fixture is skipped; all business-chain integration tests are excluded.

Missing HCDVS is a hard failure: Dioxide monitoring and mock approval do not
provide cryptographic consensus verification. No product-based verification
bypass is included. Real-chain/end-to-end validation of this staged combination
is not claimed.

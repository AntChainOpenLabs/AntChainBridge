# Offline Monitor optimization checks

These tools check the optimized Ethereum/EVM and Mychain Monitor source paths.
They do not connect to a business chain, register logical chains, or load a
Relayer configuration. Ganache runs in process for the JavaScript checks.

With compatible, already installed `solc`, `ganache` and `ethers` modules:

```sh
node tools/monitor-gas/source-test.cjs /path/to/compiler/node_modules \
  /path/to/runtime/node_modules /path/to/new/source-results
node tools/monitor-gas/test.cjs /path/to/compiler/node_modules \
  /path/to/runtime/node_modules /path/to/new/destination-results
```

Each output directory must be new. Source tests cover byte-exact calldata
encoding, dirty padding, blacklists, author preservation, one AM event, and
removal of the V1 unordered auxiliary success event. Destination tests cover
authenticated proof writers/consumers, message binding, one-time consumption,
invalid signatures and rejection/rollback behavior.

`compile.cjs` uses cached EVM and MySolidity compilers. Its historical Mychain
default has optimization disabled; pass `--optimize-mychain` for an optimized
comparison. Always record compiler versions and settings. Production Ethereum
builds use the separately pinned settings in `tools/ethereum-contracts/README.md`.

Generated wrappers, deployed contracts and Java initialization version checks
must be rebuilt together when adopting the changed verifier interface. These
tests do not establish compatibility of an existing deployment or a successful
real-chain transfer.

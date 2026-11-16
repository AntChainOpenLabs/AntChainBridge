# Optimized Ethereum contract build

Both Ethereum products use standard Solidity `0.8.29+commit.ab55807c`, optimizer
`enabled=true,runs=200`, `viaIR=true`, `evmVersion=london`, and IPFS metadata.
The IR pipeline keeps the optimized Xun SDP and PtcHub below EIP-170's 24,576-byte
runtime limit. These compiler conditions differ from the FISCO/MySolidity builds
and must be recorded when comparing Gas across chains.

Build each product into a new directory (the command refuses to overwrite):

```bash
python3 tools/ethereum-contracts/build.py --compiler /path/to/solc \
  --product ethereum2 --output /path/to/new/ethereum2-build
python3 tools/ethereum-contracts/build.py --compiler /path/to/solc \
  --product ethereum3 --output /path/to/new/ethereum3-build
```

Outputs include complete standard-JSON inputs/results, source/compiler SHA-256,
ABI, creation bytecode, runtime bytecode, storage layout and size checks. Regenerate
selected Web3j wrappers with the existing 4.12.1 code generator, then build the
respective offchain plugin with Java 21. Do not invoke a compiler auto-download
step that silently replaces these settings. PaperApp keeps the existing standard
0.8.13 optimized bytecode shared with the FISCO application baseline.

Ethereum3 consumes the same optimized EVM Monitor, MonitorVerifier and committee
proof-context implementation used in the recent FISCO experiments. V1 unordered
sending omits only the auxiliary approval event and avoids temporary encoding
objects. The original AM event, blacklists, author and wire encoding remain.
Destination verification keeps a message-bound, one-use result rather than
persisting the full signed payload. No AM fast path or storage packing is added.

The Java initialization path authorizes the final Monitor **proxy** as the
MonitorVerifier consumer. It checks native receipt success and getter readback,
skips an already correct binding, and rejects a conflicting nonzero binding.
The final PtcHub binding uses the same checks. Merely starting a BBC does not
perform these binding transactions; they belong to explicit initialization.

Local preparation does not prove new business communication: crosschain message
sends and later paper measurements require a separate acceptance run.

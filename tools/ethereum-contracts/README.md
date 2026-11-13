# Reproducible Ethereum Base system-contract build

Generate the ignored Web3j wrappers before compiling the Ethereum plugin. Use
standard Solidity 0.8.29+commit.ab55807c (official Linux amd64 SHA-256 checked
by the script), optimizer enabled with 200 runs, viaIR and London EVM:

```sh
python3 tools/ethereum-contracts/build.py --compiler /path/to/solc \
  --product ethereum2 --output /path/to/new/build
```

The output includes complete compiler input/results, ABI, creation and runtime
bytecode, source/compiler hashes and EIP-170 size checks. The build resolves the
root CommitteePtcVerifier contract explicitly instead of the legacy library of
the same name. Generate each listed wrapper with Web3j codegen 4.12.1 into
`com.alipay.antchain.bridge.plugins.ethereum2.abi`, then run the plugin tests.
Record compiler settings when adopting new deployments. Building a plugin does
not upgrade existing on-chain contracts.

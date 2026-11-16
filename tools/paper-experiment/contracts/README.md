# Minimal paper applications

`evm/PaperApp.sol` is shared by Ethereum and FISCO. `mychain/PaperApp.sol`
uses the chain's 32-byte Identity ABI. Both provide BasePaperApp (SDP router)
and XunPaperApp (Monitor router). Constructor binding cannot be changed.

Applications expose only `router`, `sendUnorderedMessage(domain,receiver,message)`
and authenticated `recvUnorderedMessage(domain,author,message)`. Receiving emits
`recvCrosschainMsg(domain,author,message,false)`; no application message, count,
ACK or send-event storage is maintained. Protocol events remain unchanged.

Compile with the existing cached toolchain (EVM solc 0.8.13 and
@antchain/mysolidity 1.3.0, MySolidity 0.8.14):

```
node compile.cjs /absolute/path/to/node_modules /absolute/new/output-directory
```

Both schemes use optimization with 200 runs. Output includes full compiler input,
output, ABI, bytecode, storage layouts and hashes. Existing output is rejected.
Use `evm-app` receive decoding on Ethereum/FISCO and `mychain-minimal` on Mychain.
Existing public demos and their deployments are retained. Deploy these applications
through standalone tools; service/plugin JAR replacement is unnecessary.

Serial measurements retain all planned samples including the first sample.
Receiver events and complete receipt scans establish exact payload reception;
getter-based acceptance is not available for these minimal applications.

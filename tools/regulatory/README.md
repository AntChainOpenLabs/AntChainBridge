# Regulatory live-check tools

These small Java entry points are operational acceptance tools for the
SDP V1/V2/V3 monitor compatibility work. They are intentionally kept outside
the production modules: they connect to configured test chains, deploy or call
test contracts, and therefore must only be run by an operator in an isolated
test environment.

No endpoint, account, certificate, or private key is embedded in these files.
Chain configuration is supplied at runtime through a root-only JSON file.
All FISCO tools in this directory target the regulated FISCO BCOS 3.x product
`fiscobcos3`; the Base product `fiscobcos` intentionally has no Monitor API.

## Tools

| Class | Purpose |
| --- | --- |
| `EthereumMonitorV123LiveCheck` | Send normal or monitored SDP V1/V2/V3 messages from an Ethereum demo app; an optional selector limits a run to one version |
| `EthereumReceiverLiveCheck` | Read the exact byte payload stored by an Ethereum demo receiver |
| `FiscoMonitorV123LiveCheck` | Send normal or monitored SDP V1/V2/V3 messages from a FISCO demo app |
| `FiscoReceiverLiveCheck` | Read the exact byte payload held by the FISCO demo receiver contract |
| `MychainMonitorV123LiveCheck` | Probe Monitor/SDP versions and send Mychain SDP V1/V2/V3 messages |
| `MychainReceiverLiveCheck` | Read the exact byte payload held by the Mychain receiver contract |
| `FiscoMessageScanInspect` | Inspect FISCO cross-chain message scan results |
| `FiscoReceiptInspect` | Inspect FISCO transaction receipts and emitted events |
| `FiscoSystemBindingInspect` | Verify FISCO AM/SDP/Monitor/PTC bindings |
| `FiscoMonitorVerifierRebind` | Rebind a replacement Monitor to a populated legacy MonitorVerifier after an audited upgrade |
| `FiscoSystemContractsDeploy` | Deploy the FISCO system-contract test set |
| `MychainPtcRootReconcileLiveCheck` | Probe and reconcile a legacy Mychain PTC Hub BCDNS root |
| `CrossChainPayloadInspect` | Decode captured AuthMessage/SDP payloads for byte-level comparison |

## Acceptance rules

1. Add the test sender-to-receiver pair to the Relayer ACL before sending.
2. Run normal and monitored modes sequentially because monitor control is
   contract-wide mutable state.
3. Record all three source transaction hashes.
4. Wait for the source chain's configured confirmation depth; a successful
   source receipt alone is not a cross-chain success.
5. Verify the Relayer archive and target receipt for each V1/V2/V3 message.
6. For monitored messages, verify that the reporting result reaches target
   execution.
7. Read the target business receiver and compare its bytes with the original
   payload. A successful receipt with truncated, zero-padded, or still-wrapped
   bytes is a failed acceptance test.

Do not commit the runtime configuration JSON, compiled classes, logs, account
files, or contract private keys.

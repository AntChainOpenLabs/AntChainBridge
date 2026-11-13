# Existing Mychain sender and native receipt tools

These tools call an existing EVM application by its **32-byte Identity**, and distinguish a query
error from an executed transaction. They do not deploy contracts or change Monitor/ACL settings.

## Build

Use Java 8 and a reviewed `mychain020-bbc` plugin JAR that provides
`Mychain020Client.callContract(Identity, Parameters, boolean)`. An old JAR providing only the
String overload will fail compilation; do not substitute the Identity hex string into that overload.

```sh
export JAVA_HOME=/path/to/jdk8
export MYCHAIN_PLUGIN_JAR=/path/to/reviewed/mychain020-bbc-plugin.jar
./build.sh
```

The build runs nine receipt regressions against the actual SDK JSON decoder: 404 with a default
zero receipt, 408 rejection, 413/414 pending, missing block height, confirmed success and revert,
the uint64-max sentinel, and a missing response. Successful execution of these tools does not
verify a cross-chain transfer; inspect the UCP, regulation, target receipt and application message.

## Inspect and send

```sh
java -cp "build:$MYCHAIN_PLUGIN_JAR" MychainExistingSender \
  /root-only/mychain.json SOURCE_IDENTITY ethereum.example TARGET_IDENTITY UNIQUE_MESSAGE CHECK_ONLY
java -cp "build:$MYCHAIN_PLUGIN_JAR" MychainExistingSender \
  /root-only/mychain.json SOURCE_IDENTITY ethereum.example TARGET_IDENTITY UNIQUE_MESSAGE \
  SEND_ONCE /root-only/new-attempt.txt
java -cp "build:$MYCHAIN_PLUGIN_JAR" MychainReceiptQuery /root-only/mychain.json TX_HASH
```

`SOURCE_IDENTITY`, `TARGET_IDENTITY` and `TX_HASH` accept exactly 64 hex digits, optionally prefixed
with `0x`. The target Identity is a protocol bytes32 value (Ethereum addresses require their normal
12-byte left padding). The application must implement `sendUnordered(identity,string,bytes)`.

The attempt file is created atomically, with mode 0600, **before** submission. An existing file
prevents submission even if the previous process died or the result was unknown. After receiving
any response, the hash and status are appended. Do not remove the file to retry a transaction whose
outcome is unknown; reconcile the original hash. A different file is not permission to duplicate
an existing business request. Only a genuinely new authorized message uses a new attempt file.

The query tool prints `NATIVE_RECEIPT_FOUND=True` only when the outer SDK response succeeded,
the block number is valid and a receipt exists. An executed revert has `FOUND=True` with
`STATUS=EXECUTED_FAILED`; receipt existence is not execution success. No raw transactions,
credentials or complete UCPs are printed.


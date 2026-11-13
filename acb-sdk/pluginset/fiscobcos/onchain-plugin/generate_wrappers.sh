#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 /path/to/fisco-console"
  exit 2
fi

console_dir=$1
plugin_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
sdk_dir=$(cd "$plugin_dir/../.." && pwd)
source_dir="$sdk_dir/ethereum2/onchain-plugin/solidity/sys"
output_dir="$plugin_dir/offchain-plugin/src/main/java"
generated_dir=$(mktemp -d)

"$console_dir/contract2java.sh" solidity \
  --package com.alipay.antchain.bridge.plugins.fiscobcos.abi \
  --sol "$source_dir" \
  --output "$generated_dir" \
  --no-analysis

package_dir=com/alipay/antchain/bridge/plugins/fiscobcos/abi
for contract in AuthMsg SDPMsg PtcHub CommitteePtcVerifier AppContract; do
  cp "$generated_dir/$package_dir/$contract.java" "$output_dir/$package_dir/$contract.java"
done

echo "generated FISCO Base wrappers from $source_dir"
echo "temporary generated sources retained at $generated_dir"

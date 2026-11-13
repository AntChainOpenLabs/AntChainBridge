#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
CONTRACT_PATH="${PLUGIN_DIR}/offchain-plugin/src/main/resources/contract/v1/solidity"
TMP_DIR="$(mktemp -d /tmp/antchainbridge-mysolc.XXXXXX)"

if [[ -n "${MY_SOLC:-}" ]]; then
  MY_SOLC_CMD=("${MY_SOLC}")
elif command -v mysolc >/dev/null 2>&1; then
  MY_SOLC_CMD=(mysolc)
else
  MY_SOLC_CMD=(npx --yes --package @antchain/mysolidity@1.3.0 mysolc)
fi

cd "${SCRIPT_DIR}"
mkdir -p "${CONTRACT_PATH}"

if (( $# > 0 )); then
  CONTRACTS=("$@")
else
  CONTRACTS=(
    AuthMsg
    SDPMsg
    PtcHub
    CommitteePtcVerifier
    Monitor
    MonitorVerifier
  )
fi

for contract in "${CONTRACTS[@]}"; do
  target_dir="${TMP_DIR}/${contract}"
  "${MY_SOLC_CMD[@]}" compile "${contract}.sol" \
    --targetPath "${target_dir}" \
    --targetName "${contract}" \
    --solcVersion 0.8.14

  for artifact in "${contract}.bin" "${contract}.deployed.bin"; do
    if [[ ! -s "${target_dir}/${artifact}" ]]; then
      echo "missing Mychain compiler output: ${target_dir}/${artifact}" >&2
      exit 1
    fi
  done

  cp "${target_dir}/${contract}.bin" \
    "${CONTRACT_PATH}/${contract}_sol_${contract}.bin"
  cp "${target_dir}/${contract}.deployed.bin" \
    "${CONTRACT_PATH}/${contract}.bin-runtime"
done

echo "Mychain EVM deployment and runtime binaries generated under ${CONTRACT_PATH}"
echo "Compiler intermediates retained under ${TMP_DIR}; remove individual files manually when no longer needed"

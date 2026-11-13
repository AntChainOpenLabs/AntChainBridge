# 尽量在环境变量获取，保证各个开发者的路径互不影响
if [ ! ${SDK_PATH} ]; then
  SDK_PATH="/Users/liyuan/work/antchain/tmp/odatspluginwork/"
fi
CONTRACT_PATH="${SDK_PATH}pluginset/mychain0.20/offchain-plugin/src/main/resources/contract/1.5.0/solidity/"

solcjs --bin *.sol utils/*.sol interface/*.sol
solc --bin-runtime --overwrite *.sol utils/*.sol interface/*.sol -o .

mv AuthMsgClient_sol_AuthMsgClient.bin "${CONTRACT_PATH}am_client_mychain020_0_0_1.bin"
mv P2PMsg_sol_P2PMsg.bin "${CONTRACT_PATH}am_p2p_msg_mychain020_0_0_1.bin"

rm *.bin

mv AuthMsgClient.bin-runtime "${CONTRACT_PATH}am_client_mychain020_0_0_1_runtime.bin"
mv P2PMsg.bin-runtime "${CONTRACT_PATH}am_p2p_msg_mychain020_0_0_1_runtime.bin"

rm *.bin-runtime

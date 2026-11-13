<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">AntChain Bridge Mychain监管插件系统合约库</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
  </p>
</div>

## Mychain020 BBC监管系统合约（solidity）
本目录是在`mychain0.10`基础系统合约之上增加 Monitor 和 MonitorVerifier 的监管版合约库，适配同一 Mychain 0.10 链环境。

合约编译使用蚂蚁链官方发布的
[`@antchain/mysolidity`](https://www.npmjs.com/package/@antchain/mysolidity) 1.3.0，并固定选择
Mychain Solidity 0.8.14 编译器。可先执行 `npm install -g @antchain/mysolidity@1.3.0`；
如果本机没有 `mysolc`，编译脚本会使用锁定版本的 `npx` 命令。

执行`onchain-plugin/solidity/v1`目录下的`compile_evm_all.sh`脚本可以编译 v1 EVM 合约，
并将编译生成的`*.bin`（用于合约部署）和`*.bin-runtime`（用于合约升级）
自动更新到`offchain-plugin/src/main/resources/contract/v1/solidity`目录。监管合约新增后，
该脚本也会生成并拷贝 Monitor、MonitorVerifier、PtcHub 和 CommitteePtcVerifier 的部署及
runtime 字节码。临时编译目录会保留在 `/tmp/antchainbridge-mysolc.*`，避免构建脚本执行递归删除。
也可以指定合约名定向编译，例如 `./compile_evm_all.sh Monitor SDPMsg`。
脚本同时生成部署字节码和运行时字节码，避免生成
无法升级历史系统合约的插件包。监管版 PTC Hub 和 SDP 都只在旧版存储布局末尾追加监管
字段，因此插件会在初始化监管合约前原位升级旧合约，并保留其合约身份、信任数据、域名、
消息序号及已验证区块状态。

当前监管闭环对应 Monitor V7、MonitorVerifier V2、PtcHub V3 和 CommitteePtcVerifier V2。
PTC Hub 验证 ThirdPartyProof 时，普通委员会节点仍按原阈值验签；required monitor 节点的证明
则由委员会验证器传给 MonitorVerifier。目标链 Monitor 只有在 `MONITOR_OPEN` 的 monitor 签名
验证通过后才投递业务消息；V1 验证失败会返回监管回滚消息，V2/V3 交给既有错误/atomic ACK
路径处理。`MONITOR_CLOSE` 和 `MONITOR_ROLLBACK` 保持与 Ethereum3 相同的投递语义。

CommitteePtcVerifier V2 保留旧的两参数`verifyTpProof(tpBta, tpProof)`接口，并增加接收
MonitorVerifier identity 的三参数接口；PtcHub V3 使用新接口。TpBTA、ThirdPartyProof、SDP 和
Monitor 消息编码均未改变，因此升级窗口内旧 PtcHub 仍可调用旧接口。MonitorVerifier V2 的
新增证明状态追加在旧存储布局末尾，不覆盖既有 monitor endorsement。

升级必须依次完成 MonitorVerifier、CommitteePtcVerifier、PtcHub、Monitor 和 SDP 的能力检查，
再重建双向绑定。插件优先按合约 `identity` 原址升级，不能恢复既有 MonitorVerifier identity 时
会拒绝自动部署替代合约，因为空 Verifier 不含历史 TpBTA 的 monitor endorsement。若确需替换，
必须人工提供并重新上传全部生效 TpBTA 后才能恢复监管流量。

## Mychain BBC系统合约（c++）

合约普通编译依赖[`0.10.2.7.1（336eb50）`版本的`my++`编译器](https://antdigital.com/docs/11/426717)，
合约JIT编译（合约性能更好）依赖[`2.24`版本的`my++`编译器](https://antdigital.com/docs/11/426685)

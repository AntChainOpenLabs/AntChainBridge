<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">AntChain Bridge Fisco3.*插件系统合约库</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
  </p>
</div>

> FISCO BCOS 平台兼容 Solidity 智能合约。本插件的监管系统合约以 Ethereum3 实现为唯一源码。

本插件的 AuthMsg、SDP、PTC 与 Monitor 合约和 Ethereum3 插件共用同一套
`ethereum3/onchain-plugin/solidity/sys` 源码，以避免不同 EVM 插件的协议实现漂移。
当前 SDP 实现将 V1、V2、V3 的请求消息统一路由到 Monitor，ACK 消息保持直达；
Monitor 在接收侧严格解封装后再调用业务合约。

## 开发注意事项

### Solidity 版本

FISCO BCOS 3.* 控制台编译器默认支持 Solidity 0.8 版本。

### Java SDK 集成考虑因素

FISCO-Java-SDK 在处理 bytes32 类型时存在限制。使用 Java 代码调用合约时：

- 对于 `bytes` 或 `bytesN` 类型参数，请使用 `byte[]` 类型参数
- 对于 `string` 类型参数，直接使用 `String` 类型（而非 `Utf8String`）
- 合约返回的数值可能会以带有 `0x` 前缀的十六进制字符串形式返回

### Java 接口生成

使用 [FISCO BCOS 官方控制台工具](https://fisco-bcos-doc.readthedocs.io/zh-cn/latest/docs/sdk/java_sdk/contracts_to_java.html?highlight=contract2java) 为智能合约生成 Java 接口文件。

对于嵌套层次较复杂的合约，在转换过程中需要关闭静态分析。

转换示例：

```bash
bash contract2java.sh solidity -p com.alipay.antchain.bridge.plugins.fiscobcos3 -s ./contracts/solidity/sys --no-analysis
```

仓库提供了可重复生成脚本，参数为 FISCO Console 根目录：

```bash
./generate_monitor_wrappers.sh /path/to/fisco-console
```

该脚本更新 `AuthMsg`、`SDPMsg`、`Monitor`、`MonitorVerifier`、`PtcHub`、
`CommitteePtcVerifier` 和 `AppContract` wrapper。
为避免递归删除误伤，脚本会打印并保留 `mktemp` 生成目录；确认内容后可由操作人员清理。

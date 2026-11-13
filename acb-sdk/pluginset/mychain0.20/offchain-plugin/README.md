<div align="center">
  <img alt="am logo" src="https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/antchain.png" width="250" >
  <h1 align="center">Mychain020 Plugin</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
    <a href="https://www.java.com">
      <img alt="Language" src="https://img.shields.io/badge/Language-Java-blue.svg?style=flat">
    </a>
    <a href="https://www.apache.org/licenses/LICENSE-2.0">
      <img alt="License" src="https://img.shields.io/github/license/AntChainOpenLab/AntChainBridgeRelayer?style=flat">
    </a>
  </p>
</div>

# 介绍

蚂蚁链监管插件（Mychain020 Plugin）是在原 Mychain 0.10 插件基础上新增监管能力的独立链下插件，仍适配 Mychain 0.10 链，并与`mychain0.10`基础版并行维护。

该插件增加 Monitor 和 MonitorVerifier 合约部署、监管节点背书同步、监管指令转发，以及发送方和接收方黑名单校验等功能。

当前 EVM 监管实现与 Ethereum3 保持语义一致：PtcHub V3 将 required monitor 节点证明经
CommitteePtcVerifier V2 交给 MonitorVerifier V2，Monitor V7 在 `MONITOR_OPEN` 下验签后再决定
业务投递、V1 回滚或 V2/V3 错误 ACK。Mychain 的 `identity` 地址、MySolidity 编解码及原址
`updateContract` 机制保持不变，TEE/WASM 暂不支持该监管合约链路。

对历史部署执行 `setup-bbccontracts` 时，插件会按 MonitorVerifier、CommitteePtcVerifier、
PtcHub、Monitor、SDP 的顺序检查并原址升级。如果配置中未保存 MonitorVerifier 名称，会优先
从已有 Monitor 恢复其 identity；无法恢复时会停止，避免用缺失历史 TpBTA endorsement 的空
Verifier 覆盖现网绑定。确需部署新 Verifier 时，应在维护窗口重新上传全部生效 TpBTA。

初始化必须同时核验 `MonitorVerifier.getPtcHubAddress()` 和 `PtcHub.getMonitorVerifier()`。
Relayer 的新链部署顺序是先 Monitor、后 PtcHub；插件在两者齐备后完成双向绑定，并在最终
`setPtcHubInMonitorVerifier` 中再次对账。正确绑定不重复写入，零地址才补齐；不同的非零
地址、查询失败或设置后的回读不一致都会使初始化失败，不会静默替换已有通道。
显式 PtcHub 地址（包括 JSON 内的 EVM 名称）同时决定两侧核验和设置的目标。

真链发送前还应核对 Monitor 中的验证器 identity 与 PtcHub 中一致，且该验证器返回的
PtcHub identity 正确。仅检查 `MONITOR_OPEN`、应用绑定或 TpBTA 存在不足以确认监管接收
链路完整。配置丢失验证器名称时，插件通过 Monitor getter 恢复原始 identity，不再次哈希。
安装新 JAR 或正常启动 SDK 不会修改链上绑定；既有链的补绑需另行执行显式初始化并保存
配置交易记录。两笔配置交易不能原子回滚；部分成功后须保留记录，再显式重试补齐缺项。

# 构建

**！！！目前存在部分内部依赖发布中，可能暂时无法成功构建，发布完成后即可使用以下命令进行构建**

在`pluginset/mychain0.20/offchain-plugin`目录运行maven命令，
在`pluginset/mychain0.20/offchain-plugin/target`目录下会生成插件 jar 包`mychain020-bbc-1.0.0-SNAPSHOT-plugin.jar`。
使用 Java 21 构建时，本模块显式选择兼容的 Lombok 编译处理器（`provided`），仍生成 Java 8 字节码。

```
cd pluginset/mychain0.20/offchain-plugin && mvn package -Dmaven.test.skip=true
```

## 测试

普通 Maven 测试不连接 Mychain；显式真链测试默认通过 JUnit Assume 跳过：

```shell
mvn test
```

真链监管闭环测试为
`Mychain020MonitorOnChainIntegrationTest#completeMonitorFlowOnSharedChainWithOneContractSet`。它使用一套
带 UUID 的全新合约名，在同一个测试方法内完成部署、PTC trust root/TpBTA 写入、四个监管合约
原址升级和状态保留检查、V1 正常投递/回滚、V2 错误 ACK；V3、关闭监管、缺少 monitor proof、
错误普通节点签名和控制值矩阵使用 Mychain local call，避免在共享链上重复写交易。测试设置了
29 笔共享链写交易上限，不删除链上合约或修改外部配置文件。

运行前必须准备一份专用配置副本，其中 AM、SDP、PTC、Monitor、MonitorVerifier 地址均为空。
配置中的逻辑目标节点必须是测试类锁定的`47.116.166.95 18130`；如果通过 SSH 隧道连接，只允许
把实际连接地址覆盖为 loopback。示例：

```shell
mvn -Dtest=Mychain020MonitorOnChainIntegrationTest \
  -Dmychain.integration.enabled=true \
  -Dmychain.config.path=/path/to/fresh-mychain020.json \
  '-Dmychain.integration.expectedPrimary=47.116.166.95 18130' \
  '-Dmychain.integration.connectionPrimary=127.0.0.1 18130' \
  test
```

该测试会验证账户有效、目标不是 TEE 链且区块持续增长后才写链。如果中途断开，可使用测试输出的
runId、AM、SDP、PTC、Monitor、MonitorVerifier、Receiver 和已提交交易数，通过对应
`mychain.integration.resume*`系统属性恢复同一套合约，避免重新部署。配置文件包含私钥和 TLS
材料，不得提交到代码仓库。

# 使用

将构建的插件包放置在插件服务的plugins目录下即可使用（具体参考[插件服务使用手册](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/wiki/3.-AntChain-Bridge%E8%B7%A8%E9%93%BE%EF%BC%9A%E6%8F%92%E4%BB%B6%E6%9C%8D%E5%8A%A1)）。

## 插件类型
当前蚂蚁链监管插件的产品类型定义为`mychain2`，插件名称定义为`plugin-mychain2`，与基础版的`mychain`和`plugin-mychain`相互独立。
您可以在`com/alipay/antchain/bridge/plugins/mychain020/Mychain020BBCService.java`中查看定义。

## 插件配置文件
使用插件需要特定的配置文件，蚂蚁链插件配置模板及介绍如下：

```shell
{
  "mychain_primary": "127.0.0.1 18130",
  "mychain_sslKey": "",
  "mychain_sslKeyPass": "",
  "mychain_sslCert": "",
  "mychain_trustStore": "",
  "mychain_trustStorePassword": "",
  "mychain_anchor_account": "",
  "mychain_anchor_account_pri_key": "",
  "mychain_anchor_account_pub_key": "",
  "wasm": "true",
  "contract_binary_version": "v1.5.0"
}
```

- mychain_primary：蚂蚁链节点地址，提供一个节点地址即可，包含节点网络IP和端口；
- mychain_sslKey：蚂蚁链SSL连接客户端私钥，直接读取PEM格式私钥即可（默认名称为`client.key`），可使用`cat client.key | tr '\n' '|' | sed 's/|/\\n/g' >client_key.txt`命令进行转换；
- mychain_sslKeyPass：蚂蚁链SSL连接客户端私钥解冻密码；
- mychain_sslCert：蚂蚁链SSL连接客户端证书，直接读取证书内容即可（默认名称为`client.crt`），可使用`cat client.crt | tr '\n' '|' | sed 's/|/\\n/g' >client_crt.txt`命令进行转换；
- mychain_trustStore：蚂蚁链SSL连接信任根信息，可根据`trustCa`或`ca.crt`进行转换，`trustCa`文件可直接使用base64编码转换即可，ca.crt信任根证书到trustCa信任根文件转换方式如下：
```shell
# 以下为非国密链转换方式，国密链请向相关技术人员请求支持
# keytool 为是Java提供的密钥（Key）和证书（Certificate）管理工具
keytool -import -file ca/ca.crt -noprompt -keystore trustCa -storepass mychain
keytool -exportcert -keystore ./trustCa -rfc -file ./ca.crt -alias ca
```
- mychain_trustStorePassword：蚂蚁链SSL连接信任根信息密码；
- mychain_anchor_account：插件账户名称；
- mychain_anchor_account_pri_key：插件账户私钥；
- mychain_anchor_account_pub_key：插件账户公钥；
- wasm：是否支持wasm合约部署，选填`true`或`false`，设置为`true`时插件部署合约时会自动部署wasm合约；
- contract_binary_version：蚂蚁链插件合约版本，默认为`v1.5.0`，建议使用默认版本。

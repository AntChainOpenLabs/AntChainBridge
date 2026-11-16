<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">Dioxide2 Plugin</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
  </p>
</div>



| 说明              | 链接              |
|-------------------|-----------------|
| ⭐️ 参考的dioxid_python_sdk | [dioxide_python](https://github.com/1220292040/dioxide_python)         |
| ✅ 测试通过的 dioxide     | 2025.11.18更新版本，可联系dioxid_python仓库的Contributors获取 |

# 介绍

在本路径之下，实现了 Dioxide 的异构链接入插件，包括链下插件及链上插件部分。插件产品名为
`dioxide2`，实际区块链域名由部署环境申请，两者不能混用。

- **offchain-plugin**：链下插件，使用maven管理的Java工程，使用jdk1.21和maven编译即可。基于[dioxide_python](https://github.com/1220292040/dioxide_python)开发，将必要的python_sdk逻辑转换为java_sdk逻辑，在团队提供的2025.11.18版本的链节点上测试通过。
- **onchain-plugin**：链上插件，使GCL语言开发，实现逻辑参考了ethereum2的链上插件。

# 用法

## 构建

在 offchain-plugin 下通过`mvn package -DskipTests`编译插件 Jar 包，可以在 target 下找到
`dioxide2-acb-plugin-1.0.0-plugin.jar`。

## 测试

不连接链即可检查生成的 GCL 合约是否包含 PTC/监管/回滚入口，以及 PtcHub 存储 key 是否与
Dioxide 状态编码一致：

```shell
mvn -Dexec.skip=true \
  -Dtest=DioxideMonitorContractsTest,DioxideClientStorageKeyTest \
  test
```

真链测试默认连接`127.0.0.1:62222`。应只在专用测试链上执行，例如先启动：

```shell
./diox -nuke -con -deploy:sdk_test -api:0.0.0.0:62222 -mine
```

然后按需运行共识状态读取或完整监管收发测试：

```shell
mvn -Dexec.skip=true -Dtest=DioxideBBCServiceTest#testReadConsensusState test
mvn -Dexec.skip=true \
  -Dtest=DioxideBBCServiceTest#testRelayAuthMessageAndReadCrossChainMessageReceipt \
  test
```

完整收发测试会创建随机 dapp、部署测试合约并提交多笔交易，覆盖 monitor 签名位于不同证明顺序、
非零签名正常投递、65 字节全零签名不投递，以及 ordered/unordered V1 回滚；不要在承担业务的
共享链上直接执行。

## 使用

参考[插件服务](https://github.com/AntChainOpenLab/AntChainBridgePluginServer/blob/main/README.md)（PluginServer, PS）的使用，将Jar包放到指定路径，通过PS加载即可。

### 配置文件

当在AntChainBridge的Relayer服务注册dioxide2时，需要指定PS和链类型（dioxide2），同时需要提交一个dioxide链的配置。

Dioxide 链的配置文件`dioxide2.json`主要包括节点网络连接信息、用户信息和事务协调器配置，
配置文件大致如下：

```json
{
    "rpcUrl": "http://127.0.0.1:62222/api",
    "wsRpc": "ws://127.0.0.1:62222/api",
    "privateKey": "<DIOXIDE_ACCOUNT_PRIVATE_KEY_BASE64>",
    "dappName": "AcbDapp",
    "txCoordinatorConfigFile": "/path/to/dioxide-tx.properties",
    "isPreContractDeployed": false
}
```

- rpcUrl：dioxide链的HTTP协议的JSON-RPC接口地址
- wsRpc：dioxide链的WebSocket协议的JSON-RPC接口地址
- privateKey：base64格式的用户私钥，样例详见[dioxide_python](https://github.com/1220292040/dioxide_python)给出的account_test.py
- dappName：dapp 是 Dioxide 链上承载智能合约的载体，一个 dapp 可以部署多个智能合约；地址形式为
  `<dapp_name>:<contract_name>`，dapp 名字长度限制为 4—8 个字符，上例`AcbDapp`长度为 7。
- txCoordinatorConfigFile：可选的 Dioxide 事务协调器配置路径。未设置时依次读取 JVM 属性
  `dioxide.tx.config`、环境变量`DIOXIDE_TX_COORDINATOR_CONFIG`，最后使用
  `/etc/antchain-bridge/dioxide-tx.properties`。
- isPreContractDeployed：首次部署系统合约时一般填写`false`，插件会先部署 GCL 接口和库等前置合约；
  已确认目标 dapp 及这些前置合约已经存在时才使用`true`。

`setup-bbccontracts`会把部署后的 AuthMessage、SDP、Monitor、MonitorVerifier 和 PtcHub 地址写回
BBC Context；不要在配置文件或代码仓库中提交真实私钥、数据库密码或生产事务协调器配置。
同步交易默认等待 20 秒确认，可通过 JVM 属性`dioxide.tx.confirm.timeout.ms`覆盖。

## PTC 与监管流程

Dioxide2 当前实现了结构完整的 Committee PTC 流程，用于和 Ethereum3 等完整 PTC 链互通：

1. 插件通过 Dioxide transaction block 生成高度、当前哈希、父哈希、时间戳和共识头；哈希按
   Dioxide 原始字符串的 UTF-8 字节保存，共识头 JSON 写入`stateData`，节点信息和 endorsements
   保持为空。
2. Relayer 使用实际区块链域名注册真实 BTA/TpBTA，不再临时构造空 TpBTA，也不会为 Dioxide2
   第二次调用 PTC。
3. 普通委员会节点根据已保存 BTA 的`product=dioxide2`识别 Dioxide2，在确认 BTA/TpBTA、初始
   高度、初始哈希以及前一高度的 validated state 存在后，跳过 HCDVS、ledger proof 和消息真实性验证，直接对标准
   `ThirdPartyProof.getEncodedToSign()`签名。
4. monitor 节点按消息中的监管类型工作。`MONITOR_OPEN`调用外部监管系统：通过时返回真实签名，
   拒绝或异常时返回 65 字节空签名；`MONITOR_CLOSE/ROLLBACK`沿用上报后签名路径。调用外部监管
   系统时携带 Relayer 传入的 UCP ID，便于关联监管结果和跨链生命周期。
5. Committee PTC 按 TpBTA endorsement root 同时收集普通节点和 monitor 节点证明，生成完整且
   `rawProof`非空的`ThirdPartyProof`。监管拒绝不会在 Relayer 中停止投递。

当 Ethereum3 是目标链时，Ethereum3 继续执行完整的 TpBTA、普通委员会签名和 monitor 签名验证。
有效 monitor 签名允许业务投递，空 monitor 签名由目标链 Monitor 转换为回滚或错误 ACK。

当 Dioxide2 是目标链时，链上 AuthMessage 先把 ThirdPartyProof 交给 PtcHub。PtcHub 按
`nodeId`前缀`monitor`提取监管签名，不依赖证明排列顺序；MonitorVerifier 在同一条调用链中消费
该签名。`MONITOR_OPEN`只有在签名非空且不全为零时才向业务合约投递，否则 ordered/unordered
V1 消息都会生成`MONITOR_ROLLBACK`。`MONITOR_CLOSE`和`MONITOR_ROLLBACK`不强制监管验证。

Monitor 控制值与 Ethereum3 对齐：

- `1`：`MONITOR_CLOSE`
- `2`：`MONITOR_OPEN`
- `3`：`MONITOR_ROLLBACK`

Relayer 的`setup-bbccontracts`会把控制值默认设置为`MONITOR_OPEN`，并完成 AM、SDP、Monitor、
MonitorVerifier、PtcHub 的绑定。

## 注册要求

- `dioxide2`是插件 product，不是 sender domain。BTA、TpBTA 和 lane 必须使用实际申请的 Dioxide
  域名。
- endorsement root 必须包含普通委员会节点，并包含`nodeId`以`monitor`开头的监管节点；验收部署
  应把 monitor 节点设为 required。
- 必须注册结构完整、由 Committee PTC 签发的真实 TpBTA，不能恢复旧的临时空 TpBTA 特判。
- 验收环境可使用`e30=`（`{}`的 Base64）作为最小 subject identity，但它不参与 Dioxide 共识
  或消息真实性验证。
- Dioxide2 作为 Ethereum3 的源链时，必须先确保 TpBTA、PTC trust root 和 verify anchor 已通过
  Relayer 通用同步流程上传到 Ethereum3 PtcHub。

# 注意事项

- 当前只支持 SDP V1，不通过修改版本字段模拟 V2/V3。
- 当前 PTC 实现是课题验收级安全降级：Dioxide2 委员会节点不验证 Dioxide 共识、ledger proof
  或消息真实性。
- Dioxide2 目标链 MonitorVerifier 只判断 monitor 签名是否非空且不全为零，不做密码学验签；
  Ethereum3 目标链仍使用其完整 MonitorVerifier 验签。
- `relayMonitorOrder`、`queryValidatedBlockStateByDomain`、`recvOffChainException`和 reliable retry
  不属于当前互通闭环，仍未实现或不支持。
- 上述限制意味着该实现用于课题验收和链路联调，不应宣称具备生产级 Dioxide 共识安全性。

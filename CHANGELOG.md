# [2025-11-06] 本PR所有改动总结

## acb-committeeptc
### monitor-node
复制node模块并进行修改，改动如下：
- monitor.node.client
  - 与pluginserver和监管系统进行GPRC通信的客户端
- monitor.node.commons.core 与 monitor.node.commons.enums
  - 增加监管功能所需的一些配置
- monitor.node.server
  - 在MonitorNodeServiceImpl.verifyCrossChainMessage服务中增加事中监管功能
  - 新增MonitorOrderServiceImpl服务，完成接收监管指令功能
- monitor.node.service
  - 完成上述功能的内部实现

### monitor-node-cli
复制node-cli模块，并只修改了配置文件以适配新模块名。

## acb-relayer
- 优化r-cli的命令**setup-contract**
  - 能够一键部署AM SDP PTC Monitor相关的所有合约并完成初始化
- 优化r-cli的命令**get-blockchain-contracts**
  - 能够查询AM SDP 以及Monitor合约的地址，其中Monitor合约地址将代替SDP合约地址，在DAPP合约中进行初始化

## acb-pluginserver
增加了如下接口功能（BBC插件的新增功能与之对应）
- **SetupMonitorMessage**
  - 部署Monitor合约和MonitorVerifer合约，并在Monitor合约中初始化MonitorVerifer合约的地址
- **SetMonitorContract**
  - 在SDP合约中初始化Monitor合约地址
- **SetProtocolInMonitor**
  - 在Monitor合约中初始化SDP合约地址
- **SetMonitorControl**
  - 在Monitor合约中初始化变量monitorControl，控制监管开关（默认开）
- **SetPtcHubInMonitorVerifier**
  - 在MonitorVerifer合约中初始化PtcHub合约的地址
- **RelayMonitorOrder**
  - 将监管指令发送到Monitor合约

## acb-sdk
- antchain.bridge.commons
  - 在BBCConext增加了监管合约地址和状态
  - 增加了对监管合约的序列化和反序列化
- antchain.bridge.spi
  - 增加了监管合约所需接口
- pluginset.ethereum2
  - offchain
    - 增加了支持监管合约部署、相关初始化、发送监管指令等接口
  - onchain
    - 新增monitor合约：在dapp和sdp合约层之间，完成事前监管，并能接收和存储监管指令，与monitorVerifer合约交互完成监管节点签名的验证
    - 新增monitorVerifer合约，与monitor交互；接收跨链消息时从ptcHub合约获取监管节点签名
    - 删除了sys/lib/ptc下的CommitteePtcVerifier.sol，原因如下：
      - 并没有其他合约import该合约，
      - 该合约的功能在同目录下的CommitteeLib.sol中已经实现
      - 在添加功能代码时，如果不删除会导致编译失败


## 注意事项
**如果需要对一条链chain-B下达监管指令，该链必须先接收一条跨链消息。** 原因如下：
- 监管指令上链时，是由committeeptc中的监管节点直接调用pluginserver的BBC服务来发送交易，不会经过relayer
- 监管指令在链上需要验证监管节点签名来保证指令的真实性，所以链上需事先存储监管节点的公钥
- 监管节点的公钥和其他节点一样，存储在ptchub合约接收的的tpbta中
- 按照antchain的设计，当relayer接收到一条目的链为chain-B的跨链消息时，会检查是否已经上传chain-B最新的tpbta到链上，如果没有则上传
- 所以如果chain-B没有接收过跨链消息，ptchub合约上就不会存储tpbta，从而无法获取监管节点公钥，无法完成监管指令的签名验证，导致无法成功接收监管指令


## 含监管的流程图示
- 下图为含监管的跨链流程图：

![](docs/images/含监管的跨链流程图.png)


## 其他说明

### 跨链消息结构设计变更说明
监管合约作为SDP上层合约，封装DApp消息的同时增加监管字段monitor_type和监管信息monitor_msg。

<img src="docs/images/含监管的跨链消息结构.png"  style="zoom: 33%;" />

| monitor_type值(uint32类型) | monitor_type含义                                             | monitor_msg含义(string类型) |
| ---------------------- | ------------------------------------------------------------ | --------------------------- |
| 1                      | 发送方发出的不要求监管的跨链消息                             | 可选                        |
| 2                      | 对于发送方：发出要求监管的跨链消息对于接收方：成功接收到带监管的跨链消息 | 可选                        |
| 3                      | 监管未通过，回滚到发送方的监管回滚消息                       | 可选，如监管未通过的原因    |


### 背书策略配置说明
为加入antchain的区块链配置背书策略时，监管节点需要设置为true，举例说明如下。
- 当监管开启时，监管节点会向监管系统请求跨链消息的合法性，**合法则返回一个正确签名，不合法则返回一个空签名**。
- 当监管关闭时，监管节点的运行逻辑和其他节点完全相同，只是不会在PtcHub合约与monitorVerifier合约进行签名验证。
```
{
    "committee_id": "default",
    "endorsers": [
        {
            "node_id": "node1",
            "node_public_key": {
                "key_id": "default",
                "public_key": ""
            },
            "required": true
        },
        {
            "node_id": "monitor-node",
            "node_public_key": {
                "key_id": "default",
                "public_key": ""
            },
            "required": true
        }
    ],
    "policy": {
        "threshold": ">=0"
    }
}
```


### 监管指令结构说明
acb-committeeptc/monitor-node/src/main/proto/monitorSystemgrpc.proto中的监管指令结构如下：
```
message MonitorOrder {
  string product = 1;
  string domain = 2;
  uint64 monitorOrderType = 3;
  string senderDomain = 4;
  string fromAddress = 5;
  string receiverDomain = 6;
  string toAddress = 7;
  string transactionContent = 8;
  string extra = 9;
}
```
监管节点会解析出监管指令各字段，并构造包含监管指令的交易发送到指定区块链的监管合约，最终由监管合约更新监管规则。该结构各字段含义如下：
- product
  - 监管指令要下发到的区块链的类型，例如etherum2，fiscobcos等
- domain
  - 监管指令要下发到的区块链的域名
- monitorOrderType
  - 监管指令的类型。该字段长度为32bit，采用了分层编码的设计方式（如下图），分为主类型和子类型，每种主类型标识一种监管维度，每种主类型下分多种子类型
  - 在当前设计中，每个主类型占1bit，每个子类型占3bit，即每种主类型共有8种子类型
  - 主类型的具体含义由监管系统定义。以“黑名单”作为主类型来举例，该主类型的子类型可以包含：
    - 禁止本区块链的应用合约a发送跨链交易；
    - 禁止本区块链向区块链B的应用合约b发送跨链交易；
    - 禁止本区块链向区块链B发送跨链交易等。

<img src="docs/images/监管指令类型monitorOrderType的结构.png"  style="zoom: 33%;" />

- senderDomain
  - 跨链过程中源区块链域名，处理方式随monitorOrderType含义而变化
  - 例如监管指令是“禁止某区块链域名发送跨链消息”，则监管合约会把senderDomain加入黑名单，最终效果为该区块链无法在跨链系统发送跨链消息。
- fromAddress
  - 跨链过程中源区块链的应用合约地址，处理方式随monitorOrderType含义而变化
  - 例如监管指令是“禁止某区块链的某应用合约发送跨链消息”，则监管合约会把fromAddress加入黑名单，最终效果为区块链的该应用合约无法在该跨链系统发送跨链消息。
- receiverDomain
  - 跨链过程中目的区块链域名，处理方式随monitorOrderType含义而变化
- toAddress
  - 跨链过程中目的区块链的应用合约地址，处理方式随monitorOrderType含义而变化。
- transactionContent
  - 针对可能要对跨链过程中的原始跨链消息内容本身进行审查而设计了该字段，用于在链上审查跨链消息内容的合规性。
- extra
  - 额外信息。用于存放监管系统希望在链上存储的一些监管指令描述，或者上述字段未充分考虑的情况等，也可为空。

目前监管合约中对监管指令的支持，只完成了**合约黑名单**和**控制监管开关**两种功能。
- 合约黑名单功能对应的监管指令，用二进制表示如下：
  - **1000** 0000 0000 0000 0000 0000 0000 0000
  - 即32bit中第一对4bit组表示“黑名单”及其子类型。子类型"000"表示加入黑名单，"001"表示移除出黑名单。
- 控制监管开关的监管指令，用二进制表示如下：
  -  0000 **1000** 0000 0000 0000 0000 0000 0000
  -  即32bit中第二对4bit组表示“监管控制”及其子类型。子类型"000"表示关闭监管，"001"表示开启监管。
<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">FISCO-BCOS v3 Base Plugin</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
  </p>
</div>



| 说明              | 版本              |
|-----------------|-----------------|
| ⭐️ fisco-sdk-java | `3.8.0`         |
| ✅ 测试通过的 fisco   | `3.11.0`标准链 |
| 🔄 TODO            | `3.11.0`国密链     |

# 介绍

本目录提供 FISCO BCOS 3.x 的无监管插件，产品名为 `fiscobcos`。它与
`ethereum2` 的能力边界一致：支持 AM、SDP、PtcHub、委员会验证、PTC trust root、
verify anchor 和 TpBTA，但不部署或调用 Monitor/MonitorVerifier。

需要完整监管能力时使用相邻的 `fiscobcos3` 插件。

- **offchain-plugin**：链下插件，使用maven管理的Java工程，基于JDK 21开发，使用maven编译即可。基于fisco`3.8.0`版本的[java-sdk](https://github.com/FISCO-BCOS/java-sdk)开发。

# 用法

## 构建

在offchain-plugin下通过`mvn clean package`编译插件Jar包，可以在target下找到`fiscobcos-acb-plugin-1.0.0-plugin.jar`

## 收据证明哈希与离线回归

HCDVS 的收据 Merkle 证明使用包内的 `PureJavaKeccak256`，以 Bouncy Castle
`KeccakDigest(256)` 实现 SDK 的 `Hash` 接口。算法仍为 Keccak-256，而不是
SHA3-256；继续复用 SDK 的 `MerkleProofUtility` 和原有收据根、交易及消息校验。
每次计算创建独立摘要实例，不依赖 WeDPR JNI 或线程上下文类加载器。
SDK 的其他功能仍可能使用原生库；不要从插件中移除这些资源。

复用宿主已有的 Bouncy Castle provided 依赖，不修改其版本。Base 和监管插件
采用相同实现，进行实验对比时应固定两端的代码版本与插件哈希。

在当前目录使用 Java 21 运行以下离线测试和构建，不启动节点或生成合约 wrapper：

```sh
mvn -Dtest=PureJavaKeccak256Test,FISCOBCOSReceiptProofRegressionTest,FISCOBCOSHCDVSServiceTest package
```

测试覆盖已知哈希向量、UTF-8、二进制与空输入、多叶 Merkle 证明、错误根与节点
拒绝、错误消息与锚定 AM 身份拒绝，以及 16 线程共享验证实例。
发布时还需在宿主 Spring Boot 与实际 PF4J 加载器下验证两个插件同时加载、
多收据验证和停止后重载；只在普通 Java classpath 中通过测试不足以证明插件加载正常。
插件应在组件停止时更新并核对哈希；此修复不要求重新部署合约或重发源交易。

## 本地真链测试

真链测试默认关闭，普通 `mvn test` 不要求启动 FISCO。使用 FISCO BCOS 3.11.0
标准链时，先启动节点，再显式运行低写入量验收方法：

```bash
bash /home/saraday/blockchain/fisco/nodes/127.0.0.1/start_all.sh

MAVEN_OPTS='-Xmx768m' mvn \
  -Dfisco.live.enabled=true \
  -Dfisco.live.sdkDir=/home/saraday/blockchain/fisco/nodes/127.0.0.1/sdk \
  -Dfisco.live.peer=127.0.0.1:20200 \
  -Dfisco.live.group=group0 \
  -Dtest=FISCOBCOSBBCServiceTest#testLiveEndToEnd test
```

测试直接读取 `sdkDir` 下的 `ca.crt`、`sdk.crt` 和 `sdk.key`，不会把证书私钥
写入仓库。该测试只部署一套无监管 AM、SDP、PtcHub 和应用合约；不要一次运行
该类中的全部历史细粒度测试，否则会部署多套独立合约。

## 使用

参考[插件服务](https://github.com/AntChainOpenLabs/AntChainBridge/blob/main/acb-pluginserver/README.md)（PluginServer, PS）的使用，将Jar包放到指定路径，通过PS加载即可。

### 配置文件

当在 AntChainBridge Relayer 注册无监管 FISCO BCOS 3.x 链时，链类型使用
`fiscobcos`。不要为该产品配置 Monitor 合约地址。

fisco-bcos3.0链的配置文件`fiscobcos.json`主要包括链ssl证书信息和节点网络连接信息。

#### 标准链配置文件

当FISCO链为标准链时，配置文件格式如下：

**证书文件处理：**
[参考fisco3.0官方安装文档](https://fisco-bcos-doc.readthedocs.io/zh-cn/latest/docs/quick_start/air_installation.html)，在链的安装目录`node/127.0.0.1/sdk`下找到sdk.key、sdk.crt、ca.crt等证书文件，可以使用以下命令转换为字符串格式：
```bash
cat sdk.key | awk '{if (NR>1) printf "\\n"; printf "%s", $0}END{printf "\\n"}'
```

**BCDNS配置：**
使用[Embedded-BCDNS](https://github.com/AntChainOpenLabs/AntChainBridge/blob/main/acb-relayer/README.md#%E5%90%AF%E5%8A%A8embedded-bcdns)时，需要添加`bcdnsRootCertPem`字段，该字段对应之前生成的embedded-bcdns-root.crt文件内容。

**配置文件示例：**
```json
{
    "accountFileFormat":"",
    "accountFilePath":"/secure/path/account.pem",
    "accountPassword":"",
    "bcdnsRootCertPem":"", 
    "caCert":"",
    "connectPeer":"",
    "defaultGroup":"",
    "disableSsl":"",
    "groupID":"",
    "keyStoreDir":"",
    "messageTimeout":"",
    "msgScanPolicy":"",
    "sslCert":"",
    "sslKey":"",
    "useSMCrypto":"false"
}
```

**配置字段说明：**
- `accountFileFormat`：账户文件格式
- `accountFilePath`：可选的显式 PEM 账户文件路径；生产环境建议使用受限绝对路径
- `accountPassword`：账户文件密码，未加密 PEM 可留空
- `bcdnsRootCertPem`：BCDNS根证书PEM格式内容
- `caCert`：CA证书内容（字符串格式）
- `connectPeer`：连接节点的IP地址和端口
- `defaultGroup`：默认群组设置
- `disableSsl`：是否禁用SSL连接
- `groupID`：连接节点所在的群组ID，默认为1
- `keyStoreDir`：密钥存储目录路径
- `messageTimeout`：消息超时时间设置
- `msgScanPolicy`：消息扫描策略
- `sslCert`：SSL证书内容（字符串格式）
- `sslKey`：SSL私钥内容（字符串格式）
- `useSMCrypto`：是否使用国密算法，设置为`false`表示使用标准链

#### 国密链配置文件

当fisco链为国密链时，配置文件大致如下：

```json
{
  "certPath": "/path/to/sdk/gm",
  "caCert": "/path/to/sdk/gm/gmca.crt",
  "sslCert": "/path/to/sdk/gm/gmsdk.crt",
  "sslKey": "/path/to/sdk/gm/gmsdk.key",
  "enSslCert": "/path/to/sdk/gm/gmensdk.crt",
  "enSslKey": "/path/to/sdk/gm/gmensdk.key",
  "connectPeer": "127.0.0.1:20200",
  "groupID": "1",
  "useSMCrypto": "true"
}
```
国密链配置文件中多链以下几项：
- enSslCert：sdk 国密ssl证书路径
- enSslKey：sdk 国密ssl私钥路径
- useSMCrypto：国密链标识，国密链需要添加该标识，标准链默认为`false`

[参考fisco3.0官方安装文档](https://fisco-bcos-doc.readthedocs.io/zh-cn/latest/docs/quick_start/air_installation.html)，
这些证书均可以在链的安装目录`node/127.0.0.1/sdk`下找到，例如fisco3.0国密链的相应安装目录应如下：

```shell
 $ tree sdk
sdk
├── ca.crt
├── cert.cnf
├── gm
│   ├── gmca.crt
│   ├── gmensdk.crt
│   ├── gmensdk.key
│   ├── gmsdk.crt
│   ├── gmsdk.key
│   └── gmsdk.publickey
├── sdk.crt
└── sdk.key

2 directories, 10 files
```

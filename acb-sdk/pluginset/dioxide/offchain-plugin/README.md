<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">Dioxide Plugin</h1>
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

在本路径之下，实现了dioxide的异构链接入插件，包括链下插件及链上插件部分

- **offchain-plugin**：链下插件，使用maven管理的Java工程，使用jdk1.21和maven编译即可。基于[dioxide_python](https://github.com/1220292040/dioxide_python)开发，将必要的python_sdk逻辑转换为java_sdk逻辑，在团队提供的2025.11.18版本的链节点上测试通过。
- **onchain-plugin**：链上插件，使GCL语言开发，实现逻辑参考了ethereum2的链上插件。

# 用法

## 构建

在offchain-plugin下通过`mvn clean package -DskipTests`编译插件Jar包，可以在target下找到`dioxide-acb-plugin-1.0.0-plugin.jar`

## 使用

参考[插件服务](https://github.com/AntChainOpenLab/AntChainBridgePluginServer/blob/main/README.md)（PluginServer, PS）的使用，将Jar包放到指定路径，通过PS加载即可。

### 配置文件

当在AntChainBridge的Relayer服务注册dioxide时，需要指定PS和链类型（dioxide），同时需要提交一个dioxide链的配置。

dioxide链的配置文件`dioxide.json`主要包括节点网络连接信息和用户信息，配置文件大致如下：

```json
{
    "rpcUrl": "http://127.0.0.1:62222/api",
    "wsRpc": "ws://127.0.0.1:62222/api",
    "privateKey": "WTKi+W99TEEt153Zt8isUznwXqYkA0aVWEbd7edk6AvivGov5hBLJLQbS2hk8bnC3FM8Et6+Axaw1uukce+ZEQ==",
    "dappName": "ict001",
    "isPreContractDeployed": false
}
```

- rpcUrl：dioxide链的HTTP协议的JSON-RPC接口地址
- wsPrc：dioxide链的WebSocket协议的JSON-RPC接口地址
- privateKey：base64格式的用户私钥，样例详见[dioxide_python](https://github.com/1220292040/dioxide_python)给出的account_test.py
- dappName：dapp是dioxide链上承载智能合约的载体，智能合约只能部署在某一个dapp地址下，一个dapp中可以部署多个智能合约，dapp地址的组成形式为<dapp_name>:<dapp>其中，dapp名字长度限制在4-8内，上述例子中"ict001"就是为长度为6的dapp_name
- isPreContractDeployed：部署跨链系统合约（AM合约和SDP合约）时，由于两个合约需要依赖一些自编写的库合约作为前置合约先部署上去，所以该字段一般填false


# 注意事项
- 目前仅支持SDPv1消息版本和跨链消息传输的基本功能，基于PTC的跨链消息验证功能有待开发。

## Coordinated submission and upgrade requirements

The updated writer requires a shared coordinator schema v2, an explicit network
checkpoint, and a stable submission ID supplied by the updated Relayer and
PluginServer. Upgrade these components together; the legacy one-argument entry
point is retained, but a new Dioxide writer rejects a missing identity rather
than guessing one from the payload or allocating an unsafe replacement.

Install the schema explicitly using the SQL in `antchain-bridge-plugin-lib` and
use the Python tools under `acb-sdk/tools/dioxide-tx-coordinator` for other writing
processes. No automatic migration or counter reset runs at startup. Preserve
unknown signed submissions and query their original operation or transaction.

For existing Relayer schemas, inspect the UCP pool's native-hash capacity before
adopting Dioxide. The optional `20261113_dioxide_native_hash_columns.sql` widens
only UCP pool block/transaction columns; it does not alter validated-consensus
hash columns or committee tables. Apply it explicitly after the usual backup
and schema review if those UCP columns are still limited to 66 characters.

Plain Dioxide remains a V0 trusted-relayer receiving AM. Native consensus reading
does not grant PTC capability. Missing HCDVS cannot be treated as successful
committee validation or endorsement.

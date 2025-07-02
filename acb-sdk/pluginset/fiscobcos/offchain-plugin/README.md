<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">FISCO-BCOS v3 Plugin</h1>
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

在本路径之下，实现了fisco-bcos的异构链接入插件，包括链下插件及链上插件部分

- **offchain-plugin**：链下插件，使用maven管理的Java工程，基于JDK 21开发，使用maven编译即可。基于fisco`3.8.0`版本的[java-sdk](https://github.com/FISCO-BCOS/java-sdk)开发。

# 用法

## 构建

在offchain-plugin下通过`mvn clean package`编译插件Jar包，可以在target下找到`fiscobcos-acb-plugin-1.0.0-plugin.jar`

## 使用

参考[插件服务](https://github.com/AntChainOpenLabs/AntChainBridge/blob/main/acb-pluginserver/README.md)（PluginServer, PS）的使用，将Jar包放到指定路径，通过PS加载即可。

### 配置文件

当在AntChainBridge的Relayer服务注册fisco-bcos3.0时，需要指定PS和链类型（fiscobcos），同时需要提交一个fisco链的配置。

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
<div align="center">
  <img alt="am logo" src="https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/antchain.png" width="250" >
  <h1 align="center">AntChain Bridge</h1>
</div>

## 介绍

AntChain Bridge是蚂蚁链开源的区块链之间的可信通信网络，我们致力于打造通用的异构链跨链协议，为Web3世界打造可信通信的基础设施。

仓库AntChainBridge包含了目前所有跨链相关的组件和依赖，并综合给出整体版本，提供一键编译、部署等功能，方便使用者快速启动自己的跨链应用，无论是高可用的生产场景，还是搭建本地Demo环境。

当前AntChain Bridge包含下面的组件，实现了异构链之间的通信能力，后续将开源证明转化组件的实现，完成信任闭环。

- **AntChainBridgePluginSDK**：提供开发者开发AntChain Bridge应用的所有工具，详情参考[README](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/blob/main/README.md)；
- **AntChainBridgeRelayer**：网络中负责监听、传递跨链消息的中继服务，向区块链提供加入AntChain Bridge的入口，详情参考[README](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/blob/main/README.md)；
- **AntChainBridgePluginServer**：插件服务负责运行区块链桥接组件（*Blockchain Bridge Component, BBC*）的服务，BBC以插件的形式加载到插件服务，中继等服务通过和插件服务通信实现与区块链的交互，详情参考[README](https://github.com/AntChainOpenLabs/AntChainBridgePluginServer/blob/main/README.md)；
- **BIF BCDNS Credential Server**：AntChain Bridge团队和中国信通院[星火链网](https://bitfactory.cn/)团队联合研发的区块链域名服务（*BlockChain Domain Name Service, BCDNS*），向AntChain Bridge跨链网络提供区块链域名、跨链身份管理等能力，它基于星火链提供服务；

想要详细了解AntChain Bridge，可以阅读我们[Wiki](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/wiki)的内容。

## 版本

### 组件

这里给出AntChain Bridge总版本与其他组件的版本对应关系。

| AntChainBridge |                   AntChainBridgePluginSDK                    |                    AntChainBridgeRelayer                     |                  AntChainBridgePluginServer                  | BIF BCDNS Credential Server |
| :------------: | :----------------------------------------------------------: | :----------------------------------------------------------: | :----------------------------------------------------------: | :-------------------------: |
|     v0.1.1     | [v0.2.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.2.1) | [v0.1.0](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/releases/tag/v0.1.0) | [v0.2.2](https://github.com/AntChainOpenLabs/AntChainBridgePluginServer/releases/tag/v0.2.2) |       v1.0.0-SNAPSHOT       |
|     v0.1.0     | [v0.2.0](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.2.0) | [v0.1.0](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/releases/tag/v0.1.0) | [v0.2.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginServer/releases/tag/v0.2.1) |       v1.0.0-SNAPSHOT       |
|       \        | [v0.1.2](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.1.2) |                              \                               | [v0.2.0](https://github.com/AntChainOpenLabs/AntChainBridgePluginServer/releases/tag/v0.2.0) |              \              |
|       \        | [v0.1.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.1.1) |                              \                               | [v0.1.2-SNAPSHOT](https://github.com/AntChainOpenLabs/AntChainBridgePluginServer/releases/tag/v0.1.2-SNAPSHOT) |              \              |

### 插件

这里给出AntChain Bridge SDK和异构链插件的版本对应关系。

| AntChainBridgePluginSDK                                      | Ethereum BBC Plugin                                          | EOS BBC Plugin                                               |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| [v0.2.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.2.1) | [simple-ethereum-bbc-v0.1.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/simple-ethereum-bbc-v0.1.1) | [eos-bbc-v0.1.2](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/eos-bbc-v0.1.2) |
| [v0.2.0](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.2.0) | [simple-ethereum-bbc-v0.1.0](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/simple-ethereum-bbc-v0.1.0) | [eos-bbc-v0.1.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/eos-bbc-v0.1.1) |
| [v0.1.2](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.1.2) | [simple-ethereum-bbc-v0.1.0](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/simple-ethereum-bbc-v0.1.0) | [eos-bbc-v0.1-SNAPSHOT](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/eos-bbc-v0.1-SNAPSHOT) |
| [v0.1.1](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/v0.1.1) | [simple-ethereum-bbc-v0.1-SNAPSHOT](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/releases/tag/simple-ethereum-bbc-v0.1-SNAPSHOT) | \                                                            |


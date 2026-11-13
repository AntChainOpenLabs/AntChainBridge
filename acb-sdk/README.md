<div align="center">
  <img alt="am logo" src="https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/antchain.png" width="250" >
  <h1 align="center">AntChain Bridge Plugin SDK</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
    <a href="https://www.java.com">
      <img alt="Language" src="https://img.shields.io/badge/Language-Java-blue.svg?style=flat">
    </a>
    <a href="https://github.com/AntChainOpenLab/AntChainBridgePluginSDK/graphs/contributors">
      <img alt="GitHub contributors" src="https://img.shields.io/github/contributors/AntChainOpenLab/AntChainBridgePluginSDK">
    </a>
    <a href="https://www.apache.org/licenses/LICENSE-2.0">
      <img alt="License" src="https://img.shields.io/github/license/AntChainOpenLab/AntChainBridgePluginSDK?style=flat">
    </a>
  </p>
</div>


# 介绍

AntChain Bridge将跨链互操作解释为两个层次：通信和可信，即跨链的目标在于实现区块链实体之间的可信通信。

在AntChain Bridge的架构中，中继需要与区块链进行交互，而异构链的通信协议各式各样，无法统一适配，因此AntChain Bridge抽象出了区块链桥接组件（Blockchain Bridge Component, BBC），来解决区块链和跨链网络的通信问题。

每种异构链要接入AntChain Bridge跨链网络，都需要实现一套标准的区块链桥接组件，可以分为链上和链下两部分，包括**链下插件**和**系统合约**。链下插件需要基于SDK完成开发，链上部分则通常是智能合约，要求实现特定的[接口](antchain-bridge-spi/README.md)和逻辑，为降低开发难度，我们提供了Solidity版本的[实现](./pluginset/ethereum/onchain-plugin/solidity)。

AntChain Bridge为开发者提供了SDK、手册和系统合约模板，来帮助开发者完成BBC插件和合约的开发。同时，AntChain Bridge提供了插件服务（[PluginServer](https://github.com/AntChainOpenLab/AntChainBridgePluginServer)）来运行插件，插件服务是一个独立的服务，具备插件管理和响应中继请求的功能。

在当前的工程实现中，BBC链下部分是以插件的形式实现的。AntChain Bridge实现了一套SDK，通过实现SDK中规定的接口（SPI），经过简单的编译，即可生成插件包。插件服务（PluginServer, PS）可以加载BBC链下插件，详情可以参考插件服务的介绍[文档](https://github.com/AntChainOpenLab/AntChainBridgePluginServer/blob/main/README.md)。

除此之外，要求异构链开发者实现“异构链数据验证服务（Hetero-Chain Data Verification Service, HCDVS）”，来提供给证明转化组件（PTC）完成区块链帐本数据存在性验证工作，进而做出是否为对应的跨链消息背书的决定，HCDVS目前也是通过插件的形式实现的，要求实现特定的[接口](antchain-bridge-spi/src/main/java/com/alipay/antchain/bridge/plugins/spi/ptc/IHeteroChainDataVerifierService.java)和逻辑，类似BBC插件。

区块链域名服务（BlockChain Domain Name Service, BCDNS）为AntChain Bridge跨链网络提供身份、路由、信任管理等服务，在v0.2.0版本之后，加入了BCDNS模块以及其他数据结构，比如区块链域名证书等类型和工具，并在`antchain-bridge-bcdns`中增加了基于[星火链网](https://bitfactory.cn/)的BCDNS服务的客户端实现，该BCDNS服务由[中国信息通信研究院](http://www.caict.ac.cn/)开发支持，详情请[见](https://github.com/caict-4iot-dev/BCDNS)。

在SDK中抽象了BCDNS服务的接口[IBlockChainDomainNameService](antchain-bridge-bcdns/src/main/java/com/alipay/antchain/bridge/bcdns/service/IBlockChainDomainNameService.java)，描述了BCDNS应该提供的功能，目前仅支持官方实现的BCDNS，支持的类型可[见](antchain-bridge-bcdns/src/main/java/com/alipay/antchain/bridge/bcdns/service/BCDNSTypeEnum.java)。

在v0.3.0之后，拆分出单独的BCDNS Factory模块，用于快速启动不同类型BCDNS的客户端，并剥离依赖。增加关于Embedded BCDNS的实现代码，并提供Spring Boot Starter方便快速启动Embedded BCDNS服务。

在v1.0.0之后，AntChain Bridge SDK增加了对Committee PTC客户端和基本数据结构的支持，所有PTC服务都需要实现接口[IPTCService](antchain-bridge-ptc/src/main/java/com/alipay/antchain/bridge/ptc/service/IPTCService.java)，Committee PTC的多个委员会成员对跨链消息进行投票🗳️来提供背书证明，其客户端实现参考[代码](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/CommitteePTCService.java)。

以下介绍了基于SDK的一个集成架构：

![](https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/deploy_arch_241106.png)

SDK共有六个部分，包括：

- **antchain-bridge-commons**：包含很多工具方法和数据结构，帮助BBC实现快速开发；

- **antchain-bridge-plugin-lib**：BBC插件化的依赖库，给出一个注解`@BBCService`，帮助插件开发者可以快速完成插件构建；

- **antchain-bridge-plugin-manager**：插件的管理库，提供插件的加载、生命周期管理等能力，插件服务依赖于这个库；

- **antchain-bridge-spi**：主要包含了接口`IBBCService`，描述了一个BBC实现类应该有的功能，开发者只要依次实现接口即可，详细接口介绍请[见](./antchain-bridge-spi/README.md)；

- **antchain-bridge-bcdns**：主要包含了接口`IBlockChainDomainNameService`，描述了一个BCDNS客户端应该有的功能；

- **antchain-bridge-bcdns-factory**：包含一个工厂类，支持依据类型创建BCDNS客户端，目前支持星火链网（BIF）的BCDNS客户端和Embedded BCDNS的实现，详细使用可以参考[wiki](https://github.com/AntChainOpenLabs/AntChainBridge/wiki)中“如何实现跨链”的内容；

- **antchain-bridge-ptc**：包含了PTC服务要求实现的接口和部分数据模型；

- **ptc-services/committee-ptc-core**：包含了Committee PTC的相关数据模型和客户端实现；

- **bcdns-services/embedded-bcdns**：包含了简单的集成BCDNS核心模块和SpringBoot Starter模块；

  

# 构建

**在开始之前，请您确保安装了maven和JDK，这里推荐使用[jdk-1.8](https://adoptium.net/zh-CN/temurin/releases/?version=8)版本*

## 本地安装

> [!TIP]
> 由于antchain-bridge-bcdns-factory模块引用了embedded-bcdns-core，而embedded-bcdns-core还在使用AntChain Bridge SDK 0.3.0版本，由于我们还没有在开放的maven repo上传SDK的依赖，所以这里会编译报错，可以在[pom.xml](pom.xml)的modules中注释掉最后两个embedded-bcdns-core和antchain-bridge-bcdns-factory先安装其他的模块，也可以切换到分支release/v0.3.0把SDK先mvn install。

在项目根目录下，直接使用maven编译即可：

```
mvn install -Dmaven.test.skip=true
```

这样，SDK的Jar包就被安装在本地了。

然后，可以通过在maven的pom.xml配置依赖就可以了，比如下面一段配置，`${antchain-bridge.sdk.version}`为当前仓库的版本号，可以在项目目录的[pom.xml](pom.xml)看到。

```xml
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-plugin-lib</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-plugin-manager</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-spi</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-commons</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-bcdns</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-bcdns-factory</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
<dependency>
    <groupId>com.alipay.antchain.bridge</groupId>
    <artifactId>antchain-bridge-ptc</artifactId>
    <version>${antchain-bridge.sdk.version}</version>
</dependency>
```



# 快速开始

## BBC&HCDVS插件

> [!NOTE]  
> 在1.0版本的BBC我们新加了多个接口，因此要接入AntChain Bridge 可信跨链，需要对原有的BBC插件进行升级，并开发新的HCDVS插件。
>
> 对于1.0版本之前的BBC插件，AntChain Bridge最新的Plugin Server和Relayer依旧兼容和支持，异构链依旧可以通过“V0”版本的BBC插件接入区块链，不过只提供“通信”，而不是“可信通信”。
>
> 对于V1版本插件，也就是BBC&HCDVS功能都Ready的区块链插件，目前仅提供蚂蚁链（[Mychain](pluginset/mychain0.10)）、星火链网（[BIF chain](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/tree/feat/plugins/bif/pluginset/bifchain)）和[Ethereum2.0](pluginset/ethereum2)三种，后续会有更多的链接入AntChain Bridge 1.0♻️。

### Testchain

[Testchain](pluginset/demo-testchain)是一个用于讲解如何开发BBC插件的demo工程，结合AntChain Bridge的文档，可以更好地理解BBC的开发过程，同时也提供了HCVDS插件的Demo。

详细的开发教程请参考本仓库的[Wiki](https://github.com/AntChainOpenLab/AntChainBridgePluginSDK/wiki)。

### 以太坊2.0

目前，已支持PoS的以太坊安全跨链，这里提供了以太坊的BBC&HCDVS[插件](./pluginset/ethereum2)。

### 以太坊（无验证插件）

> [!NOTE]  
> 目前仅支持BBC部分，即V0

基于SDK，我们开发了一个打通以太坊的BBC[插件](./pluginset/ethereum)。

进入以太坊插件的路径下，可以看到以下文件：

```
# tree -L 4 .        
.
├── offchain-plugin
│   ├── README.md
│   ├── pom.xml
│   └── src
└── onchain-plugin
    ├── README.md
    └── solidity
        ├── scenarios
        │   └── nft_crosschain
        └── sys
            ├── AppContract.sol
            ├── AuthMsg.sol
            ├── SDPMsg.sol
            ├── interfaces
            └── lib
```

- **offchain-plugin**工程下面，我们基于`Web3j`，实现了以太坊的BBC插件的链下部分；
- **onchain-plugin**工程下面，主要分为两部分：
  - **sys**：包含以太坊的BBC链上部分，实现了AM、SDP等逻辑。
  - **scenarios**：本路径下的`nft_crosschain`中，我们实现了一套跨链桥方案，用于ERC1155资产的跨链。

详细操作请[见](pluginset/ethereum/offchain-plugin/README.md)。

### EOS

> [!NOTE]  
> 目前仅支持BBC部分，即V0

基于SDK，我们提供了一个打通EOS链的BBC[插件](pluginset/eos)。

- **offchain-plugin**工程下面实现了EOS的BBC插件的链下部分；
- **onchain-plugin**工程下面，主要分为两部分：
  - **合约代码**：合约代码放在[路径](pluginset/eos/onchain-plugin/cpp/sys/src)下面，包含AM合约、SDP合约、Demo合约，详情请[见](pluginset/eos/onchain-plugin/README.md)。

详细操作请[见](pluginset/ethereum/offchain-plugin/README.md)。

### Mychain

基于SDK我们给出了打通蚂蚁链（Mychain）的BBC&HCDVS [插件](pluginset/mychain0.10)，目前内部依赖（mychain sdk）发布中，发布之后即可编译使用。

### BIF Chain

基于SDK我们给出了打通星火链网的BBC&HCDVS [插件](pluginset/bifchain)。

### ChainMaker

> [!NOTE]  
> 目前仅支持BBC部分，即V0

AntChain Bridge支持长安链BBC接入，参考[代码](pluginset/chainmaker)。

### Hyperledger Fabric

> [!NOTE]  
> 目前仅支持BBC部分，即V0

AntChain Bridge支持Hyperledger Fabric的BBC接入，参考[代码](pluginset/fabric)。

### FISCO BCOS

> [!NOTE]  
> 目前仅支持BBC部分，即V0

AntChain Bridge 支持 FISCO BCOS 3.x 的两套 BBC：无监管基线见
[fiscobcos](pluginset/fiscobcos)，完整监管版见 [fiscobcos3](pluginset/fiscobcos3)。
旧 FISCO BCOS 2.x 插件继续使用 [fiscobcos2](pluginset/fiscobcos2)。

### Hyperchain2.0

> [!NOTE]  
> 目前仅支持BBC部分，即V0

AntChain Bridge支持Hyperchain2.0的BBC接入，参考[代码](pluginset/hyperchain2.0)。

### Polygon PoS

> [!NOTE]  
> 目前仅支持BBC部分，即V0

AntChain Bridge支持Polygon PoS的BBC接入，参考[代码](pluginset/polygon)。



## BCDNS

### 跨链身份

BCDNS为AntChain Bridge提供了身份服务，通过签发各类身份证书，实现区块链域名申请、域名验证、Relayer互认等流程。

BCDNS会为AntChain Bridge提供域名证书、域名空间证书、Relayer证书、PTC证书和信任根证书。

#### 证书结构介绍

证书的类型枚举可以参考[代码](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/CrossChainCertificateTypeEnum.java)，证书的基本结构可以参考[代码](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/AbstractCrossChainCertificate.java)。

整体类图如下：

```mermaid
classDiagram
direction LR
class CrossChainCertificateTypeEnum {
<<enumeration>>
  +  BCDNS_TRUST_ROOT_CERTIFICATE
  +  DOMAIN_NAME_CERTIFICATE
  +  RELAYER_CERTIFICATE
  +  PROOF_TRANSFORMATION_COMPONENT_CERTIFICATE
}

class AbstractCrossChainCertificate {
   CrossChainCertificateTypeEnum type
   String version
   String id
   ObjectIdentity issuer
   byte[] credentialSubject
   IssueProof proof
   long expirationDate
   long issuanceDate
}
class IssueProof {
   byte[] rawProof
   byte[] certHash
   String sigAlgo
   String hashAlgo
}

class ObjectIdentity {
  - byte[] rawId
  - ObjectIdentityType type
}
class ObjectIdentityType {
<<enumeration>>
  +  X509_PUBLIC_KEY_INFO
  +  BID
}

ObjectIdentity "1" *--> "type 1" ObjectIdentityType 
AbstractCrossChainCertificate "1" *--> "type 1" CrossChainCertificateTypeEnum 
AbstractCrossChainCertificate "1" *--> "proof 1" IssueProof 
AbstractCrossChainCertificate "1" *--> "issuer 1" ObjectIdentity 
AbstractCrossChainCertificate  -->  IssueProof 
```

从上图，可以看到证书的类型，域名和域名空间证书，都作为`DOMAIN_NAME_CERTIFICATE`类型。

`AbstractCrossChainCertificate`为证书的主要实现，包含下列字段：

- type：证书类型；
- version：证书结构的版本号；
- id：某一本证书的ID，由BCDNS签发时确定；
- issuer：签发该证书的主体ID，使用类型`ObjectIdentity`表示，下文会有所介绍；
- credentialSubject：持有该证书的主体信息，不同类型证书有不同的主体结构，下文会有所介绍；
- proof：签发主体对该证书作出的证明，目前支持非对称密钥的签名；
- expirationDate：该证书的超时的时间戳，以秒为单位。
- issuanceDate：签发该证书的时间戳，以秒为单位。

#### 主体结构介绍

上文中提到了主体ID类型`ObjectIdentity`，目前支持两种类型`X509_PUBLIC_KEY_INFO`和`BID`。

- X509_PUBLIC_KEY_INFO

  该[类型](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/core/base/X509PubkeyInfoObjectIdentity.java)使用了`X.509 Subject Public Key Info`作为主体ID，在`rawId`字段中存放了encoded的`X.509 Subject Public Key Info`，详细可以参考[RFC5280](https://datatracker.ietf.org/doc/html/rfc5280#section-4.1.2.7)。

- BID

  该[类型](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/core/base/BIDInfoObjectIdentity.java)使用了星火链的DID[方案](https://bif-doc.readthedocs.io/zh-cn/2.0.0/bid/%E6%98%9F%E7%81%AB%E9%93%BEBID%E4%BD%93%E7%B3%BB%E4%BB%8B%E7%BB%8D.html)。星火标识`BID`(`Blockchain-based Identifier`, `BID`)，`BID`标识是基于`W3C DID`标准开发的新型分布式标识，任意实体可自主生成星火标识，在`rawId`字段中存放了encoded的BID。

证书的`credentialSubject`字段可以按照证书类型反序列化为不同的类型：

- [域名主体](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/DomainNameCredentialSubject.java)

  ```mermaid
  classDiagram
  direction LR
  class DomainNameCredentialSubject {
     String version
     DomainNameTypeEnum domainNameType
     CrossChainDomain domainName
     CrossChainDomain parentDomainSpace
     ObjectIdentity applicant
     byte[] subject
  }
  
  class DomainNameTypeEnum {
  <<enumeration>>
    +  DOMAIN_NAME
    +  DOMAIN_NAME_SPACE
  }
  
  DomainNameCredentialSubject "1" *--> "type 1" DomainNameTypeEnum 
  ```

  上图介绍了域名主体包含的字段：

  - version：该主体类型的版本号；
  - domainNameType：当前主体表达的类型，是域名的宣称还是域名空间的宣称；
  - domainName：域名或者域名空间的值；
  - parentDomainSpace：签发BCDNS的域名空间，规定根BCDNS的域名空间为空字符串，可见[代码](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/core/base/CrossChainDomain.java)的`ROOT_DOMAIN_SPACE`；l
  - applicant：主体ID；
  - subject：序列化的主体身份，根据主体ID类型，如果主体ID是`X509_PUBLIC_KEY_INFO`，则为空，如果是`BID`类型，则为[BID文档](https://bif-doc.readthedocs.io/zh-cn/2.0.0/bid/BID%E6%96%87%E6%A1%A3.html)（BID Document）内容，subject的主要作用是保存主体的公钥内容，BID文档的使用较为简单，主要是使用`publicKey`字段；

- 域名空间主体

  域名空间和域名的主体结构相同，只有`domainNameType`不同，为`DOMAIN_NAME_SPACE`。

  域名空间应当以点“.”开头，后接空间名字，比如‘’.web3“，如果是域名，则不应该以点开头。域名和域名空间的长度都不应该超过128字节，且建议使用小写英文字母表示，比如`antchain.web3`。

- [Relayer主体](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/RelayerCredentialSubject.java)

  ```mermaid
  classDiagram
  direction BT
  class RelayerCredentialSubject {
     String version
     String name
     ObjectIdentity applicant
     byte[] subjectInfo
  }
  ```

  上图介绍了Relayer主体包含的字段：

  - version：该主体类型的版本号；
  - name：Relayer的名称，这里可以随意填写；
  - applicant：主体ID；
  - subject：序列化的主体身份；

  Relayer的运行需要依赖Relayer证书，详情请参考[AntChain Bridge Relayer](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer)。

- [PTC证书](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/PTCCredentialSubject.java)

  ```mermaid
  classDiagram
  direction LR
  class PTCCredentialSubject {
     String version
  	 String name
  	 PTCTypeEnum type
     ObjectIdentity applicant
     byte[] subjectInfo
  }
  
  class PTCTypeEnum {
  <<enumeration>>
    +  EXTERNAL_VERIFIER
    +  COMMITTEE
    +  RELAY_CHAIN
  }
  
  PTCCredentialSubject "1" *--> "type 1" PTCTypeEnum 
  ```

  证明转换服务（PTC）会为AntChain Bridge 提供异构链跨链消息存在性验证服务，以及提供背书证明，PTC需要向BCDNS申请身份并注册信任根，对每条链都会提供第三方区块链信任锚（TP-BTA），以供跨链中的接收链验证跨链消息证明（TP-Proof）。

  目前计划支持多种类型的PTC，比如`PTCTypeEnum`中的委员会（COMMITTEE）、区块链（RELAY_CHAIN）、外部服务（EXTERNAL_VERIFIER），区块链类型可以看作常见的中继链跨链方案。

- [信任根证书](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/BCDNSTrustRootCredentialSubject.java)

  ```mermaid
  classDiagram
  direction BT
  class BCDNSTrustRootCredentialSubject {
     String name
  	 ObjectIdentity bcdnsRootOwner
     byte[] bcdnsRootSubjectInfo
  }
  ```

  最后是持有根域名的BCDNS信任根主体，上面字段包含主体的name，以及主体ID和主体信息。

  域名空间证书用于表示某个BCDNS持有该域名空间，该空间不可以是根空间，这些BCDNS为下级BCDNS，而根BCDNS为最高级BCDNS，比如当前的星火链BCDNS，上级BCDNS可以给下级签发域名空间证书，每个BCDNS可以签发自己空间的区块链域名。

#### 序列化介绍

跨链证书的序列化使用了AntChain Bridge 提供的一个TLV序列化工具（使用[介绍](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/utils/codec/tlv/annotation/TLVField.java)）。

跨链证书支持保存为PEM格式，证书对象可以通过[CrossChainCertificateUtil](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/bcdns/utils/CrossChainCertificateUtil.java)转化至PEM格式，比如：

```
-----BEGIN BCDNS TRUST ROOT CERTIFICATE-----
AADZAQAAAAABAAAAMQEABAAAAHRlc3QCAAEAAAAAAwA7AAAAAAA1AAAAAAABAAAA
AQEAKAAAAGRpZDpiaWQ6ZWZiVGh5NXNiRzdQM21GVXAyRVdONW9RR1g2TFVHd2cE
AAgAAAChN2RlAAAAAAUACAAAACFrRWcAAAAABgDQAAAAAADKAAAAAAADAAAAYmlm
AQA7AAAAAAA1AAAAAAABAAAAAQEAKAAAAGRpZDpiaWQ6ZWZiVGh5NXNiRzdQM21G
VXAyRVdONW9RR1g2TFVHd2cCAHoAAAB7InB1YmxpY0tleSI6W3sidHlwZSI6IkVE
MjU1MTkiLCJwdWJsaWNLZXlIZXgiOiJiMDY1NjZhZjY2NWVlNTUwNjM1ZTk4Mzc1
YzdiMzg4NzZhMmNjMzFlM2E5ZDgxODk1N2U0NmEyNGEwZjI1YTc0YTQ0Y2NlIn1d
fQcAiAAAAAAAggAAAAAAAwAAAFNNMwEAIAAAALKKKxzUTalyw2vp96hOJ3TuDsNZ
/pjgltTL+ip2bsRNAgAHAAAARWQyNTUxOQMAQAAAAMsFd4D9Gf3hiIebiq5MFTpw
IV4AUtT9d+Y8gRK/kmNySzlJ32Shw3FNj8Uvy3yjBxjO6vKOWH5Jhu936zMWOgk=
-----END BCDNS TRUST ROOT CERTIFICATE-----
```



### 星火链网（BIF）BCDNS

目前SDK支持星火链网的BCDNS服务客户端，这里介绍其配置项和如何实例化该客户端。

首先介绍配置，代码可[见](antchain-bridge-bcdns/src/main/java/com/alipay/antchain/bridge/bcdns/impl/bif/conf/BifBCNDSConfig.java)，主要分为两部分，一部分`certificationServiceConfig`是用于和颁证服务通信、鉴权，另一部分`chainConfig`用于和星火链网交互。

```json
{
	"certificationServiceConfig":{
		"authorizedKeyPem":"-----BEGIN PRIVATE KEY-----\nMFECAQEwB...WnSkTM4=\n-----END PRIVATE KEY-----\n",
		"authorizedPublicKeyPem":"-----BEGIN PUBLIC KEY-----\nMCowBQYDK2Vw...KDyWnSkTM4=\n-----END PUBLIC KEY-----\n",
		"authorizedSigAlgo":"Ed25519",
		"clientCrossChainCertPem":"-----BEGIN RELAYER CERTIFICATE-----\nAAAIA...DyLBh2ITiTQ4IVYlXkYjSBw==\n-----END RELAYER CERTIFICATE-----\n",
		"clientPrivateKeyPem":"-----BEGIN PRIVATE KEY-----\nMFECAQE...V+RqJKDyWnSkTM4=\n-----END PRIVATE KEY-----\n",
		"sigAlgo":"Ed25519",
		"url":"http://localhost:8112"
	},
	"chainConfig":{
		"bifAddress":"did:bid:efbThy5sb...5oQGX6LUGwg",
		"bifChainRpcUrl":"http://test.bifcore.bitfactory.cn",
		"bifPrivateKey":"priSPKgnr1a...JNaackZJUo",
		"domainGovernContract":"did:bid:efjQKy4HEshTueHGKzrJPATKoFeNgHKo",
		"ptcGovernContract":"did:bid:efgTq9DtP2zHAhmKp7M4BhN6AVYMVWV2",
		"relayerGovernContract":"did:bid:efSnvCFJSnpWiQiVhnh8Eimgyi4RoNpA"
	}
}
```

下面对各个配置项给出解释：

首先是颁证服务的配置：

- authorizedKeyPem：有权限申请跨链身份（Relayer、PTC）的私钥，填入PEM格式的PKCS#8的私钥，目前仅支持Ed25519算法。
- authorizedPublicKeyPem：`authorizedKeyPem`对应的公钥，填入PEM格式的PKCS#8的公钥，目前仅支持Ed25519算法。
- authorizedSigAlgo：`authorizedKeyPem`私钥的签名算法，目前仅支持[Ed25519](http://oid-info.com/get/1.3.101.112)算法。
- clientCrossChainCertPem：Relayer的跨链证书，需要提前从BCDNS处获取。
- clientPrivateKeyPem：Relayer跨链证书持有者的私钥，填入PEM格式的PKCS#8的私钥，目前仅支持Ed25519算法。
- sigAlgo：`clientPrivateKeyPem`私钥的签名算法，目前仅支持Ed25519算法。
- url：颁证服务的URL。

然后是[星火链网](https://bif-doc.readthedocs.io/zh-cn/2.0.0/quickstart/%E5%BF%AB%E9%80%9F%E6%8E%A5%E5%85%A5%E6%98%9F%E7%81%AB%E9%93%BE.html)的配置：

- bifAddress：星火链网的账户地址，这里需要使用Relayer的公钥来生成该地址，可以参考[代码](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/blob/develop/r-cli/src/main/java/com/alipay/antchain/bridge/relayer/cli/command/UtilsCommands.java#L196)。
- bifChainRpcUrl：星火链网节点的RPC地址。
- bifChainRpcPort：星火链网节点的PRC端口，如果不需要则可以不填。
- clientPrivateKeyPem：星火链网账户的私钥，这里需要使用Relayer的私钥`clientPrivateKeyPem`来生成该地址，可以参考[代码](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/blob/6658dfa599b73b1aa4f3cf156e1fc1d72c5cb7c6/r-cli/src/main/java/com/alipay/antchain/bridge/relayer/cli/command/UtilsCommands.java#L203C20-L203C42)。
- domainGovernContract：域名管理合约，参考[星火链网BCDNS](https://github.com/caict-4iot-dev/BCDNS)，依赖的星火链网BCDNS应当有唯一一本域名管理合约。
- ptcGovernContract：PTC身份管理合约，依赖的星火链网BCDNS应当有唯一一本PTC身份管理合约。
- relayerGovernContract：Relayer身份管理合约，依赖的星火链网BCDNS应当有唯一一本Relayer身份管理合约。

准备完配置之后，可以根据[这里](antchain-bridge-bcdns/src/main/java/com/alipay/antchain/bridge/bcdns/impl/BlockChainDomainNameServiceFactory.java)创建一个BCDNS实例。

### Embedded BCDNS

Embedded BCDNS是内嵌在服务内部的BCDNS，提供中心化的权威服务，会使用一把私钥为跨链网络提供认证、准入等功能，按照服务端要求可以通过简单配置接入BCDNS，具体内容可以参考[这里](bcdns-services/embedded-bcdns/README.md)。

## PTC

### 基本概念

#### 跨链通道（Cross Chain Lane）

AntChain Bridge支持合约发送消息给另一条链的另一本合约，必然存在四元组：（发送链域名、发送合约、接收链域名、接收合约），这个四元组就是AntChain Bridge定义的跨链通道（Cross Chain Lane），类似地，定义链与链之间的二元组（发送链域名、接收链域名）。

```mermaid
classDiagram
direction BT
class CrossChainChannel {
  - CrossChainDomain receiverDomain
  - CrossChainDomain senderDomain
}
class CrossChainLane {
  - CrossChainIdentity senderId
  - CrossChainIdentity receiverId
  - CrossChainChannel crossChainChannel
}

CrossChainLane  ..>  CrossChainChannel : «create»
CrossChainLane "1" *--> "crossChainChannel 1" CrossChainChannel 

```

- senderId：发送合约的32字节ID，AntChain Bridge要求发送的跨链消息中，所有合约都要映射到32字节的空间中，这往往是BBC插件合约中实现的，比如Ethereum 20bytes的地址通过前缀补零的方式完成映射；
- receiverId：接收合约的32字节ID；
- crossChainChannel：CrossChainChannel 发送链与接收链域名；

#### PTC信任根（PTC Trust Root）

一个PTC加入AntChain Bridge网络之前，必须要将自己至少一个信任根发布到BCDNS，PTCTrustRoot包含了用于验证PTC背书的验证信息，比如Committee PTC的公钥集合，以及PTC开放的网络信息，比如Committee PTC各个节点的IP地址等。

在PTCTrustRoot中，包含了一个版本号-VerifyAnchor验证锚的映射，如果PTC更新了自己的验证信息，比如中继链节点变更了，则需要增加版本号，来增加BCDNS上存储的映射里面的验证锚，已存在版本号的验证锚不得修改，在中继链场景下，这个“版本号”可以理解为中继链块高，AntChain Bridge网络则可以通过新的VerifyAnchor来验证PTC给的证明，比如TpBTA。

```mermaid
classDiagram
direction BT
class PTCTrustRoot {
  - Map~BigInteger, PTCVerifyAnchor~ verifyAnchorMap
  - AbstractCrossChainCertificate ptcCrossChainCert
  - SignAlgoEnum sigAlgo
  - byte[] networkInfo
  - CrossChainDomain issuerBcdnsDomainSpace
  - byte[] sig
}
class PTCVerifyAnchor {
  - BigInteger version
  - byte[] anchor
}

PTCTrustRoot "1" *--> "verifyAnchorMap *" PTCVerifyAnchor 

```

PTCVerifyAnchor：

- version：该PTCVerifyAnchor的版本号，版本号必须是递增的，代表先后顺序；
- anchor：序列化的PTC验证信息，Committee PTC可以参考[🔗](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/types/trustroot/CommitteeVerifyAnchor.java)；

PTCTrustRoot：

- verifyAnchorMap：上文所提到的验证锚映射，key为版本号，value为VerifyAnchor；
- ptcCrossChainCert：PTC的跨链身份证书📄；
- sigAlgo：签名算法，参考枚举类型`SignAlgoEnum`；
- networkInfo：PTC的网络信息🛜，不同类型PTC不同，比如Committee PTC参考[🔗](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/types/network/CommitteeNetworkInfo.java)；
- issuerBcdnsDomainSpace：签发PTC证书的BCDNS域名空间，比如跟域名空间为空字符串；
- sig：PTC证书的持有者的签名，使用的上面字段sigAlgo的算法；

#### 区块链信任锚（Blockchain Trust Anchor, BTA）

```mermaid
classDiagram
direction BT
class AbstractBlockchainTrustAnchor {
  - byte[] amId
  - SignAlgoEnum bcOwnerSigAlgo
  - BigInteger initHeight
  - byte[] initBlockHash
  - byte[] bcOwnerSig
  - int subjectVersion
  - ObjectIdentity ptcOid
  - String subjectProduct
  - byte[] extension
  - byte[] subjectIdentity
  - byte[] bcOwnerPublicKey
  - CrossChainDomain domain
}
class BlockchainTrustAnchorV1 {
  - int version = 1
}
class IBlockchainTrustAnchor {
<<Interface>>

}
class SignAlgoEnum {
<<enumeration>>
  +  SM3_WITH_SM2
  +  ED25519
  +  KECCAK256_WITH_SECP256K1
  +  SHA256_WITH_RSA
  +  SHA256_WITH_ECDSA
}

AbstractBlockchainTrustAnchor  ..>  IBlockchainTrustAnchor 
AbstractBlockchainTrustAnchor "1" *--> "bcOwnerSigAlgo 1" SignAlgoEnum 
BlockchainTrustAnchorV1  -->  AbstractBlockchainTrustAnchor 

```

BTA是区块链接入AntChain Bridge的信任基础信息，其核心作用就是绑定域名和实际的区块链网络，通常包含区块链的一种快照，比如某个高度的共识信息，可以通过这种快照验证区块链数据的合法性、存在性，确认跨链消息是从域名绑定的区块链发出的，BTA是通过域名跨链证书持有者的签名和在`subjectIdentity`等字段填入区块链快照的方式来实现上面功能的。

- amId：一般放入AM合约地址，通常会用来验证跨链消息的来源，比如AM合约的事件；
- bcOwnerSigAlgo：签名算法，参考枚举类型`SignAlgoEnum`；
- initHeight：初始区块高度，从该高度开始支持AntChain Bridge跨链；
- initBlockHash：初始区块hash，从该区块hash开始支持AntChain Bridge跨链，高度和hash会用于PTC出具TpBTA和锚定共识状态验证；
- bcOwnerSig：区块链域名证书持有者的私钥对整个BTA的签名；
- subjectVersion：当前BTA数据的版本，将支持BTA更新，每次更新版本号要求大于之前版本，初始为0；
- ptcOid：要求为BTA对应链进行背书的PTC ObjectIdentity，唯一对应一个PTC服务，表明区块链域名Owner是认可PTC服务的；
- subjectProduct：BTA对应链的类型，需要和使用的HCDVS插件类型对应；
- extension：拓展信息，通常包含一些需要携带给PTC的配置信息，比如希望TpBTA背书的跨链通道、Committee的背书策略等；
- subjectIdentity：区块链的快照主体内容，这需要和HCDVS逻辑相对应，HCDVS会使用这个字段的内容来作为后续验证共识状态和跨链消息的锚定信息:anchor:；
- bcOwnerPublicKey：区块链域名持有者的X509公钥；
- domain：该BTA将要绑定的域名，每个域名仅能绑定一个BTA；

#### 第三方区块链信任锚（Third-Party Blockchain Trust Anchor, TpBTA）

```mermaid
classDiagram
direction BT
class ThirdPartyBlockchainTrustAnchor {
  - BigInteger ptcVerifyAnchorVersion
  - int version
  - CrossChainLane crossChainLane
  - byte[] endorseProof
  - int btaSubjectVersion
  - int tpbtaVersion
  - PTCCredentialSubject signerPtcCredentialSubject
  - HashAlgoEnum ucpMessageHashAlgo
  - byte[] endorseRoot
}
class ThirdPartyBlockchainTrustAnchorV1 {
  + int MY_VERSION = 1
}

ThirdPartyBlockchainTrustAnchorV1  -->  ThirdPartyBlockchainTrustAnchor 

```

TpBTA是PTC在完成BTA验证之后，对某个跨链通道出具的背书证明，TpBTA中包含了用于验证PTC对跨链数据证明的所必需的锚定数据，比如Committee PTC的公钥集合、背书策略等内容，接收链系统合约想要验证跨链证明，则必须先获得对应的TpBTA。

每个TpBTA是针对发送链为起点的跨链通道来进行背书的，每一条跨链SDP消息，都会必须匹配到至少一个TpBTA才可以被验证、背书和发出，同时这也意味着该跨链通道的接收链会接收到该PTC的证明，使用该TpBTA可以验证该证明，按照TpBTA的跨链通道，对于发送链的所有跨链通道可以形成一定的偏序关系，这里对TpBTA做出分类：

1. 一元组（Blockchain Level）：sender domain

   TpBTA的跨链通道仅包含发送链域名，覆盖范围最广，所有的该链发出的消息都可以匹配该类型。

2. 二元组（Channel Level）：sender domain, receiver domain

   TpBTA的跨链通道包含发送域名、接收域名，即所有发送、接收链之间的消息都匹配到该类型。

3. 四元组（Lane Level）：sender domain, sender id, receiver domain, receiver id

   TpBTA的跨链通道包含发送域名、发送合约ID、接收域名、接收合约ID，即所有发送合约、接收合约之间的消息都匹配到该类型，该类型范围最小。

因此形成偏序关系：*Blockchain Level >= Channel Level >= Lane Level*。

这里规定：

:1st_place_medal: **TpBTA必须是三种类型中的一个，不支持其他跨链通道；**

:2nd_place_medal: **所有有效的TpBTA之间不允许存在交集，当有交集时，优先选择最大的TpBTA执行跨链验证和背书；**

下面介绍字段：

- ptcVerifyAnchorVersion：PTC信任根中VerifyAnchor的锚定版本号，表示TpBTA是由该版本的VerifyAnchor来提供背书的；
- version：TpBTA数据结构的版本，当前使用版本1；
- crossChainLane：当前TpBTA背书的跨链通道；

- endorseProof：PTC对TpBTA的证明，不同类型PTC结构不同，比如Committee PTC证明参考[🔗](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/types/basic/CommitteeEndorseProof.java)；
- btaSubjectVersion：当前TpBTA背书的BTA的版本号；
- tpbtaVersion：当前TpBTA的版本号，顺序递增，用于未来支持TpBTA更新；
- signerPtcCredentialSubject：PTC证书中，身份持有者的信息，参考[🔗](#主体结构介绍)；
- ucpMessageHashAlgo：用于对UCP消息计算hash值的算法，参考[🔗](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/utils/crypto/HashAlgoEnum.java)；
- endorseRoot：PTC为该跨链通道背书的信任根，可以用于验证第三方证明，比如Committee PTC的*[CommitteeEndorseRoot](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/types/tpbta/CommitteeEndorseRoot.java)*；

#### 共识状态（Consensus State）

区块链的账本一致性依赖于共识状态，区块链的状态往往是线性或者存在最高置信度的，通常用高度来表示状态的版本，AntChain Bridge使用【共识状态】来表示某高度下的区块链状态，比如蚂蚁链的区块头等数据。

```mermaid
classDiagram
direction BT
class ConsensusState {
  - byte[] consensusNodeInfo
  - long stateTimestamp
  - byte[] hash
  - BigInteger height
  - byte[] endorsements
  - byte[] parentHash
  - byte[] stateData
  - CrossChainDomain domain
}
class ValidatedConsensusState {
  - int tpbtaVersion
  - short vcsVersion
  - PTCTypeEnum ptcType
  - ObjectIdentity ptcOid
  - byte[] ptcProof
}
class ValidatedConsensusStateV1 {
  + short MY_VERSION = 1
}

ValidatedConsensusState  -->  ConsensusState 
ValidatedConsensusStateV1  -->  ValidatedConsensusState 

```

ConsensusState是共识状态的模型实现，在BBC接口中，就有readConsensusState这样的接口，要求插件从异构链的区块映射、组装一个ConsensusState对象并返回，ValidatedConsensusState则是经过PTC验证之后返回的已验证共识状态，简单来讲是经过PTC签名的，HCVDS将收到h高度的ConsensusState，并用h-1高度的ValidatedConsensusState来验证它，并产生h高度的ValidatedConsensusState。

字段介绍：

ConsensusState

- consensusNodeInfo：在当前高度的共识信息，在BBC插件里面放入，在HCDVS的验证共识状态接口使用，通常是存放共识节点公钥集合等内容；
- stateTimestamp：当前高度区块的时间戳，单位为毫秒；
- hash：区块hash值；
- height：区块块高；
- endorsements：区块链共识系统对该区块的证明，比如共识节点签名集合；
- parentHash：当前区块的父区块hash；
- stateData：共识状态的序列化数据，往往是区块头数据、共识切换证明等内容；
- domain：该区块所在区块链的域名，BBC插件内部往往不需要填入该字段；

ValidatedConsensusState

- tpbtaVersion：PTC为该共识状态背书所使用的TpBTA；
- vcsVersion：当前ValidatedConsensusState结构的版本，当前为1；
- ptcType：背书的PTC的类型，比如COMMITTEE；
- ptcOid：背书的PTC的ObjectIdentity，参考上面对于PTC证书的介绍；
- ptcProof：PTC对ValidatedConsensusState的背书证明，不同的PTC类型不同；

#### 第三方证明（Third-Party Proof, TpProof）

TpProof是PTC为跨链消息开出的证明，保证了这笔跨链消息的确实是从发送链域名对应的AM合约发出的，并对此进行了签名✍️，TpProof可以通过TpBTA中的背书信任根（endorseRoot）来验证👀。

```mermaid
classDiagram
direction BT
class ThirdPartyProof {
  - CrossChainLane tpbtaCrossChainLane
  - byte[] rawProof
  - int tpbtaVersion
  - ThirdPartyResp resp
}
class ThirdPartyResp {
  - byte[] body
}

ThirdPartyProof "1" *--> "resp 1" ThirdPartyResp 
ThirdPartyProof  ..>  ThirdPartyResp : «create»

```

- tpbtaCrossChainLane：用于提供证明的背书信任根的TpBTA的跨链通道；
- tpbtaVersion：用于提供证明的背书信任根的TpBTA的版本号，它和上面👆tpbtaCrossChainLane唯一确定一个TpBTA；
- rawProof：PTC对消息给出的证明，Committee PTC可以参考[🔗](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/types/basic/CommitteeEndorseProof.java)；
- resp：ThirdPartyResp 包含了跨链消息，即类[CrossChainMessage](antchain-bridge-commons/src/main/java/com/alipay/antchain/bridge/commons/core/base/CrossChainMessage.java)，序列化后放在字段body；

### Committee PTC

#### 配置

在SDK的这里提供了Committee PTC的[客户端](ptc-services/committee-ptc-core/src/main/java/com/alipay/antchain/bridge/ptc/committee/CommitteePTCService.java)，通过简单的配置即可和Committee PTC建立连接。

配置如下：

- heartbeat_interval：对每个Committee节点进行心跳，探测其是否在线，默认为5000ms；
- network：committee的网络信息
  - committee_id：要连接的committee的ID，每个committee有唯一ID；
  - nodes：各个节点的具体网络信息
    - node_id：该节点的ID，在当前committee中是唯一的；
    - endpoint：该节点的网络地址，格式为【协议头】【IP】【端口】，目前协议头仅支持grpcs://，即GRpc over TLS；
    - tls_cert：该节点服务端的TLS证书；
- ptc_certificate：Committee PTC的PEM格式的跨链证书；
- request_threads_pool_core_size：grpc客户端线程池持续维护的线程数目，默认为4；
- request_threads_pool_max_size：grpc客户端线程池可用的最大线程数目，默认为4；
- tls_client_pem_cert：TLS客户端证书，用来访问各个Committee节点，如果Committee要求TLS双向验证，那么需要将该TLS证书发送给Committee运营方，除此之外，默认为空即可；
- tls_client_pem_pkcs8_key：TLS客户端私钥，用来访问各个Committee节点，建立安全链接，如果👆证书为空，则私钥也为空；

```
{
    "heartbeat_interval": 5000,
    "network": {
      "committee_id": "default",
      "nodes": [
        {
          "endpoint": "grpcs://172.16.0.50:10080",
          "node_id": "node1",
          "tls_cert": "-----BEGIN CERTIFICATE-----\nMIIDoTCCAo...Sgx6o9JYouD2E\n-----END CERTIFICATE-----\n"
        },
        {
          "endpoint": "grpcs://172.16.0.50:10180",
          "node_id": "node2",
          "tls_cert": "-----BEGIN CERTIFICATE-----\nMIIDoTCC...gx6o9JYouD2E\n-----END CERTIFICATE-----\n"
        },
        {
          "endpoint": "grpcs://172.16.0.50:10280",
          "node_id": "node3",
          "tls_cert": "-----BEGIN CERTIFICATE-----\nMIIDoT...YouD2E\n-----END CERTIFICATE-----\n"
        },
        {
          "endpoint": "grpcs://172.16.0.50:10380",
          "node_id": "node4",
          "tls_cert": "-----BEGIN CERTIFICATE-----\nMIIDo...JYouD2E\n-----END CERTIFICATE-----\n"
        }
      ]
    },
    "ptc_certificate": "-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\nAADDAQAAAA...ShZHbAQ==\n-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n",
    "request_threads_pool_core_size": 4,
    "request_threads_pool_max_size": 4,
    "tls_client_pem_cert": "-----BEGIN CERTIFICATE-----\nMIIDmzCCA...OLeDWS7jk\n-----END CERTIFICATE-----\n",
    "tls_client_pem_pkcs8_key": "-----BEGIN PRIVATE KEY-----\nMIIEvA...q/TBAg6w==\n-----END PRIVATE KEY-----\n"
  }
```

#### 使用

像下面一样，初始化PTCService对象即可，rawConfig即为上面Json配置的bytes内容。

```java
CommitteePTCService ptcService = new CommitteePTCService();
ptcService.startup(rawConfig);
```



# 社区治理

AntChain Bridge 欢迎您以任何形式参与社区建设。

您可以通过以下方式参与社区讨论

- 钉钉

![scan dingding2024](https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/dingding2024.png?x-oss-process=image/resize,w_400/quality,Q_100)

- 邮件

发送邮件到`antchainbridge@service.alipay.com`

# License

详情参考[LICENSE](LICENSE)。

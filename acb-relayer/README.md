<div align="center">
  <img alt="am logo" src="https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/antchain.png" width="250" >
  <h1 align="center">AntChain Bridge Relayer</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
    <a href="https://www.java.com">
      <img alt="Language" src="https://img.shields.io/badge/Language-Java-blue.svg?style=flat">
    </a>
    <a href="https://github.com/AntChainOpenLab/AntChainBridgeRelayer/graphs/contributors">
      <img alt="GitHub contributors" src="https://img.shields.io/github/contributors/AntChainOpenLab/AntChainBridgeRelayer">
    </a>
    <a href="https://www.apache.org/licenses/LICENSE-2.0">
      <img alt="License" src="https://img.shields.io/github/license/AntChainOpenLab/AntChainBridgeRelayer?style=flat">
    </a>
  </p>
</div>

## 介绍

蚂蚁链跨链桥中继（*AntChain Bridge Relayer*）是蚂蚁链跨链开源项目的重要组件，负责连接区块链、区块链域名服务（*BCDNS*）和证明转化组件（*PTC*），完成可信信息的流转与证明，实现区块链互操作。

*AntChain Bridge Relayer*是从蚂蚁链跨链产品[ODATS](https://antdigital.com/products/odats)中开源出来的组件，并按照[IEEE 3205](https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/ieee/p3205/IEEE_3205-2023_Final.pdf)对跨链逻辑进行了升级，目前开源版本已经支持协议中通信相关的基本流程，包括统一跨链数据包（*Unified Crosschain Packet, UCP*）、可认证消息（*Authentic Message, AM*）、智能合约数据报（*Smartcontract Datagram Protocol, SDP*）等消息的处理，以及基于BCDNS的实现了区块链身份管理流程和区块链之间的消息寻址功能，目前支持中国信息通信研究院基于星火链开发的BCDNS服务。

*AntChain Bridge Relayer*将功能实现分为两部分，分别为通信和可信，目前*AntChain Bridge Relayer*已经实现区块链合约之间的通信功能，支持接入*Committee PTC*为跨链提供验证与背书能力，提供灵活可靠的区块链互操作能力。



## 快速开始

**在开始之前，请您确保安装了maven和JDK，这里推荐使用[openjdk-1.8](https://adoptium.net/zh-CN/temurin/releases/?version=8)版本*

**确保安装了AntChain Bridge Plugin SDK，详情请[见](https://github.com/AntChainOpenLabs/AntChainBridge/blob/main/acb-sdk/README.md)*

> [!IMPORTANT]  
>
> 注意当前Relayer要求SDK版本为1.0.0-SNAPSHOT

### 编译

在项目根目录运行maven命令即可：

```
mvn package -Dmaven.test.skip=true
```

在`r-bootstrap/target`下面会产生一个压缩包`acb-relayer-x.y.z.tar.gz`，将该压缩包解压到运行环境即可。

解压之后可以看到以下文件：

```
tree .
.
├── README.md
├── bin
│   ├── acb-relayer.service
│   ├── init_tls_certs.sh
│   ├── print.sh
│   ├── start.sh
│   └── stop.sh
├── config
│   ├── application.yml
│   └── db
│       └── ddl.sql
└── lib
    └── r-bootstrap-0.1.0.jar

4 directories, 9 files
```



### 环境

AntChain Bridge Relayer使用了MySQL和Redis，这里建议使用docker快速安装依赖。

首先通过脚本安装docker，或者在[官网](https://docs.docker.com/get-docker/)下载。

```
wget -qO- https://get.docker.com/ | bash
```

然后下载MySQL镜像并启动容器，注意这里指定了时区为`+08:00`，请修改为您的时区。

```
docker run -itd --name mysql-test -p 3306:3306 -e MYSQL_ROOT_PASSWORD='YOUR_PWD' mysql:8 --mysql-native-password=ON --default_time_zone +08:00
```

然后下载Redis镜像并启动容器：

```
docker run -itd --name redis-test -p 6379:6379 redis --requirepass 'YOUR_PWD' --maxmemory 500MB
```



### 配置

#### 数据库

在开始之前，需要初始化中继的数据库，这里提供一个[DDL](r-bootstrap/src/main/resources/db/ddl.sql)，或者解压之后在路径`config/db/ddl.sql`找到，在MySQL执行即可生成数据库`relayer`。

#### TLS

这里初始化中继的TLS证书，会在`tls_certs`路径下生成`relayer.crt`和`relayer.key`。

```
bin/init_tls_certs.sh 
    ___            __   ______ __            _           ____         _      __
   /   |   ____   / /_ / ____// /_   ____ _ (_)____     / __ ) _____ (_)____/ /____ _ ___
  / /| |  / __ \ / __// /    / __ \ / __ `// // __ \   / __  |/ ___// // __  // __ `// _ \
 / ___ | / / / // /_ / /___ / / / // /_/ // // / / /  / /_/ // /   / // /_/ // /_/ //  __/
/_/  |_|/_/ /_/ \__/ \____//_/ /_/ \__,_//_//_/ /_/  /_____//_/   /_/ \__,_/ \__, / \___/
                                                                            /____/        

[ INFO ]_[ 2023-12-25 20:32:17.170 ] : generate relayer.key successfully
[ INFO ]_[ 2023-12-25 20:32:17.170 ] : generate relayer.crt successfully
```

#### 中间件

然后，找到`config/application.yml`，配置MySQL和Redis信息到配置文件：

```yaml
spring:
  application:
    name: antchain-bridge-relayer
  profiles:
    active: env
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/relayer?serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
    password: YOUR_PWD
    username: root
  redis:
    host: localhost
    port: 6379
    password: YOUR_PWD
```

#### 跨链身份

最后，需要向BCDNS服务申请中继身份证书，用于与BCDNS和其他中继进行交互，这里推荐搭建信通院基于星火链实现的[BCDNS](https://github.com/caict-4iot-dev/BCDNS)服务，目前没有提供测试网服务，需要开发者自行运行该服务。

使用CLI工具（请参考CLI[文档](r-cli/README.md)）为中继生成私钥、公钥。

```
    ___    ______ ____     ____   ______ __     ___ __  __ ______ ____
   /   |  / ____// __ )   / __ \ / ____// /    /   |\ \/ // ____// __ \
  / /| | / /    / __  |  / /_/ // __/  / /    / /| | \  // __/  / /_/ /
 / ___ |/ /___ / /_/ /  / _, _// /___ / /___ / ___ | / // /___ / _, _/
/_/  |_|\____//_____/  /_/ |_|/_____//_____//_/  |_|/_//_____//_/ |_|

                             CLI 0.1.0

relayer:> generate-relayer-account --keyAlgo Ed25519
private key path: /path/to/private_key.pem
public key path: /path/to/public_key.pem
```

然后，生成*证书签名请求（CSR）*，将会得到一个Base64字符串，用于向BCDNS申请中继证书，具体申请操作请参考BCDNS操作文档。

```
relayer:> generate-relayer-csr --oidType BID --pubkeyFile /path/to/public_key.pem
your CSR is
AADDAAAAAAABAAAAMQIAAQAAAAMEAAg...Rgr3E5mUOCsRbrou6AjbBAAAAAAA
```

如果BCDNS返回的是Base64格式的证书，可以使用CLI工具转换成PEM格式，以用于Relayer。

```
relayer:> convert-cross-chain-cert-to-pem --base64Input AAAIAgAAAAABAAAAMQEAK...wWf/zi60DKnQ7xaCA==
-----BEGIN RELAYER CERTIFICATE-----
AAAIAgAAAAABAAAAMQEAKAAAAGRpZDpiaWQ6ZWY5OVJ6OFRpN3g0aTZ6eUNyUHlG
aXk5dXRzV0JKVVcCAAEAAAADAwA7AAAAAAA1AAAAAAABAAAAAQEAKAAAAGRpZDpi
...
4QlxLUp70uRK43ECAAcAAABFZDI1NTE5AwBAAAAAbA8zkKXCI4Iwp6KBERXOqKln
JT/qn36in7+iU6SsNEz0rsJpmEvVRT6adNVY7zS/ni35JwWf/zi60DKnQ7xaCA==
-----END RELAYER CERTIFICATE-----
```

在获得PEM格式的中继证书和密钥之后，将其配置到文件中，这里假设将证书和密钥分别放在`cc_certs/relayer.crt`和`cc_certs/private_key.pem`：

```
relayer:
  network:
    node:
      sig_algo: Ed25519
      crosschain_cert_path: file:cc_certs/relayer.crt
      private_key_path: file:cc_certs/private_key.pem
```

如果仅需要将程序运行起来，或者进行某些测试，可以使用测试用例中提供的[证书](r-bootstrap/src/test/resources/cc_certs/relayer.crt)和[密钥](r-bootstrap/src/test/resources/cc_certs/private_key.pem)，请不要将该证书与密钥用于生产。

#### 配置加密

Relayer支持使用[Jasypt](https://github.com/ulisesbocchio/jasypt-spring-boot)加密配置。

- 找到你的配置文件`/path/to/your/application.yml`，将所有你想要加密的配置改成`DEC(...)`格式的文本。

  ```
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/relayer?serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
      #比如这里
      password: DEC(password)
      username: root
  ```

- 进入项目的r-bootstrap目录，使用maven[插件](https://github.com/ulisesbocchio/jasypt-spring-boot?tab=readme-ov-file#encryption)对配置文件进行加密。

  ```
  mvn jasypt:encrypt -Djasypt.plugin.path="file:/path/to/your/application.yml" -Djasypt.encryptor.password=the_password
  ```

  **忽略执行时的`ClassNotFoundException`。*

- 将得到下面配置文件：

  ```
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/relayer?serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
      #比如这里
      password: ENC(DLDD9/HtY1mBx3ez1f6k9iCTt8VFANfx7n3g7gtweJ1DNI5GgdNUu8SUXYegWLbB)
      username: root
  ```

- 使用加密之后的配置文件启动服务即可。

### 运行

通过运行`bin/start.sh -h`，可以看到运行方式。

- 可以直接运行`start.sh`启动服务进程。

- 可以通过运行`start.sh -s`作为系统服务启动，支持自动重启等功能。

- 如果配置文件进行了加密，则使用`start.sh -P the_password`启动。

```
 start.sh - Start the AntChain Bridge Relayer

 Usage:
   start.sh <params>

 Examples:
  1. start in system service mode：
   start.sh -s
  2. start in application mode:
   start.sh
  3. start with configuration encrypted:
   start.sh -P your_jasypt_password

 Options:
   -s         run in system service mode.
   -P         your jasypt password.
   -h         print help information.
```

成功运行之后，可以在`log/antchain-bridge-relayer`看到日志文件。



## 命令行交互工具（CLI）

AntChain Bridge Relayer提供了一个命令行交互工具，详情请见使用[文档](r-cli/README.md)。



## 进阶操作

### 启动集群

Relayer可以启动多个节点，以实现水平拓展。

启动第一个节点之后，在第二台机器解压程序安装包，并将配置文件、TLS证书密钥、跨链证书密钥拷贝到对应的路径，启动程序即可。

多节点启动之后，可以使用CLI工具查询当前节点状态：

```
relayer:> query-curr-active-nodes
[
	{
		"last_active_time":1703737607000,
		"node_ip":"172.16.0.49",
		"active":true,
		"node_id":"172.16.0.49"
	},
	{
		"last_active_time":1703737607000,
		"node_ip":"172.16.0.50",
		"active":true,
		"node_id":"172.16.0.50"
	}
]
```

节点的ID默认使用机器IP，此外还可以配置为UUID模式，更改配置重启Relayer进程即可：

```
relayer:
  engine:
    node_id_mode: UUID
```

### Relayer交互

不同的Relayer之间可以互相转发跨链消息，具体流程为：

- 本地Relayer收到域名A发往域名B的跨链消息，但是域名B的路由信息在本地Relayer不存在，则需要先挂起该方向的跨链消息；
- 本地Relayer向BCDNS查询该域名的路由信息，找到网络中对接该链的Relayer；
- 本地Relayer发起“握手”流程，建立于网络中的Relayer的可信连接；
- 两个Relayer之间建立域名A和域名B的可信通道；
- 本地Relayer将挂起的跨链消息发送到网络Relayer，并提交到域名B链上。

在可以被发现之前，本地Relayer需要完成：

- 设置本地网络地址，该地址目前支持http和https，后续考虑支持grpcs等方式，比如`https://localhost:8082`，也可以设置多个，用","隔开即可。

  ```
  relayer:> set-local-endpoints --endpoints https://172.16.0.49:8082
  ```

  可以通过`get-local-endpoints`查询当前endpoints信息。

- 对本地启动过Anchor服务的区块链，将其域名注册到BCDNS，使得网络中其他Relayer可以发现并向本地Relayer转发跨链消息。

  ```
  relayer:> register-domain-router --domain domain.web3.net
  ```

  可以通过下面请求获取到BCDNS的路由信息：

  ```
  relayer:> query-domain-router --domain domain.web3.net
  {"destDomain":{"domain":"domain.web3.net","domainSpace":false},"destRelayer":{"netAddressList":["https://localhost:8082"],"relayerCert":{"credentialSubject":"AADVAAAAAAAD...hNDRjY2UifV19","credentialSubjectInstance":{"applicant":{"rawId":"ZGlkOmJpZDplZmJ...1b1FHWDZMVUd3Zw==","type":"BID"},"name":"relay","rawSubjectPublicKey":"r2Ze5VBjX...yWnSkTM4=","subject":"eyJwdWJsaWNLZXkiO...jZSJ9XX0=","subjectInfo":"eyJwd...J9XX0=","subjectPublicKey":{"algorithm":"Ed25519","encoded":"MCowBQYDK2V...qJKDyWnSkTM4=","format":"X.509","pointEncoding":"r2Ze5V...ifV19","expirationDate":1733811853,"id":"did:bid:efGeAv4Jr7V2FSyun77m4xTFmTDfG8nh","issuanceDate":1702275853,"issuer":{"rawId":"ZGlkOmJpZDpl...NTdtRENwQw==","type":"BID"},"proof":{"certHash":"Gaw4gcwXzn2i...K6HaPWBxXM=","hashAlgo":"SM3","rawProof":"kMZ/tvT19Tk...TQ4IVYlXkYjSBw==","sigAlgo":"Ed25519"},"type":"RELAYER_CERTIFICATE","version":"1"},"relayerCertId":"did:bid:efGeAv4Jr7V2FSyun77m4xTFmTDfG8nh"}}
  ```


### 启动Embedded BCDNS

> [!IMPORTANT]  
> Relayer从0.3.0版本开始支持启动内嵌的BCDNS服务，注意Relayer使用的Embedded BCDNS相关依赖的版本。

Embedded BCDNS是内嵌在服务内部的BCDNS，提供中心化的权威服务，会使用一把私钥为跨链网络提供认证、准入等功能，按照服务端要求可以通过简单配置接入BCDNS，具体内容可以参考[这里](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/tree/main/bcdns-services/embedded-bcdns/README.md)。

通过在中继的配置增加下面一项，重启即可启动Embedded BCDNS，详细的配置可以参考AntChain Bridge SDK关于如何使用Embedded的[README](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK/tree/main/bcdns-services/embedded-bcdns/README.md)。

```yaml
acb:
  bcdns:
    embedded:
      server-on: true
      root-private-key-file: file:/path/to/embedded-bcdns-root-private-key.key
      root-cert-file: file:/path/to/embedded-bcdns-root.crt
```

上面配置中的`root-private-key-file`和`root-cert-file`，可以通过CLI命令`generate-bcdns-root-cert`来生成，详细用法参考[这里](r-cli/README.md#55-生成BCDNS根证书)。

需要在DB额外创建一些Embedded BCDNS的表，通过这里的SQL[脚本](https://github.com/AntChainOpenLabs/AntChainBridge/blob/main/acb-sdk/bcdns-services/embedded-bcdns/embedded-bcdns-state-jdbc-spring-boot-starter/src/main/resources/ddl/mysql/ddl.sql)

重新启动Relayer，启动日志中会看到：

```
INFO 63164 --- [           main] .EmbeddedBcdnsJdbcStateAutoConfiguration : start jdbc bcdns state
INFO 63164 --- [           main] b.b.e.s.a.EmbeddedBcdnsAutoConfiguration : start embedded bcdns server on 0.0.0.0:8090
```



## 社区治理

AntChain Bridge 欢迎您以任何形式参与社区建设。

您可以通过以下方式参与社区讨论

- 钉钉

![scan dingding](https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/dingding2024.png?x-oss-process=image/resize,w_400/quality,Q_100)

- 邮件

发送邮件到`antchainbridge@service.alipay.com`

## License

详情参考[LICENSE](./LICENSE)。

<div align="center">
  <img alt="am logo" src="https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/antchain.png" width="250" >
  <h1 align="center">AntChain Bridge Committee PTC</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
    <a href="https://www.java.com">
      <img alt="Language" src="https://img.shields.io/badge/Language-Java-blue.svg?style=flat">
    </a>
    <a href="https://github.com/AntChainOpenLab/AntChainBridgeRelayer/graphs/contributors">
      <img alt="GitHub contributors" src="https://img.shields.io/github/contributors/AntChainOpenLab/AntChainBridgeCommitteePtc">
    </a>
    <a href="https://www.apache.org/licenses/LICENSE-2.0">
      <img alt="License" src="https://img.shields.io/github/license/AntChainOpenLab/AntChainBridgeCommitteePtc?style=flat">
    </a>
  </p>
</div>

## 💡介绍

在AntChain Bridge的设计中，证明转化组件（*Proof Transform Component, PTC*）负责提供可信的跨链数据验证和背书功能，PTC作为一种角色和抽象，目前有多种实现类型，委员会PTC（*Committee PTC*）就是其中一种实现。

顾名思义，在跨链中Committee PTC就是通过委员会的形式，每个成员备有自己的私钥，对跨链数据进行验证，然后使用私钥对跨链数据进行签名，然后返回其背书结果，客户端需要收集这些签名，最终作为跨链数据的第三方证明（*Third Party Proof, TpProof*），用于后续在接收链提交跨链消息时进行验证。

Committee PTC的共分为两部分：

- node：执行验证和背书的节点，作为服务端响应客户端请求，通过加载异构链数据验证服务（*Hetero-Chain Data Verification Service, HCDVS*）来完成对不同异构链数据的验证过程，比如验证共识状态（一般是对区块头的抽象）、跨链消息证明（往往是帐本数据的Merkle Proof）等的验证，验证之后，committee node会对数据进行数字签名并返回，committee node的服务地址将被公开在BCDNS中。
- supervisor：用于管理Committee作为PTC的身份信息，持有PTC跨链证书对应的私钥，负责更新BCDNS上的PTC信任根，包含committee nodes的公钥和网络信息，便于AntChain Bridge中的其他参与者可以轻松获取并验证Committee出具的证明。

## 🔜快速开始

### 环境

Committee Node使用了MySQL，这里建议使用docker快速安装依赖。

首先通过脚本安装docker，或者在[官网](https://docs.docker.com/get-docker/)下载。

```
wget -qO- https://get.docker.com/ | bash
```

然后下载MySQL镜像并启动容器，注意这里指定了时区为`+08:00`，请修改为您的时区。

```
docker run -itd --name mysql-test -p 3306:3306 -e MYSQL_ROOT_PASSWORD='YOUR_PWD' mysql:8 --mysql-native-password=ON --default_time_zone +08:00
```

### 编译

- Committee PTC要求Java21，请提前[下载](https://adoptium.net/temurin/releases/)并安装；
- 下载并安装maven；
- 提前安装[AntChain Bridge SDK](https://github.com/AntChainOpenLabs/AntChainBridgePluginSDK)，当前Committee 使用了`1.0.0-SNAPSHOT`版本；

运行下面命令以编译：

```
mvn clean package -DskipTests 
```

Committee Node的制品在`node/target/committee-node-0.1.0-SNAPSHOT.tar.gz`可以找到，Supervisor的制品可以在`supervisor/target/supervisor-cli-bin.tar.gz`找到。

### 安装

将Committee Node压缩包拷贝到安装目录，解压之后可见文件夹`committee-node`，目录bin下面是一些脚本，lib下是程序jar包，config下放置了配置文件，node-cli-bin.tar.gz包含了一个CLI工具，用于配置服务：

```
╰─± tree . -L 2
.
├── README.md
├── bin
│   ├── committee-node.service
│   ├── init_tls_certs.sh
│   ├── print.sh
│   ├── start.sh
│   └── stop.sh
├── config
│   └── application.yml
│   └── ddl.sql
├── lib
│   └── committee-node-0.1.0-SNAPSHOT.jar
└── node-cli-bin.tar.gz

3 directories, 9 files
```

类似的，将Supervisor的压缩包解压到安装目录，可以看到`supervisor-cli`，目录结构和Committee Node类似。

```
╰─± tree . -L 2
.
├── README.md
├── bin
│   └── start.sh
├── conf
│   └── config.json
└── lib
    └── supervisor-cli.jar

3 directories, 4 files
```

### 启动服务

> [!IMPORTANT]  
> 请确保你的执行环境默认使用JDK21，可用java -version检查🧐

**第一步**运行脚本`bin/init_tls_certs.sh`，会在`tls_certs`目录下生成TLS证书：

```
╰─± tree tls_certs 
tls_certs
├── server.crt
├── server.key
└── trust.crt
```

然后解压node-cli-bin.tar.gz，运行：

```
# cd node-cli && bin/start.sh
  _   _   ___   ____   _____
 | \ | | / _ \ |  _ \ | ____|
 |  \| || | | || | | ||  _|
 | |\  || |_| || |_| || |___
 |_| \_| \___/ |____/ |_____|

        CLI 0.1.0-SNAPSHOT

node:>
```



**第二步**，输入命令生成Node的密钥，`--outDir`制定密钥存储路径，生成之后，可以在该路径看到，` private_key.pem、public_key.pem`两个文件：

```
generate-node-account --outDir /path/to/committee-node1/committee-node/
```



**第三步**，在Supervisor 解压的目录`supervisor-cli`下面，运行bin/start.sh，启动Supervisor CLI：

```
bin/start.sh
   _____ __  ______  __________ _    ___________ ____  ____
  / ___// / / / __ \/ ____/ __ \ |  / /  _/ ___// __ \/ __ \
  \__ \/ / / / /_/ / __/ / /_/ / | / // / \__ \/ / / / /_/ /
 ___/ / /_/ / ____/ /___/ _, _/| |/ // / ___/ / /_/ / _, _/
/____/\____/_/   /_____/_/ |_| |___/___//____/\____/_/ |_|


                             CLI 0.1.0-SNAPSHOT

supervisor:>
```

首先为PTC证书生成私钥、公钥，committeeId是当前这个委员会的唯一ID，outDir指定了私钥公钥存储的路径：

```
generate-ptc-account --committeeId default --outDir /path/to/supervisor-cli/
```

然后，运行下面命令，生成证书签署（CSR）请求的Base64内容：

```
generate-ptc-csr  --pubkeyFile /path/to/supervisor-cli/public_key.pem
your CSR is
AADGAAAAAAABAAAAMQIAAQAAAAIEAAgAAAAAAAAAAAAAAAUACAAAAAAAAAAAAAAABgCWAAAAAACQAAAAAAABAAAAMQEABQAAAG15cHRjAgABAAAAAQMAawAAAAAAZQAAAAAAAQAAAAABAFgAAAAwVjAQBgcqhkjOPQIBBgUrgQQACgNCAATZOh906PXjpuaffXOFQ//9wn5Y5WPDz738pPzFy/pOk/KuEt/gw5VeupcZS2NPrBBzSKm1DUyDR9ZvJthwJbNnBAAAAAAA
```

通过BCDNS申请PTC证书，这里需要星火链BCDNS线下签署PTC证书，请将上面👆的Base64发送给BIF BCDNS运维人员，详情请跳转BIF BCDNS[文档](https://github.com/caict-4iot-dev/BCDNS?tab=readme-ov-file#%E7%A4%BA%E4%BE%8B)📄。如果BCDNS返回的是Base64格式的证书，可以使用CLI工具转换成PEM格式。

```
supervisor:> convert-cross-chain-cert-to-pem --base64Input AAAIAgAAAAABAAAAMQEAK...wWf/zi60DKnQ7xaCA==
-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----
AAAIAgAAAAABAAAAMQEAKAAAAGRpZDpiaWQ6ZWY5OVJ6OFRpN3g0aTZ6eUNyUHlG
aXk5dXRzV0JKVVcCAAEAAAADAwA7AAAAAAA1AAAAAAABAAAAAQEAKAAAAGRpZDpi
...
4QlxLUp70uRK43ECAAcAAABFZDI1NTE5AwBAAAAAbA8zkKXCI4Iwp6KBERXOqKln
JT/qn36in7+iU6SsNEz0rsJpmEvVRT6adNVY7zS/ni35JwWf/zi60DKnQ7xaCA==
-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----
```

把上面的PEM格式PTC跨链证书保存到文件，打开Supervisor CLI配置文件./conf/config.json，找到ptc_certificate字段，把PTC证书文件的路径填到这个字段，要求重启Supervisor CLI。

**第四步**，准备BIF BCDNS的客户端配置文件。

Supervisor需要访问BIF BCDNS，因此要注册BCDNS服务。目前支持星火链网的BCDNS服务客户端，这里介绍其配置项和如何实例化该客户端。首先介绍配置，代码可[见](file:///Users/zouxyan/IdeaProjects/odats-plugins/antchain-bridge-bcdns/src/main/java/com/alipay/antchain/bridge/bcdns/impl/bif/conf/BifBCNDSConfig.java)，主要分为两部分，一部分`certificationServiceConfig`是用于和颁证服务通信、鉴权，另一部分`chainConfig`用于和星火链网交互。

```json
{
    "certificationServiceConfig": {
      "authorizedKeyPem": "-----BEGIN EC PRIVATE KEY-----\nMHQCAQE...==\n-----END EC PRIVATE KEY-----",
      "authorizedPublicKeyPem": "-----BEGIN PUBLIC KEY-----\nMFYwEAYH...WzZw==\n-----END PUBLIC KEY-----",
      "authorizedSigAlgo": "Keccak256WithSecp256k1",
      "clientCrossChainCertPem": "-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\nAADD...HbAQ==\n-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----",
      "clientPrivateKeyPem": "-----BEGIN EC PRIVATE KEY-----\nMHQCAQE...==\n-----END EC PRIVATE KEY-----",
      "sigAlgo": "Keccak256WithSecp256k1",
      "url": "http://ip:8112"
    },
    "chainConfig": {
      "bifAddress": "",
      "bifChainRpcUrl": "http://test.bifcore.bitfactory.cn",
      "bifPrivateKey": "",
      "certificatesGovernContract": "",
      "domainGovernContract": "",
      "ptcGovernContract": "",
      "relayerGovernContract": "",
      "ptcTrustRootGovernContract": "did:bid:ef93ANo97CLU9kMm6KQxX9sU5Gdvcbjr",
      "tpBtaGovernContract": ""
    }
  }
```

下面对各个配置项给出解释：

首先是颁证服务的配置：

- authorizedKeyPem：有权限申请跨链身份（Relayer、PTC）的私钥，填入PEM格式的PKCS#8的私钥，这里可以使用CLI刚刚生成的私钥private_key.pem。

  > [!TIP]
  > 可以这样将私钥打印为一行：cat private_key.pem | tr '\n' '|' | sed 's/|/\\n/g'

- authorizedPublicKeyPem：`authorizedKeyPem`对应的公钥，填入PEM格式的PKCS#8的公钥，这里可以使用CLI刚刚生成的公钥public_key.pem。

- authorizedSigAlgo：`authorizedKeyPem`私钥的签名算法，目前支持Keccak256WithSecp256k1、Ed25519、SM3withSM2、SHA256withECDSA、SHA256withRSA五种算法，这里可以使用刚刚生成私钥的算法：Keccak256WithSecp256k1；

- clientCrossChainCertPem：PTC的跨链证书，需要提前从BCDNS处获取，这里使用刚才从BIF BCDNS获取到的PTC证书。

- clientPrivateKeyPem：Relayer跨链证书持有者的私钥，填入PEM格式的PKCS#8的私钥，这里可以使用CLI刚刚生成的私钥private_key.pem。

- sigAlgo：`clientPrivateKeyPem`私钥的签名算法，目前支持Keccak256WithSecp256k1、Ed25519、SM3withSM2、SHA256withECDSA、SHA256withRSA五种算法，这里可以使用刚刚生成私钥的算法：Keccak256WithSecp256k1；

- url：颁证服务的URL。

然后是[星火链网](https://bif-doc.readthedocs.io/zh-cn/2.0.0/quickstart/快速接入星火链.html)的配置，由于Supervisor不需要一些配置信息，所以上面的例子没有包含部分配置：

- bifAddress：星火链网的账户地址，这里需要使用Relayer的公钥来生成该地址，可以参考[代码](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/blob/develop/r-cli/src/main/java/com/alipay/antchain/bridge/relayer/cli/command/UtilsCommands.java#L196)。Supervisor不需要。
- bifChainRpcUrl：星火链网节点的RPC地址。Supervisor需要！⚠️
- bifPrivateKey：星火链网账户的私钥，这里需要使用Relayer的私钥`clientPrivateKeyPem`来生成该地址，可以参考[代码](https://github.com/AntChainOpenLabs/AntChainBridgeRelayer/blob/6658dfa599b73b1aa4f3cf156e1fc1d72c5cb7c6/r-cli/src/main/java/com/alipay/antchain/bridge/relayer/cli/command/UtilsCommands.java#L203C20-L203C42)。Supervisor不需要。
- certificatesGovernContract：跨链证书管理合约，部署在星火链网之上，这些合约应该从BIF BCDNS运维人员处获取！📢Supervisor不需要。
- domainGovernContract：域名管理合约，参考[星火链网BCDNS](https://github.com/caict-4iot-dev/BCDNS)，依赖的星火链网BCDNS应当有唯一一本域名管理合约。Supervisor不需要。
- ptcGovernContract：PTC身份管理合约，依赖的星火链网BCDNS应当有唯一一本PTC身份管理合约。Supervisor不需要。
- relayerGovernContract：Relayer身份管理合约，依赖的星火链网BCDNS应当有唯一一本Relayer身份管理合约。Supervisor不需要。
- ptcTrustRootGovernContract：PTC信任根管理合约，*下面将使用Supervisor向BCDNS注册信任根*💪。
- tpBtaGovernContract：TpBTA管理合约，TpBTA是PTC对于特定跨链通道的背书验证根，包含具体背书公钥信息和验证规则。Supervisor不需要。

准备完毕后，保存到文件，比如bcdns.json备用。

在Supervisor CLI运行，启动BIF BCDNS客户端。

```
start-bcdns-client --bcdnsType BIF --bcdnsClientConfigPath ./bcdns.json
```



**第五步**，构造PTC Trust Root，并注册到BCDNS。

假设准备部署四个节点，那么第一、二步应该初始化了四份密钥。

准备四份注册文件，比如下面一份。endpoint_url是一号Committee Node的服务端URL，这里默认使用grpc over tls；keys放入一号节点签名对应的公钥，default为公钥的名字，这里会和Committee Node自己的配置对应，所以不要乱填🙅；node_id是该节点在委员会的ID，这里会和Committee Node自己的配置对应，所以不要乱填🙅；tls_cert是第一步生成的TLS证书，填入即可。将准备好的一号节点配置文件存为“node1.json”。

```
{
    "endpoint_url": "grpcs://172.16.0.1:10080",
    "keys": {
      "default": "-----BEGIN PUBLIC KEY-----\nMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEdCE/9G1o0QT7fhB8fa/6UIuAMBCfACna\nNHDS+nD3jvqKHI1d2H1d4+pFzYLMqqMDyZdPy0FYbXcc16BrLWB/sQ==\n-----END PUBLIC KEY-----\n"
    },
    "node_id": "node1",
    "tls_cert": "-----BEGIN CERTIFICATE-----\nMIIDoTC...6o9JYouD2E\n-----END CERTIFICATE-----\n"
  }
```

依次准备好node1.json、node2.json、node3.json、node4.json。

然后启动Supervisor CLI，运行下面👇命令，注意committeeNodeIds对应配置文件的名字，committeeNodesInfoDir为存放这些配置文件的路径，这里要求node1.json、node2.json、node3.json、node4.json都放在这里路径下，运行后得到一个Base64：

```
generate-ptc-trust-root --committeeNodeIds node1,node2,node3,node4 --committeeNodesInfoDir /path/to/config-files
```

拷贝这个Base64，输入下面命令，将Base64作为ptcTrustRootStr的参数粘贴上去，然后运行即可，这里会向BIF BCDNS发请求注册：

```
add-ptc-trust-root --ptcTrustRootBase64 Qkw2ZHIyM...yUWplbE
```



**第六步**，准备Committee Node的配置文件`config/application.yml`，可以看到下面是初始化的配置文件，按需要修改下面配置，其他默认即可：

- spring.datasource：首先配置好数据库的url、密码等，支持Jasypt加密，下文会介绍如何使用；
- committee.node.id：当前运行节点的ID，自定义即可，比如node1、node2等，不可以和其他节点相同；
- committee.node.credential.private-key-file：Node签名的私钥；
- committee.node.credential.cert-file：PTC证书，这需要supervisor先完成向BCDNS的申请证书的操作；
- committee.plugin.repo：HCDVS插件的安装目录；

> [!WARNING]  
> 如果你想在同一台机器上启动多个committee node服务，记得修改两个端口的配置：
> 首先是committee.node.admin.port，以及grpc.server.port，否则启动时会有端口冲突导致启动失败

```yaml
spring:
  application:
    name: committee-ptc
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/committee_node?serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
    password: YOUR_PWD
    username: root
logging:
  file:
    path: ./logs
  level:
    app: INFO
# setting for committee
committee:
  id: default
  node:
    id: node1
    credential:
      sign-algo: "Keccak256WithSecp256k1"
      private-key-file: "file:private_key.pem"
      cert-file: "file:ptc.crt"
  plugin:
    # where to load the hetero-chain plugins
    repo: ./plugins
    policy:
      # limit actions of the plugin classloader
      classloader:
        resource:
          ban-with-prefix:
            # the plugin classloader will not read the resource file starting with the prefix below
            APPLICATION: "META-INF/services/io.grpc."
grpc:
  server:
    port: 10080
    security:
      # enable tls mode
      enabled: true
      # server certificate
      certificate-chain: file:tls_certs/server.crt
      # server key
      private-key: file:tls_certs/server.key
      # Mutual Certificate Authentication
      trustCertCollection: file:tls_certs/trust.crt
      # clientAuth: REQUIRE
```



**第七步**，安装HCDVS插件。

将需要支持的链的插件放到Node安装路径的./plugins下面，也就是配置项`committee.plugin.repo指定的目录。比如这里安装了蚂蚁链和星火链网的验证插件：

```
tree plugins/
plugins/
|-- bif-bbc-plugin-0.1-SNAPSHOT-plugin.jar
`-- mychain010-bbc-1.0.0-SNAPSHOT-plugin.jar

0 directories, 2 files
```



**第八步**，启动Node服务

在Node安装目录的config目录下，找到ddl.sql，为Node初始化数据库。

然后运行下面命令启动节点：

```
bin/start.sh 
```

可以通过查看日志的方式查看是否成功启动，日志路径在安装目录的：log/committee-node，application.log记录了除ERROR以外日志，error.log记录ERROR日志。



**第九步**启动服务，在CLI执行初始化的命令。

启动Node CLI，运行下面命令，使用为上面Supervisor CLI准备的BCDNS配置文件，在节点注册BIF BCDNS：

```
register-bcdnsservice --bcdnsType bif --propFile /path/to/supervisor-cli/bcdns.json
```

之后可以通过运行get-bcdnsservice查看已注册的BCDNS。

## 🔧其他

Committee Node支持使用[Jasypt](https://github.com/ulisesbocchio/jasypt-spring-boot)加密配置。

- 找到你的配置文件`/path/to/your/application.yml`，将所有你想要加密的配置改成`DEC(...)`格式的文本。

  ```
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/committee_node1?serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true
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

- 使用加密之后的配置文件启动服务，运行：

  ```
  bin/start.sh -P the_password
  ```



## 🤝社区治理

AntChain Bridge 欢迎您以任何形式参与社区建设。

您可以通过以下方式参与社区讨论

- 钉钉

![scan dingding](https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/dingding2024.png?x-oss-process=image/resize,w_400/quality,Q_100)

- 邮件

发送邮件到`antchainbridge@service.alipay.com`

## 📄License

详情参考[LICENSE](./LICENSE)。


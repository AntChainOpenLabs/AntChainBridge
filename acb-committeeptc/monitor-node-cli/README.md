<div align="center">
  <img alt="am logo" src="https://antchainbridge.oss-cn-shanghai.aliyuncs.com/antchainbridge/document/picture/antchain.png" width="250" >
  <h1 align="center">Monitor Node CLI</h1>
</div>

# 介绍

Monitor Node CLI目前功能和Node CLI相同。
Monitor Node CLI工具是用于管理Committee Monitor Node的交互式命令行工具，它可以完成BCDNS服务注册等工作。

# 使用

## 编译

**在开始之前，请您确保安装了maven和JDK，这里推荐使用[jdk-21](https://adoptium.net/zh-CN/temurin/releases/?version=21)版本**

**确保安装了AntChain Bridge Plugin SDK，详情请[见](acb-sdk/README.md)**

在monitor-node-cli模块根目录运行maven命令即可：

```shell
cd monitor-node-cli && mvn package -Dmaven.test.skip=true
```

在`monitor-node-cli/target`目录下会生成一个压缩包`monitor-node-cli-bin.tar.gz`，解压该压缩包即可使用。

解压编译生成包后可以看到文件如下：

```
./monitor-node-cli
├── README.md
├── bin
│   └── start.sh
└── lib
    └── monitor-node-cli.jar

2 directories, 3 files
```

## 启动

查看脚本帮助信息：

```shell
$ ./bin/start.sh -h

 start.sh - Start the AntChain Bridge Committee Node Command Line Interface Tool

 Usage:
   start.sh <params>

 Examples:
  1. start with the default server address `localhost` and default port `10088`：
   start.sh
  2. start with specific server address and port:
   start.sh -H 0.0.0.0 -p 10088

 Options:
   -H         admin server host of committee node.
   -p         admin server port of committee node.
   -h         print help information.

```

启动命令执行情况如下：

```shell
$ ./r-cli/bin/start.sh

  _   _   ___   ____   _____
 | \ | | / _ \ |  _ \ | ____|
 |  \| || | | || | | ||  _|
 | |\  || |_| || |_| || |___
 |_| \_| \___/ |____/ |_____|

        CLI 0.1.0-SNAPSHOT

monitor-node:> 
```

启动成功后即可在`monitor-node:>`启动符后执行cli命令。

# 命令操作详情

- 直接输入`help`可以查看支持命令概况
- 直接输入`version`可以查看当前中继CLI工具版本
- 直接输入`history`可以查询历史命令记录

### add-ptc-trust-root

手动增加PTC Trust Root 到Committee Node存储里。

参数：

- rawPtcTrustRootFile：文件路径，指向包含Base64格式的序列化PTC Trust Root内容的文件；

执行：

```
add-ptc-trust-root --rawPtcTrustRootFile /path/to/ptctrustroot-file
```

### register-bcdnsservice

在Committee Node中，注册特定的BCDNS服务，即BCDNS的客户端。

命令参数如下：

- `--bcdnsType`：（必选）BCDNS服务类型，提供`bif`和 `embedded`和两种类型。其中`bif`为目前可用的星火链网BCDNS服务，为中继外部依赖服务，`embedded`为嵌入式BCDNS服务（计划开发中，敬请期待）；
- `--domainSpace`：（可选）当前中继服务的域名空间名，一个域名空间名绑定一个BCDNS服务，该项默认为空字符串，即当前中继的根域名空间名默认为空字符串；
- `--propFile`：（必选）配置文件路径，即初始化BCDNS服务存根所需的客户端配置文件路径，例如`/path/to/bif_bcdns_conf.json`，该配置文件可以使用`5.3 generate-bif-bcdns-conf`命令生成；
- `--bcdnsCertPath`：（可选）BCDNS服务的证书路径，可不提供，若未提供该证书命令执行注册时会向BCDNS服务请求证书。

用法如下：

```shell
relayer:> register-bcdnsservice --bcdnsType bif --propFile /path/to/bif_bcdns_conf.json
success
```

### get-bcdnsservice

用于查询指定域名空间中继所绑定的BCDNS服务信息。

命令参数如下：

- `--domainSpace`：（可选）中继的域名空间名，该项默认为空字符串。

用法如下：

```shell
# 当前中继域名空间名为空字符串，故直接使用默认域名空间名
relayer:> get-bcdnsservice
{"domainSpace":"","domainSpaceCertWrapper":{"desc":"","domainSpace":"","domainSpaceCert":{"credentialSubject":"AADhAAAA...YTAifV19","credentialSubjectInstance":{"applicant":{"rawId":"ZGlkOmJp...RENwQw==","type":"BID"},"bcdnsRootOwner":{"$ref":"$.domainSpaceCertWrapper.domainSpaceCert.credentialSubjectInstance.applicant"},"bcdnsRootSubjectInfo":"eyJwdWJs...MCJ9XX0=","name":"root_verifiable_credential","rawSubjectPublicKey":"Q3hxGTc6...i3cJwqA=","subject":"eyJwdWJs...MCJ9XX0=","subjectPublicKey":{"algorithm":"Ed25519","encoded":"MCowBQYDK...i3cJwqA=","format":"X.509","pointEncoding":"Q3hxGTc6...i3cJwqA="}},"encodedToSign":"AACHAQAA...In1dfQ==","expirationDate":1733538286,"id":"did:bid:ef29QeET...2Mzdj8ph","issuanceDate":1702002286,"issuer":{"rawId":"ZGlkOmJp...ZUdNQw==","type":"BID"},"proof":{"certHash":"+9D7B4Eh...vA1cBaE=","hashAlgo":"SM3","rawProof":"RND0SpVq...C6aMDA==","sigAlgo":"Ed25519"},"type":"BCDNS_TRUST_ROOT_CERTIFICATE","version":"1"},"ownerOid":{"rawId":"ZGlkOmJp...RENwQw==","type":"BID"}},"ownerOid":{"rawId":"ZGlkOmJp...RENwQw==","type":"BID"},"properties":"ewogICJj...IH0KfQoK","state":"WORKING","type":"BIF"}
```

### delete-bcdnsservice

用于删除指定域名空间的中继所绑定的BCDNS服务，删除后可重新绑定其他BCDNS服务。

命令参数如下：

- `--domainSpace`：（可选）中继的域名空间名，该项默认为空字符串。

用法如下：
```shell
# 删除BCDNS服务
relayer:> delete-bcdnsservice
success

# 查询BCDNS服务
relayer:> get-bcdnsservice
not found
```

### get-bcdnscertificate 查询BCDNS服务证书

用于查询指定域名空间的中继所绑定的BCDNS服务的证书。

命令参数如下：

- `--domainSpace`：（可选）中继的域名空间名，该项默认为空字符串。

用法如下：

```shell
relayer:> get-bcdnscertificate
-----BEGIN BCDNS TRUST ROOT CERTIFICATE-----
AAAVAgAAAAABAAAAMQEAKQAAAGRpZDpiaWQ6ZWYyOVFlRVRRcDVnOHdabXBLRTNR
......
pp1tvNQJKwumjAw=
-----END BCDNS TRUST ROOT CERTIFICATE-----

```

### stop-bcdnsservice 停止BCDNS服务

用于停止指定域名空间的中继所绑定的BCDNS服务的运行。

命令参数如下：

- `--domainSpace`：（可选）中继的域名空间名，该项默认为空字符串。

用法如下：

```shell
# 停止BCDNS服务
relayer:> stop-bcdnsservice
success

# 停止后查看BCDNS服务信息可以看到，信息详情中的状态为`FROZEN`
relayer:> get-bcdnsservice
{"domainSpace":"","domainSpaceCertWrapper":{......},"ownerOid":{......},"properties":"......","state":"FROZEN","type":"BIF"}
```

### restart-bcdnsservice 重启BCDNS服务

用于重新启动指定域名空间的中继所绑定的BCDNS服务。

命令参数如下：

- `--domainSpace`：（可选）中继的域名空间名，该项默认为空字符串。

用法如下：

```shell
# 重启BCDNS服务
relayer:> restart-bcdnsservice
success

# 停止后查看BCDNS服务信息可以看到，信息详情中的状态为`WORKING`
relayer:> get-bcdnsservice
{"domainSpace":"","domainSpaceCertWrapper":{......},"ownerOid":{......},"properties":"......","state":"WORKING","type":"BIF"}
```

### generate-node-account

Committee Node初始化时，用来生成节点需要的私钥和公钥文件，后续将用来完成背书签名✍️。

参数：

- keyAlgo：私钥的算法，支持：SECP256K1、RSA、ECDSA（secp256r1）、SM2、ED25519；
- outDir：密钥文件存储的文件夹路径，默认当前路径；

用法如下：

```
node:> generate-node-account --keyAlgo SECP256K1 --outDir ./
private key path: /path/to/./private_key.pem
public key path: /path/to/./public_key.pem
```


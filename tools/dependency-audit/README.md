# 部署依赖基线检查

2026-10-21 核对了本地测试环境的 36 个部署产物，以及远程 PluginServer、Relayer、三台委员会节点、监管节点和 CLI 的实际 JAR。比较包括 Spring Boot 内嵌依赖 JAR 的 SHA-256、插件 Maven 元数据和实际类文件，不能仅凭 `pom.properties` 推断 fat JAR 使用的类版本。

| 范围 | 结果与处理 |
| --- | --- |
| Relayer、PluginServer、committee-node、monitor-node、Relayer／节点／监管 CLI | 本地和远程对应产物的第三方依赖 JAR 完全一致，无需改版本或替换产物 |
| dioxide、dioxide2、ethereum2、ethereum3、fiscobcos | 对应产物的版本元数据与关键类指纹一致，保留 |
| mychain0.20 | 将测试依赖间接选中的 Guava 32.1.3-android、Protobuf 3.25.1 恢复到生产基线 18.0、3.5.1；隔离测试依赖的版本影响 |
| mychain0.10 | 显式固定 Protobuf 3.5.1、Commons Lang 3.8.1、SLF4J 1.7.25；隔离测试依赖，并固定 SDK 内 Guava 的解包优先级 |
| fiscobcos3 | 当前源码使用 BCOS 3.x SDK 3.8.0，与远程旧 BCOS 2.x 产物是功能迁移差异；保留与 Base BCOS 3.x 插件相同的 SDK／运行依赖组合，不能直接还原旧 POM 后部署 |
| MonitorSystemServer | 对应远程产物的依赖版本和关键类一致；保留解决 fat JAR 签名问题的 Shade 过滤配置 |
| MonitorSystemClient、MonitorOrderClient、UcpParser | 远程盘点未发现对应运行产物，近期变更未修改它们的 POM；未更改依赖 |
| PluginServer CLI | 远程有产物，本地测试目录没有对应安装件；源码 POM 无本轮相关升级，不执行安装 |

BCOS 3.x SDK 的 Gson 2.10.1、Commons Lang 3.12.0、Commons IO 2.11.0、SLF4J 1.7.36 来自 SDK 自身的依赖声明。旧的 BCOS 2.x SDK 不能满足当前源码中的 `org.fisco.bcos.sdk.v3` 调用。远程若仍使用 BCOS 2.x，不能直接用当前 `fiscobcos3` JAR 替换，需单独核定链版本与迁移范围。

## Mychain 构建与验收

两个模块均继续生成 Java 8 目标字节码。Lombok 1.18.32 仅用于兼容 Java 21 的注解处理器，以 `provided` 声明，不打包到运行产物。SDK、Bouncy Castle、Netty 及此前的功能修复不变。

`committee-ptc-core` 虽是测试依赖，其传递依赖仍会参与 Maven 版本仲裁，可能改变另一路生产依赖的最终版本。两个模块现在明确排除这条测试路径上的 Guava、Protobuf，再固定生产基线，防止重编译重新引入差异。

Mychain SDK 本身包含第三方类。`mychain0.10` 的独立 assembly 描述符只优先解包 SDK 中的 Guava；其余类保持正常依赖顺序。这样既保留已部署 SDK 中的类，也保留外部依赖提供的兼容类，避免删除它们或让其他库意外覆盖。`mychain0.20` 维持外部 Guava 18.0 优先的既有顺序。两个插件各自保留自己的基线，不相互强制统一。

构建后运行以下只读检查（不初始化 SDK、不连接链或数据库）：

```sh
python3 tools/dependency-audit/verify_mychain_artifacts.py \
  acb-sdk/pluginset/mychain0.10/offchain-plugin/target/mychain010-bbc-1.0.0-SNAPSHOT-plugin.jar \
  acb-sdk/pluginset/mychain0.20/offchain-plugin/target/mychain020-bbc-1.0.0-SNAPSHOT-plugin.jar
```

检查包含依赖元数据、Guava／Protobuf／Commons Lang／SLF4J 完整类族指纹、其他关键类指纹、未打包 Lombok、插件自身字节码不高于 Java 8。失败返回非零退出码；`--output` 只允许创建新文件。

`mychain-baseline.json` 保存本次远程 Linux x86_64 运行产物基线，不包含配置或凭据。更换 SDK、架构或正式升级依赖时，应先完成专门的兼容性验证，再审查更新基线；不能为让检查通过而自动重新生成基线。

本次离线验收不代替远程维护窗口内的启动与真链验收，也没有修复既有链上的 Monitor／PtcHub 绑定或推进积压消息。

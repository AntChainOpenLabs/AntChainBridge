# Dioxide 并发提交与 ISN 运维说明

## 原因和保证

Dioxide `tx.compose` 默认读取当前 ISN，并不预留。消息会话锁与插件对象锁不能保护不同业务域、
不同类加载器或不同主机上共用的链账户。生产 diox04/diox11 共用账户，因此两个插件和 Python
发送工具必须连接同一个协调数据库。

协调键是 `networkId + effective account`：普通调用使用 sender，委托调用使用 delegatee；
Ed25519 地址带类型后缀与裸地址使用同一个协调键。不能用域名、插件类型或 RPC URL 分别计数。
每次新分配核对已归档区块 checkpoint，以及节点 ISN 已观察值，节点回退时停止新分配。

数据库行锁与命名锁保护预留、签名保存和广播；等待链上 ISN 接受不占数据库事务锁。签名数据和
由签名字节确定性计算的交易哈希在广播前提交。RPC 响应丢失后先查询 `dx.isn`：若节点 ISN 已前进，
直接恢复该哈希；若未前进，才重播原始签名字节。因此断电、RPC 响应丢失、JVM 重启都不会使同一
operationId 换号重签。数据库不可用时禁止降级。
已签名记录从不删除或回退；uint32 最大值之后必须停止，由运维核查，不能回绕。

Relayer 传入的 submissionId 为 UCP + 目标域 + relay-am 的稳定摘要，与可能全零的 messageId
及业务正文无关。同一正文的两条独立 UCP 不去重；同一 submissionId 的参数变更直接拒绝。
查询 SDP 序号的共享 mailbox 从查询提交至完整确认、读回结果都受独立分布式锁保护。

## 安装与配置

1. 新安装使用 `antchain-bridge-plugin-lib/src/main/resources/db/dioxide_tx_coordinator.sql`。已有 v1
   数据库在停止所有 Dioxide 写入并备份后，只运行一次 `db/dioxide_tx_coordinator_v1_to_v2.sql`。
   v2 使用 bridge_tx_account、bridge_tx_submission、bridge_tx_attempt 三张表；运行账号仅需这些表
   的 SELECT/INSERT/UPDATE，DDL 由运维执行。
2. 用已核实的归档区块设置 networkId、checkpointHeight、checkpointHash。所有同网络实例必须一致；
   恢复旧快照或更换网络后禁止直接更换 namespace 绕过旧未决记录，应先对账。
3. 参照 dioxide-tx.properties.example 创建 root-only 配置、密码文件，均为 0600。
   默认路径 /etc/antchain-bridge/dioxide-tx.properties；可用 DIOXIDE_TX_COORDINATOR_CONFIG 或
   Java 系统属性 dioxide.tx.config 覆盖。域配置 txCoordinatorConfigFile 具有最高优先级。
4. 同时部署新 SPI、Plugin Server、Relayer、dioxide 和 dioxide2 插件。旧 Relayer 不传提交标识，
   新 Dioxide 插件会拒绝不带标识的跨链提交，而不是降级到不安全路径。
5. 在调用脚本使用的 Python 虚拟环境中执行 `pip install ./acb-sdk/tools/dioxide-tx-coordinator`。
   Runner 使用 crosschain 配套 PR 的 adapter。旧 Python SDK 直接调用不受本组件保护，
   共享签名账户不能再通过未接入协调的脚本发交易；不能给其它进程配置独立的本地计数器。

本组件不保存私钥。signed_tx 是已签名交易，虽然不能用于重签，也不能公开或写入操作日志。
发布包、Git、PR 和诊断清单均不包含真实数据库凭据、私钥或完整 signed_tx。

## 发送、部署工具与恢复

维护工具位于 scripts/。发送操作必须提供独立 operation-id；同一操作恢复时复用该 ID：

```bash
python scripts/dioxide_send_message.py --operation-id test-20260904-001 \
  --config /root/workspace2026/dioxide2.json --dapp kt3_20 \
  --app-contract AppContractV2 --target-domain TARGET_DOMAIN \
  --target-identity TARGET_32_BYTE_HEX --message 'controlled test'
```

以上是示意参数，不应直接对真实业务合约执行。部署脚本也要求 operation-id，并为部署和各个绑定
阶段使用独立标识；本次 ISN 修复不执行任何合约重部署。

Python 的测试币准备、Dapp 创建也使用协调入口及独立阶段 ID。Dapp 等待失败会抛异常，
不能把底层 SDK 的 false 返回值当作完成。首次部署前先确认相应脚本也使用同一版共享模块。

协调部署入口保持原 Python SDK 的构造参数编码：`None` 表示没有构造参数，写入
`cargs` 的元素为 `""`；显式 `{}` 仍编码为 `"{}"`。批量部署保留源码与参数的对应顺序，
单合约部署使用相同规则，不能将 `None` 序列化成字符串 `"null"`。

- SIGNED：签名和可恢复交易哈希已经持久化，可能尚未广播，也可能广播后进程退出。
- UNKNOWN：广播结果未知；先查询链上 ISN，确认未接受时才重播原始签名字节，不得换 ISN。
- REJECTED：节点明确拒绝且链上 ISN 未前进；同一 operationId 可用相同 ISN 创建新 attempt。
- BROADCAST：已拿到哈希，原提交标识直接返回同一哈希。
- FINALIZED/FAILED：来自完整链上执行结果，不能将失败冒充成功。

运维查询只选择 operation_id/account/isn/tx_hash/state/last_error，不输出 signed_tx。
CLI 或 Relayer 使用原提交标识恢复时，参数必须与首次一致；业务发起方不得换标识掩盖超时。
若原签名交易已过期或链已回滚，保留记录并人工对账，不自动构造替代交易。

## 测试

使用独立 MySQL（本地验证容器 crosschain-isn-test-mysql，127.0.0.1:18236），不要使用生产库运行
测试 fixture。安装 SQL 后，设置 Maven 属性 isn.test.jdbc 运行 JdbcTransactionCoordinatorTest。
Python 测试以 ISN_TEST_MYSQL=1 开启；JAVA_PROBE_JAVA 和 JAVA_PROBE_CLASSPATH 指向 Java 8
以及 plugin-lib 的 test-classes/classes/测试 MySQL 驱动，启用两个 Java + 两个 Python 进程回归。

覆盖账户分配、响应丢失、签名失败、同操作参数冲突、网络 checkpoint 改变、节点计数回退、数据库
不可用、uint32 边界、查询 mailbox 竞争、跨语言恢复 Java 在签名落库后强制退出的记录。
跨进程测试还覆盖签名前退出后的事务回滚，以及两个 Java、两个 Python 进程对同一查询
mailbox 的 32 次写入/等待/读回，结果不能串线。所有 fixture 表只在本地 isn_test 库创建。
两个 Dioxide 插件单独运行 DioxideClientFinalityTest，避免默认 BBC 集成测试误发真实链交易。
插件构建加 -Dexec.skip=true，使用已提交 GCL 包装资源，本次不生成或变更链上合约代码。

## 发布与回滚

Python 单合约和批量部署沿用原 SDK 的等待规则：`max(60, (compile_time or 0) * 6)` 秒。
协调提交的确认等待使用该值乘以 1000（毫秒）；SDK `wait_for_deploy` 使用原秒值。
例如 `compile_time=20` 时两个阶段各最多等待 120 秒，`compile_time=10` 时各 60 秒。
部署等待超时不会触发重新签名或广播；保留原 operation 和交易记录，先只读对账。

先备份服务包、配置和新增协调表，暂停相关提交，确认在途请求收敛；先安装协调存储和配置，
再部署服务包、插件、Python adapter，进行小批和并发验收。保留各文件 SHA-256、版本和测试 UCP。
回滚先暂停提交、对账未决记录，再恢复旧服务包；协调数据不能回滚、删除或复用旧序号。
旧代码存在已知并发缺陷，不能直接恢复旧版并发流量。

历史 TXN_ABORTED 业务本次仅列诊断清单，不补发、不修改原 UCP 或监管记录。合约内部异常与
ISN abort 分开记录。公开 API 结构、链账户、合约地址、节点和公网端口保持原样。

## 复用的工程经验

预留必须发生在并发主体共享的持久边界；RPC compose 不等于预留，线程锁不等于跨进程锁。
重试的单位是已持久化的提交操作，不是重新签名的业务请求。最终确认、执行成功、业务成功是
不同事实，不能因外层哈希存在或确认等待超时就推导出业务成功。

Dioxide relay group 可能含其他业务的交易，必须保留 groupHash:数组下标，只遍历引用成员。
组缓存可以共享，但选择成员时不能修改缓存本身；Fastjson JSONObject(Map) 使用原 Map，
不是复制。回归测试通过真实 RPC JSON 检查一组中的两个有效成员，不受另一个失败成员影响。

运维服务恢复也需要校验依赖：systemd 停止 Runner 可能连带停止 Backend，启动 Runner
并不保证 Backend 自动恢复。每次切换后显式检查两个服务及公共查询接口。

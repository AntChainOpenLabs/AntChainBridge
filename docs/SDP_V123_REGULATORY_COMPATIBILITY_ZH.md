# SDP V1/V2/V3 监管兼容与部署说明

## 1. 目标与范围

本次升级解决“发送端经过 Monitor 封装，目标链 SDP 却直接把监管信封交给业务合约”的不对称问题，并将 Ethereum、FISCO BCOS 与 Mychain 的监管路由从 SDP V1 扩展到 V2/V3。

兼容原则如下：

- 普通模式与监管模式使用同一套 SDP V1/V2/V3 接口；
- Monitor 在发送侧封装，在接收侧解封后才调用业务合约；
- request/response 以及 ACK 路径使用相同的监管规则；
- 目标业务合约收到的 `bytes` 必须与源业务消息逐字节一致；
- 原生协议只实现 SDP V1 的链不伪装成 V2/V3 支持。

## 2. 当前能力矩阵

| 链插件 | 普通 SDP | 监管 SDP | 接收侧 Monitor 解封 | 备注 |
| --- | --- | --- | --- | --- |
| Ethereum2 | V1/V2/V3 | 不支持 | 不适用 | 无监管基线，保留完整 PTC |
| Ethereum3 | V1/V2/V3 | V1/V2/V3 | 支持 | Monitor V5，SDP 保留 V1 兼容接口 |
| FISCO BCOS 3.x Base（`fiscobcos`） | V1/V2/V3 | 不支持 | 不适用 | 无监管基线，与 Ethereum2 复用合约语义并保留完整 PTC |
| FISCO BCOS 3.x Xun（`fiscobcos3`） | V1/V2/V3 | V1/V2/V3 | 支持 | Monitor V5，与 Ethereum3 复用同一套合约源码 |
| FISCO BCOS 2.x（`fiscobcos2`） | 旧插件能力 | 不支持 | 不适用 | 保留的历史 2.x 插件，不再占用 `fiscobcos3` 产品名 |
| Mychain 0.20 | V1/V2/V3 | V1/V2/V3 | 支持 | Monitor V7，包含安全变长字节解码和 monitor 证明验证 |
| Dioxide（`dioxide`） | V1 | V1 | 按 V1 原生路径 | 旧插件按 V0 能力运行，不配置 PTC Hub |
| Dioxide2（`dioxide2`） | V1 | V1 | 支持 | 结构完整的 Committee PTC 流程；Dioxide 真实性验证按验收要求降级 |

## 3. 调用路由

监管开启时，完整路由为：

```text
源业务合约
  -> 源链 Monitor 封装
  -> 源链 SDP V1/V2/V3
  -> AuthMessage / Relayer / PTC
  -> 目标链 SDP V1/V2/V3
  -> 目标链 Monitor 校验并解封
  -> 目标业务合约
```

监管关闭时仍经过相同的 Monitor 路由，但信封中的控制类型为 `MONITOR_CLOSE`；这样不会因两端策略切换而让业务合约收到封装层。

## 4. 本次修复的根因

### 4.1 V2/V3 接收端绕过 Monitor

旧实现只在 V1 路径上完成 Monitor 转发，部分 V2/V3 request 和 ACK 入口会直接调用业务合约。修复后，三个 SDP 版本的发送、接收与 ACK 均通过 Monitor 的对应入口。

### 4.2 多 EVM 编译器的非 32 字节对齐 payload 错位

旧版 Ethereum、FISCO 和 Mychain Monitor 使用的汇编变长字节解码会随标准 solc、FISCO solc、MySolidity 的内存对齐差异产生不同结果。典型现象是 36 字节业务消息只剩尾部片段和大量 `0x00`，但交易回执仍然成功。

Ethereum3/FISCO BCOS 3.x Xun 的 Monitor V5 与 Mychain Monitor V7 均依照旧 ACB EVM codec 的“逆序 32 字节块 + 末尾长度”格式显式计算边界，再逐字节恢复逻辑内容，避免依赖编译器敏感的内存拷贝。各链插件的期望实现版本也同步提高，`setup-bbccontracts` 会检测旧运行码；支持原位升级的链保持原地址，不支持安全原位升级的插件会部署新 Monitor 并把新地址写回链上下文和业务测试合约。

### 4.3 Mychain 监管证明未进入 MonitorVerifier

旧 Mychain0.20 的 PTC Hub 只调用两参数委员会验签接口，CommitteePtcVerifier 把 monitor 节点
当普通委员会节点处理，Monitor 收到 `MONITOR_OPEN` 后又直接解封并投递，因此外部监管拒绝并
不能阻止目标业务执行。

修复后的 PtcHub V3 调用三参数验证接口。CommitteePtcVerifier V2 保留旧接口用于升级兼容，
同时把 required monitor 节点的 lane、committeeId、签名和待签正文交给 MonitorVerifier V2；
普通委员会节点仍执行原阈值验签。Monitor V7 在同一笔 AuthMessage/SDP 原子调用中消费该证明：
V1 拒绝时构造 `MONITOR_ROLLBACK` 返回源链且不调用业务合约，V2/V3 拒绝时沿用 SDP 的失败和
atomic ACK 处理。跨链消息、TpBTA 和 ThirdPartyProof 编码均未变化。

### 4.4 Mychain PTC Hub 保留了旧 BCDNS 根

EVM 运行码升级只替换 code，不会再执行构造函数。因此旧 PTC Hub 升级后仍可能保留历史 BCDNS 根，随后验证新 TPBTA 时报 `issuer not found`。

PTC Hub V2 增加 owner-only 的根证书对账入口；Mychain 插件在可重复的 `setup-bbccontracts` 流程中探测旧运行码、必要时升级，并用当前配置的 BCDNS 根执行对账。

### 4.5 重复 setup 降级 SDP 就绪状态

旧 `setupSDPMessageContract()` 在合约已经完成绑定时仍把聚合状态写回 `CONTRACT_DEPLOYED`，使后续任务判断为“SDP not ready”。修复后，重复 setup 保留 `CONTRACT_READY`。

### 4.6 替换 Monitor 时丢失既有监管节点背书

MonitorVerifier 的 `monitorNodeEndorseInfoMap` 在 TPBTA 写入 PTC Hub 时同步生成。旧 FISCO
升级流程在替换 Monitor 时同时部署了一个空 MonitorVerifier，再让已有 PTC Hub 指向它；新
Verifier 虽然地址双向绑定正确，但没有历史 TPBTA 的监管节点背书，所有监管消息都会在目标链
以 `MonitorVerifierMsg: no monitor node endorse info` 回滚。

`fiscobcos3` 的升级流程仅替换存在问题的 Monitor，并复用当前 PTC Hub 已绑定的旧 MonitorVerifier，
从而保留既有背书状态。已产生空 Verifier 的环境必须在审计后将新 Monitor 和 PTC Hub 同时绑定
回旧 Verifier；只修改其中一侧会留下不一致状态。

### 4.7 V0 目标链被误走 PTC Hub 同步

发送链存在 BTA 时，Relayer 需要把该链的 TPBTA 同步到支持链上 PTC Hub 的接收链。旧判断只
看发送链是否有 BTA，没有再检查接收链是否真的配置了 PTC Hub。旧`dioxide`插件使用 V0 BBC 与
原生 SDP V1，`ptc_contract` 为空；因此 Ethereum 到 Dioxide 的消息在监管已批准后仍会被
`hasTpBta` 的“不支持 V1 BBC 接口”异常阻断，根本没有进入 `relayAuthMessage`。

修复后仍以发送链 BTA 决定是否需要可信同步，但在同步前先读取目标链 BBC Context。只有目标链
存在非空 PTC 合约时才执行 `hasTpBta/addTpBta`；没有 PTC Hub 的 V0 目标按其原生 AM 路径继续
提交。这个判断不降低 Ethereum、FISCO、Mychain 之间的可信校验，它们的目标链 Context 都包含
已配置 PTC Hub。

### 4.8 Dioxide 归档终态只出现在 `State`

Dioxide 的入口交易和 relay group 通常同时返回 `ConfirmState`，但最终调用 SDP/业务合约的 relay
交易可能只返回 `State=DUS_FINALIZED` 或 `State=DUS_ARCHIVED`，没有 `ConfirmState`。旧插件只按
`ConfirmState` 判断最终性，因此链上已经归档且业务执行成功的交易仍被解释为 `unknown/pending`，
Relayer 无法把 `sdp_msg_pool` 收敛为成功，也无法补报监管执行结果。

Dioxide 与 Dioxide2 插件现在同时读取两套节点状态：`TXN_FINALIZED/TXN_ARCHIVED` 与
`DUS_FINALIZED/DUS_ARCHIVED` 均表示可靠终态；`DUS_INVALID/DUS_FORKED/DUS_ARCHIVED_UNCLE`
表示终态失败。`transaction not found` 仍保留为未确认错误，不会因为另一笔成功交易或增加重试
次数而被伪造成成功。Relayer 的批量确认也按单条消息隔离原生回执异常，避免一条坏记录阻塞同批
其它已确认交易。

### 4.9 Dioxide 重建后合约 CID 与 Relayer 高度游标同时失效

Dioxide 测试网发生快照恢复或重建时，节点块高可能回退，但 Relayer 的 `polling`、`sync`、
`notify_*` 游标仍保留在原来的更高位置。旧轮询逻辑只在远端块高更高时更新记录，因此会长期
停留在不可达高度；如果同一 dapp 又部署了新版本系统合约，合约名不变但
`ContractVersionID` 已递增，旧 BBC Context 中“非空”的 CID 也会被误判为已部署。

修复后，Relayer 在发现 `recordedHeight > latestHeight` 时给出明确的高度回退错误，要求先停止
anchor 并对账 `polling/sync/notify` 游标，避免把无效高度伪装成普通节点超时。Dioxide 与
Dioxide2 插件的 AM、SDP、Monitor 就绪检查也会查询当前 `dx.contract_info`；只有配置 CID 与
当前合约版本完全一致时才认为合约已经部署。

恢复流程必须作为一个受控事务执行：备份链配置和游标、停止 anchor、部署并绑定新合约、同时
更新顶层地址/异构 BBC Context/base64 `raw_conf`、把三类游标重置到部署块前一块、清除对应
Redis 高度缓存，最后启动 anchor 并确认它追到最新块高。只改 CID 或只改数据库高度都会留下
半更新状态。

### 4.10 Mychain 目标交易哈希缺少跨系统十六进制前缀

Mychain 插件返回的原生交易哈希是 64 位十六进制字符串且不带 `0x`；Ethereum 和 FISCO BCOS
插件返回的同类字段已经带有 `0x`。旧 Relayer 将插件值直接写入
`target-chain-submission` 和 `target-chain-execution`，导致同一个监管接口里的固定 32 字节
十六进制哈希格式不一致。

修复只发生在监管上报边界：裸 64 位 hex 增加 `0x`，已有 `0x` 保持不变，`0X` 只统一前缀
大小写；Dioxide Base32、空值、长度不符或含非十六进制字符的值均原样保留，不猜测或左补缺失
字节。Relayer 数据库和 Mychain 节点查询仍使用裸 64 位原生哈希，`/ucps` 首报中的源链
`provableData.txHash` 也不受影响。

### 4.11 Dioxide2 PTC 与监管链路不完整

旧 Dioxide2 路径在 Relayer 中临时构造只有 sender domain 的空 TpBTA，再额外调用一次 PTC；
Committee PTC 看到域名字符串`dioxide2`后只调用一个 monitor 节点，并返回空 ThirdPartyProof。
这既把 product 和实际区块链域名混为一谈，也无法形成 Ethereum3 链上委员会验证器所需的完整
证明。

修复后 Dioxide2 插件实现`readConsensusState`以及 PTC trust root、verify anchor、TpBTA 的链上
存取和部署绑定。普通 node 与 monitor-node 根据已保存 BTA 的`product=dioxide2`识别降级范围，
保留 BTA/TpBTA、初始高度、初始哈希检查，并要求前一高度的 validated state 存在，但跳过 Dioxide HCDVS、ledger proof
和消息真实性验证，并按真实 TpBTA 版本、lane 与正文签发标准委员会证明。其他 product 仍执行
原有完整验证。

monitor-node 不再按 sender domain 旁路监管层。`MONITOR_OPEN`调用外部监管系统，通过时返回
真实签名，拒绝、异常或无有效响应时返回 65 字节空签名；`MONITOR_CLOSE/ROLLBACK`上报后继续
签名。Committee PTC 按 endorsement root 聚合 required/optional 普通节点与 monitor 节点，生成
`rawProof`非空的完整 ThirdPartyProof。Relayer 只调用一次 PTC，保存该证明并继续上报监管状态；
监管拒绝也继续向目标链投递，由目标链产生回滚或错误 ACK。

Dioxide2 作为目标链时，AuthMessage 将 ThirdPartyProof 交给链上 PtcHub。PtcHub 扫描整个委员会
证明并按`nodeId`前缀`monitor`提取签名，因此不依赖签名顺序；MonitorVerifier 只以“非空且不全
为零”作为验收级通过条件。`MONITOR_OPEN`失败时 ordered/unordered SDP V1 都不调用业务合约，
而是构造`MONITOR_ROLLBACK`返回源链。该目标链检查不等同于生产级密码学验签。

## 5. 部署与升级顺序

1. 备份当前插件 JAR、Relayer 链配置与合约地址。
   FISCO Base 使用 `fiscobcos`，监管版使用 `fiscobcos3`，不能通过切换 Monitor 开关共用同一注册记录。
   历史上以 `product=fiscobcos` 注册的监管链不会自动改名；升级时应以 `fiscobcos3` 新建或人工迁移
   blockchainId、BBC Context、合约地址、BTA/TpBTA 和 Anchor 配置，完成核对后再停止旧记录。
2. 核对 `bcdns_root_cert_pem` 与当前 BCDNS/PTC 证书的 issuer owner。
3. 替换插件 JAR，先重启 Plugin Server，再确认 Relayer 恢复对插件的连接。
4. 对目标链执行一次 `setup-bbccontracts`；Mychain 按 MonitorVerifier → CommitteePtcVerifier → PtcHub → Monitor → SDP 的顺序原位升级并重建绑定。不要假设其他链的地址都会保持不变。
5. 读取 Monitor/SDP/PTC 实现版本与绑定关系，再发起新的链上验收交易。
6. 分别使用监管关闭和监管开启模式发送 V1/V2/V3，同时验证源交易、Relayer 归档、监管结果、目标交易和业务 payload。

如果节点块高小于 Relayer 已记录高度，不得直接启动 anchor。应先确认这是权威链回退而不是
临时 RPC 分叉，再按 4.9 的顺序重置全部相关游标；基线应不晚于新系统合约部署块前一块。

对旧`dioxide`插件还应确认`get-blockchain-contracts`的`ptc_contract`为`empty`。这是旧插件
V0/V1 原生路径的能力声明，不应通过伪地址强行开启 PTC Hub 接口。对`dioxide2`则相反：完成
`setup-bbccontracts`后必须得到非空 PtcHub，同时存在 MonitorVerifier 并完成双向绑定。

同时应抽样读取最终 relay 交易：若没有 `ConfirmState`，必须确认其 `State` 已到
`DUS_FINALIZED` 或 `DUS_ARCHIVED`；不能仅观察入口 tx0 的归档状态。

不要只以“目标交易 success”作为验收标准；必须读取目标业务合约的事件或状态，确认 payload 没有 Monitor 外层且逐字节一致。

## 6. 验收查询

大屏数据 API 可用于核对 UCP 生命周期与监管上报：

```bash
curl -k 'https://47.94.7.98:18443/api/data/messages?page=1&size=20&sourceDomain=eth04&targetDomain=my02'
curl -k 'https://47.94.7.98:18443/api/data/message/{ucpId}'
```

单条详情中至少应看到：

- 源链与目标链交易哈希；
- `regulation.reportStatus=REPORTED`；
- `regulation.status=APPROVED`（对允许通过的测试样例）；
- `regulation.currentStage=TARGET_EXECUTED`；
- `regulation.completeness` 中四个阶段均为 `true`。

对 Mychain 目标消息还应同时验证：`target.txHash` 是可直接查询节点的裸 64 位值，而
`regulation.targetSubmission.txHash` 与 `regulation.targetExecution.txHash` 都是同一个
`0x`+64 位值。两种表示服务于不同边界，不能把监管格式反写到链原生查询字段。

## 7. 回滚

- 应用回滚：恢复备份 JAR 并重启 Plugin Server/Relayer；
- 配置回滚：用 Relayer CLI 恢复备份的链配置；
- 链上合约不删除；如需恢复旧运行码，必须使用已审核的旧 runtime 并保留原地址/存储布局。
- 回滚后重新执行最小 V1 普通与监管双路径验收。

## 8. 已知边界

- Dioxide 当前只支持原生 SDP V1，本次不通过改写版本字段伪造 V2/V3。
- Dioxide2 的 PTC 降级只对已保存 BTA 的`product=dioxide2`生效；product 名不能当作 sender
  domain。其共识状态和最小 subject identity 仅用于维持 PTC 结构，不提供生产级真实性保证。
- Dioxide2 目标链只判断 monitor 签名非空且不全为零；Dioxide2 发往 Ethereum3 时，Ethereum3
  仍会完整验证 TpBTA、普通委员会签名与 monitor 签名。
- Mychain Maven 单测在没有私有 GitHub Packages 凭据时会因 Mychain SDK `401` 无法解析；发布时仍需使用生产插件依赖完成 Java 编译，并使用官方 MySolidity `0.8.14` 生成部署/runtime 字节码。
- 历史失败队列与本次新发验收交易分开统计，不应用存量失败掩盖或否定新路径的结果。
- Mychain MonitorVerifier 若无法从配置或既有 Monitor 恢复 identity，插件会停止升级而不是自动
  部署空 Verifier；人工替换后必须重新上传所有生效 TpBTA 以恢复 monitor endorsement。

## 9. 2026-08-26 生产复验

- Ethereum、FISCO BCOS 与 Mychain 的 Monitor/SDP/PTC 绑定均为 `DEPLOY_FINISHED`；三条链的
  普通和监管 V1/V2/V3 已分别完成双向实链验证，目标业务合约读取到的 payload 与源消息一致。
- Dioxide 明确保持原生 SDP V1。Dioxide/Dioxide2 插件启用 `State` 终态识别后，新发
  Ethereum `eth04` → Dioxide `diox04` 监管消息生成 UCP
  `148af653290e9d1b2e6d57632f9a1bf18607232350728e30976f395496cfe73d`，目标交易
  `42csksb2dk7em6qz5mayhpagqpcdr9h7rzy7kkj67gntmedcd5mg`。
- 该消息为 SDP V1，PTC 四节点签名权重 100%，监管为 `APPROVED/TARGET_EXECUTED`，四项完整性
  全部为 `true`；目标入口交易和 relay group 均为 `DUS_ARCHIVED/TXN_ARCHIVED`，两级 Invocation
  均为 `IVKRET_SUCCESS`。
- 从 relay group 的业务 body 解出的正文为
  `eth-to-diox-statefix-regulated-1787692602-sdp-v1`，与源交易发送值逐字节一致，证明目标侧收到的
  是 Monitor 解封后的业务内容。

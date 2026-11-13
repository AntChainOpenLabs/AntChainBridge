# 跨链事务时延实验工具

提供 ETH、Mychain、FISCO 的负载发送、Relayer 指标聚合和目标应用逐条接收验收。本轮工具完善只进行离线验收；真链预实验另行安排。工具不部署／升级合约、不切换监管或信任配置、不修改数据库，也不重播交易。

## 实验设计：每种方案、每个方向 5 轮

六个有向路径为 `ETH→MYC`、`ETH→FISCO`、`MYC→ETH`、`MYC→FISCO`、`FISCO→ETH`、`FISCO→MYC`。每个方向分别运行 Base、Xun **各 5 轮**：

- 每轮 5 TPS、30 秒、150 条无序 SDP V1 消息，每条应用载荷恰好 512B。
- 合计 `6 × 2 × 5 = 60` 轮、`60 × 150 = 9,000` 条计划发送样本，不是系统全部链上交易总数。
- 第 1 条在 0 秒调度，第 150 条在 29.8 秒调度；不是瞬间发送。默认 16 个工作线程，实际 SDK 调用可能排队，超时／失败不补发。
- `experiment.round` 为本次单轮编号。正式模式仅允许 `1..5`，一次命令仅发送一轮，不自动执行整套实验。
- 每轮终态与接收验收结束、保存原始证据后再安排下一轮，不并行注入不同路径。

| 方案 | Ethereum | Mychain | FISCO |
| --- | --- | --- | --- |
| Base | `ethereum2` | `mychain` | `fiscobcos` |
| Xun | `ethereum3` | `mychain2` | `fiscobcos3` |

工具检查路由、方案和两端产品是否一致。Base 必须使用预先部署的独立普通链路，不能用 `MONITOR_CLOSE` 代替。两种方案可使用相同节点，但应用／系统合约及注册配置须分别核对。本工具不增加 Dioxide 正式实验适配器。

## 构建与离线检查

发送、查询和验收工具使用 Java 21；Relayer 与 Mychain 插件的构建沿用 Java 8。SDK 依赖应事先准备好；不在实验期间升级 SNAPSHOT 依赖。

```shell
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH \
mvn -o -f tools/paper-experiment/pom.xml package
python3 -B -m unittest discover -s tools/paper-experiment -p 'test_*.py' -v
```

输出可执行工具 `tools/paper-experiment/runner/target/paper-experiment-runner-1.0-SNAPSHOT-all.jar`，包含三个链 SDK。首次准备依赖时仍需将 Ethereum3、Mychain、FISCO Base/Xun 插件安装至本地 Maven 仓库；ETH/FISCO 发送及验收按已有应用 ABI 工作，不自动部署合约。各适配器作为库使用，命令统一进入 `ExperimentCli`。

正式环境显式配置 Relayer：

```yaml
relayer:
  platform_report:
    enabled: false
  experiment_metrics:
    enabled: true
```

指标输出 `${logging.file.path}/antchain-bridge-relayer/experiment-metrics.log`，每行含 `EXPERIMENT_METRIC `。埋点默认关闭，外部平台上报默认开启，须在实验环境另行配置。Xun 使用受控监管模拟器：

```shell
JAVA_OPTS="-Dmonitor.verify.delay.ms=500" ./SimpleMonitorSystem/bin/start.sh
```

正式 Xun 实验固定等待 **500ms** 并返回通过，延迟仅作用于监管 verify，不是整个链下阶段的固定耗时。保留模拟器实际启动命令和日志；此前 1000ms 的历史记录保持原条件，不混入本轮正式结果。正式实验前测量发送端和 Relayer 的时钟偏差；`formal=true` 要求两者配置值均在 ±20ms 内，工具不会代替 NTP 实测。

## 配置与发送前检查

复制 `experiment.properties.example` 到仓库外私有目录。填写两端产品／链 ID／域名／应用、私有 SDK 配置路径和当前代码提交、插件制品路径。清单只保存公开身份、载荷、代码版本及制品 SHA-256，不写 SDK 配置正文、私钥或完整签名交易。

Mychain 两端应用必须显式设置 `source.app.mode`／`target.app.mode`：

- `name`：使用当前链配置的哈希算法计算合约 Identity。
- `identity`：接受完整 32 字节十六进制值，可带 `0x`，直接调用 Identity 重载，禁止再次哈希。
- 不自动猜测名称或 Identity。查询确认 EVM 应用存在；Mychain 源应用还会对首条完整发送参数执行 `setLocal()` 模拟，失败则零发送。

ETH 源配置要求 `ethNoncePolicy=FAST`。ETH/FISCO 应用使用 20 字节地址或合法零填充身份。目标事件配置：

| `target.event.profile` | 应用接收事件 |
| --- | --- |
| `evm-app` | ETH/FISCO `recvCrosschainMsg(string,bytes32,bytes,bool)` |
| `mychain-legacy` | Mychain `amNotify(string,identity,string)` |
| `mychain-monitor` | Mychain `UnorderedMessageReceived(string,identity,bytes,uint256)` |

先只读检查，输出目录必须不存在：

```shell
java -jar tools/paper-experiment/runner/target/paper-experiment-runner-1.0-SNAPSHOT-all.jar \
  check /secure/experiment.properties /data/check-round-01
```

`check` 只查询／本地模拟，不占用正式逻辑轮、不发交易。`send` 会再次执行检查。

## 单轮发送与不可覆盖记录

```shell
java -jar tools/paper-experiment/runner/target/paper-experiment-runner-1.0-SNAPSHOT-all.jar \
  send /secure/experiment.properties
```

`experiment.state.dir` 是持久化运行登记目录，同一实验所有轮次必须共用它。逻辑轮键为 `experiment.id + scheme + route + round`，在任何 SDK 初始化前独占登记；换输出目录不能重复运行同一逻辑轮。输出目录必须不存在。失败登记保留，不自动删除、清空或解锁；重新安排实验应使用新的逻辑轮／实验编号，并保留原失败记录，不能伪装成原轮续跑。

输出顺序：

1. `run-plan.json`：运行 UUID、全体样本序号／attempt ID、原始载荷 Base64 和 SHA-256。
2. `run-manifest.json`：检查成功后固定解析后的应用 Identity、目标确定区块起点；在首次发送前落盘。
3. `attempts.jsonl`：每笔 SDK 调用前落盘 `STARTED`，调用结果追加落盘。
4. `sender.jsonl`：发送结果和原始 `t0`。回执证实失败为 `SEND_FAILED`；结果不明为 `UNKNOWN`，保留已知原哈希；不自动重试。
5. `run-complete.json`：所有发送任务正常收集完结果后独占创建。它不代表目标链已接收成功。启动或执行异常时尽力独占保存 `run-stopped.json`，不代替原交易结果。

任何已有文件均拒绝覆盖。意图落盘失败不得进入该笔 SDK 发送。中断可能留下只有意图、没有哈希／原始 `t0` 的样本；这些不补造、不补发，正式轮判无效。

## 中断后的只读对账

```shell
java -jar tools/paper-experiment/runner/target/paper-experiment-runner-1.0-SNAPSHOT-all.jar \
  reconcile /secure/experiment.properties /data/original-round /data/reconcile-round-01
```

对账只查询日志中已知的原哈希及原生收据，输出新的 `reconciliation.jsonl` 和摘要，原始记录逐字节保留。哈希未知、RPC 不可用或暂时查不到交易时保持 `UNKNOWN`；未开始的样本为 `NOT_SENT`。允许读取有效日志前缀，但不截断／修复原文件。对账不能将中断轮恢复成有效正式轮，没有续发、签名或重播入口。

## 目标应用接收证据采集

所有样本有明确源结果并在 Relayer 达到终态后，再观察至少 60 秒，扫描目标应用从发送前起点至当前确定区块的完整区间：

```shell
java -jar tools/paper-experiment/runner/target/paper-experiment-runner-1.0-SNAPSHOT-all.jar \
  verify /secure/experiment.properties /data/original-round /data/reception-round-01 \
  /data/experiment-metrics.log
```

可在末尾列出多个轮转指标文件。终态等待由 `terminal.wait.seconds` 配置控制，代码默认 900 秒；涉及 ETH 的方向设置为 `1500`（25 分钟），示例配置采用该值，之后再观察 60 秒。查询原生区块／收据，记录应用、源域、作者、事件位置、原始载荷及哈希；Mychain／FISCO 使用其确定性共识提交高度，ETH 使用 RPC `finalized`。拒绝以最新但未确定区块替代 ETH finalized。

输出 `receptions.jsonl` 和 `reception-coverage.json`。扫描缺失、RPC 异常、基线／区块哈希变化均不输出完整通过。getter 仅作人工辅助；一次接收的结论限定于记录的确定区块观察区间，不能证明无限未来不会发生重复。旧 Mychain `amNotify` 本身不区分有序／无序，因此以固定的源端 unordered V1 调用及原目标收据关联为前提，记录 `orderEvidence=SOURCE_UNORDERED_V1`；其他接收事件记录 `APP_EVENT`，不伪造旧事件不存在的顺序标志。

## 严格聚合与分层结论

```shell
python3 tools/paper-experiment/aggregate_latency.py \
  --manifest /data/original-round/run-manifest.json \
  --sender /data/original-round/sender.jsonl \
  --completion /data/original-round/run-complete.json \
  --relayer /data/experiment-metrics.log \
  --receptions /data/reception-round-01/receptions.jsonl \
  --coverage /data/reception-round-01/reception-coverage.json \
  --output-dir /data/final-round-01 --expected-samples 150
```

每个输入参数可接多份对应文件，支持汇总 5 轮。重复传入同一发送文件、重复样本、缺失／额外样本、源交易／UCP 一对多或多对一、清单冲突均明确报错。相同链上事件／状态日志的重复读取可以去重；不同事件位置或不同目标交易中的重复接收不得去重。允许同一目标交易接收多个不同样本。

输出归档输入记录、`transactions.csv`、`diagnostics.csv`、`summary.csv`、`pooled-summary.csv`、`validation.json`。原始输入不改写，输出目录也必须是新的。结果字段：

- `protocolStatus`：源发送、Relayer 关联、目标交易确认和时延是否成立。
- `applicationStatus`：应用正确、来源正确、无序语义与原始 512B 内容匹配，且观察区间内只接收一次。
- `protocolValidRun` 与 `validRun` 分别统计协议和完整验收；有效轮必须样本完整唯一、全部协议成功和应用验收通过、无调度超限、无数据冲突。`formalRun`／`formalValidRun` 额外标记是否通过正式模式与 150 条检查；非正式预实验不能凭 `validRun` 充作正式结果。
- `pooled-summary.csv` 以有效轮的轮均值平均值作为主表指标；5 个正式有效且不同的轮号 `1..5` 才标记 `completeGroup=true`。无效轮和剔除原因均保留。

校验失败退出码为 `2`，通过为 `0`。旧版日志必须显式使用 `--allow-legacy --sender ... --relayer ... --output-dir ...`，仅输出协议历史统计和 `LEGACY_UNVERIFIED`，永远不生成新版 `validRun=true`。不自动把历史记录补成正式证据。

## 时间定义与解释范围

`t0` 为发送线程进入源 SDK 前的实际时间，发送意图落盘在它之前；`t1` 沿用 UCP INSERT 返回且数量检查通过后的埋点（不是事务 COMMIT 时间）；`t2` 为调用目的链插件前捕获的时间；`t3` 为 Relayer 首次确认目标交易执行成功的时间。

`Ts=t1-t0`、`To=t2-t1`、`Tt=t3-t2`、`Ttotal=t3-t0`。后续应用验收和额外 60 秒观察不改变 `t3`。空闲降频、调度和监管等待仍计入原有时延，本轮不修改这些行为。

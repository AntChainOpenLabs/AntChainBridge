# 数据库升级

已有数据库不会重复执行 `ddl.sql` 中的表定义。升级中继服务前，请按版本顺序手动执行本目录中的 SQL 脚本，并在执行前备份数据库。

## 2026-08-20

执行 `20260820_anchor_process_lane_index.sql`，使不同 TPBTA lane 的锚定任务能够分别保存进度。

## 2026-10-12

`20261012_expand_validated_consensus_hash.sql` 保留早期将哈希列扩展到 128 个字符的方案，不再作为本分支 Dioxide 接入的必需升级步骤。

当前委员会及监管节点对 `dioxide2`／`dioxide` 使用 SHA-256 数据库查询键，完整原始状态保留在 `raw_vcs`；由委员会 PTC 自行保存验证状态时，不需要因此扩容 Relayer 的表。不要自动执行该历史脚本；已有数据转换或其他 PTC 存储方式应单独评估。说明及显式维护工具见仓库根目录的 `tools/dioxide-consensus-hash/README.md`。

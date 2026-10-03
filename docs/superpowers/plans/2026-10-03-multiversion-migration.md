# ContentStudio 多版本迁移计划

Spec: ../specs/2026-10-03-multiversion-migration-design.md

全局约束见设计；保留六个既有修改文件与11个 JUnit 测试类。

## Task 1: Forge 构建架构
保存补丁与旧 JAR，在迁移分支基于已验收 EntityControl 通用脚本引入 Stonecutter/MDG/Java21。删除 Entity 专属依赖与资源处理，使用 JEI；仅启用 Forge。迁移 CI 与节点属性、JAR 命名；保留 JUnit 平台及 launcher。
Interfaces: 共用节点、核心解析、Mixin/reobf/架构检查供 Task 2 使用。
Verification: gradlew :1.20.1-forge:build --offline；JAR 检查；原有测试结果；旧 JAR 指令/资源对比。Expected: 0 构建/测试失败，Java65、JAVA_17、refmap、原 Core 范围保留；Forge 业务指令保持。

## Task 2: NeoForge 移植
启用1.21.1，运行 compileJava 收集真实 API 差异。以条件分支保留 Forge，实现原生组件配置/编辑/解析及 RecipeHolder/Codec、战利品注册表、村民交易持久化、JEI API。先增加有意义的组件与持久化回归，复现失败再修复，保留原有11个测试类。
Interfaces: Task 1 的共用架构与检查，Task 3 消费双节点产物。
Verification: buildAll --offline；JUnit；独立运行验证插件通过现有 PCL2 加载世界。Expected: 启动与 Mixin 链接正常，原生组件的配方/战利品/交易保留信息，旧物品NBT拒绝。

## Task 3: 验收与本地提交
最终构建、产物检查、基线对比、独立代码审查、修复重要问题并复验。记录未覆盖范围与 26 未启用。清理运行测试插件，保留发布产物，写验收报告，git diff --check 后本地提交。
Verification: buildAll、Verify-ReleaseJar、报告、git status。Expected: 两个节点通过；本地提交无推送；26仅预留。

Review Focus: 原生组件不能被漏掉/转换为旧NBT；配置和编辑器字段一致；配方移除原始目录语义/后续脚本不被误删；战利品递归追加只执行一次；交易自定义标记和禁止补货随存档保留；Forge 分支不得改变玩法。

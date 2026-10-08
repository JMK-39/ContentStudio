# Native recipe validation — 2026-10-09

## English

This run used the existing installed Minecraft/Forge/NeoForge files with disposable copies of the user's game profiles and save. No Minecraft version was downloaded. The recipe-viewer mods JEI, EMI and REI were absent.

For each editable serializer type, one real datapack recipe was changed through ContentStudio's actual client/server packets, saved, reloaded, checked in the live recipe manager, reopened to verify lossless JSON preservation, and restored. A native codec fingerprint (NeoForge) or native recipe wire fingerprint (Forge) had to change and then return to its original value. Samples whose own serialization is unstable were skipped in favor of stable samples. This verifies native editing and application, not every recipe or a physical production cycle in every machine.

Cells below are **passed serializer types / unverified serializer types**. A dash means no matching dependency was included in this run, not a claim that no community port exists. There are 26 reference mod families and 54 family/version combinations with tested recipes. Library or bundled-mod serializers also appear in the raw reports.

| Mod family | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 |
| --- | --- | --- | --- |
| `ae2` | 5 / 1 | 9 / 3 | 9 / 3 |
| `alloy_smelter` | 1 / 0 | 1 / 0 | — |
| `ars_nouveau` | 13 / 0 | 16 / 2 | — |
| `avaritia` | 7 / 1 | 7 / 1 | 7 / 1 |
| `cataclysm` | 2 / 0 | 2 / 0 | — |
| `confluence` | — | 15 / 1 | — |
| `create` | 15 / 2 | 15 / 2 | — |
| `draconicevolution` | 1 / 0 | 1 / 0 | — |
| `eidolon` | 8 / 1 | 9 / 1 | — |
| `enderio` | 10 / 1 | 11 / 0 | 11 / 0 |
| `extendedcrafting` | 3 / 1 | 3 / 1 | 3 / 1 |
| `farm_and_charm` | 6 / 0 | 8 / 0 | — |
| `farmersdelight` | 2 / 2 | 2 / 2 | — |
| `goety` | 6 / 0 | 8 / 0 | — |
| `iceandfire` | 1 / 0 | — | — |
| `immersiveengineering` | 26 / 11 | 26 / 11 | — |
| `industrialforegoing` | 6 / 0 | 6 / 0 | — |
| `irons_spellbooks` | 3 / 0 | 3 / 0 | — |
| `justdirethings` | — | 5 / 0 | 4 / 0 |
| `kaleidoscope_cookery` | 10 / 0 | 10 / 0 | — |
| `kaleidoscope_tavern` | 3 / 0 | 3 / 0 | — |
| `mekanism` | 27 / 2 | 26 / 3 | — |
| `mysticalagriculture` | 7 / 1 | 7 / 1 | 8 / 1 |
| `spore` | 4 / 0 | 1 / 1 | — |
| `tacz` | 1 / 0 | — | — |
| `touhou_little_maid` | 1 / 0 | 1 / 0 | — |

Totals, including bundled/library serializers: Forge **173 passed, 23 unverified**; NeoForge 1.21.1 **200 passed, 30 unverified**; NeoForge 26.1.2 **45 passed, 6 unverified**. There are no failed cases in the final per-type reports. Unverified types exposed no stable serializer-visible editable primitive in the available samples; they are not marked as working. See each JSON report for IDs, changed fields and reasons.

The final regression run additionally checks stale drafts, invalid serializers/conditions, unknown data preservation, copy collisions including disabled datapack IDs, live output quantities, restoration, abandoned-page replies and malformed Advanced drafts. 26.1.2 also rejects output templates exceeding the item's maximum stack size before writing. GUI captures cover English and Simplified Chinese at 854×480 and 1920×1080 with automatic GUI scaling, actual mod samples and search/group navigation. Delivery artifact hashes are recorded separately. Forge and 1.21.1 broad matrices preceded the final review safeguards and were followed by focused regressions; the 26.1.2 broad matrix was rerun after the output-template safeguard. Final native fluid/chemical sprite checks run separately on all three nodes, including untinted lava on 26.1.2. The 26.1.2 fluid-model API and its nullable tint source were reproduced as failing runtime fixtures before correction. Compact pass markers and unit-test counts are in `runtime-checks.json` and `unit-tests.json`; full local game logs and launch arguments are excluded. The representative hub screenshots use the opt-in long-label fixture, which is absent from release JARs.

Known scope: only valid original datapack JSON and saved native overrides enter the editor. Special/dynamic, runtime-only and synthetic viewer recipes may not expose editable fields. Unknown diagram types use a labelled generic preview; there is no promise of automatically reproducing every mod's custom renderer. Each Minecraft version keeps its native schema. Shared panel and control geometry remains identical; the native font rasterizer can look rougher at fractional canvas scales, especially Forge at 854×480.

## 简体中文

本轮使用用户已经安装的 Minecraft、Forge 和 NeoForge 文件，并在整合包目录内使用一次性的配置与存档副本；没有下载新的游戏版本。测试环境未安装 JEI、EMI 或 REI。

每种可编辑解析器选取一条真实数据包配方，通过实际客户端与服务端消息修改、保存、重载，检查运行中的配方管理器，重新打开并核对完整 JSON，最后恢复。NeoForge 使用原生 Codec 指纹，Forge 使用原生配方网络序列化指纹，要求修改后发生变化、恢复后与原值一致。自身序列化不稳定的样例会换成稳定样例。这验证的是原生配方编辑与应用，不代表逐条测试了全部配方或逐台运行了机器生产流程。

上表每格表示 **通过的配方类型数 / 未验证的类型数**。破折号表示本轮未安装对应依赖，不代表不存在社区移植版。参考范围为 26 个模组系列，共验证了 54 个模组与游戏版本组合；原始报告还包含前置库和捆绑模组的配方类型。

含前置库与捆绑模组在内：Forge 通过 **173** 种、未验证 **23** 种；NeoForge 1.21.1 通过 **200** 种、未验证 **30** 种；NeoForge 26.1.2 通过 **45** 种、未验证 **6** 种。最终逐类型报告中没有失败项。未验证项的可用样例没有暴露稳定且能影响原生序列化结果的可编辑字段，因此没有把它们计作成功。各 JSON 报告列出配方 ID、修改字段与原因。

最后的回归还检查过期草稿、无效解析器与条件、自定义数据保留、复制 ID 冲突（包括已禁用数据包配方）、实际产物数量、恢复、离开页面后的迟到响应，以及无效高级草稿返回预览。26.1.2 额外检查超出最大堆叠数量的产物模板会在写入前被拒绝。界面截图覆盖英文和简体中文、854×480 和 1920×1080、自动 GUI 缩放，使用真实模组样例并验证搜索与分组跳转。交付产物哈希单独记录。Forge 与 1.21.1 的大范围矩阵先于最后的复查保护，之后运行了针对性回归；26.1.2 在补充产物模板校验后重新运行了大范围矩阵。最后的原生液体与化学物质图标检查在三个节点分别运行，包括 26.1.2 无着色的岩浆。26.1.2 液体模型接口与可为空的着色来源问题均先由运行时用例复现失败，再修复。精简通过记录与单元测试数量见 `runtime-checks.json` 和 `unit-tests.json`；完整本地日志与启动参数不会提交。示例主页截图使用专门的长文本验证组件，发布 JAR 不包含该组件。

范围限制：编辑器读取有效的原始数据包 JSON 与已保存的原生覆盖；动态特殊配方、仅运行时存在的配方和查看器虚拟配方可能没有可编辑字段。未知布局使用明确标注的通用预览，不承诺自动复刻每个模组的专用渲染器。各游戏版本保留自己的原生数据格式。共用面板与控件几何布局保持一致；原生字体在画布非整数缩放时可能较粗糙，尤其是 Forge 的 854×480 窗口。

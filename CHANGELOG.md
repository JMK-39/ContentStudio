# Changelog / 更新日志

## 26.10.4 — 2026-10-04

### English

- Enabled Minecraft 26.1.2 / NeoForge 26.1.2.112 (Java 25) with JEI 29.43; releases now cover Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2.
- Villager trades on 26.1.2 stay on the existing config, editor and storage: 26.1 keeps vanilla trades in data-driven trade sets and NeoForge removed the trade events, so Mixins on villagers and wandering traders apply the configured groups, custom offers and vanilla-trade switches when offers are rolled. Vanilla offers are built from the profession's trade set like vanilla does; the wandering trader's common and uncommon sets are levels 1 and 2, and its new buying set stays vanilla.
- Clients have no trade sets on 26.1.2, so the server sends the trade editor its vanilla trade previews before the editor opens, on dedicated and integrated servers alike.
- Recipes on 26.1.2: removal rules filter datapack recipe JSON before scripts change it, configured recipes join the loaded recipe map before the server finalizes it, and recipe summaries come from recipe displays. While datapacks reload, a recipe's output item is read from its display or JSON result and its tags from the staged tag contents. The server sends every recipe type to clients so the removal page and JEI integration see the same recipes.
- Loot editing on 26.1.2 uses the reloadable loot registries, optional loot table keys and the loot table codec.
- Fixed on 1.21.1 and 26.1.2: ContentStudio's own recipe bundle shares the recipe folder and was inspected as an unparsable recipe; it is now skipped.
- 26.1.2 rejects damageable stackable items in trade and recipe components like command-given items; its item parser no longer checks this.
- The JUnit suites keep running on 1.20.1 and 1.21.1 (36 and 56 tests). They boot Minecraft through a stand-in mod list that FancyModLoader 11 does not support, so 26.1.2 is checked in a running server: 31 runtime checks pass, including recipe removal, configured recipes and restore across datapack reloads, network encoding of every recipe, loot replacement and trade previews; the same fixture passes 30 checks on 1.21.1. The client GUI validation is not ported to 26.1.2 yet.

- Keep long editor labels, headings, names and IDs inside their layout bounds, using the shared scrolling text API. Short text retains its original alignment. Applies to villager trades, follow items, loot, recipes, item components and tooltip editors.
- Separate Save and Back in the global loot removal editor and keep its counters clear of the panel border.
- Require KineticCore 26.10.4+ and let the shared tooltip API wrap follow-item tooltip components to the available screen width. Very tall tooltips still need a central Core follow-up; see docs/tooltip-height-follow-up.md.
- Both enabled nodes pass offline builds and existing checks. Reviewed real English/Chinese GUI captures at 854×480 and 1536×864, with automatic GUI scale and extra long translation probes.

### 简体中文

- 启用 Minecraft 26.1.2 / NeoForge 26.1.2.112（Java 25），JEI 为 29.43；发布版本覆盖 Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2。
- 26.1.2 的村民交易沿用现有配置、编辑器与存储：26.1 把原版交易放在数据驱动的交易集中，NeoForge 也移除了交易事件，因此由村民与流浪商人的 Mixin 在生成交易时应用配置的交易组、自定义交易与原版交易开关。原版交易按原版方式从职业交易集生成；流浪商人的普通与稀有交易集对应 1、2 级，新增的收购交易集保持原版。
- 26.1.2 的客户端没有交易集，因此服务端会在交易编辑器打开前发送原版交易预览，独立服务端与单人游戏均如此。
- 26.1.2 的配方：移除规则在脚本修改之前过滤数据包配方 JSON，配置配方在服务端完成配方加载前加入配方表，配方摘要来自配方展示。数据包重载期间，配方产物从展示或 JSON 结果读取，产物标签从待应用的标签内容读取。服务端向客户端发送所有配方类型，让移除页面与 JEI 联动看到同样的配方。
- 26.1.2 的战利品编辑使用可重载战利品注册表、可选战利品表键与战利品表编解码器。
- 修复 1.21.1 与 26.1.2：ContentStudio 自己的配方包与配方共用目录，此前会被当作无法解析的配方检查，现在跳过。
- 26.1.2 的物品解析器不再检查可损坏且可堆叠的物品，现在交易与配方组件像命令给予的物品一样拒绝这种组合。
- JUnit 测试继续在 1.20.1 与 1.21.1 运行（36 与 56 项）。它们通过替身模组列表启动 Minecraft，FancyModLoader 11 不支持这种方式，因此 26.1.2 在实际服务端中检查：31 项运行时检查通过，包括数据包重载中的配方移除、配置配方与恢复、所有配方的网络编码、战利品替换与交易预览；同一套检查在 1.21.1 通过 30 项。客户端 GUI 验证尚未移植到 26.1.2。
- 村民交易、跟随物品、战利品、配方、物品组件和提示编辑器的长标签、标题、名称及 ID 现在使用核心滚动文字 API，限制在各自布局范围内；短文字保持原有对齐。
- 分开全局战利品移除页的保存与返回按钮，让计数文字避开面板边框。
- 要求 KineticCore 26.10.4+；跟随物品悬浮提示交给统一 API 按屏幕可用宽度换行。过高提示仍需核心后续处理，复现见 docs/tooltip-height-follow-up.md。
- 两个启用节点通过离线构建及现有检查；已在真实客户端、自动 GUI 缩放下检查中英文 854×480 与 1536×864 截图，并增加超长翻译验证。

---

2026年10月04日 — Language key validation / 语言键一致性检查

- Require identical authored English/Chinese keys and string values in source, version overrides and packaged resources; generated formatting keys are rejected during builds.

- 强制检查源码、版本覆盖与最终资源的中英文完整键名一致、值为字符串；构建禁止派生格式语言键。

---

## 26.10.3 — 2026-10-03

### English

- Migrated to a shared Stonecutter source tree and ModDevGradle (MDG) builds for Minecraft 1.20.1 / Forge and 1.21.1 / NeoForge, both using Java 21. The 26.1.2 node remains reserved and disabled.
- Release JARs now identify loader, Minecraft version, and mod version: `contentstudio-<loader>-<minecraft>-<version>.jar`. Required KineticCore is 26.10.3+; optional JEI is 15.20+ on Forge and 19+ on NeoForge.
- Added native NeoForge item component parsing and editing, component-aware recipe ingredients and outputs, `RecipeHolder` handling, and recipe codecs. Forge retains its item NBT workflow; no old item-NBT converter is provided.
- Adapted NeoForge loot to reloadable loot registries, registry-aware codecs, native `set_components`, and component-patch removal modes. Adapted trades to native component stacks and payment predicates.
- Moved the detailed bilingual tutorial to local Wiki drafts, corrected installation requirements, and documented version-specific syntax and parameter names. The Wiki is pending publication.

- Preserved numeric types in custom component data and trade IDs/no-restock settings across native offer save/load and copy. Fixed component validation after changing the selected item.
- Offline builds pass for both enabled nodes; 36 Forge and 56 NeoForge tests pass, with 24 real 1.21.1 runtime checks including datapack reload.

### 简体中文

- 迁移到 Stonecutter 共享源码树与 ModDevGradle（MDG）构建，启用 Minecraft 1.20.1 / Forge 和 1.21.1 / NeoForge 两个节点，均使用 Java 21。26.1.2 节点仍仅预留、未启用。
- 发布 JAR 文件名现包含加载器、Minecraft 版本和模组版本：`contentstudio-<加载器>-<Minecraft版本>-<模组版本>.jar`。必需 KineticCore 26.10.3+；可选 JEI 在 Forge 为 15.20+，在 NeoForge 为 19+。
- 新增 NeoForge 原生物品组件解析与编辑、组件配方材料和产物、`RecipeHolder` 处理及配方 codec。Forge 保留物品 NBT 流程，不提供旧物品 NBT 转换器。
- NeoForge 战利品适配可重载战利品注册表、带注册表上下文的 codec、原生 `set_components` 与组件补丁移除模式；交易适配原生组件物品堆与支付谓词。
- 将详细双语教程移至本地 Wiki 草稿，修正安装要求，并说明分版本语法与参数名称。Wiki 待上线。

- 保留自定义组件数据的数值类型，并使交易 ID 和禁止补货设置随原生交易存档、读取及复制保留；修复切换所选物品后的组件校验。
- 两个已启用节点均通过离线构建；Forge 36 项、NeoForge 56 项测试通过，1.21.1 的 24 项实际运行检查（含数据包重载）通过。

---

2026年10月02日 13时53分

- Recipe preview pages now pass KineticGraphics through the shared preview interface. Native rendering is unwrapped only inside the JEI adapter.
- Enabled addon architecture validation without source-level warning suppressions.
- Full build, 36 recipe workflow tests, final-JAR reference checks, and development-client startup pass.

- 配方预览页面通过共享预览接口传递 KineticGraphics，仅在 JEI 适配器内解包原版绘制上下文。
- 接入附属架构验证，未添加源码级警告抑制。
- 完整构建、36 项配方流程测试、最终 JAR 引用检查和开发客户端启动验证通过。

---

2026年09月29日（原记录未标注小时、分钟）

- Added a complete recipe removal workflow with rule editing, previews, and an impact list.
- Updated recipe, loot, villager trade, and follow item editors.
- Updated compatibility with KineticCore 26.9.29.

- 新增完整的配方移除流程，支持编辑规则、预览结果和查看受影响的配方。
- 更新配方、战利品、村民交易和跟随物品编辑器。
- 更新对 KineticCore 26.9.29 的兼容。

2026年10月04日 — Language key validation / 语言键一致性检查

- Require identical authored English/Chinese keys and string values in source, version overrides and packaged resources; generated formatting keys are rejected during builds.

- 强制检查源码、版本覆盖与最终资源的中英文完整键名一致、值为字符串；构建禁止派生格式语言键。

---

# Changelog / 更新日志

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

# Workstation recipe expansion — 2026-10-09

## English

This extension adds dedicated native recipe previews for Powah, Youkais' Feasts and Twilight Forest. Their own JEI category implementations informed the ingredient roles, workstation icons and diagrams. ContentStudio neither loads JEI category classes nor requires a recipe-viewer mod. Cuisine base IDs are resolved through Youkais' Feasts' native ingredient collector; entity previews use KineticCore's 3D preview API. Existing late recipe overrides, removal rules, Advanced Fields and the editor's outer control geometry remain in place.

### Actual save/reload results

Each editable type below was exercised with one real installed recipe through the actual client/server save handler: change a serializer-visible field, save, reload, confirm that the live native recipe fingerprint changes, reopen and compare the complete JSON, then restore and confirm the original fingerprint. This verifies editing and application of the sampled recipes; it does not claim every recipe or a physical production cycle in every machine was tested.

Cells are **passed types / unverified types**. A dash means no corresponding dependency was included in this extension.

| Mod | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 |
| --- | --- | --- | --- |
| Powah | 1 / 0 | 1 / 0 | 1 / 0 |
| Youkais' Feasts | 10 / 0 | 10 / 0 | — |
| Twilight Forest | 3 / 3 | 6 / 7 | — |
| Total | **14 / 3** | **17 / 7** | **1 / 0** |

The per-type JSON files record the recipe ID, changed field and status. Unverified special serializers exposed no editable primitive affecting their native recipe in the available samples; they are not marked as working. Twilight Forest's newer block/entity conversions use data maps rather than native recipe JSON. Synthetic JEI displays and non-recipe systems cannot be made editable merely by finding a JEI category.

Powah 26.1.2 was tested with **7.0.4-alpha**, GuideME **26.1.15-beta** and Cloth Config **26.1.154**. The other two mods were included only on 1.20.1 and 1.21.1. Exact dependency versions, download sources and SHA-256 hashes are recorded in [dependencies.json](dependencies.json). The original broad compatibility matrix remains in the [initial report](../2026-10-09-native-recipes).

### Preview and build checks

- **1920×1080 only**, automatic GUI scaling, English (US) and Simplified Chinese. Forge: 20 pages / 48 captures; NeoForge 1.21.1: 23 / 54; NeoForge 26.1.2: 7 / 22. No 480p or font checks were performed in this extension.
- Actual previews include a five-material mixed-cuisine row, native cuisine base definitions, separate fermentation material/fluid roles, uncrafting's consumed count of eight, and Forge Twilight Forest entity models. Empty fluid stacks encoded as `{}` no longer become question-mark slots. Static items are kept inside their diagram; recipe slots retain native styling.
- `buildAll --offline` and fixture packaging passed for all three enabled nodes, including architecture, language-key parity, Mixin target and release-reference checks. The release JARs contain Java 17 / 21 / 25 bytecode respectively and exclude validation classes. Forge retains `JAVA_17` Mixin compatibility, its refmap and manifest entry.
- Tag inputs cycle real tag members and show a lower-right `#`, retaining quantities and tag identity. Unsaved native, Advanced Fields and vanilla editor fixtures include `minecraft:planks`, a historical paper carrier, a new real-item carrier, and two captures separated by a rotation interval. The fixture checks multiple real alternatives and counted tags; unit tests reject false tag matches inside NBT/components.
- Left-click opens the selector and returns through its real selection/back-navigation callback without losing neighbouring inputs or the output. Right-click opens editing/removal actions. Native primitive fields open a usable editor; removing a shared shaped ingredient clears only its selected physical cell. Menu screenshots supplement the start/rotation captures.
- Forge ran 87 unit tests: 78 passed and nine existing stand-in-loader villager tests were skipped; 1.21.1 passed all 107 tests. Pure ingredient-tag tests load only JSON code, avoiding game-class loading in the stand-in JVM. The existing FML 11 configuration on 26.1.2 disables stand-in-loader unit tests; its native save/reload and preview checks ran in the real game instead. The new empty-fluid, full-row-spacing, tag-identity and single-slot-removal regressions were observed failing before correction.
- Installed Minecraft and loader files were reused with disposable game-directory/save copies inside the user's existing profiles. JEI, EMI and REI were absent. Games were muted and tutorial notifications disabled; only owned test clients were launched. Full local logs, private launch arguments and test fixture JARs are excluded from this report.

Compact runtime markers are in [runtime-checks.json](runtime-checks.json), test counts in [unit-tests.json](unit-tests.json), compiled artifact checks in [delivery-artifacts.json](delivery-artifacts.json), and installed-profile hashes in [deployment.json](deployment.json). [runtime-artifact-comparison.json](runtime-artifact-comparison.json) records the hashes of the runtime-tested and delivered JARs; the final files are byte-for-byte identical.

Representative full-resolution frames include [tag rotation start](forge-tag-start.png), [next tag member](forge-tag-cycle.png), [Vanilla slot menu](forge-vanilla-menu-zh.png), [1.21.1 slot menu](neo21-tag-menu-zh.png), [26.1.2 slot menu](neo26-tag-menu-zh.png), [entity conversion](forge-entity-conversion.png), and [a full cuisine ingredient row](forge-cuisine-row-zh.png).

### Source references

- [Powah energizing category](https://github.com/Technici4n/Powah/blob/1.21.1/src/main/java/owmii/powah/compat/jei/JeiEnergizingCategory.java).
- [Youkais' Feasts cuisine category](https://github.com/Minecraft-LightLand/Youkai-Homecoming/blob/1.21-lite/src/main/java/dev/xkmc/youkaishomecoming/compat/jei/CuisineRecipeCategory.java), [fermentation category](https://github.com/Minecraft-LightLand/Youkai-Homecoming/blob/1.21-lite/src/main/java/dev/xkmc/youkaishomecoming/compat/jei/FermentRecipeCategory.java).
- [Twilight Forest uncrafting category](https://github.com/TeamTwilight/twilightforest/blob/1.21.1/src/main/java/twilightforest/compat/jei/categories/JEIUncraftingCategory.java), [drying category](https://github.com/TeamTwilight/twilightforest/blob/1.21.1/src/main/java/twilightforest/compat/jei/categories/DryingCategory.java). Installed Forge JAR contents supplied its exact legacy recipe schema and texture regions.

Representative captures and overview sheets are stored alongside this report; overview sheets are reduced copies of the full 1080p captures.

## 简体中文

本轮为 Powah、幻想乡乐事和暮色森林增加原生配方专用预览。参考它们自己的 JEI 联动实现确认材料用途、工作台图标和配方布局；ContentStudio 不加载 JEI 分类类，也不要求安装配方查看器。料理基础 ID 使用幻想乡乐事的原生材料收集方法解析，实体使用核心的 3D 模型预览 API。保留现有晚期配方覆盖、移除规则、高级字段和编辑器外层控件布局。

上表每格表示 **通过的类型数 / 未验证的类型数**，破折号表示本轮未加入对应依赖。每种可编辑类型选取一条真实配方，通过实际客户端与服务端保存处理修改字段、保存、重载，确认运行中的原生配方指纹改变，再重新打开核对完整 JSON，最后恢复并确认指纹回到原值。这验证所选样例的编辑与应用，不代表逐条测试所有配方，也不代表逐台运行机器生产流程。

Forge 通过 **14** 种、未验证 **3** 种；1.21.1 通过 **17** 种、未验证 **7** 种；26.1.2 通过 Powah **1** 种。未验证的特殊解析器样例未暴露能影响原生配方的可编辑字段，因此没有计作成功。暮色森林新版的方块与实体转换属于数据映射，不是配方 JSON；JEI 虚拟展示和非配方系统不能仅因存在 JEI 联动就变成可编辑配方。

本轮只使用 **1920×1080**、自动 GUI 缩放、英文与简体中文：Forge 20 页、48 张截图，1.21.1 23 页、54 张截图，26.1.2 7 页、22 张截图。没有测试 480p 或字体。实际预览检查覆盖五材料混合料理、原生料理基础材料、发酵材料与液体分离、消耗八件物品的拆解，以及 Forge 暮色森林的实体模型。空对象形式的液体不再显示问号槽位，静态物品完整放在配方图内，配方槽位保持原生样式。

标签输入轮换真实成员，右下角显示 `#`，保留数量与标签身份。原生预览、高级字段和原版编辑器的未保存草稿用例包括 `minecraft:planks`、历史纸张载体与新选择的真实物品载体，并间隔一个轮换周期再次截图。运行时检查多个真实成员和带数量的标签；单元测试确保不会把 NBT 或组件内部的自定义 `tag` 字段误当作材料。

左键打开选择器，并通过真实选择与返回回调恢复页面，保留相邻材料和产物；右键展开编辑与移除菜单。原生简单字段可进入有效编辑页，删除共用材料的有序配方格子时，只清空所选位置。除初始与轮换截图外，也保留菜单截图。

三个启用节点的离线构建与测试组件打包通过，包括架构、中英文语言键、Mixin 目标和发布引用检查。产物分别使用 Java 17、21、25 字节码；Forge 保留 `JAVA_17` Mixin 级别、refmap 和清单声明，发布 JAR 不含验证类。Forge 共 87 项单元测试，78 项通过，9 项既有的模拟加载器村民测试跳过；1.21.1 的 107 项全部通过。标签提取测试只加载纯 JSON 代码，避免在模拟 JVM 中加载游戏类；26.1.2 现有 FML 11 构建配置禁用了模拟加载器单元测试，改由真实游戏中的保存、重载与预览用例验证。空液体、完整材料行、标签身份与单格移除的回归用例均先复现失败，再修正。

测试复用用户现有游戏和加载器文件，在版本目录内使用一次性游戏目录与存档副本，没有下载 Minecraft。未安装 JEI、EMI 或 REI，测试客户端静音并禁用教程。完整日志、私有启动参数和验证组件 JAR 不提交。26.1.2 使用 Powah 7.0.4-alpha、GuideME 26.1.15-beta 和 Cloth Config 26.1.154；幻想乡乐事、暮色森林仅加入 1.20.1 与 1.21.1 的依赖。具体依赖、逐类型结果、精简运行记录、产物与安装哈希见本目录 JSON；首批大范围适配结果保留在上一份报告中。

`runtime-artifact-comparison.json` 记录实测 JAR 与交付 JAR 的哈希，最终文件逐字节完全一致。上方链接提供标签轮换前后、原版与新版槽位菜单、实体模型和完整料理材料行的原始 1080p 截图；概览图是这些截图的缩小副本。

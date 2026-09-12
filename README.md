# Content Studio

[English](#english) | [简体中文](#简体中文)

## English

### Overview

A visual content-authoring environment for recipes, loot tables, villager and wandering-trader trades, and item tooltips. It is designed for modpack authors and server administrators who want to manage content without hand-editing every data file.

The project is designed around in-game administration. Where a feature changes shared gameplay data or server rules, the server remains authoritative; client-only presentation features stay local to the client. Configuration screens use KineticCore's UI and configuration infrastructure.

### Key Features

- Visual recipe creation, editing, preview and removal for supported vanilla processing types.
- Loot editing for entities, blocks and loot-table containers with weighted pools and removal rules.
- Villager profession/level trade editing and wandering-trader trade control.
- NBT-aware item tooltip rules with normal, Shift and Alt presentation modes.
- Search, filtering, item return/inspection and preview workflows through KineticCore.
- Optional JEI, EMI and REI interoperability for item/recipe discovery.

### Dependencies

| Type | Dependency |
|---|---|
| Required | Forge 47.4.0+ |
| Required | KineticCore 26.9.8+ |
| Optional | JEI 15.20+ |
| Optional | EMI |
| Optional | Roughly Enough Items (REI) |

### Access and Configuration

- Open the KineticCore configuration center with its configured F6 entry and select **Content Studio**.
- Server-owned settings are saved by the server and synchronized where the feature requires client awareness.
- Client-only presentation settings remain local.
- Individual feature areas document their own data/configuration paths below.
- Search, list selection, item/entity inspection, tooltips and return/navigation controls reuse KineticCore UI components where available.

## Detailed Feature Reference

### Recipe Editor

#### Overview

**Recipe Editor** provides an in-game workflow for creating, editing, previewing and removing recipes. Server administrators and modpack authors can manage supported recipe types visually instead of maintaining every recipe by hand in JSON.

Recipe Editor uses a **datapack-driven, server-applied** workflow. Custom recipes and removal rules are stored in KineticCore's dedicated datapack directory and are applied through Minecraft's server datapack reload process.

Data file:

```text
config/kineticcore/datapack/data/contentstudio/recipe_bundle.json
```

The file contains both:

- `recipes`: custom recipes created and managed by Recipe Editor.
- `removals`: recipe-removal rules.

#### Features

##### Visual Recipe Editing

Supported recipe types:

- Crafting Table
- Furnace
- Blast Furnace
- Smoker
- Smithing Table
- Stonecutter

The editor uses visual item slots for recipe inputs and outputs and provides item search, tag ingredients, NBT editing and output-count controls.

Crafting recipes can switch between **shaped** and **shapeless** modes.

##### Items and Tags
Recipe inputs can use either regular items or item tags.

Tag ingredients use full IDs beginning with `#`, for example:

```text
#forge:ingots/iron
#minecraft:planks
```

Paper is used only as the visual placeholder for tag ingredients inside the editor. The actual recipe still uses the corresponding Item Tag.

Item search supports localized names, item IDs, `@modid` and `#tagid` queries.

##### NBT Matching

Input ingredients support three NBT matching modes:

- **None**: match the item type without validating NBT.
- **Weak**: the item must contain the required NBT but may contain additional NBT.
- **Strong**: the item's NBT must match the configured NBT exactly.

An NBT editor is available for editing the required data directly.

Recipe types that support output NBT can optionally preserve the configured NBT on the result item.

##### Managed Custom Recipes

The custom-recipe management screen can:

- Browse recipes managed by Recipe Editor.
- Preview inputs and outputs.
- Edit existing recipes.
- Delete existing recipes.
- Refresh recipe records from the server.
- Display invalid recipe data for repair.

Recognizable invalid slots are shown with error placeholders so they can be replaced in the editor instead of silently hiding the broken recipe.

Unresolved configuration entries that still need to be retained are preserved where possible when other recipes are saved, reducing accidental data loss during visual editing.

#### Recipe Removal Manager

The removal manager browses the server recipe catalog by output item and supports removing either individual recipes or groups of recipes.

Supported removal modes:

- `RECIPE_ID`: remove one exact Recipe ID.
- `OUTPUT`: remove recipes by output item.
- `MOD`: remove recipes by source mod.
- `TAG`: remove recipes by item tag.
- `TYPE`: remove recipes by recipe type.

##### Item-Based Browsing

The item list can be searched by item name, ID, mod or tag. Selecting an item displays its server recipes on the right side.

Recipe outlines show the current state:

- **Yellow**: selected recipe.
- **Blue**: hovered recipe.
- **Red**: disabled by a removal rule.
- **Green**: currently valid.

Detailed state information is shown through hover tooltips instead of permanently occupying list space.

Clicking a Recipe ID only **selects** the recipe. The built-in detailed preview is opened explicitly with the **Preview Recipe** button.

##### Bulk Removal Scope

The removal-scope menu provides quick access to:

- `@Mod`
- `#Tag`
- Recipe Type

These options are used to create or adjust bulk removal rules.

Unsaved removal edits can be reverted. Saving writes the rules to `recipe_bundle.json` and reloads server datapacks so the changes are applied immediately.

#### Recipe Preview
Recipe Editor includes its own recipe preview screen for displaying the selected recipe's inputs, output and basic recipe information.

It also provides optional integration with:

- JEI
- EMI
- REI

None of these recipe viewers is required at runtime.

When only one supported viewer is available, the Recipe Viewer button opens it directly. When multiple viewers are installed, Recipe Editor provides a selector menu.

When JEI is available, Recipe Editor can precisely open the selected recipe where supported. EMI and REI are used to open recipe results for the selected item.

Without any external recipe viewer installed, Recipe Editor's built-in editing, preview and removal features remain available.

#### Recipe Rules
##### Crafting

- Shaped and shapeless recipes are supported.
- Up to 9 input slots.
- Output count range: 1-64.
- Output NBT is supported.

##### Smithing

- Requires template, base and addition inputs.
- All three inputs are required.
- Output NBT is currently not preserved.

##### Furnace

- Single input.
- Cooking time: 200 ticks.
- Experience: 0.

##### Blast Furnace

- Single input.
- Cooking time: 100 ticks.
- Experience: 0.

##### Smoker

- Single input.
- Cooking time: 100 ticks.
- Experience: 0.

##### Stonecutter

- Single input.
- Configurable output count.

#### Save Workflow
Creating, editing or deleting a custom recipe first writes the change to:

```text
config/kineticcore/datapack/data/contentstudio/recipe_bundle.json
```

To avoid repeatedly reloading all server datapacks during a sequence of edits, custom-recipe changes are saved first and then applied with a single datapack reload **after the complete recipe-editing flow is closed**.

Recipe-removal rules are saved to the same data file and trigger a server datapack reload immediately when the removal changes are saved.

During server startup or datapack reload, Recipe Editor reads `recipe_bundle.json` and applies its custom recipes and removal rules to the final server recipe set.

#### Permissions

Recipe Editor is a server administration feature.

Opening the editor and modifying, deleting or applying recipe data requires **Minecraft permission level 2 or higher**.

#### Opening Recipe Editor

Recipe Editor registers a server-managed configuration page with KineticCore.

Open the **Recipe Editor** page from KineticCore's configuration interface to enter the recipe hub.

#### Building from Source

The current source project uses:

- ForgeGradle 6.0.24
- Official Mappings
- Forge 47.4.0+ toolchain

Default local dependency directory:

```text
D:/IDEA_Caches/libs
```

For source compilation, that directory must contain KineticCore and JEI JARs recognized by `localMod()`. JEI is a `compileOnly` dependency in the source project and remains optional at runtime.

Default build output directory:

```text
D:/NEWMODS
```

Generated mod filename:

```text
contentstudio-<version>.jar
```

---

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Recipe Editor** | Recipe creation, editing, removal, and pending changes are stored in the dedicated Recipe Editor datapack. Changes are applied through a server datapack reload after the editor is closed. |
| **Open Recipe Editor** | Open the recipe hub to create, edit, or remove supported recipe types. Empty inputs, empty outputs, and unauthorized saves are rejected. |

#### GUI and Editors
| Item | Description |
|---|---|
| **None** | Does not check NBT data during crafting. |
| **Weak Match** | Checks essential NBT (e.g., Enchantments, Potions). |
| **Strong Match** | Strict NBT comparison (must be identical). |
| **on** | The output item will retain the current NBT data. |
| **off** | The output item will have no NBT data. |
| **Select %s to Remove** | Click an entry below to add it to the removal list. |

#### Editable Options
- Mod ID
- Recipe ID
- Output Item
- Item Tag
- Recipe Type
- Shapeless
- Shaped
- None
- Weak Match
- Strong Match
- All Items
- Inventory
- Equipped
- All
- Left: Clear Slot
- Weak
- Item must have all required NBTs but can have extra.
- Strong
- Item NBT must match exactly.
- No NBT data is checked for crafting.
- Enabled
- Output will have specified NBT.
- Disabled
- Output will be pure vanilla item.

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/datapack/data/contentstudio/recipe_bundle.json`

### Loot Editor

#### Overview

**Loot Editor** is the visual loot-table editing module for this project. It edits server-loaded loot tables for entities, blocks and containers and stores custom overrides in a dedicated configuration file.

#### Key Features

- Entity loot-table editor.
- Block loot-table editor.
- Chest/container loot-table editor.
- Visual loot pool and entry management.
- Rolls, quantities, weights and conditional loot data.
- Runtime override application to server loot tables.
- Global chest append rules.
- Global chest item-removal rules.
- Loot-table exclusion list for global chest rules.
- Server-authoritative save and reload flow.

#### Configuration

```text
config/kineticcore/loot_overrides.json
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Loot Editor** | Loot override rules are managed through dedicated editors for each target type; server permission is checked for reads and saves. |
| **Edit Entity Loot** | Edit entity loot tables, pools, conditions, chances, and override rules. |
| **Edit Block Loot** | Edit block loot tables and override rules. Saved changes follow the editor resource-reload flow. |
| **Edit Container Loot** | Edit loot tables used by chests and structure containers. The server validates targets and save permission. |

#### GUI and Editors
| Item | Description |
|---|---|
| **Confirm Reset** | This restores the current loot table and discards unsaved changes. |
| **Delete the entire pool?** | All rewards, conditions, and functions in this pool will also be deleted. |
| **Global Loot Removal** | Rules here block matching items from the final output of every loot chest; right-click a rule to remove it. |
| **global exclude** | Click a loot table on the left to exclude it; right-click an excluded entry to remove it. |

#### Editable Options
- Override Mode
- Append Mode
- Match Rule
- Item ID
- NBT Only
- Fuzzy NBT
- Exact NBT
- ID Match
- Match the item ID only and ignore NBT.
- Match the same item ID only when the generated stack has any NBT.
- Match the same item ID when its NBT contains all configured NBT conditions.
- Match the same item ID only when its full NBT exactly equals the configured NBT.
- Match: %s
- NBT condition saved

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/loot_overrides.json`

### Trade Editor

#### Overview

**Trade Editor** is the villager behavior optimization and trade-management module for this project. It reduces unnecessary AI work in dense trading halls while providing configurable follow behavior, trade-level protection, and a visual trade editor for villagers and wandering traders.

#### Key Features

- Configurable villager AI check interval.
- Trapped-villager detection to stop pointless pathfinding.
- Minimal merchant updates preserved during trade level-up timers.
- Item-based villager following.
- Visual follow-item editor.
- Profession- and level-based villager trade editor.
- Wandering trader support.
- Add, replace-level, replace-all, and disable-level trade modes.
- Configurable offer count per profession level.
- Enable/disable and weight controls for vanilla trades.
- Full custom-offer parameters including two inputs, output, NBT, uses, XP, multipliers, demand, special price, restock, and weight.
- Server-authoritative persistence and runtime trade-pool publication.

#### Configuration

```text
config/kineticcore/villager.toml
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Enable Item Following** | When enabled, villagers will follow players holding specific items (like emeralds), similar to how animals behave. |
| **Follow Item List** | Open the visual lure-item editor. Add items directly through the item selector, preview their icons, and right-click an item to remove it. |
| **Villager AI Check Interval (Seconds)** | Time between villager AI and pathfinding checks. Default is 5 seconds; minimum is 0.05 seconds. |
| **Trade Level-Up Protection** | When enabled, lobotomized villagers will still perform minimal updates during trade level-up, preventing trading hall villagers from leveling up extremely slowly. |
| **villager** | Edit villager AI check frequency, trade level-up protection, and held-item following directly here. Use the dedicated trade editor for professions, NBT, uses, XP, restocking, and vanilla trade overrides. |
| **Open Villager Trade Editor** | Edit profession/level rules, custom buy/sell items and NBT, uses, XP, price multiplier, restocking, weights, and vanilla trade overrides. The server rechecks permission level 2 when opening and saving. |
| **behavior** | This section contains only server-side villager behavior and performance settings. Trade rules, lure items, and trade overrides are grouped under Villager Editors below. |
| **Villager Editors** | This section groups villager settings that use dedicated GUIs. Lure items use KineticCore's shared item-list editor, while trade rules continue to use the full trade editor. |

#### GUI and Editors
| Item | Description |
|---|---|
| **Back** | Return to the previous screen. This does not save automatically. Click Save first if you want changes to take effect. |
| **clear** | Clear trade content search |
| **Search Trades** | Search trades for the current profession by item name, item ID, NBT, count, level, weight, max uses, or XP. |
| **buy a** | A Input 1 Count: How many first-input items the player must pay. Range: 1~64. |
| **buy b** | B Input 2 Count: How many second-input items the player must pay. Use 0 to disable the second input. |
| **sell** | Reward Count: How many items the player receives after trading. Range: 1~64. |
| **Demand** | The current demand value of this trade. It affects the final price together with the price multiplier. |
| **clear current** | Clear Current: When editing a single trade, only clears the current right-side editor content.<br>It does not clear the whole level or profession. |
| **delete** | Delete the selected trade. Vanilla trades are disabled; custom trades are removed. |
| **save** | Save the trade shown on the right. If a vanilla trade is selected and its contents changed, it is converted to a custom trade. |
| **Add Trade** | Add Trade: Save the current level settings, then jump to a new single-trade editor. |
| **arrow** | Expand / Collapse: Click this small arrow only to expand or collapse the current level trade list. |
| **Clear Level** | Clear Level: Remove this level's custom trades and remove all vanilla default trades from the main list.<br>Removed vanilla trades can be restored by right-clicking them in the removed preview. |
| **row** | Level Row: Left-click to create a new trade for this level; Right-click to clear all trades in this level; Click the small arrow on the left to expand or collapse this level. |
| **Max Uses** | The maximum number of times this trade can be completed before it needs to restock. |
| **nbt** | Edit NBT for this item slot. Leave empty for a normal item. |
| **Pick Count** | Pick Count: How many trades this level will pick from the trade pool. For example, 2 means only 2 weighted trades will appear. |
| **Price Mult** | Controls how strongly demand affects the trade price. Set to 0 to ignore demand-based price changes. |
| **profession** | Click the search box to choose a profession, or type a profession name |
| **Search Profession** | Click to show all villager profession suggestions. After selecting a profession, the left list shows all vanilla and custom trades for it. |
| **Reset Profession** | Reset Profession: Restore removed vanilla trades and delete all custom trades and level settings for the current profession. A confirmation popup will appear. |
| **Restore All** | Restore All: Restore all disabled vanilla trades for the current profession across all levels. Custom trades are kept. |
| **Removed** | Removed Vanilla Trades: Open all removed vanilla trades for the selected profession across all levels. Right-click a trade to restore it. |
| **restock** | When enabled, this trade restocks using vanilla mechanics. When disabled, restocking will not restore this trade. |
| **reward** | When enabled, the player receives vanilla trade XP from this offer. |
| **Save** | Save the current villager trade config to villager.toml.<br>Newly generated or refreshed villager trades will use the new rules. |
| **clear** | Clear Search: Clear the profession search box and unselect the current villager profession. |
| **A Input 1** | A Input 1: The first input item in the vanilla villager trade UI. The player must provide this item to trade. |
| **B Input 2** | B Input 2: The second input item in the vanilla villager trade UI. Leave it empty and set count to 0 if unused. |
| **Reward** | Reward: The final item the player receives after trading. Click the icon to open the item selector. |
| **Special Price** | A direct price adjustment applied to this trade. Negative values can reduce the price. |
| **Uses** | The number of times this trade has already been used. Once it reaches the maximum uses, restocking is required. |
| **Weight** | Higher weight makes this trade more likely to be selected. A weight of 0 prevents it from being selected. |
| **XP** | The profession XP granted to the villager when this trade is completed. |
| **Clear** | Clear this trade item without deleting the whole trade. |
| **Undo %s/%s** | Undo the previous unsaved change (Ctrl+Z) |

#### Editable Options
- Add
- Disabled
- Replace
- A Input 1
- B Input 2
- Reward
- Clear

#### Config Defaults
| Key | Default |
|---|---|
| `villager.follow_enable` | `true` |
| `villager.tickInterval` | `100` |
| `villager.trade_update_protection` | `true` |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/villager.toml`

### Tooltip Editor

#### Overview

**Tooltip Editor** is the visual item-tooltip editor for this project. It provides an in-game editor for KubeJS tooltip rules so pack authors can select items, edit text, and configure display conditions without manually maintaining the whole script.

The server validates the submitted rule database and generates `kubejs/client_scripts/tooltipadd.js`.

#### Key Features

- Central overview of configured items.
- Search by item ID or localized name.
- Append and overwrite modes.
- Target-line selection for overwrite rules.
- No-key, Shift, Alt, and Shift+Alt conditions.
- Multiple rules per item.
- Ctrl + left-drag rule reordering.
- Minecraft `§` formatting codes.
- Item-level configuration removal.
- Server-side validation of IDs, modes, line numbers, key conditions, limits, and text.
- Server-authoritative script persistence.
- Safer temporary-file replacement when writing the generated script.

#### Generated File

```text
kubejs/client_scripts/tooltipadd.js
```

The generated script also acts as the rule database that Tooltip Editor reads back into its editor.

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Item Tooltip Management** | Manage Tooltip rules in the dedicated editor, which generates KubeJS client_scripts/tooltipadd.js so the script does not need manual editing. The server validates administrator permission, item IDs, modes, line numbers, key conditions, and text. |
| **Open Tooltip Editor** | Add overwrite or append Tooltip rules for items, with target line, Shift/Alt conditions, and text. Success is shown only after the server actually writes the script. |

#### Editable Options
- Overwrite
- Append
- Delete this rule

### Building from Source

- Minecraft: `1.20.1`
- Java: `17`
- ForgeGradle: `6.0.24`
- Gradle: the project is pinned to the `8.1.1` Wrapper; do not import it with Gradle 9 directly.
- Local development JARs are controlled by `local_libs_dir` and can be overridden in `gradle.properties` or with a project property.
- Typical build command: `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS.
- Development and release artifacts use `contentstudio` as the current project identifier.

## 简体中文

### 模组定位

面向整合包作者和服务器管理者的可视化内容制作工具，可编辑配方、战利品、村民/流浪商人交易以及物品 Tooltip，减少直接手写数据文件的需求。

本项目以游戏内管理为核心。涉及共享玩法数据、世界规则或服务器规则的功能由服务端权威处理；仅影响显示的客户端功能保持本地生效。配置界面统一使用 KineticCore 提供的 GUI 与配置基础设施。

### 主要功能

- 支持多种原版加工类型的配方创建、编辑、预览与移除。
- 支持实体、方块和容器战利品编辑，可配置权重、池与移除规则。
- 支持村民职业/等级交易以及流浪商人交易管理。
- 支持基于物品与 NBT 的 Tooltip 规则，并支持普通、Shift、Alt 显示模式。
- 通过 KineticCore 提供搜索、筛选、物品返回/识别与预览流程。
- 可选兼容 JEI、EMI 与 REI，用于物品和配方检索。

### 依赖

| 类型 | 依赖 |
|---|---|
| 必需 | Forge 47.4.0+ |
| 必需 | KineticCore 26.9.8+ |
| 可选 | JEI 15.20+ |
| 可选 | EMI |
| 可选 | Roughly Enough Items (REI) |

### 打开方式与配置

- 使用 KineticCore 配置中心对应的 F6 入口，选择 **Content Studio**。
- 服务端规则由服务端保存，并在需要时同步给客户端。
- 纯显示类客户端设置只在本地生效。
- 各功能自己的配置/数据路径在下方详细功能说明中列出。
- 搜索、列表选择、物品/实体信息读取、悬浮提示、返回与导航等操作尽可能复用 KineticCore GUI 组件。

## 完整功能参考

### 配方编辑器

#### 模组简介

**配方编辑器（Recipe Editor）** 是 本项目中的可视化配方管理附属模组，用于在游戏内完成配方新增、修改、预览和移除，不需要手动编写传统配方 JSON。

Recipe Editor 采用 **数据包驱动 + 服务端统一应用** 的方式管理配方。自定义配方与配方移除规则统一保存到 KineticCore 的专用数据包目录，并通过 Minecraft 的服务器数据包重载流程生效。

数据文件位置：

```text
config/kineticcore/datapack/data/contentstudio/recipe_bundle.json
```

该文件同时保存：

- `recipes`：Recipe Editor 创建和管理的自定义配方。
- `removals`：配方移除规则。

#### 主要功能

##### 可视化配方编辑

当前支持以下配方类型：

- 工作台配方
- 熔炉配方
- 高炉配方
- 烟熏炉配方
- 锻造台配方
- 切石机配方

编辑器使用可视化物品槽直接设置材料和产物，并提供物品搜索、标签材料、NBT 编辑、输出数量等功能。

工作台配方可在 **有序 / 无序** 两种模式之间切换。

##### 物品与标签材料

输入材料既可以使用普通物品，也可以使用物品标签。

标签必须使用以 `#` 开头的完整标签 ID，例如：

```text
#forge:ingots/iron
#minecraft:planks
```

编辑器会使用纸作为标签材料的可视化占位图标，但实际合成判定仍然使用对应的 Item Tag，纸本身不会成为配方材料。

物品搜索支持：

- 本地化名称
- 物品 ID
- `@模组ID`
- `#标签ID`

##### NBT 匹配

输入材料支持三种 NBT 匹配模式：

- **无匹配**：只要求物品类型符合，不校验 NBT。
- **弱匹配**：物品必须包含要求的 NBT，但允许存在额外 NBT。
- **强匹配**：物品 NBT 必须与设定内容严格一致。

支持通过 NBT 编辑器直接编辑需要匹配的数据。

对于支持输出 NBT 的配方，可以决定产物是否保留当前设置的 NBT 数据。

##### 已添加配方管理

Recipe Editor 提供独立的新增配方管理界面，用于查看当前由 Recipe Editor 管理的全部自定义配方。

支持：

- 浏览已添加配方
- 查看配方材料与产物
- 编辑已有配方
- 删除已有配方
- 从服务端重新同步配方列表
- 错误配方可视化提示

无法正常解析的配置不会被简单隐藏。能够识别到的错误槽位会使用错误占位物品显示，方便进入编辑器重新选择正确物品后修复。

在保存其他正常配方时，无法解析但仍需保留的配置条目会尽量保留，避免因为一次可视化编辑直接丢失无关配置。

#### 配方移除管理

配方移除界面按物品浏览当前服务器配方，并允许对具体配方或一组配方建立移除规则。

支持五种移除方式：

- `RECIPE_ID`：按完整 Recipe ID 单独移除。
- `OUTPUT`：按输出物品移除。
- `MOD`：按来源模组移除。
- `TAG`：按物品标签移除。
- `TYPE`：按配方类型移除。

##### 按物品浏览

左侧物品列表可以直接搜索物品名称、ID、模组或标签。选中物品后，右侧显示该物品对应的服务器配方。

配方列表使用描边表示当前状态：

- **黄色**：当前选中的配方。
- **蓝色**：鼠标正在悬停。
- **红色**：该配方已被移除规则禁用。
- **绿色**：该配方当前有效。

详细状态通过鼠标悬浮提示查看，不额外占用列表空间。

点击配方 ID 只负责**选中配方**，不会自动打开预览界面。需要查看 Recipe Editor 内置详细预览时，使用 **“预览配方”** 按钮。

##### 批量移除范围

“移除范围”菜单可展开：

- `@模组`
- `#标签`
- `配方类型`

用于快速创建或调整批量移除规则。

配方移除修改在保存前可以撤销；点击保存后写入 `recipe_bundle.json` 并重新加载服务器数据包，使移除规则立即应用。

#### 配方预览与外部配方查看器

Recipe Editor 自带配方预览界面，可查看当前选中配方的输入、输出和基本配方信息。

同时支持以下外部配方查看器作为**可选兼容**：

- JEI
- EMI
- REI

这些模组均不是 Recipe Editor 的运行时必装依赖。

如果只安装一个可用的配方查看器，点击“配方查看器”会直接使用该查看器；如果同时安装多个，则会展开选择菜单供玩家切换。

JEI 可用时，Recipe Editor 可以在支持的情况下精确定位当前选中的配方；EMI 和 REI 用于打开对应物品的配方查看结果。

未安装任何外部配方查看器时，不影响 Recipe Editor 自带的配方编辑、配方预览和配方移除功能。

#### 各配方类型当前规则

##### 工作台

- 支持有序和无序配方。
- 最多 9 个输入槽。
- 输出数量范围为 1～64。
- 支持输出 NBT。

##### 锻造台

- 固定为模板、底材、附加物三个输入。
- 三个输入均不能为空。
- 当前锻造输出不保留输出 NBT。

##### 熔炉

- 单输入。
- 烧炼时间固定为 200 tick。
- 经验固定为 0。

##### 高炉

- 单输入。
- 烧炼时间固定为 100 tick。
- 经验固定为 0。

##### 烟熏炉

- 单输入。
- 烧炼时间固定为 100 tick。
- 经验固定为 0。

##### 切石机

- 单输入。
- 支持自定义输出数量。

#### 保存与应用机制

新增、编辑或删除自定义配方时，修改首先写入：

```text
config/kineticcore/datapack/data/contentstudio/recipe_bundle.json
```

为了避免连续编辑时频繁重载整套服务器数据包，自定义配方的新增、修改和删除会先保存到数据文件中。**关闭完整配方编辑流程后统一执行一次服务器数据包重载并应用。**

配方移除规则则在移除管理界面点击保存后写入同一个数据文件，并立即触发服务器数据包重载。

服务器启动或数据包重载时，Recipe Editor 会重新读取 `recipe_bundle.json`，将其中的自定义配方和移除规则应用到最终服务器配方集合。

#### 权限

Recipe Editor 属于服务器管理功能。

打开编辑器、修改配方、删除配方以及保存配方移除规则需要 **Minecraft 权限等级 2 或更高**。

#### 打开方式

Recipe Editor 会向 KineticCore 注册服务器管理配置页面。

在 KineticCore 的配置界面中进入 **“配方编辑器”** 页面，即可打开 Recipe Editor 的配方编辑中心。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **配方编辑器** | 配方新增、修改、移除与待应用内容统一写入 Recipe Editor 专用数据包。关闭编辑界面后由服务器数据包重载统一生效。 |
| **打开配方编辑器** | 进入配方总览，可新增、修改或移除支持类型的配方。空输入、空输出和无权限保存会被拒绝。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **无** | 合成时不校验任何 NBT 数据。 |
| **弱匹配** | 仅匹配核心 NBT（如附魔、药水效果）。 |
| **强匹配** | 严格比对所有 NBT 标签（必须完全一致）。 |
| **on** | 产出的物品将保留当前的 NBT 数据。 |
| **off** | 产出的物品将不包含任何 NBT 数据。 |
| **选择要移除的 %s** | 点击下方的条目以添加到移除列表。 |

#### 可编辑字段、模式与分类索引

- 模组 ID
- 配方 ID
- 输出物品
- 物品标签
- 配方类型
- 无序
- 有序
- 弱匹配
- 强匹配
- 全部物品
- 背包
- 已装备
- 全部
- 装备
- 左键:清空该槽位
- 物品必须包含要求的所有 NBT，但可以有额外多余的 NBT。
- 物品的 NBT 必须与要求完全一致。
- 合成时不校验任何 NBT 数据。
- 开启
- 成品带有设定的 NBT 数据。
- 关闭
- 成品将作为纯净物品输出。

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/datapack/data/contentstudio/recipe_bundle.json`

### 战利品编辑器

#### 模组定位

**Loot Editor** 是 本项目中的战利品表可视化编辑模块。它直接针对服务器当前加载的 Loot Table 工作，用 GUI 管理实体掉落、方块掉落、容器战利品和全局容器规则，并把自定义覆盖保存在独立配置文件中。

#### 主要功能

- **实体战利品编辑器**：浏览实体及其当前战利品表，并为指定实体新增或覆盖掉落规则。
- **方块战利品编辑器**：针对方块 Loot Table 调整产出内容。
- **容器战利品编辑器**：浏览和修改结构箱、地牢箱等容器使用的战利品表。
- **追加与覆盖思路**：既可以保留原有掉落并追加内容，也可以建立自定义覆盖数据。
- **Pool / Entry 可视化维护**：可以编辑战利品池和条目，而不是直接手写整份 Loot Table JSON。
- **数量、权重与 Roll 数据**：支持对战利品池和条目的抽取参数进行编辑。
- **条件化掉落**：编辑器支持维护与玩家击杀、抢夺、火焰等掉落条件相关的数据。
- **全局容器追加**：可以向符合条件的容器 Loot Table 统一追加奖励池。
- **全局容器移除**：可以从容器战利品中统一过滤指定物品。
- **容器排除列表**：可把不应参与全局容器规则的 Loot Table 加入排除列表。
- **运行时重载**：保存后重新构建服务端 Loot Table 数据，使新规则作用于之后的掉落和容器生成。
- **服务端权威编辑**：客户端只负责显示和编辑草稿，最终保存与 Loot Table 替换由服务器完成。

#### 配置文件

```text
config/kineticcore/loot_overrides.json
```

该文件保存：

- 单独 Loot Table 的覆盖数据。
- 全局容器追加规则。
- 全局容器移除物品列表。
- 全局容器排除 Loot Table 列表。

#### 使用建议

通过 F6 打开 Loot Editor 页面，然后根据目标选择“实体 / 方块 / 容器”编辑器。修改战利品前建议先确认目标真正使用的 Loot Table，尤其是 Lootr、结构模组或会动态替换容器战利品的环境。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **战利品编辑** | 战利品覆盖配置无需手动修改文件。请选择目标类型进入专用编辑器，读取与保存都会在服务器校验管理权限。 |
| **编辑实体战利品** | 编辑实体对应的战利品表、奖池、条件、概率与覆盖规则。 |
| **编辑方块战利品** | 编辑方块对应的战利品表与覆盖规则。保存后按编辑器流程应用资源重载。 |
| **编辑容器战利品** | 编辑箱子与结构容器使用的战利品表。服务器会校验目标与保存权限。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **确认恢复** | 本操作会恢复当前目标的战利品表，未保存修改会丢失。 |
| **删除整个奖池？** | 奖池中的全部奖励、条件和函数都会一并删除。 |
| **全局删除战利品** | 这里的规则会从所有战利品箱最终产物中拦截匹配物品；右键规则可移除。 |
| **global exclude** | 点击左侧战利品表加入排除，右键已排除条目可移除。 |

#### 可编辑字段、模式与分类索引

- 覆盖模式
- 追加模式
- 匹配规则
- 物品 ID
- 仅带 NBT
- NBT 模糊匹配
- NBT 精准匹配
- ID 匹配
- 仅 NBT
- 模糊 NBT
- 精准 NBT
- 只匹配物品 ID，忽略 NBT。
- 匹配相同物品 ID，并且只拦截带有任意 NBT 的物品。
- 匹配相同物品 ID，实际 NBT 只要包含已填写的 NBT 条件即可。
- 匹配相同物品 ID，并要求完整 NBT 与已填写条件完全一致。
- 匹配：%s
- 已保存 NBT 条件

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/loot_overrides.json`

### 交易编辑器

#### 模组定位

**Trade Editor** 是 本项目中的村民行为优化与交易管理模块。它同时解决大量村民集中时的 AI 消耗问题，并提供物品诱导跟随、交易升级保护以及完整的村民 / 流浪商人可视化交易编辑器。

#### 主要功能

- **村民 AI 降频**：可调低村民寻路和检测频率，减少大型交易所中大量村民持续 AI 更新造成的性能压力。
- **困住检测**：当村民被实体拥挤或周围方块限制在狭小空间时，可停止无意义寻路。
- **交易升级保护**：即使村民处于低频更新状态，在职业升级倒计时期间仍执行最低限度的商人更新，避免升级被拖慢。
- **物品诱导跟随**：村民可以像动物一样跟随手持指定物品的玩家。
- **可视化诱导物品编辑器**：直接通过物品图标添加或移除诱导物品，不需要手写 ID。
- **村民交易编辑器**：按职业和等级浏览、添加、修改、删除交易。
- **流浪商人交易编辑**：流浪商人使用独立入口参与同一套交易管理。
- **等级交易池控制**：支持 `追加`、`替换当前等级`、`替换全部`、`禁用当前等级` 等规则。
- **每级抽取数量**：可控制某个职业等级最终从候选池中随机抽取多少条交易。
- **默认交易开关**：可以禁用指定原版交易，也可以恢复被隐藏的默认交易。
- **默认交易权重**：调整默认交易在候选池中的出现权重。
- **自定义交易完整参数**：支持买入 A、买入 B、卖出物品、数量、NBT、最大使用次数、村民经验、价格倍率、需求值、特殊价格、玩家经验、当前使用次数、是否补货和权重。
- **NBT 编辑**：交易输入和输出都可以配置自定义 NBT。
- **运行时交易池更新**：保存后直接更新服务器交易池；新的村民或后续升级生成的交易使用新规则。
- **服务端权威编辑**：客户端只负责界面，实际保存由服务器校验管理员权限后完成。

#### 配置文件

```text
config/kineticcore/villager.toml
```

主要包含：

- 村民 AI 检查间隔。
- 交易升级保护。
- 诱导跟随开关与物品列表。
- 职业 / 等级交易组。
- 自定义交易。
- 默认交易覆盖和权重。

#### 生效说明

- AI 检查间隔和诱导跟随规则会在后续服务器 Tick 中使用新值。
- 交易池保存后立即更新服务器候选规则。
- 已经持有旧 `MerchantOffers` 的现有村民不会被强制全部重建交易；测试修改时建议使用新生成村民或触发新的职业等级交易。

#### 常用入口

- `F6`：进入 **Trade Editor** 配置页。
- 交易编辑器：从 Trade Editor 配置页进入。
- 诱导物品编辑器：从 Trade Editor 配置页进入。
- `/kt reload`：重新读取并发布村民交易配置。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **启用物品诱导跟随** | 当此项开启时，村民会像动物一样跟随手持特定物品（如绿宝石）的玩家。 |
| **诱导物品列表** | 打开可视化诱导物品编辑器，通过物品选择器直接添加物品并预览图标；右键物品即可移除。 |
| **村民 AI 检查间隔（秒）** | 降低村民寻路和检测频率。数值越大越节省性能，但反应更迟钝；默认 5 秒，最小 0.05 秒。 |
| **交易升级保护** | 开启后，被脑叶切除的村民在交易升级期间仍会执行最低限度更新，避免交易所村民升级非常慢。 |
| **villager** | 村民 AI 检查频率、交易升级保护和手持物品跟随可直接修改。职业交易、NBT、次数、经验、补货和原版交易覆盖请使用专用交易编辑器。 |
| **打开村民交易编辑器** | 编辑职业/等级交易规则、自定义买入与卖出物品及 NBT、交易次数、经验、价格倍率、补货、权重和原版交易覆盖。打开与保存都会由服务器重新检查等级 2 管理权限。 |
| **behavior** | 这里仅显示村民行为与性能相关的服务端设置。职业交易、诱导物品和交易覆盖统一放在下方的村民编辑器分组中。 |
| **村民编辑器** | 这里集中放置需要专用 GUI 的村民配置。诱导物品使用核心通用物品列表编辑器，交易规则继续使用完整交易编辑器。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **返回** | 返回上一个界面，不会自动保存配置。需要生效请先点击保存。 |
| **clear** | 清空交易内容搜索 |
| **搜索交易内容** | 搜索当前职业的交易，支持物品名称、物品 ID、NBT、数量、等级、权重、最大交易次数和经验。 |
| **buy a** | A 输入一数量：玩家需要提交的第一个输入物品数量。范围 1~64。 |
| **buy b** | B 输入二数量：玩家需要提交的第二个输入物品数量。填 0 表示不使用第二输入物品。 |
| **sell** | 获得数量：交易完成后玩家得到的物品数量。范围 1~64。 |
| **需求值** | 当前交易的需求值，会配合价格倍率影响最终交易价格。 |
| **clear current** | 清空当前：当前正在编辑单条交易时，只清空右侧当前内容。<br>不会清空整个等级，也不会清空职业。 |
| **delete** | 删除左侧当前选中的交易。默认交易会被禁用，自定义交易会被移除。 |
| **save** | 保存右侧交易内容。若当前选择的是默认交易并修改了物品或参数，会自动转换成自定义交易。 |
| **添加交易** | 添加交易：先保存当前等级设置，然后跳转到新的单条交易编辑界面。 |
| **arrow** | 展开/收纳：点击这个小箭头，只负责展开或收纳当前等级的交易列表。 |
| **清空等级** | 清空等级：移除当前等级的自定义交易，并从主列表移除当前等级所有原版默认交易。<br>被移除的默认交易可在预览删除界面右键还原。 |
| **row** | 等级条操作：左键新增当前等级交易；右键清空当前等级全部交易；点击左侧小箭头展开或收纳当前等级交易列表。 |
| **最大次数** | 这条交易在补货前最多可以完成的交易次数。 |
| **nbt** | 编辑这个物品槽位的 NBT。留空表示普通物品。 |
| **抽取数量** | 抽取数量：当前等级最终会从交易池里抽出多少条交易。例如填 2，就只会出现 2 条符合权重的交易。 |
| **价格倍率** | 需求变化对交易价格的影响倍率。设置为 0 时不受需求值影响。 |
| **profession** | 点击搜索框选择职业，或直接输入职业名称 |
| **搜索职业** | 点击后会显示所有村民职业补全。选择职业后，左侧会显示这个职业 1~5 级的所有自定义交易。 |
| **重置职业** | 重置职业：恢复当前职业被移除的默认交易，并删除当前职业所有自定义交易与等级设置。点击后会二次确认。 |
| **还原全部** | 还原全部：恢复当前职业所有等级中被禁用的原版默认交易。不会删除自定义交易。 |
| **预览删除** | 预览删除的默认交易：打开当前职业所有等级中被移除的原版默认交易列表。右键单条交易可还原。 |
| **restock** | 开启后，这条交易会按原版机制补货。关闭后，这条交易不会通过补货恢复次数。 |
| **reward** | 开启后，玩家完成这条交易时会获得原版交易经验奖励。 |
| **保存** | 保存当前村民交易配置到 villager.toml。<br>保存后，后续新生成或刷新交易的村民会使用新规则。 |
| **clear** | 清空搜索：清空职业搜索框，并取消当前选择的村民职业。 |
| **A 输入一** | A 输入一：对应原版村民交易界面的第一个输入物品。玩家必须提供这个物品才能完成交易。 |
| **B 输入二** | B 输入二：对应原版村民交易界面的第二个输入物品。不需要第二输入时，保持为空并把数量设为 0。 |
| **获得** | 获得：这是交易完成后玩家最终获得的物品。点击图标可打开物品搜索器更换物品。 |
| **特殊价格** | 直接附加到当前交易价格上的特殊价格修正，可使用负数降低价格。 |
| **已用次数** | 这条交易当前已经被使用的次数。达到最大次数后需要补货才能继续交易。 |
| **权重** | 权重越高，这条交易被抽取到的概率越高。设置为 0 时不会被抽取。 |
| **经验** | 完成这条交易后给予村民的职业经验值。 |
| **清除** | 清除这个交易物品，不删除整条交易。 |
| **撤销 %s/%s** | 撤销上一步未保存的修改（Ctrl+Z） |

#### 可编辑字段、模式与分类索引

- 追加
- 禁用
- 替换
- A 输入一
- B 输入二
- 获得
- 清除

#### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `villager.follow_enable` | `true` |
| `villager.tickInterval` | `100` |
| `villager.trade_update_protection` | `true` |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/villager.toml`

### Tooltip 编辑器

#### 模组定位

**Tooltip Editor** 是 本项目中的物品悬浮提示可视化编辑模块。它把常见的 KubeJS Tooltip 规则做成游戏内编辑器，让整合包作者可以直接选择物品、编辑文本和显示条件，而不需要手工维护整段脚本。

编辑完成后，服务器会校验规则并生成 KubeJS 客户端脚本 `tooltipadd.js`。

#### 主要功能

- **Tooltip 总览**：集中查看已经配置过的全部物品。
- **物品搜索**：按物品 ID 或名称查找目标物品。
- **追加模式**：把新的说明文字追加到物品 Tooltip 底部。
- **覆盖模式**：覆盖指定 Tooltip 行；第 0 行可以用于替换物品名称显示。
- **目标行控制**：覆盖规则可以指定具体行号。
- **按键条件**：支持无条件、`Shift`、`Alt`、`Shift + Alt` 四种显示方式。
- **多条规则**：同一个物品可以拥有多条独立 Tooltip 规则。
- **规则排序**：支持通过 `Ctrl + 鼠标左键` 拖动规则调整顺序。
- **Minecraft 格式代码**：文本支持 `§` 颜色与格式代码。
- **物品级删除**：可以直接删除某个物品的整组 Tooltip 配置。
- **服务端校验**：服务器会验证物品 ID、模式、行号、按键条件、规则数量与文本有效性。
- **服务器权威写盘**：保存成功以服务器真实写入脚本为准，客户端不会直接修改服务器脚本文件。
- **安全写入**：脚本先写临时文件，再替换正式文件，降低写盘中断造成文件损坏的风险。

#### 生成文件

```text
kubejs/client_scripts/tooltipadd.js
```

该文件同时作为当前 Tooltip 规则数据库。Tooltip Editor 会读取其中的规则对象并重新还原到编辑器，因此不建议手工破坏自动生成的脚本结构。

#### 常用入口

- `F6`：进入 KineticCore 配置中心，在 **Tooltip Editor** 页面打开 Tooltip 编辑器。
- `/kt reload`：重新读取 Tooltip 数据。

#### KubeJS 说明

Tooltip Editor 的运行目标是生成：

```text
kubejs/client_scripts/tooltipadd.js
```

因此要让生成后的客户端 Tooltip 脚本真正执行，需要游戏环境中存在能够运行该脚本的 KubeJS 客户端脚本环境。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **物品 Tooltip 管理** | Tooltip 规则使用专用编辑器管理并生成 KubeJS client_scripts/tooltipadd.js，不需要手动修改脚本。服务器会校验管理员权限、物品 ID、模式、行号、按键条件和文本。 |
| **打开 Tooltip 编辑器** | 为指定物品添加覆盖或追加 Tooltip，可设置目标行、Shift/Alt 条件和文本。保存后只有服务器真实写入脚本成功才会提示成功。 |

#### 可编辑字段、模式与分类索引

- 覆盖
- 追加
- 删除此规则

### 从源码构建

- Minecraft：`1.20.1`
- Java：`17`
- ForgeGradle：`6.0.24`
- Gradle：项目固定使用 `8.1.1` Wrapper，请不要使用 Gradle 9 直接导入。
- 默认本地依赖目录由 `local_libs_dir` 控制，可在 `gradle.properties` 或命令行参数中覆盖。
- 常用构建命令：`gradlew.bat build`（Windows）或 `./gradlew build`（Linux/macOS）。
- 生成的开发/发布文件以 `contentstudio` 作为当前工程标识。

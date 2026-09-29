# Content Studio

[![🇺🇸 English](https://img.shields.io/badge/%F0%9F%87%BA%F0%9F%87%B8_English-2F81F7?style=for-the-badge)](#english) [![🇨🇳 简体中文](https://img.shields.io/badge/%F0%9F%87%A8%F0%9F%87%B3_%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-DC2828?style=for-the-badge)](#chinese) [![CurseForge](https://img.shields.io/badge/CurseForge-Open-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/contentstudio)

<a id="english"></a>

## English

Content Studio gives pack authors and server administrators in-game editors for recipes, loot tables, villager trades, and item tooltips. Searchable item selection, NBT editing, previews, and persistent rules help manage content directly inside a world.

### Installation and access

| Component | Requirement |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.2 or newer |
26.9.27 or newer; required |
| Recipe viewers | JEI 15.20+, EMI, and REI are optional |
| Item tooltips | Rendered by Content Studio on clients; no KubeJS dependency |

Install Content Studio and its required dependencies on the client and server for multiplayer editing. Press **F6**, the default KineticCore configuration-center key, and open the relevant Content Studio page. Editor data requests and changes are checked on the server and require permission level **2**.

### Recipe creation and removal

| Creation editor | Input layout |
| --- | --- |
| Crafting table | 3×3 shaped or shapeless ingredients |
| Furnace, blast furnace, smoker | One processing ingredient |
| Smithing table | Template, base, and addition |
| Stonecutter | One cutting ingredient |

- Choose ingredients from items or item tags, and set the output stack and count.
- Input NBT can be ignored, matched as a required subset, or matched exactly. Output NBT retention is a separate setting.
- Search by name, ID, `@modid`, or `#tag`; inventory and equipment views help select existing stacks.
- Browse saved additions in the preview, reopen a recipe for editing, or mark it for deletion.
- Unresolved items appear as barrier placeholders. Replace invalid slots before saving.

The removal manager shows each original datapack recipe's ID, status, and matching rules. Select an output to inspect its individual acquisition paths. Choose a mod, output, item tag, or recipe type to preview a broader rule: the impact list groups all matching original recipes by output and starts fully selected. An output checkbox controls only the recipe IDs currently listed under that output; expand the group to change one recipe. Searching the list does not change hidden selections. Right-clicking an item and choosing a recipe type opens the same impact list for that type across **all** outputs.

| Confirmation | Saved rule | Effect on future original recipes |
| --- | --- | --- |
| **Remove selected recipes only** | One exact ID rule for each selected recipe | New IDs are unaffected |
| **Keep removing within this range** | One persistent range rule; unchecked current recipe IDs become exclusions on that rule | New matching IDs are removed, even for an unchecked output |

When editing an existing range rule, **Remove selected recipes only** replaces that range rule with exact ID rules for the checked recipes. Other range rules remain in place and may still remove an unchecked recipe.

An exclusion belongs to one rule and one original recipe ID. If several rules remove the same recipe, restoring that recipe excludes its ID from every matching range rule and clears its exact ID rule. Other recipes under those range rules remain removed. A missing excluded ID is retained so the exclusion works again if its datapack returns. External recipe-viewer entries without a server-confirmed original ID are preview-only; JEI can still open their details when available.

Removal rules run against the original datapack recipe pool **before** KubeJS and CraftTweaker recipe scripts write recipes. Recipes added later by either optional script mod, including recipes that reuse a removed original ID, and recipes made in Content Studio's own editor are protected from these rules. Rule status describes the original recipe; a later recipe with the same ID may still be craftable. This behavior needs no KubeJS or CraftTweaker installation or ID naming convention.

**Save timing:** recipe additions and edits are written to the bundle first. Closing the complete recipe-editing session requests a server datapack reload to apply pending changes. The removal screen submits the complete draft with **Save and apply**; wait for the server's applied result before treating the draft as saved or testing crafting. If validation or writing fails, the draft remains available to correct and retry. If writing succeeds but reload fails, the rules are on disk but have not yet been applied.

### Entity, block, and container loot

Open the appropriate loot editor and select an entity, block, or container loot table. Changes affect subsequent loot generation for that table.

- Inspect and edit pools and entries, including item stacks and generated item NBT.
- Adjust pool roll ranges and luck-based bonus rolls.
- Edit entry weights, counts, chances, and supported drop conditions; entity-specific controls apply to entity death loot.
- Apply an entry or pool edit to the working table, then use **Save** on the main editor to persist it.
- Restore a table to its underlying resource definition by clearing the Content Studio override.

Container tools also provide global additions, global removal rules, and a table exclusion list. Removal rules match an item ID, the same item with any NBT, a required NBT subset, or exact NBT. Excluded tables receive neither global additions nor global removals.

The server validates saved table data and updates the affected runtime loot tables. These changes do not replace items already generated inside an opened chest.

### Villager and wandering-trader trades

Manage trades by profession and level. Wandering traders have common and rare groups. A group can add trades, replace a level, replace all levels, or disable a level, with a configurable number of weighted offers to select.

Custom offers support two payment stacks and one result, including NBT. Controls include maximum/current uses, villager experience, player experience rewards, price multiplier, demand, special price, restocking, and selection weight. Default offers can be disabled or reweighted without rebuilding the profession.

Saving applies changes live to standard trade pools. Optional late-override mode reapplies rules after villager or wandering-trader trade refresh, helping control trades dynamically appended during that refresh.

The module also provides villager follow items and AI check/pathfinding intervals. The default interval is 100 ticks; setting it to 1 restores the original frequency. Trade-update protection preserves the minimal updates needed for trade level-up timers when AI is reduced.

### Tooltip authoring

The tooltip editor saves rules to **`config/kineticcore/contentstudio_tooltips.json`**. Rules are keyed by item ID and append text or replace a specified line; line 0 is the item name. Conditions are always, Shift only, Alt only, or Shift+Alt. Text-color controls are included.

Content Studio renders the rules directly on clients. The server sends them when a player joins and after an editor save or `/kt reload`, so saved changes apply immediately to connected players. These rules do not contain an NBT matching condition. Existing KubeJS tooltip scripts are neither imported nor modified; remove an old `tooltipadd.js` yourself if KubeJS still runs it and duplicates the new tooltips.

### Files and application behavior

Paths are relative to the game/server instance running the editor's server side.

| File | Contents and application |
| --- | --- |
| `config/kineticcore/datapack/data/contentstudio/recipe/recipe_bundle.json` | Added recipes and removal rules; applied through the recipe reload flow |
| `config/kineticcore/loot_overrides.json` | Table overrides, global container rules, and exclusions; editor saves update runtime tables |
| `config/kineticcore/villager.toml` | Trade rules, follow items, and villager settings; trade editor saves apply live |
| `config/kineticcore/contentstudio_tooltips.json` | Native tooltip rules; server synchronizes them to clients |

These paths belong to the instance, rather than separate editor files in each world. Include the relevant files when distributing a pack, and retain a copy before replacing a set of rules.

### Practical workflow

1. Enter a world with administrator permission and open the editor through F6.
2. Start with one recipe, loot table, or profession and make a small change.
3. Save using that editor's workflow; leave the complete recipe interface to apply pending recipe additions.
4. Verify by crafting, generating fresh loot, or checking refreshed trades.
5. For tooltips, save a rule and inspect that item's tooltip on a connected client.


<a id="chinese"></a>

## 简体中文

Content Studio 面向整合包作者与服务器管理员，提供配方、战利品表、村民交易和物品提示的游戏内编辑器。通过物品搜索、NBT 编辑、预览与持久化规则，可以直接在世界中制作和调整内容。

### 安装与入口

| 组件 | 要求 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.2 或更新版本 |
| KineticCore | 必需，26.9.27 或更新版本 |
| 配方查看器 | JEI 15.20+、EMI、REI 为可选依赖 |
| 物品提示 | 由 Content Studio 客户端直接渲染，无需 KubeJS |

多人游戏中，在客户端和服务端安装 Content Studio 及必需前置。按 **F6** 打开 KineticCore 配置中心，再进入对应页面。F6 是默认按键；编辑器的数据请求与修改由服务端校验，需要 **2 级管理权限**。

### 新增与移除配方

| 新增编辑器 | 输入形式 |
| --- | --- |
| 工作台 | 3×3 有序或无序合成 |
| 熔炉、高炉、烟熏炉 | 单个加工材料 |
| 锻造台 | 模板、基础物品与附加材料 |
| 切石机 | 单个切割材料 |

- 可选择具体物品或物品标签作为材料，并设置输出物品与数量。
- 输入 NBT 支持不匹配、包含所需 NBT 的弱匹配、完全一致的强匹配；输出是否保留 NBT 单独设置。
- 搜索支持名称、ID、`@模组ID` 和 `#标签`，也可从背包或装备中选择已有物品。
- 在新增配方预览中查看、重新编辑配方，或标记删除。
- 无法解析的物品以屏障占位显示；保存前需要修复错误槽位。

移除管理器会直接显示每条原始数据包配方的 ID、状态及命中规则。选中产物可逐条检查获取途径；选择模组、产物、物品标签或配方类型后，可预览该范围内**所有**受影响的原始配方，按产物分组，初始全部勾选。取消一个物品分组的勾选，只影响该组当前列出的配方 ID；展开后也可逐条勾选。搜索不会改变被隐藏条目的勾选状态。右键物品选择配方类型，会打开同一份类型影响列表，范围覆盖所有产物。

| 确认方式 | 保存的规则 | 以后新增的原始配方 |
| --- | --- | --- |
| **只移除当前勾选的配方** | 为每个勾选的配方建立精确 ID 规则 | 新 ID 不受影响 |
| **按此范围持续移除** | 保存一条范围规则；未勾选的当前配方 ID 成为该规则的排除项 | 匹配的新 ID 仍会被移除，即使产物曾取消勾选 |

排除项只属于一条规则和一条原始配方 ID。如果多条规则同时移除同一配方，“恢复这一条”会将它从每条命中的批量规则中排除，并清除针对该 ID 的单条规则；其他配方仍受批量规则影响。暂时不存在的排除 ID 会保留，以便数据包恢复时继续生效。没有服务端确认的原始配方 ID 的外部加工配方只能预览；战利品、交易等获取来源分类不会混入配方列表。安装 JEI 时仍可查看外部加工配方的详情。

移除规则只处理 **KubeJS 和 CraftTweaker 配方脚本写入前**的原始数据包配方。两个可选脚本模组后续新增的配方，包括复用已移除原始 ID 的配方，以及 Content Studio 自身编辑器新增的配方，都不受这些规则影响。规则状态描述的是原始配方；同 ID 的后续配方仍可能可以制作。此边界不依赖安装 KubeJS 或 CraftTweaker，也不依赖配方 ID 命名习惯。

**保存时机：**新增和修改首先写入配方数据文件；退出整个配方编辑流程后，才请求服务端数据包重载。移除页面通过**保存应用**提交完整草稿；收到服务端“已应用”结果后，才能视为保存成功并检查合成。校验或写入失败时草稿保留，可修改后重试；文件写入成功但重载失败时，规则已在磁盘上，却尚未应用到游戏。

### 实体、方块与容器战利品

进入对应战利品编辑器，选择实体、方块或容器战利品表。修改影响该表之后生成的掉落内容。

- 查看和编辑掉落池、条目、物品及生成物品携带的 NBT。
- 调整掉落池抽取次数范围与基于幸运值的额外抽取次数。
- 编辑权重、数量、概率和支持的掉落条件；实体专用条件用于实体死亡掉落。
- 在条目或掉落池子页面点击应用后，还需要回到主编辑器保存。
- 清除 Content Studio 覆盖，可恢复底层资源中的原始战利品表定义。

容器编辑器额外提供全局追加、全局移除和战利品表排除名单。移除规则可按物品 ID、同物品携带任意 NBT、包含指定 NBT、完全一致 NBT 匹配。排除名单中的表不会接受全局追加或全局移除。

服务端校验保存内容后会更新相关运行时战利品表，不会替换已经开箱并生成的现有物品。

### 村民与流浪商人交易

按职业和等级管理村民交易，流浪商人则区分普通与稀有交易组。每组可追加、替换当前等级、替换全部等级或禁用等级，并设置按权重抽取的交易数量。

自定义交易支持两种买入物品和一种卖出物品，各槽位可携带 NBT。可设置最大/当前使用次数、村民经验、玩家经验奖励、价格倍率、需求值、特殊价格、是否补货和抽取权重。对于默认交易，可单独禁用或调整权重，无需重建整个职业。

保存后实时更新标准交易池。可选的“交易后覆盖”模式会在村民或流浪商人刷新交易结束后再应用规则，用于处理其他模组在刷新过程中动态追加的交易。

该模块还提供村民诱导跟随物品和 AI 检测、寻路间隔设置。默认间隔为 100 Tick，设为 1 可恢复原版频率。交易升级保护会在减少 AI 更新时保留交易升级计时所需的最低限度更新。

### 物品提示编辑

Tooltip 编辑器把规则保存到 **`config/kineticcore/contentstudio_tooltips.json`**。规则按物品 ID 保存，可追加文本或替换指定行，其中第 0 行为物品名称。显示条件包括始终显示、仅 Shift、仅 Alt、Shift+Alt，并提供文字颜色编辑。

Content Studio 客户端直接渲染规则。玩家加入时、编辑器保存后以及执行 `/kt reload` 后，服务端会同步规则，已连接玩家无需重进世界即可看到修改。规则不包含 NBT 匹配条件。旧 KubeJS 提示脚本不会迁移或修改；若仍通过 KubeJS 运行旧 `tooltipadd.js`，请自行移除，以免提示重复。

### 文件位置与生效方式

下列路径相对于承担编辑器服务端逻辑的游戏或服务器实例。

| 文件 | 内容与生效方式 |
| --- | --- |
| `config/kineticcore/datapack/data/contentstudio/recipe/recipe_bundle.json` | 新增配方和移除规则，通过配方重载流程应用 |
| `config/kineticcore/loot_overrides.json` | 战利品表覆盖、容器全局规则及排除名单，编辑器保存后更新运行时表 |
| `config/kineticcore/villager.toml` | 交易、跟随物品与村民设置，交易编辑保存实时应用 |
| `config/kineticcore/contentstudio_tooltips.json` | 原生 Tooltip 规则，由服务端同步至客户端 |

这些配置属于游戏实例，不是分别保存在每个世界目录中的编辑器文件。分发整合包时应携带所需文件，替换整套规则前可保留一份副本。

### 建议操作流程

1. 以具备管理权限的玩家进入世界，通过 F6 打开编辑器。
2. 先针对一个配方、一张战利品表或一个职业进行小范围修改。
3. 按当前编辑器流程保存；新增配方还需退出完整配方界面，应用待处理修改。
4. 通过实际合成、新生成的掉落或刷新后的交易检查结果。
5. 保存 Tooltip 规则后，直接在已连接的客户端检查对应物品的提示。

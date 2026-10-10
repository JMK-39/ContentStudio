2026年10月10日 — Editor navigation and tag indicators / 编辑页导航与标签标记

- Move recipe, loot, villager trade and item tooltip editor Back controls to the upper left when the header has space, using the lower left for inventory-style layouts.
- Tag ingredient icons show their gold # marker at the upper left; item quantities remain at the lower right.
- Recipe item lists and workstation entry icons use the API checkerboard slot background. Vanilla workstation recipe diagrams keep their native backgrounds.

- 配方、掉落、村民交易和物品悬浮提示编辑页的返回入口优先放在左上角，背包式界面顶部空间不足时使用左下角。
- 标签材料物品图标的金色 # 标记改为左上角，物品数量保持在右下角。
- 配方物品列表与工作台入口图标使用 API 棋盘格背景；原版工作台配方示意图保留原版背景。

---

# Changelog / 更新日志

## 2026-10-10 — JEI recipe editing / JEI 配方编辑

### English

- Use installed JEI categories to draw third-party recipe editors, including their original backgrounds, ingredients and animations. Without JEI, vanilla recipes keep visual editing, third-party editing entries are hidden, and previously saved recipes keep loading.
- Add independent third-party recipes from registered types and templates, with mod filters in recipe management.
- Add, duplicate, reorder and remove Create assembly steps, and edit their materials while retaining existing recipe data.
- Browse recipes by output icons, with recipe IDs in tooltips. Keep conditional recipe branches intact and group Forge workstation recipes under Vanilla.
- Add smooth scrolling and draggable scrollbars to workstation entries; clarify recipe parameters, including Goety ritual settings.
- Keep previews safe when languages or resource packs reload. Ambiguous or display-only JEI slots remain previews; their recipe fields can still be edited separately.
- Restore normal-sized villager trade icons and lower-right quantities, and show more blocks in the loot editor.

### 简体中文

- 第三方配方编辑直接使用已安装 JEI 的分类绘制，显示原有背景、材料和动画。未安装 JEI 时，原版配方保留可视化编辑，隐藏第三方新增和编辑入口，已保存的配方仍正常加载。
- 按已注册类型和模板新增独立第三方配方，配方管理支持模组分类筛选。
- 支持添加、复制、调整顺序和移除机械动力序列组装步骤，编辑步骤材料并保留原有配方数据。
- 配方列表使用产物图标，悬浮提示显示配方 ID；保留条件配方的分支，将 Forge 工作台配方归入原版。
- 工作台入口支持平滑滚动和拖动滑块，补充配方参数说明，包括诡厄巫法的仪式设置。
- 修复切换语言或重载资源时的预览问题。无法确定对应字段的槽位和纯展示槽位保留预览，可单独编辑配方字段。
- 村民交易图标恢复正常大小，数量显示在右下角；方块掉落编辑器可显示更多方块。

## 2026-10-09 — More workstation recipe previews / 更多工作台配方预览

### English

- Add dedicated previews and workstation entries for Powah energizing, Youkais' Feasts cooking and fermentation, and Twilight Forest uncrafting, block conversion and drying recipes where available.
- Show Twilight Forest entity conversions with rotating 3D previews and the mod's own recipe frames and arrows.
- Keep cuisine base ingredients, both mixed-cuisine ingredient groups, fluids and uncrafting input counts visible while editing their native recipe data.
- Reserve space between full ingredient rows, arrows and outputs, and omit empty fermentation fluid slots without changing the recipe editor's outer layout. The editor remains usable without JEI.
- Cycle real matching items in tag ingredient slots instead of paper icons, with a lower-right `#` badge and the tag ID in the tooltip. Keep tag rules when saving, including existing recipe records.
- Left-click recipe slots to choose ingredients; right-click for item editing and removal. Preserve data matching options and clear only the selected cell in shaped recipes.

### 简体中文

- 为 Powah 充能、幻想乡乐事的烹饪与发酵，以及暮色森林的拆解、方块转换和晾晒配方补充专用预览与工作台入口，按对应版本可用的功能显示。
- 暮色森林实体转换使用可旋转的 3D 模型预览，并显示模组原生的配方边框与箭头。
- 编辑时显示料理基础材料、混合料理的两组材料、液体和拆解物品数量，保留各版本原生配方数据。
- 为完整材料行、箭头与产物预留间距，隐藏发酵配方的空液体槽位，保持配方编辑器外层布局一致，无需安装 JEI 即可使用。
- 标签材料槽位轮换显示实际匹配物品，不再用纸张图标代替；右下角显示 `#` 标记，悬浮提示列出标签 ID。保存时保留标签规则，也支持已有配方记录。
- 配方槽位左键选择材料，右键展开物品编辑与移除菜单；保留数据匹配选项，有序配方只清空所点的格子。

## 2026-10-09 — Third-party recipe editor / 第三方配方编辑器

### English

- Add a searchable, scrollable recipe hub grouped into Vanilla and installed mods, with workstation icons and separate Create processing/assembly groups.
- Add an independent clickable recipe preview editor with native workstation textures, input/output slots and adjacent parameters; JEI is optional.
- Show native fluid and Mekanism chemical sprites in recipe slots, retaining their original colors and editable quantities.
- Add a searchable editor for installed mods' datapack recipes. Edit native item, tag, quantity, chance, time and other fields while retaining unedited custom data.
- Expand nested fields, add optional fields and array members, duplicate or remove members, and copy recipes to unused IDs. Save and reload directly, or restore the original recipe.
- Validate drafts with the installed recipe serializer before saving and reject stale drafts or conflicting copy IDs. Keep the existing workstation layouts and recipe-removal behavior.

### 简体中文

- 配方主页增加搜索和滚动浏览，按原版与模组分组，以工作台图标进入；机械动力分为基础工艺与序列组装。
- 新增独立的可点击配方预览编辑，使用原生工作台贴图、材料与产物槽位，并在旁边修改参数，无需安装 JEI。
- 配方槽位显示原生液体与通用机械化学物质图标，保留原有颜色，并可修改对应数量。
- 新增已安装模组的数据包配方搜索与字段编辑，可修改原生物品、标签、数量、概率、时间等字段，并保留未修改的专有数据。
- 支持展开嵌套字段、新增可选字段与数组成员、复制或移除成员，以及复制配方到新的 ID；可直接保存重载或恢复原配方。
- 保存前使用已安装模组的配方解析器校验，拒绝过期草稿与重复的复制 ID；保留原有工作台布局和配方移除行为。

## 2026-10-08 — Recipe appearance and item previews / 配方界面与物品预览

### English

- Vanilla workstation screens and JEI recipe layouts retain their original backgrounds, slots and item positions. The custom recipe browser keeps KineticCore's checkerboard item slots; recipe removal previews use native slot textures.
- Keep item icons inside their frames and separate neighboring loot and custom recipe-list slots. Small icons in custom editors retain clear item backgrounds.
- Recipe browser stack counts appear in the bottom-right corner in white with a dark shadow, making them easier to read over item icons.

### 简体中文

- 原版工作台和 JEI 配方界面保留原有背景、槽位与物品位置；自绘配方浏览列表保留 KineticCore 的棋盘格物品背景，配方移除预览使用原版槽位贴图。
- 物品图标与边框保留间距，战利品和自绘配方列表的相邻格子不再紧贴；自绘编辑器中的小图标也有清晰的物品背景。
- 配方浏览列表的物品数量显示在右下角，使用白字与深色阴影，便于在物品图标上辨认。

## 2026-10-08 — Recipe removal list spacing / 配方移除列表间距

### English

- In the recipe removal impact list, item icons keep a gap from the row frames instead of touching them.

### 简体中文

- 配方移除影响列表的物品图标与行边框保留间距，不再紧贴边框。

## 2026-10-07 — Trade sources and editor drafts / 交易来源与编辑草稿

### English

- Add a server trade-source setting: all registered trades or only ContentStudio custom trades. It applies independently of the existing Late Override switch, including offers already present on villagers and wandering traders.
- Save valid trades after server confirmation while retaining rejected records in the editor. Show exact validation issues, navigate to the affected record, and remove an invalid record with Undo available.
- Confirm unsaved changes when leaving. Add an unbound trade-editor shortcut that suspends and resumes the same draft, including form quantities, toggles and Undo state.
- Keep item quantities when replacing a trade item, allow right-click clearing, synchronize toggle values, and validate native item components on 1.21.1 and 26.1.2. Forge 1.20.1 continues to use NBT.
- In singleplayer, unsaved editor drafts remain separate from the trade rules confirmed by the server.

### 简体中文

- 新增服务端交易来源设置：所有已注册交易，或仅 ContentStudio 自定义交易。与现有“后覆盖”开关独立生效，并处理村民、流浪商人已有的交易。
- 收到服务端保存确认后提交有效交易，无效记录仍保留在编辑器中。显示具体校验问题、定位对应记录，并支持移除无效记录及撤销恢复。
- 离开时确认未保存的修改。新增默认不绑定的交易编辑器快捷键，暂存并恢复同一份草稿，包括表单数量、开关和撤销状态。
- 替换交易物品时保留数量，支持右键清空槽位，同步开关内部值；1.21.1 与 26.1.2 按原生数据组件校验，Forge 1.20.1 继续使用 NBT。
- 单人游戏中未保存的编辑草稿与服务端已确认的交易规则分开。

## 2026-10-06 — Windows fit their content / 窗口按内容大小显示

### English

- The villager lure item editor shows as many item rows as its items need plus one free row (4 to 13) and sits in the middle of the window, growing as items are added, instead of a fixed full-height grid around two or three items. The grid frame keeps 3 px clear of the slots instead of running along their edges.
- The recipe hub is only as tall as its workbench buttons need, without the large empty area between them and the bottom buttons.
- In the villager trade editor, the level arrows are drawn once instead of twice 1 px apart on 1.20.1 and 1.21.1, and the arrows, the Late Override switch and the item slot buttons keep 2 px from their row and panel frames and from the Clear button. In the loot editors the pool row buttons keep 2 px inside the row frame, and the tooltip editor's colour swatches are 2 px apart with the reset button clear of Back.

### 简体中文

- 村民引诱物品编辑器按物品数量显示所需行数并多留一行空行（4 至 13 行），位于窗口中央，添加物品时随之增长，不再是固定整高的网格里只放两三个物品。网格边框与物品格保持 3 像素，不再压在格子边缘上。
- 配方中心的高度按工作台按钮所需决定，按钮与底部按钮之间不再留下一大片空白。
- 村民交易编辑器中，等级箭头在 1.20.1 与 1.21.1 上只绘制一次，不再错开 1 像素重复显示；箭头、"延迟覆盖"开关与物品格按钮同行边框、面板边框以及清除按钮保持 2 像素。战利品编辑器中奖池行的按钮与行边框保持 2 像素；提示编辑器的颜色色块之间留 2 像素，重置按钮不再紧贴返回按钮。

## 2026-10-05 — Tooltip rules and match mode / 提示规则与匹配模式

### English

- Saving custom tooltip rules updates the player who saved straight away; other players receive the new rules when they log in, instead of every online player being sent them on each save. The reload command still refreshes everyone.
- The match mode menu of global chest-loot removal marks the current mode yellow as a single choice.

### 简体中文

- 保存自定义提示规则后，保存的玩家立即生效；其他玩家在登录时获得新规则，不再每次保存都发送给所有在线玩家。重载命令仍会刷新所有人。
- 全局箱子战利品移除的匹配模式菜单作为单选，用黄色标出当前模式。

## 2026-10-05 — Same data editor on every version / 各版本使用同一数据编辑器

### English

- On 1.21.1 and 26.1.2, item component data (`[damage=5]`) in recipes, loot entries and trades is now edited in Core's NBT editor, the same editor Forge uses for NBT, instead of a separate page, so the screen looks the same on every version. Requires KineticCore 26.10.5+, which also draws 26.1.2 container page backgrounds (such as the vanilla recipe screens) in place with their slots.
- Fixed a NeoForge mod-loading warning screen appearing on every 26.1.2 client start.

### 简体中文

- 1.21.1 与 26.1.2 中配方、战利品条目与交易的物品数据组件（`[damage=5]`）改用核心 NBT 编辑器编辑，与 Forge 编辑 NBT 相同，不再使用单独页面，各版本界面一致。要求 KineticCore 26.10.5+，该版本同时修复 26.1.2 容器页面（例如原版配方界面）背景与槽位错位。
- 修复 26.1.2 客户端每次启动都显示 NeoForge 模组加载警告界面的问题。

## 2026-10-05 — Vanilla recipe slot textures / 原版配方槽位贴图

### English

- Keep the recipe editor's vanilla container slots visible by removing the custom slot texture overlay. Use vanilla slot textures in the recipe browser while preserving its existing 22-pixel cells, item scale and actions.
- Preserve editor backgrounds, controls, slot positions and interactions.

### 简体中文

- 移除配方编辑器覆盖原版容器槽位的自定义贴图；配方浏览器使用原版槽位贴图，保留既有 22 像素格子、物品缩放与操作。
- 保留编辑器背景、控件、槽位位置与交互。

## 26.10.4 — 2026-10-04

### English

- Enabled Minecraft 26.1.2 / NeoForge 26.1.2.112 (Java 25) with JEI 29.43; releases now cover Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2.
- Villager trades on 26.1.2 keep the existing configuration, editor and saved data. Configured groups, custom offers and vanilla-trade switches apply to villagers and wandering traders. The wandering trader's common and uncommon trades are levels 1 and 2; its new buying trades stay vanilla.
- Clients have no trade sets on 26.1.2, so the server sends the trade editor its vanilla trade previews before the editor opens, on dedicated and integrated servers alike.
- On 26.1.2, recipe removal rules apply before script changes, configured recipes are added during server loading, and the removal editor and JEI integration see the same recipes of every type.
- Loot editing is supported on 26.1.2.
- Fixed on 1.21.1 and 26.1.2: ContentStudio's own recipe bundle shares the recipe folder and was inspected as an unparsable recipe; it is now skipped.
- On 26.1.2, trade and recipe item components reject combining durability with stacking, as command-given items do.

- Keep long editor labels, headings, names and IDs inside their layout bounds with scrolling text. Short text retains its original alignment. Applies to villager trades, follow items, loot, recipes, item components and tooltip editors.
- Separate Save and Back in the global loot removal editor and keep its counters clear of the panel border.
- Requires KineticCore 26.10.4+. Follow-item tooltips wrap to the available screen width; tooltips taller than the screen may still extend beyond it.

### 简体中文

- 启用 Minecraft 26.1.2 / NeoForge 26.1.2.112（Java 25），JEI 为 29.43；发布版本覆盖 Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2。
- 26.1.2 的村民交易沿用现有配置、编辑器与存档数据。配置的交易组、自定义交易与原版交易开关作用于村民和流浪商人。流浪商人的普通与稀有交易对应 1、2 级，新增的收购交易保持原版。
- 26.1.2 的客户端没有交易集，因此服务端会在交易编辑器打开前发送原版交易预览，独立服务端与单人游戏均如此。
- 26.1.2 中，配方移除规则在脚本修改之前生效，配置配方在服务端加载时加入，移除编辑器与 JEI 联动可以看到同样的各类型配方。
- 26.1.2 支持战利品编辑。
- 修复 1.21.1 与 26.1.2：ContentStudio 自己的配方包与配方共用目录，此前会被当作无法解析的配方检查，现在跳过。
- 26.1.2 的交易与配方物品组件像命令给予的物品一样，不允许同时设置耐久与堆叠。
- 村民交易、跟随物品、战利品、配方、物品组件和提示编辑器的长标签、标题、名称及 ID 现在在各自布局范围内滚动；短文字保持原有对齐。
- 分开全局战利品移除页的保存与返回按钮，让计数文字避开面板边框。
- 要求 KineticCore 26.10.4+。跟随物品悬浮提示按屏幕可用宽度换行；比屏幕更高的提示仍可能超出屏幕。

---

## 26.10.3 — 2026-10-03

### English

- Supports Minecraft 1.20.1 / Forge and 1.21.1 / NeoForge. Minecraft 26.1.2 is not yet supported.
- Release JARs now identify loader, Minecraft version, and mod version: `contentstudio-<loader>-<minecraft>-<version>.jar`. Required KineticCore is 26.10.3+; optional JEI is 15.20+ on Forge and 19+ on NeoForge.
- Added native NeoForge item component parsing and editing, including recipe ingredients and outputs. Forge retains its item NBT workflow; no old item-NBT converter is provided.
- NeoForge loot supports native `set_components` and component-patch removal modes. Trade items and payment matching use native item components.

- Preserved numeric types in custom component data and trade IDs/no-restock settings across native offer save/load and copy. Fixed component validation after changing the selected item.

### 简体中文

- 支持 Minecraft 1.20.1 / Forge 和 1.21.1 / NeoForge，尚不支持 Minecraft 26.1.2。
- 发布 JAR 文件名现包含加载器、Minecraft 版本和模组版本：`contentstudio-<加载器>-<Minecraft版本>-<模组版本>.jar`。必需 KineticCore 26.10.3+；可选 JEI 在 Forge 为 15.20+，在 NeoForge 为 19+。
- 新增 NeoForge 原生物品组件解析与编辑，支持配方材料和产物。Forge 保留物品 NBT 流程，不提供旧物品 NBT 转换器。
- NeoForge 战利品支持原生 `set_components` 与组件补丁移除模式；交易物品与支付匹配使用原生物品组件。

- 保留自定义组件数据的数值类型，并使交易 ID 和禁止补货设置随原生交易存档、读取及复制保留；修复切换所选物品后的组件校验。

---

2026年09月29日（原记录未标注小时、分钟）

- Added a complete recipe removal workflow with rule editing, previews, and an impact list.
- Updated recipe, loot, villager trade, and follow item editors.
- Updated compatibility with KineticCore 26.9.29.

- 新增完整的配方移除流程，支持编辑规则、预览结果和查看受影响的配方。
- 更新配方、战利品、村民交易和跟随物品编辑器。
- 更新对 KineticCore 26.9.29 的兼容。

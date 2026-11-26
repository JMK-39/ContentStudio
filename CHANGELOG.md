2026年11月26日 — 26.11.26 — Empty loot fallback / 空战利品回退
- Explicit air and unavailable item IDs resolve to empty loot entries, preserving their roll weights and conditions. Invalid tables yield no items. Explicit vanilla barrier rewards still work, and the source JSON is preserved.
- 明确配置的空气和缺失物品在运行时变为空战利品条目，保留抽取权重和条件。无法解析的整张战利品表不再产出物品。正常配置的原版屏障仍可掉落，原始 JSON 不会被改写。

2026年11月24日 — 26.11.24 — Configuration directory / 配置目录
- Tooltip, loot override and villager configuration files now live under `config/kineticcore/contentstudio/`, with automatic migration that preserves existing destinations. Shared data-pack paths are unchanged. Requires KineticCore 26.11.24.
- 物品提示、战利品覆盖与村民配置归档至 `config/kineticcore/contentstudio/`，自动迁移旧文件并保留已有目标文件；共享数据包路径保持不变，需要 KineticCore 26.11.24。

2026年11月14日 — 26.11.14 — TaCZ 1.2.0 compatibility / TaCZ 1.2.0 兼容
- Verified native gunsmith recipe editing with TaCZ 1.2.0 on Forge 1.20.1. Saves and restores update the server immediately without a datapack reload; closing the editor synchronizes recipes to clients. Result groups and output item metadata are preserved through saving, reopening and synchronization.
- 在 Forge 1.20.1 上验证 TaCZ 1.2.0 原生工作台配方编辑。保存与恢复立即在服务端生效，无需重载数据包；关闭编辑器后同步给客户端。保存、重新打开和同步时保留产物分组及物品数据。

2026年11月11日 — 26.11.11 — Recipe saves without a reload each / 保存配方不再逐次重载
- Saving, creating, restoring, removing or bringing back a recipe in the native recipe editor no longer reloads every datapack each time. The change is stored at once, and everything saved is applied by one reload when the recipe pages are closed (or when the editing player leaves a dedicated server). Ten saves used to mean ten reloads, each freezing a large pack for tens of seconds; now it is one.
- 在原生配方编辑器中保存、新建、恢复、移除或恢复被移除的配方时，不再每次都重载全部数据包。修改立即写入，所有修改在关闭配方界面时统一重载一次（专用服务器上编辑的玩家退出时也会应用）。原来保存十次就重载十次，大型整合包里每次都要卡几十秒，现在只重载一次。
- On 1.20.1, TACZ gunsmith table recipes need no reload at all: the saved recipe is put straight into the running recipe manager, exactly as a reload would load it, so it works at the table at once; players are sent the recipes when the recipe pages are closed.
- 1.20.1 上，TACZ 枪械工作台配方完全不需要重载：保存的配方直接放进运行中的配方表，与重载后的结果完全一致，立刻可以在工作台使用；关闭配方界面时再把配方同步给玩家。

2026年11月3日 — 26.11.3 — Loot tables kept in data packs / 数据包中的战利品表
- Loot tables that are files in KineticCore data packs (config/kineticcore/datapack), such as the ones RealmControl's structure editor creates, are edited in their file: Save writes the file and takes effect at once, with no override, so the table travels with its pack. Tables written since the last reload are listed too. Entries whose mod is not installed stay in the file and are left out when it loads.
- KineticCore 数据包（config/kineticcore/datapack）中以文件形式存在的战利品表（例如 RealmControl 结构编辑器新建的表）直接编辑其文件：保存即写入文件并立即生效，不再生成覆盖记录，表随数据包一起分发。上次重载后新写入的表也会列出。所属模组未安装的条目保留在文件中，加载时去掉。
- Other mods can open the chest loot editor on one table, and Back returns to where they were; RealmControl's structure editor opens it for a container's table.
- 其他模组可以打开箱子战利品编辑器并定位到一张表，返回后回到原来的界面；RealmControl 结构编辑器用它编辑容器的战利品表。

2026年11月2日 — 26.11.2 — Quieter recipe loading / 配方加载不再误报
- 1.20.1: Recipes that use item tags are no longer reported as unusable ("missing or empty item tag") when a world loads.
- 1.20.1：使用物品标签的配方在进入世界时不再被误报为不可用（“missing or empty item tag”）。
- 1.20.1: An empty recipe file that a datapack uses to delete a recipe is no longer reported as unreadable.
- 1.20.1：数据包用来删除配方的空文件不再被报告为无法解析。

2026年10月27日 — 26.10.27 — Recipe item data / 配方物品数据修复
- Native recipe field icons and item selections retain NBT and components, including TACZ variant IDs. Replacing an item clears its previous item data while retaining recipe quantities and probabilities.
- 原生配方字段图标与物品选择保留 NBT、组件及 TACZ 型号；更换物品时清除旧物品数据，保留配方数量与概率。
- Native TACZ output previews use its registered result decoder, including custom NBT. Recipe search includes intermediate items, materials, tags and NBT variants.
- TACZ 原生产物预览使用其已注册的解析器并保留自定义 NBT；配方搜索支持半成品、材料、标签和 NBT 变体。

2026年10月26日 — 26.10.26 — Clearer editors / 更清晰的编辑界面
- Titles are centred on the screen in the loot entry, loot pool, follow items, native recipe, workstation, tooltip and ID picker pages.
- 战利品条目、奖池、诱导物品、原生配方、工作台、提示规则与 ID 选择页面的标题改为屏幕居中。
- Native recipe editor: the seven bottom buttons moved to the top row beside the ID field, with shorter labels; the diagram and field list use the freed space. The Create sequence page has its pager and Add Step in the header and one step per row.
- 原生配方编辑：底部 7 个按钮移到顶部 ID 行右侧并缩短文字，图示与字段列表使用腾出的空间；机械动力组装步骤页的翻页与新增步骤放在顶栏，每行一个步骤。
- Loot pool settings are a compact dialog with Back, Delete and Apply in the header; hint lines that repeated the tooltips are gone (loot entry, pool, global exclude, villager level settings).
- 奖池设置改为紧凑对话框，返回、删除、应用在顶栏；去掉与悬浮提示重复的说明文字（战利品条目、奖池、全局排除、村民等级设置）。
- Recipe removal uses standard 22 px item slots and keeps the selected recipe's actions at the top of its details. Removed villager trades are one line each with standard slots; the trade buttons and the source button are shorter and at the top.
- 配方移除改用 22 像素标准物品格，所选配方的操作按钮放在详情区顶部；被移除的村民交易每条一行并使用标准物品格，交易按钮与“来源”按钮缩短并移到顶部。
- The workstation search sits in the header; the vanilla recipe cards match the workstation cards; fluid and effect pickers search by ID or name.
- 工作台搜索框移到顶栏；原版配方卡片与工作台卡片一致；流体与效果选择器按 ID 或名称搜索。

2026年10月24日 — 26.10.24 — Buttons at the top, standard slots / 按钮置顶与统一物品格
- 配方管理器：“配方管理”按钮缩短为文字所需宽度，标题以屏幕中心居中；村民跟随物品网格的图标批量绘制。
- Recipe removal, removal impact, recipe preview, recipe types and recipe tags are redesigned: buttons in the header, item icons in standard slots instead of long id rows (right-click for view / remove), the preview centred in the free area.
- 配方移除、移除影响、配方预览、配方类型与配方标签界面重新排版：按钮全部放在顶栏，物品以标准物品格图标显示而非长 ID 列表（右键查看/移除），预览居中显示。
- Native recipe browser: search next to Back with live results (no search button), the mod filter beside it, one more row of items. Native recipe fields: all buttons at the top, field names in Chinese, item fields as clickable icons (no id, no Select button), 12 rows per page; the Add Field dialog has its buttons at the top.
- 原生配方浏览：搜索框紧挨返回、输入即搜（移除搜索按钮），模组筛选在其右，多显示一行物品；原生配方字段：按钮全部置顶，字段名翻译为中文，物品字段直接显示可点击图标（无 ID、无选择按钮），每页 12 行；新增字段弹窗按钮置顶。
- Tooltip rules: the hub shows items at normal size (right-click Edit/Delete); the editor header holds only the item icon and one row of colour swatches.
- 提示规则：总览以正常大小显示物品（右键编辑/删除）；编辑页顶部只显示物品图标与一行颜色块。
- Villager follow items use the standard item slots.
- 村民跟随物品改用标准物品格。

2026年10月20日 — 26.10.20 — Compact loot and recipe editors / 紧凑战利品与配方编辑
- Block and entity loot editors: the target grid and the reward pools are layout-editor areas, so the line between them can be dragged; the grid then shows as many whole columns as fit and the reward panel starts right after the grid, so no gap opens before the divider. The block grid uses the full height and shows 14 rows instead of 13. Close and Cancel buttons read Back; the mod picker in the recipe browser and the global-removal mode button show a down triangle.
- 方块与实体战利品编辑器：左侧目标网格与右侧奖池成为布局编辑区域，可拖动两者之间的分界；网格按能放下的整列显示，奖池面板紧接网格之后开始，分界线前不再留空。方块网格使用全部高度，显示 14 行（原 13 行）。“关闭”“取消”统一为“返回”；配方浏览的模组选择与全局移除模式按钮显示向下三角。
- Chinese text on 1.21.1 and 26.1.2 no longer puts spaces around 数据组件 (for example 编辑数据组件 instead of 编辑 数据组件).
- 1.21.1 与 26.1.2 的中文不再在“数据组件”两侧留空格（例如“编辑数据组件”而不是“编辑 数据组件”）。
- Villager trades: the content starts right under the header and the profession search begins next to Back, using the space between them.
- 村民交易：内容紧贴顶栏下方开始，职业搜索框紧接返回按钮，利用两者之间的空白。
- Lure items: Add Item (now shorter) and Save sit in the header row, so the grid shows 17 rows instead of 15.
- 诱导物品：“添加物品”（已缩短）与保存移到顶栏，物品网格由 15 行增加到 17 行。
- Loot editors: Apply in the reward editor and Apply / Delete Pool in the pool editor sit in the header row (top-right); the pool dialog is shorter. Block loot shows the same item size on the left and right, the divider follows the column count, and the reward list starts higher, runs 4 px wider and keeps its scrollbar 3 px from the frame.
- 战利品编辑：奖励编辑的“应用修改”和奖池编辑的“应用设置”“删除奖池”移到顶栏右上角，奖池弹窗更矮；方块战利品左右物品大小一致，分界线按实际列数确定，奖励列表上移、加宽 4 像素，滚动条距边框 3 像素。
- The tooltip editor colour palette starts on the toolbar line instead of two pixels above it, with the reset button centred on both rows. The villager trade editor leaves four pixels between the Restock toggle and the trade source button.
- 提示编辑器的颜色色板与工具栏按钮顶部对齐（原先高出 2 像素），重置按钮居中于两行色板；村民交易编辑器的补货开关与交易来源按钮之间保留 4 像素间距。
- Recipe entry cards fill the available width; management actions move to the top and workstation lists gain visible rows. Recipe mod entries use localized labels and can be searched by localized name, English name or ID.
- 配方入口卡片向右扩展，管理按钮移到顶部，工作站列表增加可见行；模组入口补充中文译名，并支持按译名、英文名或 ID 搜索。
- Entity loot keeps four columns of 50 px cards with 2 px gaps, while returning the unused left-column width to the reward editor. Header actions use consistent four-pixel gaps.
- Entity rewards use a tighter scrollbar gutter and start eight pixels higher, giving the list more width and height. Editors use one white outer frame with plain content backgrounds and gray-white dividers; controls, item cards and interactive row states keep their outlines.
- The native recipe browser fits 26 columns and 13 complete rows of compact slots. Filters and actions share the top row; saved-recipe tabs retain their own compact row.
- Returning from a recipe before its browser finishes loading resumes the interrupted batch, retaining loaded recipes, scroll position and search text. Late replies cannot reopen the editor or replace the active batch.
- Native recipe editors use the four-pixel outer inset and keep quantity, chance and other useful properties. A simple item/tag identity already edited through its diagram slot is omitted from the duplicate sidebar; an empty sidebar gives its width back to the diagram. Slot Fields, Advanced and saving preserve the original recipe data.
- 实体战利品保留四列 50 像素生物格与 2 像素间距，将左栏多余宽度交还奖励编辑区；页头按钮统一为 4 像素间距。
- 实体奖励进一步收紧滚动条旁间距，列表上移 8 像素并增加宽高；编辑器仅保留白色外框，内容区使用背景和灰白分隔线，控件、物品格及可交互行状态保留描边。
- 原生配方浏览器改为 26 列、13 个完整行，筛选与操作控件集中在顶行，已存配方标签保留紧凑的独立行。
- 浏览器尚未加载完时进入配方再返回，会继续中断的分批加载，保留已有列表、滚动位置与搜索文字；过期回复不会重新打开编辑页或覆盖当前批次。
- 原生配方编辑器使用 4 像素外边距，保留数量、概率等有效属性；图中槽位已能选择的简单物品或标签不再重复占据右栏，空右栏自动让出预览宽度。槽位字段、高级编辑与保存保持原始配方数据。
- Native recipe item slots, structural-field icons, sequence icons and follow-item lists use native item tooltips. Item, entity, fluid and effect choices refresh their visible controls as soon as they return.
- Slot Fields opens the containing object or array for primitive ingredients instead of an empty field page; structural child editors stay disabled while a recipe request is pending.
- Workstation lists and removed default trades stop at their exact content edge; removed-trade clicks and item tooltips use the same clipped viewport. Invalid recipe fields block sequence reordering and material duplication until corrected. Enter no longer changes the selected villager profession.
- 原生配方槽位、字段图标、序列图标及跟随物品列表使用原版物品提示；物品、生物、流体和效果选择回填后立即刷新可见控件。
- 字符串等基础类型材料的“槽位字段”打开所属对象或数组，不再进入空字段页；配方请求等待期间不进入结构编辑子页。
- 工作站列表与已移除默认交易按实际内容末端滚动，恢复交易点击和物品提示与绘制共用裁剪范围；配方字段存在无效输入时暂不重排序列或复制材料，避免错误下标错位。回车不再切换村民职业。


---

2026年10月16日 — Loot editing and entity cards / 战利品编辑与生物格

- Entity loot cards use the shared 50 px slots with 2 px gaps and the item-slot background; the wider search and left column fit four cards per row.
- Large editors use the standard 640×360 canvas with Back four pixels from the frame; search fields gain forty pixels where space allows.
- Delete uses the existing selected-row actions for loot pools and rewards, recipe slots and saved recipes, villager offers and follow items, and tooltip items and rules, with the same confirmations and save flow.
- 生物战利品格统一为核心 50 像素、间距 2 像素并使用物品槽背景；搜索框和左栏加宽，每行可放四格。
- 大型编辑页统一使用 640×360 画布，返回按钮距边框四像素；搜索框在空间允许时加宽四十像素。
- Delete 键复用当前选中项的既有删除动作，覆盖战利品池与奖励、配方槽与已存配方、村民交易与跟随物品、Tooltip 物品与规则，并保留原有确认和保存流程。

---

2026年10月13日 — Recipe hub and editors / 配方主页与编辑器

- The Vanilla entry opens the original vanilla workstation editor again, with its familiar clicks: item search on an empty slot, NBT editing with Shift+click and NBT matching with right-click. Tag ingredients show their actual items with a # badge.
- Recipe Management combines added recipes, removed original recipes and changed third-party recipes as tabs of one page.
- Third-party recipe properties use translated names and suitable controls: switches, number fields, item, tag, fluid and effect pickers, and choices such as Create's heat level. Fields no longer have to be typed by their JSON names.
- An original third-party recipe can be removed and restored from its editor. The editor title names the workstation.
- Recipes listing the same item with different counts, shaped patterns that repeat a key, and categories whose JEI display uses the mod's own objects now bind their slots for editing.
- The tag selection page has a full panel background.
- Third-party recipe slots have tooltips stating whether they are inputs or outputs and what left and right clicks do.
- Iron's Spells alchemist cauldron and Touhou Little Maid altar recipes show their real JEI display in the editor. Recipe types without a JEI display use the built-in diagram and properties panel instead of the raw field page.
- Slots in recipes that repeat a material or pattern key, such as mechanical crafting, sequenced assembly and rituals, can each be selected and edited.
- Entity fields and entity slots, such as Twilight Forest transformation powder, open the KineticCore entity selector with model previews and show entity names. Cooking pot results shown twice, and the item ingredients and results of Cuisine Delight plates, bind for editing.
- The third-party recipe list loads every matching recipe into one grid with a draggable scrollbar instead of showing about three rows per page with page buttons. The count below shows how many recipes match.
- The third-party recipe list keeps its controls in one top row: mod filter, a shorter search field, the match count, Search and New Recipe; the grid uses the freed space for another row of recipes.
- In the removal impact preview each row ends with buttons instead of starting with [x] and + text: Recipes/Hide shows the recipes of an item, and the state button switches removal between Remove, Keep and, for partly selected items, Partial.
- Twilight Forest uncrafting and Just Enough Breeding information are not recipes and no longer appear in the recipe editor.
- A pack recipe of a type no installed mod provides is logged as one warning line with the reason instead of a full stack trace each time recipes reload, so saving a recipe no longer fills the server log.
- Hovering a slot in the third-party recipe editor shows its tooltip again: the item, whether the slot is an input (material) or the output (result), and what left and right clicks do. The diagram's scrollbar control covered the slots and hid these tooltips.
- Slots in the vanilla workstation editor also say whether they are an input (material) or the output (result).

- 原版入口重新打开原有的原版工作台编辑器，操作与以前相同：空槽位点击搜索物品，Shift+点击编辑 NBT，右键切换 NBT 匹配；标签材料显示实际物品与 # 标记。
- “配方管理”把新增配方、移除的原配方与修改过的第三方配方合并为同一页面的标签页。
- 第三方配方属性使用汉化名称和合适的控件：开关、数字框，物品、标签、流体与效果选择器，以及机械动力加热等级等选项，无需再手动输入 JSON 字段名。
- 可在编辑页移除或恢复第三方原配方；编辑页标题显示所属工作台。
- 同一物品不同数量的产物、重复使用同一键位的有序配方，以及 JEI 显示使用模组自有对象的分类，现在都能绑定槽位进行编辑。
- 标签选择页使用完整的面板背景。
- 第三方配方槽位的悬浮提示会说明是输入槽还是输出槽，以及左键、右键的操作。
- 铁魔法炼金锅与车万女仆祭坛配方在编辑页显示真实的 JEI 界面；没有 JEI 显示的配方改用通用示意图和属性面板编辑，不再进入原始字段页。
- 动力合成、序列组装、仪式等重复材料或重复键位的配方可以逐个选中槽位编辑。
- 生物字段与生物槽位（如暮色森林变化之粉）改用核心带模型预览的生物选择器，并显示生物名称；厨锅重复显示的产物，以及 Cuisine Delight 摆盘的具体物品材料与成品，可以绑定编辑。
- 第三方配方列表把所有匹配的配方载入同一个网格，可拖动滚动条浏览，不再每页只显示约三行并需要翻页；下方显示匹配的配方数量。
- 第三方配方列表的控件集中在顶部一行：模组筛选、缩短的搜索框、匹配数量、搜索与新建配方；网格利用腾出的空间多显示一行配方。
- 移除影响预览的每一行末尾改为按钮，不再在开头显示 [x] 和 + 文字：“查看配方/收起”展开物品的配方，状态按钮在“移除”“保留”以及部分选中时的“部分移除”之间切换。
- 暮色森林反向合成与 Just Enough Breeding 的繁殖信息不属于配方，不再出现在配方编辑中。
- 整合包中某个配方的类型若没有已安装模组提供，现在只记录一行带原因的警告，不再在每次配方重载时打印完整堆栈；保存配方不会再刷满服务器日志。
- 第三方配方编辑器中悬停槽位会重新显示提示：物品、该槽位是输入（材料）还是输出（产物），以及左右键的作用。此前配方图的滚动条控件覆盖了槽位，导致提示无法显示。
- 原版工作台编辑器的槽位提示同样会说明是输入（材料）还是输出（产物）。

---

2026年10月12日 — 26.10.12 — Recipe classification and editing / 配方分类与编辑

- Group recipes by their registered runtime recipe type owner, including active conditional branches. Vanilla workstation types from any mod appear under Vanilla; preserve existing recipe IDs.
- Save edits in type-owner files such as `minecraft.json`, `create.json` and `tacz.json`. Read existing ContentStudio files without automatic conversion or deletion. TACZ Workshop legacy recipes are left for explicit conversion.
- Use the shared item grid with normal-sized items, two-unit slot gaps and lower-right counts. Preserve native output NBT, components and supported TACZ builder results.
- Keep the editor open after saving and refresh its loaded session and parent list so the same recipe can be saved again. Retain drafts on failure. Preserve unusable existing entries and protect ordinary recipe files from settings-file collisions.
- Restrict JEI selection outlines to actual ingredient slots and omit duplicate slot identifiers from recipe parameters. Requires KineticCore 26.10.12+.

- 按运行时注册配方类型所属模组分类，条件配方使用生效分支；任何模组提供的原版工作台类型均归入原版，保留现有配方 ID。
- 按类型所属模组保存到 `minecraft.json`、`create.json`、`tacz.json` 等文件；保持现有 ContentStudio 配方文件读取兼容，不自动转换或删除旧文件，TACZ 工坊旧配方留待单独转换。
- 产物列表使用共享物品网格，物品正常大小、槽位间距为两个界面单位，数量位于右下角；保留原生 NBT、组件与受支持的 TACZ 构建结果。
- 保存后保持当前编辑页并更新已加载会话与父列表，可再次保存同一配方；失败时保留草稿。保留无法使用的旧条目，保护同名普通配方文件不被配置覆盖。
- JEI 选择框使用实际材料槽位边界，配方参数不再重复显示材料和产物标识；需要 KineticCore 26.10.12+。

---

2026年10月11日 — Recipe storage / 配方存储目录

- Store native recipe overrides beside `recipe_bundle.json` under `data/contentstudio/recipe/`. Move existing override files automatically, preserving edits already in the new directory.
- 原生配方覆盖文件与 `recipe_bundle.json` 统一放在 `data/contentstudio/recipe/` 下；自动移入旧覆盖文件，同 ID 保留新目录已有修改。

---

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

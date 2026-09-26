# 配方移除：原始数据包与脚本写入边界验证

状态：**隔离服务端配方生命周期验证已执行；图形界面人工验收仍待执行**。下文的操作步骤保留为复现说明；实际结果单独记录在下一节。

## 2026-09-27 实测结果

使用 Minecraft 1.20.1、Forge 47.4.2，在本工作树 `build/verification/` 下的四个独立服务端实例执行。KubeJS 使用本地 1.20.1 Forge JAR，CraftTweaker 使用 Forge 14.0.60；这些脚本模组只在隔离实例里加载，项目没有新增对它们的编译或运行依赖。通过临时验证模组的 `/recipeverify` 查询服务端原配方目录、当前 RecipeManager 的 ID、实际 `matches/assemble` 和合成网格选择结果。验证模组及实例均位于忽略的 `build/verification/`，未写入用户的游戏目录。

| 组合 | 启动时原配方目录 / 当前配方数 | 观察结果 |
| --- | --- | --- |
| 无脚本模组 | 1178 / 1176 | `fixture:scope_target` 等三条原配方移除；`fixture:scope_excluded` 可用；编辑器配方可用。`/reload` 后一致。 |
| 仅 KubeJS | 1178 / 1178 | 原 `fixture:kjs_recreated` 已移除，同 ID KubeJS 配方以木棍合成仍可用；`fixture:kjs_custom` 可用；排除项与编辑器配方可用。 |
| 仅 CraftTweaker | 1178 / 1179 | 原 `crafttweaker:fixture` 已移除，同 ID CRT 配方以羽毛合成仍可用；`crafttweaker:crt_added` 可用；`/reload` 后一致。 |
| 两者同时加载 | 1178 / 1181 | KubeJS、CRT 两条同 ID 重建配方和各自新增配方均可用；三条被规则命中的原配方不可用，排除项和编辑器配方可用；`/reload` 后一致。 |

在双模组实例里随后加入 `fixture:future_new`（石按钮合成同一产物）再执行 `/reload`：原配方目录变为 1179 条，新配方被持续范围规则移除，`fixture:scope_excluded` 仍可合成。KubeJS 联动首次实测发现优先级 1200 的 HEAD 钩子未执行；改为 1000 后上述结果通过。未执行客户端图形界面点击测试、异常配置与写入失败故障注入；这些项目不能据此标记通过。

## 隔离实例与记录

使用 Minecraft 1.20.1、项目要求的 Forge、Content Studio 和 KineticCore 版本，为以下四种组合分别复制一份干净实例和世界，不复用 `config/kineticcore/datapack/data/contentstudio/recipe/recipe_bundle.json`。KubeJS、CraftTweaker（CRT）和 JEI 都是验证实例中的可选模组；不得把它们加入 Content Studio 的编译或运行依赖。

| 实例 | KubeJS | CraftTweaker | 启动检查 | `/reload` 检查 | 实际配方 ID、日志顺序和结论 |
| --- | --- | --- | --- | --- | --- |
| A | 无 | 无 | 通过 | 通过 | 原配方过滤、单条排除和编辑器配方可用 |
| B | 有 | 无 | 通过 | 通过 | KubeJS 新增及同 ID 重建配方可用 |
| C | 无 | 有 | 通过 | 通过 | CRT 新增及同 ID 重建配方可用 |
| D | 有 | 有 | 通过 | 通过 | 两种脚本配方均可用；后增数据包配方仍受范围规则约束 |

在每个实例中记录 Minecraft、Forge、KineticCore、Content Studio、KubeJS、CraftTweaker 的实际版本，以及 `latest.log` 中原始配方过滤、KubeJS 配方事件、CRT 脚本和 Content Studio 编辑器配方加入的先后位置。查看实际注册的 ID 和工作台合成结果，不能只看 JEI 列表。启动后和手动 `/reload` 后各做一次。使用 F6 打开编辑器的账号需要 2 级管理权限。

## 原始数据包夹具

在每个测试世界的 `datapacks/contentstudio-recipe-removal-fixture/` 放入同一份数据包。Minecraft 1.20.1 的 `pack.mcmeta`：

```json
{"pack":{"pack_format":15,"description":"Content Studio recipe removal verification"}}
```

下面四个文件都使用相同的 JSON 结构，只替换 `ingredients[0].item`。`fixture:scope_excluded` 用来验证单条排除；后两个 ID 要由脚本复用，以验证移除发生在脚本写入之前。

| 相对数据包路径 / 原始 ID | 原始输入 |
| --- | --- |
| `data/fixture/recipes/scope_target.json` / `fixture:scope_target` | `minecraft:paper` |
| `data/fixture/recipes/scope_excluded.json` / `fixture:scope_excluded` | `minecraft:wheat_seeds` |
| `data/fixture/recipes/kjs_recreated.json` / `fixture:kjs_recreated` | `minecraft:flint` |
| `data/crafttweaker/recipes/fixture.json` / `crafttweaker:fixture` | `minecraft:bone_meal` |

以 `scope_target.json` 为例：

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [{"item": "minecraft:paper"}],
  "result": {"item": "minecraft:nautilus_shell"}
}
```

另建 `data/fixture/tags/items/sea_result.json`，内容为 `{"replace":false,"values":["minecraft:nautilus_shell"]}`，用于单独验证 `TAG = fixture:sea_result`。先确认数据包由 `/datapack list enabled` 列出。在**未安装脚本模组且尚无移除规则**的 A 实例中，四种原始输入都应能合成鹦鹉螺壳；安装脚本模组的实例中，复用 ID 的脚本可能已经替换对应原始输入，必须先记录实际基线。若基础夹具未加载，先排查数据包和日志，不应把后续结果归因于移除规则。数据包格式 15 依据 [Minecraft 1.20 发布说明](https://feedback.minecraft.net/hc/en-us/articles/16499677456781-Minecraft-Java-Edition-1-20-Trails-Tales)。

## 脚本与编辑器夹具

仅在装有 KubeJS 的实例，将下段脚本放在实例的 `kubejs/server_scripts/contentstudio_recipe_fixture.js`。第一个配方使用自定义 `fixture` 命名空间；第二个复用被移除的原始 ID，且改用与原始配方不同的输入。语法参照 [KubeJS 官方配方教程](https://kubejs.com/wiki/tutorials/recipes)。

```js
ServerEvents.recipes(event => {
  event.shapeless('minecraft:nautilus_shell', ['minecraft:glass_bottle'])
    .id('fixture:kjs_custom')
  event.shapeless('minecraft:nautilus_shell', ['minecraft:stick'])
    .id('fixture:kjs_recreated')
})
```

仅在装有 CraftTweaker 的实例，将下段脚本放在实例的 `scripts/contentstudio_recipe_fixture.zs`。`fixture` 这个配方名预期注册为 `crafttweaker:fixture`，与同名原始数据包配方碰撞；实际 ID 须由运行结果确认。语法参照 [CraftTweaker 1.20.1 配方教程](https://docs.blamejared.com/1.20.1/en/tutorial/Recipes/Crafting/crafting_table/)。

```zenscript
craftingTable.addShapeless("crt_added", <item:minecraft:nautilus_shell>, [<item:minecraft:clay_ball>]);
craftingTable.addShapeless("fixture", <item:minecraft:nautilus_shell>, [<item:minecraft:feather>]);
```

在 Content Studio 的配方编辑器中另建一条工作台无序配方：一个 `minecraft:amethyst_shard` 合成一个 `minecraft:nautilus_shell`，保存并完成编辑器要求的重载。记录编辑器分配的实际 ID。这个配方应在后续所有移除规则下仍可合成。

## 操作顺序与预期

1. 进入移除管理器，按产物 `minecraft:nautilus_shell` 查看原始配方。应能定位上表四条原始 ID。新建 `OUTPUT = minecraft:nautilus_shell` 规则，确认影响列表默认全选；取消 `fixture:scope_excluded` 的勾选，选 **按此范围持续移除**，再在主页面 **保存应用**。等到服务端明确返回已应用。
2. 查看配方目录和工作台：`minecraft:wheat_seeds` 应仍能合成；`minecraft:paper`、`minecraft:flint`、`minecraft:bone_meal` 的**原始**配方应被移除。若脚本已安装，其 `minecraft:glass_bottle`、`minecraft:stick`、`minecraft:clay_ball`、`minecraft:feather` 合成途径应存在；`minecraft:amethyst_shard` 对应的 Content Studio 配方也应存在。若两个模组都安装，两条复用 ID 的脚本配方应分别存在。此处要特别区分“原始同 ID 配方已移除”和“后来写入的同 ID 配方仍可制作”。
3. 保留这条规则，**之后**新增 `data/fixture/recipes/future_new.json`，内容沿用模板、输入改为 `minecraft:stone_button`。执行 `/reload`；`fixture:future_new` 应被持续规则移除，而当前排除的 `fixture:scope_excluded` 仍可合成。不要把 `future_new.json` 放入第一轮数据包。
4. 在新复制的实例中单独测试 **只移除当前勾选的配方**：只勾选 `fixture:scope_target`，保存应用后它应消失；随后添加 `future_new.json` 并 `/reload`，新 ID 应仍可合成。这与步骤 3 区分了精确 ID 规则和持续范围规则。
5. 在另一次干净运行中建立两个能命中 `fixture:scope_target` 的批量规则（例如 `OUTPUT = minecraft:nautilus_shell` 与 `MOD = fixture`）。只在一条规则中排除它时，它应仍显示“已移除”及另一条阻挡原因。使用 **恢复这一条** 后，应从所有命中规则中排除该 ID、删除对应精确 ID 规则，同时保留其他配方的移除效果。
6. 右键有原始配方的物品，选择 `minecraft:crafting` 类型。打开的影响列表应包含其他产物的原始工作台配方，而不只是被右键的物品；仅搜索或折叠分组不应改变隐藏配方的选择。一个产物组取消勾选后，只排除它当前列出的 ID。需在 640×360 窗口检查长 ID、分页、滚动和中英文按钮。
7. 在 A 的干净副本中分别保存 `RECIPE_ID = fixture:scope_target`、`MOD = fixture`、`OUTPUT = minecraft:nautilus_shell`、`TAG = fixture:sea_result`、`TYPE = minecraft:crafting` 规则，分别确认对应原始配方被移除。每种规则单独用干净副本，以免前一条规则掩盖当前结果。`TYPE` 应采用实际解析后的配方类型。
8. 用专门提供虚拟配方的 JEI 测试夹具检查“无服务端原始 ID”的条目：应只允许预览，不出现可执行的移除操作。没有这种夹具时记为“未覆盖”，不能用普通已注册的 JEI 配方代替。在一次性实例中使配方配置读取失败，再 `/reload`，应保留原始配方并记录错误；不能因损坏配置而清空配方。对不可写配置目录或可注入的重载失败做独立故障测试：保存前的草稿应留在客户端；若文件已写入但重载失败，应显示“已写入但尚未应用”。

逐项记录**实际**输入、输出、配方 ID、界面状态、合成结果、相关日志时间/顺序和结论。若任一步不符，停止把矩阵标为通过，先定位并修复实现。

## 构建与结果记录

从隔离工作树执行，不要把构建输出写入生产整合包目录：

```powershell
.\gradlew.bat test --no-daemon --stacktrace
.\gradlew.bat build --no-daemon --stacktrace -Poutput_mods_dir=build/release
git diff --check
```

| 检查 | 状态 | 实际命令输出或日志位置 |
| --- | --- | --- |
| JUnit 全量测试 | 通过 | `gradlew test build --no-daemon --console=plain -Poutput_mods_dir=build/release`，BUILD SUCCESSFUL |
| Gradle 构建 | 通过 | 同一命令，`build/release/` |
| A：无脚本模组，启动与 `/reload` | 通过 | `build/verification/none/`，`/recipeverify` |
| B：仅 KubeJS，启动与 `/reload` | 通过 | `build/verification/kubejs/`，`/recipeverify` |
| C：仅 CraftTweaker，启动与 `/reload` | 通过 | `build/verification/crt/`，`/recipeverify` |
| D：两者同时安装，启动与 `/reload` | 通过 | `build/verification/both/`，`/recipeverify` |
| 客户端 UI、故障恢复与 JEI 虚拟配方 | 未实测 | 需要图形客户端或故障注入夹具 |

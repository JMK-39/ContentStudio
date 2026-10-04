# GUI verification / 界面验证

## English

Date: 2026-10-04. Core: 26.10.4 (ece4f8c). Enabled targets: Forge 1.20.1 and NeoForge 1.21.1.

- Offline buildAll passed; existing Forge 36 and NeoForge 56 tests passed. Architecture, reference and Mixin checks passed (7 / 8 mixins, zero problems). Authored language parity: 871 base keys and 64 NeoForge override keys per language.
- The test-only GuiLongTextValidation fixture opens 22 actual editor states in the existing NeoForge 21.1.252 installation. English and Chinese were captured at 854×480 and 1536×864, GUI scale automatic. Additional English translation suffixes and delayed captures exercise clipping and scrolling. No desktop input or extra game download was used.
- The completed recheck reports captures=102, failures=0, userSettingsRestored=true. The final five-page recheck reports captures=30, failures=0, userSettingsRestored=true. These markers confirm execution, not automatic layout assertions; screenshots were reviewed separately. The initial run provided the remaining editor captures and was not counted as a complete fixture pass.
- Real screenshots cover all changed pages, including entity/block entry variants and valid/invalid component editing. Populated global-removal and exclusion rows could not be captured because the server replaced fixture data; their bounds were reviewed in source.
- Long left/right/explicit-width tooltip probes fit horizontally with Core 26.10.4. A 50-line tooltip still exceeds screen height; see tooltip-height-follow-up.md. This central limitation remains open.
- Original options.txt bytes were restored (SHA256 50498E5F02C94CAADB7B7752AE4F4C304CF3463FD27382E950FA6348F70AD919). The temporary validation JAR was moved out of mods after the client stopped normally. Production JARs remain installed; game memory settings were preserved.

Evidence is stored locally in .gradle/gui-long-text-20261004: build-final.log, client-recheck.log, client-final.log and phase-numbered PNGs. The fixture is excluded from release JARs.

## 简体中文

日期：2026-10-04。核心：26.10.4（ece4f8c）。启用目标：Forge 1.20.1、NeoForge 1.21.1。

- buildAll 离线构建通过；现有 Forge 36 项、NeoForge 56 项测试通过。架构、引用及 Mixin 检查通过（7 / 8 个 Mixin，零问题）；两种语言均有 871 个基础键及 64 个 NeoForge 覆盖键。
- 测试专用 GuiLongTextValidation 在用户现有 NeoForge 21.1.252 客户端中打开 22 个真实编辑器状态。自动 GUI 缩放下分别检查中英文 854×480、1536×864 截图，并用超长英文翻译和延迟截图检查裁剪及滚动；没有操作桌面输入或额外下载游戏。
- 完成的复查记录 captures=102、failures=0、userSettingsRestored=true；最终五页复查记录 captures=30、failures=0、userSettingsRestored=true。这些标记只证明执行完成，布局由截图另行检查；首次运行提供其余界面截图，不计为整轮通过。
- 截图覆盖所有修改页面，含实体/方块条目分支及组件有效/错误状态。服务器响应替换了全局移除和排除列表的测试数据，因此未截图验证填充后的这两类行；已检查其源码边界。
- 26.10.4 下左右边缘及指定宽度的长提示横向适配通过；50 行提示仍纵向越界，见 tooltip-height-follow-up.md，此核心问题仍待处理。
- options.txt 已逐字节恢复，SHA256 为 50498E5F02C94CAADB7B7752AE4F4C304CF3463FD27382E950FA6348F70AD919。客户端正常退出后已移走测试 JAR，保留正式产物与原内存设置。

本机证据位于 .gradle/gui-long-text-20261004，包含构建日志、两次复查日志和分阶段 PNG；正式 JAR 不包含测试模块。

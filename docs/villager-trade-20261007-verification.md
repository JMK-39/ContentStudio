# Villager trade merge verification / 村民交易合并验证

## English

The merge starts from official commit `f0c979e`, using the third-party archive only as a behavior reference. Production code keeps the current public KineticPage/KineticUi architecture and existing editor layout, trade modes, late override, remote permission checks and version-native item data.

- Fresh offline `buildAll` plus all three opt-in fixture archives passed: 36 seconds, 45 tasks. Compilers and bytecode are Java17/61 on Forge1.20.1, Java21/65 on NeoForge1.21.1, Java25/69 on NeoForge26.1.2. Gradle's daemon remains Java21.
- Architecture/language checks, Mixin targets (8 Forge / 9 per NeoForge node) and final-JAR Core references report zero problems. Authored EN/CN keys:930; NeoForge packaged keys:943. Forge includes its refmap and `JAVA_17` Mixin configuration. Fixture classes are absent from release archives.
- Forge JUnit:47 tests,0 failures,9 loader-dependent skips. NeoForge1.21.1:67 tests,0 failures. The26.1.2 unit task remains disabled by the existing loader limitation; its initialized-client fixture runs the registry-dependent validation assertions.
- All three installed profiles passed100 checks and16 captures each: English/Chinese,854×480/1920×1080, errors/problem/source/close menus. Menu rectangles including buttons stay inside the unchanged editor frame. Rejected drafts, actual server ACKs, canonical19-field records, independent source/late settings, shortcut form state, removal/Undo and unchanged-original partial-draft discard/suspension are covered.
- The unchanged-original partial-draft case first reproduced a failure in1.21.1 after65 successful checks, then passed after explicit restoration of the latest accepted rules. This is an actual installed-loader regression check.
- Final Forge acceptance uses the existing full modpack with its original8GiB heap and Core26.10.8. Earlier attempts encountered Windows commit-memory exhaustion and the old Core deduplication filter's startup deadlock. After the requested removal of deduplication, the final100-check/16-capture run passed with the original cleanup and keyword/severity settings; only the obsolete deduplication entry was migrated away. No unrelated user/IDE/game process was stopped and no new Minecraft installation was downloaded.
- Dedicated-server sessions, low-permission users and forced disk-write failures were not launched. Their server guards and reply codecs were reviewed; codec denial fields were exercised in the initialized fixture.
- Release JARs are in `D:/NEWMODS`; existing matching modules in the three installed profiles were updated and hash-checked. Owned fixture JARs were removed after exit. Latest captured villager config bytes and touched language/GUI/fullscreen settings match their pre-test values. A copied verification save is retained; original saves were not edited.

Local detailed logs, SHA256 manifest and captures are under the ignored `.gradle/villager-parity-20261007/` directory. Source-only mode deliberately does not reconstruct vanilla offers already removed when switching back to merge-all; future normal generation uses the selected mode.

## 简体中文

本次合并以官方提交 `f0c979e` 为基准，第三方压缩包仅用于参考功能行为。正式代码保留当前公开的 KineticPage/KineticUi 架构、现有编辑器布局、交易模式、后覆盖、远程权限检查和各版本原生物品数据写法。

- 最新离线 `buildAll` 及三份可选验证插件构建通过：36秒、45个任务。实际编译器和字节码分别为 Forge1.20.1 的 Java17/61、NeoForge1.21.1 的 Java21/65、NeoForge26.1.2 的 Java25/69；Gradle 守护进程仍使用 Java21。
- 架构、语言键、Mixin目标（Forge8个，每个NeoForge节点9个）和发布JAR核心引用检查均为0问题。源语言文件两边各930键，NeoForge打包后两边各943键。Forge包含refmap，Mixin兼容级别为`JAVA_17`；正式JAR不包含验证插件类。
- Forge单元测试47项、0失败、9项因加载器条件跳过；NeoForge1.21.1为67项、0失败。26.1.2沿用现有加载器限制，关闭独立单元测试任务，由已初始化的游戏内验证插件执行依赖注册表的断言。
- 三个已安装版本各通过100项检查、16张截图：英文/中文，854×480/1920×1080，问题标记/问题菜单/来源菜单/关闭菜单。包含按钮的菜单矩形保持在原编辑器边框内。覆盖无效草稿保留、真实服务端确认、19列记录标准化、来源与后覆盖独立、快捷键表单恢复、移除/撤销，以及未改动原始无效草稿在部分保存后放弃/暂存的边界情况。
- 未改动原始草稿的边界情况先在1.21.1实机中重现：65项检查后失败；显式恢复最后接受的规则后通过，形成实际加载器内的回归验证。
- Forge最终验收使用现有完整整合包、原8GiB内存分配和Core26.10.8。早先启动曾遇到Windows可提交内存不足，以及旧核心去重过滤器的启动死锁；按要求移除去重后，最终100项检查、16张截图全部通过。保持原有清理、关键词和日志级别设置，仅自动移除旧去重配置项。不停止无关用户、IDE或游戏进程，不另行下载Minecraft。
- 未启动独立服务端、低权限用户和强制写盘失败场景；服务端保护与响应编解码经过审查，拒绝原因字段在已初始化的验证插件中检查。
- 正式JAR输出到`D:/NEWMODS`，三个现有版本目录中已安装的对应模块已更新并核对哈希。游戏退出后移除自建验证插件。最新备份的村民配置字节及所触及的语言、GUI缩放、全屏设置与测试前一致。保留复制的验证存档，未编辑原始存档。

详细日志、SHA256清单和截图位于被Git忽略的`.gradle/villager-parity-20261007/`目录。仅自定义交易模式切回合并模式时，不会重建已经删除的原版交易；后续正常生成使用所选模式。

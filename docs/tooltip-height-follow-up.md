# Tall tooltip follow-up / 过高悬浮提示后续处理

## English

Core commit ece4f8c / 26.10.4 fixes horizontal fitting. ContentStudio now requires it and sends Components to the wrapping tooltip API instead of preformatted lines.

A vertical overflow still reproduces in the existing Minecraft 1.21.1 / NeoForge 21.1.252 client, English, automatic GUI scale:

- Window 854×480, GUI-scaled screen 427×240.
- Request 50 single-line Components through KineticOverlays.requestTooltip(lines, guiWidth-4, guiHeight-8).
- Only part of the 50 lines is visible; the tooltip extends beyond both top and bottom.
- The 1536×864 screenshot reproduces the same limitation.

Evidence: D:/IDEAWork/ContentStudio/.gradle/gui-long-text-20261004/0-25-tooltip-tall-start.png.
Fixture: src/test/java/dev/xyat/contentstudiovalidation/GuiLongTextValidation.java, TooltipProbePage.
Installed Core SHA256 equals rebuilt output: F8C93B1477F836EC7AE6B65BB7F06465BCFC752516DF404BA689A02815E04758.

Root cause: GuiOverlayRuntime limits width then delegates to vanilla tooltip rendering; DefaultTooltipPositioner cannot fit content taller than the screen. Current public tooltip APIs have no height budget or reader for excess content.

Please handle tall text/item/formatted tooltips centrally, keeping all content accessible, for example through a bounded scrollable viewport. Preserve styles, item tooltip components and anchor coordinates. Do not merely discard lines. Core was not edited and no addon-specific tooltip scrolling was added.

Follow-up validation: long unbroken lines at both horizontal edges; 50 styled lines; long item names/lore and custom components; English/Chinese; small/enlarged windows; all supported versions/loaders.

## 简体中文

核心 ece4f8c / 26.10.4 已修复横向宽度适配。ContentStudio 现要求此版本，使用可换行提示 API 传递 Component，取消提前排版的绕过路径。

现有1.21.1 / NeoForge21.1.252客户端仍可复现纵向越界，英语、GUI缩放自动：

- 窗口854×480，GUI缩放后的屏幕427×240。
- 通过 KineticOverlays.requestTooltip(lines, guiWidth-4, guiHeight-8) 显示50行Component。
- 只能看到部分内容，提示框上下均超出屏幕。
- 1536×864同样复现。

证据：D:/IDEAWork/ContentStudio/.gradle/gui-long-text-20261004/0-25-tooltip-tall-start.png。
复现代码：src/test/java/dev/xyat/contentstudiovalidation/GuiLongTextValidation.java 的 TooltipProbePage。
测试目录的核心SHA256与重建产物完全一致：F8C93B1477F836EC7AE6B65BB7F06465BCFC752516DF404BA689A02815E04758。

原因：GuiOverlayRuntime限制宽度后交给原版绘制，DefaultTooltipPositioner无法适配内容高于屏幕；当前公开提示API没有高度预算或超长内容阅读能力。

请在核心统一处理过高的文本、物品和排版提示，保留全部内容的可访问性，例如有边界的可滚动视口；不要简单裁掉行。需保留样式、物品提示组件及锚点坐标。本次没有修改核心，也没有在附属另写提示滚动逻辑。

后续验证：左右边缘的无空格长行、50行带样式文字、长物品名称/描述与自定义提示组件；中英文；小窗口与放大窗口；支持的全部版本与加载器。

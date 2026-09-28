package dev.xyat.contentstudio.tooltip.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.tooltip.TooltipNetwork;

public final class TooltipConfigGui {
    public static final String PAGE_ID = "contentstudio:tooltip";

    private TooltipConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.contentstudio.tooltip.tooltip.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(KineticI18n.translatable("cfg.contentstudio.tooltip.tooltip.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.contentstudio.tooltip.tooltip.description"))
                .action(
                        "open_editor",
                        KineticI18n.translatable("cfg.contentstudio.tooltip.tooltip.open_editor"),
                        TooltipNetwork::requestEditor,
                        KineticI18n.translatable("cfg.contentstudio.tooltip.tooltip.open_editor.tooltip")
                )
                .build());
    }

    // 原 create(Screen parent)：以当前界面为父打开本模组配置中心 / Former create(Screen parent): opens this mod's config hub as a child of the current screen.
    public static void open() {
        KTConfigApi.openOwner("contentstudio");
    }
}

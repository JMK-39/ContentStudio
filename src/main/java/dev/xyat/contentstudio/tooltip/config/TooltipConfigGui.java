package dev.xyat.contentstudio.tooltip.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.tooltip.TooltipNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TooltipConfigGui {
    public static final String PAGE_ID = "contentstudio:tooltip";

    private TooltipConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.contentstudio.tooltip.tooltip.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(Component.translatable("cfg.contentstudio.tooltip.tooltip.apply_notice"))
                .pageDescription(Component.translatable("cfg.contentstudio.tooltip.tooltip.description"))
                .action(
                        "open_editor",
                        Component.translatable("cfg.contentstudio.tooltip.tooltip.open_editor"),
                        TooltipNetwork::requestEditor,
                        Component.translatable("cfg.contentstudio.tooltip.tooltip.open_editor.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "contentstudio");
    }
}

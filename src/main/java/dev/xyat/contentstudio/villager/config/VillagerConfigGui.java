package dev.xyat.contentstudio.villager.config;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VillagerConfigGui {
    public static final String PAGE_ID = "contentstudio:villager";
    public static final String EDITOR_PAGE_ID = "contentstudio:editors";

    private VillagerConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(buildSettingsPage());
        KTConfigApi.register(buildEditorsPage());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "contentstudio");
    }

    private static KTConfigPage buildSettingsPage() {
        return KTConfigPage.builder(PAGE_ID, Component.translatable("cfg.contentstudio.villager.villager.category"))
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(Component.translatable("cfg.contentstudio.villager.villager.apply_notice"))
                .pageDescription(Component.translatable("cfg.contentstudio.villager.villager.behavior.description"))
                .tickSecondsValue(
                        "tick_interval",
                        Component.translatable("cfg.contentstudio.villager.villager.tick_interval"),
                        () -> VillagerConfig.villagerTickInterval,
                        value -> VillagerConfig.villagerTickInterval = value,
                        100,
                        1,
                        600,
                        Component.translatable("cfg.contentstudio.villager.villager.tick_interval.tooltip")
                )
                .booleanValue(
                        "trade_update_protection",
                        Component.translatable("cfg.contentstudio.villager.villager.trade_update_protection"),
                        () -> VillagerConfig.enableVillagerTradeUpdateProtection,
                        value -> VillagerConfig.enableVillagerTradeUpdateProtection = value,
                        true,
                        Component.translatable("cfg.contentstudio.villager.villager.trade_update_protection.tooltip")
                )
                .booleanValue(
                        "follow",
                        Component.translatable("cfg.contentstudio.villager.villager.follow"),
                        () -> VillagerConfig.enableVillagerFollow,
                        value -> VillagerConfig.enableVillagerFollow = value,
                        true,
                        Component.translatable("cfg.contentstudio.villager.villager.follow.tooltip")
                )
                .build();
    }

    private static KTConfigPage buildEditorsPage() {
        return KTConfigPage.builder(
                        EDITOR_PAGE_ID,
                        Component.translatable("cfg.contentstudio.villager.villager.editors")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(Component.translatable("cfg.contentstudio.villager.villager.editors.description"))
                .action(
                        "open_follow_item_editor",
                        Component.translatable("cfg.contentstudio.villager.villager.items"),
                        VillagerNetwork::requestFollowItemEditor,
                        Component.translatable("cfg.contentstudio.villager.villager.items.tooltip")
                )
                .action(
                        "trade_editor",
                        Component.translatable("cfg.contentstudio.villager.villager.trade_editor"),
                        VillagerNetwork::requestTradeEditor,
                        Component.translatable("cfg.contentstudio.villager.villager.trade_editor.tooltip")
                )
                .build();
    }
}

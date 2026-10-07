package dev.xyat.contentstudio.villager.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;

public final class VillagerConfigGui {
    public static final String PAGE_ID = "contentstudio:villager";
    public static final String EDITOR_PAGE_ID = "contentstudio:editors";

    private VillagerConfigGui() {
    }

    public static void load() {
        dev.xyat.contentstudio.villager.client.VillagerClientKeyBindings.register();
        KTConfigApi.register(buildSettingsPage());
        KTConfigApi.register(buildEditorsPage());
    }

    // 原 create(Screen parent)：以当前界面为父打开本模组配置中心 / Former create(Screen parent): opens this mod's config hub as a child of the current screen.
    public static void open() {
        KTConfigApi.openOwner("contentstudio");
    }

    private static KTConfigPage buildSettingsPage() {
        return KTConfigPage.builder(PAGE_ID, KineticI18n.translatable("cfg.contentstudio.villager.villager.category"))
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.MIXED)
                .applyNotice(KineticI18n.translatable("cfg.contentstudio.villager.villager.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.contentstudio.villager.villager.behavior.description"))
                .tickSecondsValue(
                        "tick_interval",
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.tick_interval"),
                        () -> VillagerConfig.villagerTickInterval,
                        value -> VillagerConfig.villagerTickInterval = value,
                        100,
                        1,
                        600,
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.tick_interval.tooltip")
                )
                .booleanValue(
                        "trade_update_protection",
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.trade_update_protection"),
                        () -> VillagerConfig.enableVillagerTradeUpdateProtection,
                        value -> VillagerConfig.enableVillagerTradeUpdateProtection = value,
                        true,
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.trade_update_protection.tooltip")
                )
                .booleanValue(
                        "follow",
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.follow"),
                        () -> VillagerConfig.enableVillagerFollow,
                        value -> VillagerConfig.enableVillagerFollow = value,
                        true,
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.follow.tooltip")
                )
                .build();
    }

    private static KTConfigPage buildEditorsPage() {
        return KTConfigPage.builder(
                        EDITOR_PAGE_ID,
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.editors")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .pageDescription(KineticI18n.translatable("cfg.contentstudio.villager.villager.editors.description"))
                .action(
                        "open_follow_item_editor",
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.items"),
                        VillagerNetwork::requestFollowItemEditor,
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.items.tooltip")
                )
                .action(
                        "trade_editor",
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.trade_editor"),
                        dev.xyat.contentstudio.villager.client.VillagerClientActions::requestTradeEditor,
                        KineticI18n.translatable("cfg.contentstudio.villager.villager.trade_editor.tooltip")
                )
                .build();
    }
}

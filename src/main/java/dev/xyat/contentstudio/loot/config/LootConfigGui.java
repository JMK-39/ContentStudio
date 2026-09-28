package dev.xyat.contentstudio.loot.config;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.client.LootClientHandler;

public final class LootConfigGui {
    public static final String PAGE_ID = "contentstudio:loots";

    private LootConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(KineticI18n.translatable("cfg.contentstudio.loot.loots.apply_notice"))
                .pageDescription(KineticI18n.translatable("cfg.contentstudio.loot.loots.description"))
                .action(
                        "open_entity_loot_editor",
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.entity"),
                        () -> LootClientHandler.requestOpenEditor(LootEntryInfo.MODE_ENTITY),
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.entity.tooltip")
                )
                .action(
                        "open_block_loot_editor",
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.block"),
                        () -> LootClientHandler.requestOpenEditor(LootEntryInfo.MODE_BLOCK),
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.block.tooltip")
                )
                .action(
                        "open_chest_loot_editor",
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.chest"),
                        () -> LootClientHandler.requestOpenEditor(LootEntryInfo.MODE_CHEST),
                        KineticI18n.translatable("cfg.contentstudio.loot.loots.chest.tooltip")
                )
                .build());
    }

    // 原 create(Screen parent)：以当前界面为父打开本模组配置中心 / Former create(Screen parent): opens this mod's config hub as a child of the current screen.
    public static void open() {
        KTConfigApi.openOwner("contentstudio");
    }
}

package dev.xyat.contentstudio.loot.config;

import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.config.client.KTConfigPage;
import dev.xyat.kineticcore.api.config.client.KTConfigScope;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.client.LootClientHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LootConfigGui {
    public static final String PAGE_ID = "contentstudio:loots";

    private LootConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.contentstudio.loot.loots.title")
                )
                .scope(KTConfigScope.SERVER_AUTHORITATIVE)
                .serverManaged()
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .applyNotice(Component.translatable("cfg.contentstudio.loot.loots.apply_notice"))
                .pageDescription(Component.translatable("cfg.contentstudio.loot.loots.description"))
                .action(
                        "open_entity_loot_editor",
                        Component.translatable("cfg.contentstudio.loot.loots.entity"),
                        () -> LootClientHandler.requestOpenEditor(LootEntryInfo.MODE_ENTITY),
                        Component.translatable("cfg.contentstudio.loot.loots.entity.tooltip")
                )
                .action(
                        "open_block_loot_editor",
                        Component.translatable("cfg.contentstudio.loot.loots.block"),
                        () -> LootClientHandler.requestOpenEditor(LootEntryInfo.MODE_BLOCK),
                        Component.translatable("cfg.contentstudio.loot.loots.block.tooltip")
                )
                .action(
                        "open_chest_loot_editor",
                        Component.translatable("cfg.contentstudio.loot.loots.chest"),
                        () -> LootClientHandler.requestOpenEditor(LootEntryInfo.MODE_CHEST),
                        Component.translatable("cfg.contentstudio.loot.loots.chest.tooltip")
                )
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreenForOwner(parent, "contentstudio");
    }
}

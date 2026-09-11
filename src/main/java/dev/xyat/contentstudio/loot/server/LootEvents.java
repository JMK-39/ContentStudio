package dev.xyat.contentstudio.loot.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class LootEvents {
    private static final LootOverrideReloadListener RELOAD_LISTENER = new LootOverrideReloadListener();
    private static boolean registered;

    private LootEvents() {
    }

    public static void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.addListener(LootEvents::onLootTableLoad);
            MinecraftForge.EVENT_BUS.addListener(LootEvents::onAddReloadListener);
            MinecraftForge.EVENT_BUS.addListener(LootEvents::onServerAboutToStart);
            registered = true;
        }
    }

    private static void onLootTableLoad(LootTableLoadEvent event) {
        if (LootTableOverrideStore.isLoadEventBypass()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        LootTable replacement = LootTableOverrideStore.createLoadEventReplacement(server, event.getName());
        if (replacement != null) {
            event.setTable(replacement);
        }
    }

    private static void onAddReloadListener(AddReloadListenerEvent event) {
        LootTableOverrideStore.invalidateLootReferenceCache();
        event.addListener(RELOAD_LISTENER);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        LootTableOverrideStore.applyAll(event.getServer());
    }

    private static final class LootOverrideReloadListener extends SimplePreparableReloadListener<Object> {
        @Override
        protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return new Object();
        }

        @Override
        protected void apply(Object prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                LootTableOverrideStore.applyAll(server, resourceManager);
            }
        }
    }
}

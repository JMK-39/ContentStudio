package dev.xyat.contentstudio.loot.server;

import javax.annotation.Nonnull;

import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.loot.event.KineticLootEvents;
import dev.xyat.kineticcore.api.resource.event.KineticResourceEvents;
import dev.xyat.kineticcore.api.runtime.KineticServerRuntime;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.loot.LootTable;

public final class LootEvents {
    private static final LootOverrideReloadListener RELOAD_LISTENER = new LootOverrideReloadListener();
    private static boolean registered;

    private LootEvents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

//? if >=1.21 {
/*
*///?} else {
        KineticLootEvents.onTableLoad(KineticEventPriority.NORMAL, context -> {
            if (LootTableOverrideStore.isLoadEventBypass()) {
                return;
            }
            MinecraftServer server = KineticServerRuntime.currentServer();
            if (server == null) {
                return;
            }
            LootTable replacement = LootTableOverrideStore.createLoadEventReplacement(server, context.id());
            if (replacement != null) {
                context.table(replacement);
            }
        });


//?}
        KineticResourceEvents.onAddReloadListener(KineticEventPriority.NORMAL, context -> {
            LootTableOverrideStore.invalidateLootReferenceCache();
//? if >=1.21 {
/*            context.addListener(new LootOverrideReloadListener(context.serverResources().fullRegistries(),
                    context.serverResources().getRegistryLookup()));
*///?} else {
            context.addListener(RELOAD_LISTENER);
//?}

        });

        KineticServerEvents.onAboutToStart(
                KineticEventPriority.NORMAL,
                LootTableOverrideStore::applyAll
        );
        registered = true;
    }

//? if >=1.21 {
/*    private static final class LootOverrideReloadListener extends SimplePreparableReloadListener<Object> {
        private final net.minecraft.server.ReloadableServerRegistries.Holder registries;
        private final net.minecraft.core.HolderLookup.Provider lookup;
        private LootOverrideReloadListener() { this.registries = null; this.lookup = null; }
        private LootOverrideReloadListener(net.minecraft.server.ReloadableServerRegistries.Holder registries,
                net.minecraft.core.HolderLookup.Provider lookup) {
            this.registries = registries;
            this.lookup = lookup;
        }
        @Override
        @Nonnull
        protected Object prepare(@Nonnull ResourceManager resources, @Nonnull ProfilerFiller profiler) { return new Object(); }
        @Override
        protected void apply(@Nonnull Object prepared, @Nonnull ResourceManager resources, @Nonnull ProfilerFiller profiler) {
            if (registries != null) LootTableOverrideStore.applyAll(registries, lookup, resources);
        }
    }

*///?} else {
    private static final class LootOverrideReloadListener extends SimplePreparableReloadListener<Object> {
        @Override
        @Nonnull
        protected Object prepare(@Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
            return new Object();
        }

        @Override
        protected void apply(@Nonnull Object prepared, @Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
            MinecraftServer server = KineticServerRuntime.currentServer();
            if (server != null) {
                LootTableOverrideStore.applyAll(server, resourceManager);
            }
        }
    }

//?}
}

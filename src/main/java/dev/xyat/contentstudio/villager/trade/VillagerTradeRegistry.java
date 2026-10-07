package dev.xyat.contentstudio.villager.trade;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
//? if <26.1 {
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import dev.xyat.kineticcore.api.villager.event.KineticVillagerEvents;
//?}
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VillagerTradeRegistry {
    private static final Map<String, VillagerConfig.TradeGroup> ACTIVE_GROUPS = new HashMap<>();
    private static final Map<String, List<VillagerConfig.TradeOfferData>> ACTIVE_OFFERS = new HashMap<>();
    private static final Map<String, VillagerConfig.VanillaTradeOverride> ACTIVE_OVERRIDES = new HashMap<>();
    // Up to 1.21.1 the vanilla trade tables are rewritten in place; their original listings are kept here.
    //? if <26.1
    private static final Map<String, List<VillagerTrades.ItemListing>> BASELINE_LISTINGS = new HashMap<>();

    private static boolean sessionPrepared;
    private static MinecraftServer activeServer;
    private static boolean registered;
    private static volatile TradeConfigSnapshot authoritativeConfig = TradeConfigSnapshot.empty();

    private record TradeConfigSnapshot(List<String> groups, List<String> offers, List<String> overrides, boolean lateOverride) {
        private TradeConfigSnapshot {
            groups = List.copyOf(groups);
            offers = List.copyOf(offers);
            overrides = List.copyOf(overrides);
        }

        private static TradeConfigSnapshot empty() {
            return new TradeConfigSnapshot(List.of(), List.of(), List.of(), false);
        }
    }

    private VillagerTradeRegistry() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        KineticServerEvents.onAboutToStart(KineticEventPriority.HIGHEST, server -> {
            if (activeServer != server) {
                resetSession();
                activeServer = server;
            }
            prepareSession();
        });

        // 26.1 keeps trades in data and has no trade events: the updateTrades mixins apply the configuration when a
        // villager or wandering trader rolls its offers.
        //? if <26.1 {
        KineticVillagerEvents.onVillagerTrades(KineticEventPriority.LOWEST, context -> {
            prepareSession();
            ResourceLocation professionId = KineticRegistries.villagerProfessions().id(context.profession());
            if (professionId == null) {
                return;
            }
            String owner = professionId.toString();
            for (int level = 1; level <= 5; level++) {
                List<VillagerTrades.ItemListing> trades = context.trades(level);
                if (trades == null) {
                    continue;
                }
                captureBaseline(owner, level, trades);
                applyPublishedPool(owner, level, trades);
            }
        });

        KineticVillagerEvents.onWandererTrades(KineticEventPriority.LOWEST, context -> {
            prepareSession();
            String owner = VillagerConfig.WANDERING_TRADER_ID;
            captureBaseline(owner, 1, context.genericTrades());
            captureBaseline(owner, 2, context.rareTrades());
            applyPublishedPool(owner, 1, context.genericTrades());
            applyPublishedPool(owner, 2, context.rareTrades());
        });
        //?}

        KineticServerEvents.onStopped(KineticEventPriority.NORMAL, server -> {
            if (activeServer == null || activeServer == server) {
                restoreBaselinePools();
                resetSession();
                activeServer = null;
            }
        });
        registered = true;
    }

    public static boolean isSessionUnavailable() {
        return !sessionPrepared;
    }

    public static void reloadFromDisk() {
        VillagerConfig.load();
        if (sessionPrepared) {
            captureAuthoritativeState();
            rebuildActiveState();
            publishAllLive();
        }
    }

    public static List<String> getAuthoritativeTradeGroups() {
        return authoritativeConfig.groups();
    }

    public static List<String> getAuthoritativeTradeOffers() {
        return authoritativeConfig.offers();
    }

    public static List<String> getAuthoritativeTradeOverrides() {
        return authoritativeConfig.overrides();
    }

    public static boolean isActiveTradeLateOverride() {
        return authoritativeConfig.lateOverride();
    }

    public static VillagerConfig.TradeGroup getActiveTradeGroup(String owner, int level) {
        return ACTIVE_GROUPS.get(tradeKey(owner, level));
    }

    public static List<VillagerConfig.TradeOfferData> getActiveTradeOffers(String owner, int level) {
        List<VillagerConfig.TradeOfferData> offers = ACTIVE_OFFERS.get(tradeKey(owner, level));
        return offers == null ? List.of() : new ArrayList<>(offers);
    }

    public static boolean hasActiveCustomOffers(String owner, int level) {
        List<VillagerConfig.TradeOfferData> offers = ACTIVE_OFFERS.get(tradeKey(owner, level));
        return offers != null && !offers.isEmpty();
    }

    public static VillagerConfig.VanillaTradeOverride getActiveVanillaTradeOverride(String owner, int level, int vanillaIndex) {
        return ACTIVE_OVERRIDES.get(overrideKey(owner, level, vanillaIndex));
    }

    public static boolean isActiveVanillaTradeDisabled(String owner, int level, int vanillaIndex) {
        VillagerConfig.VanillaTradeOverride override = getActiveVanillaTradeOverride(owner, level, vanillaIndex);
        return override != null && !override.enabled();
    }

    public static int getActiveVanillaTradeWeight(String owner, int level, int vanillaIndex) {
        VillagerConfig.VanillaTradeOverride override = getActiveVanillaTradeOverride(owner, level, vanillaIndex);
        return override == null ? 1 : override.weight();
    }

    public static boolean hasActiveVanillaOverrides(String owner, int level) {
        String prefix = tradeKey(owner, level) + "|";
        for (String key : ACTIVE_OVERRIDES.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasActiveLevelChanges(String owner, int level) {
        return VillagerConfig.isLocalCustomTradesOnly()
                || getActiveTradeGroup(owner, level) != null
                || hasActiveVanillaOverrides(owner, level)
                || hasActiveCustomOffers(owner, level);
    }

    //? if <26.1 {
    public static VillagerTrades.ItemListing[] getBaselineListings(String owner, int level) {
        List<VillagerTrades.ItemListing> listings = BASELINE_LISTINGS.get(tradeKey(owner, level));
        return listings == null ? null : listings.toArray(VillagerTrades.ItemListing[]::new);
    }
    //?}

    public static boolean applyAndSaveLive(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean lateOverride
    ) {
        return applyAndSaveLiveDetailed(groups, offers, overrides, lateOverride).success();
    }

    public record TradeSaveOutcome(boolean success, String failureCode) {
        public static TradeSaveOutcome ok() {
            return new TradeSaveOutcome(true, "");
        }

        public static TradeSaveOutcome failure(String code) {
            return new TradeSaveOutcome(false, code == null ? "unknown" : code);
        }
    }

    public static TradeSaveOutcome applyAndSaveLiveDetailed(
            List<String> groups,
            List<String> offers,
            List<String> overrides,
            boolean lateOverride
    ) {
        if (!sessionPrepared || activeServer == null) {
            return TradeSaveOutcome.failure("server_unavailable");
        }
        VillagerConfig.TradeValidationResult validation = VillagerConfig.validateTradeListsDetailed(groups, offers, overrides);
        if (!validation.issues().isEmpty()) {
            for (VillagerConfig.TradeValidationIssue issue : validation.issues()) {
                VillagerModule.LOGGER.error(
                        "Rejected villager trade config entry kind={} index={} owner={} level={} slot={} reason={} value={}",
                        issue.kind(), issue.index(), issue.owner(), issue.level(), issue.slot(), issue.reasonKey(), issue.value()
                );
            }
            return TradeSaveOutcome.failure("invalid_data");
        }

        List<String> oldGroups = new ArrayList<>(VillagerConfig.villagerTradeGroups);
        List<String> oldOffers = new ArrayList<>(VillagerConfig.villagerTradeOffers);
        List<String> oldOverrides = new ArrayList<>(VillagerConfig.villagerDefaultTradeOverrides);
        boolean oldLateOverride = VillagerConfig.enableVillagerTradeLateOverride;
        TradeConfigSnapshot oldAuthoritative = authoritativeConfig;
        try {
            VillagerConfig.replaceTradeLists(groups, offers, overrides);
            VillagerConfig.enableVillagerTradeLateOverride = lateOverride;
            VillagerConfig.save();
            captureAuthoritativeState();
            rebuildActiveState();
            publishAllLive();
            return TradeSaveOutcome.ok();
        } catch (Exception e) {
            VillagerConfig.replaceTradeLists(oldGroups, oldOffers, oldOverrides);
            VillagerConfig.enableVillagerTradeLateOverride = oldLateOverride;
            authoritativeConfig = oldAuthoritative;
            rebuildActiveState();
            republishAfterRollback(e);
            VillagerModule.LOGGER.error("Failed to apply villager trade configuration live", e);
            return TradeSaveOutcome.failure("write_failed");
        }
    }

    public static TradeSaveOutcome applyAndSaveTradeSourceMode(String mode) {
        if (!sessionPrepared || activeServer == null) {
            return TradeSaveOutcome.failure("server_unavailable");
        }
        VillagerConfig.TradeSourceMode requested = VillagerConfig.TradeSourceMode.parse(mode);
        if (requested == null) {
            return TradeSaveOutcome.failure("invalid_mode");
        }

        VillagerConfig.TradeSourceMode previous = VillagerConfig.getTradeSourceMode();
        try {
            VillagerConfig.saveTradeSourceModeSnapshot(requested, authoritativeConfig.groups(), authoritativeConfig.offers(),
                    authoritativeConfig.overrides(), authoritativeConfig.lateOverride());
            publishAllLive();
            return TradeSaveOutcome.ok();
        } catch (Exception e) {
            VillagerConfig.setTradeSourceMode(previous);
            republishAfterRollback(e);
            VillagerModule.LOGGER.error("Failed to save villager trade source mode", e);
            return TradeSaveOutcome.failure("write_failed");
        }
    }

    private static void republishAfterRollback(Exception failure) {
        try {
            publishAllLive();
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void resetSession() {
        sessionPrepared = false;
        authoritativeConfig = TradeConfigSnapshot.empty();
        ACTIVE_GROUPS.clear();
        ACTIVE_OFFERS.clear();
        ACTIVE_OVERRIDES.clear();
        //? if <26.1
        BASELINE_LISTINGS.clear();
    }

    private static void prepareSession() {
        if (sessionPrepared) {
            return;
        }

        VillagerConfig.load();
        captureAuthoritativeState();
        rebuildActiveState();
        sessionPrepared = true;
    }

    private static void captureAuthoritativeState() {
        authoritativeConfig = new TradeConfigSnapshot(VillagerConfig.villagerTradeGroups, VillagerConfig.villagerTradeOffers,
                VillagerConfig.villagerDefaultTradeOverrides, VillagerConfig.enableVillagerTradeLateOverride);
    }

    private static void rebuildActiveState() {
        ACTIVE_GROUPS.clear();
        ACTIVE_OFFERS.clear();
        ACTIVE_OVERRIDES.clear();

        VillagerConfig.TradeValidationResult validation = VillagerConfig.validateTradeListsDetailed(
                authoritativeConfig.groups(), authoritativeConfig.offers(), authoritativeConfig.overrides());
        for (String line : validation.validGroups()) {
            VillagerConfig.TradeGroup group = VillagerConfig.TradeGroup.parse(line);
            if (group != null) {
                ACTIVE_GROUPS.put(tradeKey(group.profession, group.level), group);
            }
        }

        Set<String> validOffers = new HashSet<>(validation.validOffers());
        for (int index = 0; index < authoritativeConfig.offers().size(); index++) {
            String line = authoritativeConfig.offers().get(index);
            if (!validOffers.contains(line)) {
                continue;
            }
            VillagerConfig.TradeOfferData data = VillagerConfig.TradeOfferData.parse(line, index);
            if (data != null) {
                ACTIVE_OFFERS.computeIfAbsent(tradeKey(data.profession(), data.level()), key -> new ArrayList<>()).add(data);
            }
        }

        for (String line : validation.validOverrides()) {
            VillagerConfig.VanillaTradeOverride override = VillagerConfig.VanillaTradeOverride.parse(line);
            if (override != null) {
                ACTIVE_OVERRIDES.put(overrideKey(override.profession(), override.level(), override.vanillaIndex()), override);
            }
        }
    }

    //? if >=26.1 {
    /*private static void publishAllLive() {
    }

    private static void restoreBaselinePools() {
    }
    *///?} else {
    private static void captureBaseline(String owner, int level, List<VillagerTrades.ItemListing> trades) {
        BASELINE_LISTINGS.put(tradeKey(owner, level), new ArrayList<>(trades));
    }

    private static void applyPublishedPool(String owner, int level, List<VillagerTrades.ItemListing> target) {
        List<VillagerTrades.ItemListing> published = buildPublishedPool(owner, level);
        if (published == null) {
            return;
        }
        target.clear();
        target.addAll(published);
    }

    private static List<VillagerTrades.ItemListing> buildPublishedPool(String owner, int level) {
        List<VillagerTrades.ItemListing> baseline = BASELINE_LISTINGS.get(tradeKey(owner, level));
        if (baseline == null) {
            return null;
        }

        VillagerConfig.TradeGroup group = getActiveTradeGroup(owner, level);
        boolean hasOverrides = hasActiveVanillaOverrides(owner, level);
        boolean hasCustomOffers = hasActiveCustomOffers(owner, level);
        if (VillagerConfig.isLocalCustomTradesOnly()) {
            List<VillagerTrades.ItemListing> published = new ArrayList<>();
            if (group != null && (group.disableLevel() || group.offerCount <= 0)) {
                return published;
            }
            for (VillagerConfig.TradeOfferData data : getActiveTradeOffers(owner, level)) {
                if (data.weight() > 0) {
                    published.add(new TradeListing(data));
                }
            }
            return published;
        }
        if (group == null && !hasOverrides && !hasCustomOffers) {
            return new ArrayList<>(baseline);
        }

        List<VillagerTrades.ItemListing> published = new ArrayList<>();
        if (group != null && group.disableLevel()) {
            return published;
        }
        if (group != null && !"add".equals(group.mode) && group.offerCount <= 0) {
            return published;
        }

        for (int i = 0; i < baseline.size(); i++) {
            if (isActiveVanillaTradeDisabled(owner, level, i)) {
                continue;
            }
            if (getActiveVanillaTradeWeight(owner, level, i) <= 0) {
                continue;
            }
            published.add(baseline.get(i));
        }

        boolean publishCustom = group == null || !"add".equals(group.mode) || group.offerCount > 0;
        if (!publishCustom) {
            return published;
        }

        for (VillagerConfig.TradeOfferData data : getActiveTradeOffers(owner, level)) {
            if (data.weight() > 0) {
                published.add(new TradeListing(data));
            }
        }
        return published;
    }

    private static void publishAllLive() {
        for (String key : new ArrayList<>(BASELINE_LISTINGS.keySet())) {
            int separator = key.lastIndexOf('|');
            if (separator <= 0 || separator >= key.length() - 1) {
                continue;
            }

            String owner = key.substring(0, separator);
            int level;
            try {
                level = Integer.parseInt(key.substring(separator + 1));
            } catch (NumberFormatException e) {
                continue;
            }

            List<VillagerTrades.ItemListing> published = buildPublishedPool(owner, level);
            if (published != null) {
                publishStaticPool(owner, level, published);
            }
        }
    }

    private static void restoreBaselinePools() {
        for (Map.Entry<String, List<VillagerTrades.ItemListing>> entry : BASELINE_LISTINGS.entrySet()) {
            String key = entry.getKey();
            int separator = key.lastIndexOf('|');
            if (separator <= 0 || separator >= key.length() - 1) {
                continue;
            }

            int level;
            try {
                level = Integer.parseInt(key.substring(separator + 1));
            } catch (NumberFormatException e) {
                continue;
            }

            publishStaticPool(key.substring(0, separator), level, entry.getValue());
        }
    }

    private static void publishStaticPool(String owner, int level, List<VillagerTrades.ItemListing> listings) {
        VillagerTrades.ItemListing[] array = listings.toArray(VillagerTrades.ItemListing[]::new);
        if (VillagerConfig.isWanderingTrader(owner)) {
            VillagerTrades.WANDERING_TRADER_TRADES.put(level, array);
            return;
        }

        ResourceLocation id = KineticResourceIds.tryParse(VillagerConfig.clean(owner));
        if (id == null) {
            return;
        }

        VillagerProfession profession = KineticRegistries.villagerProfessions().get(id);
        var tradesByLevel = VillagerTrades.TRADES.get(profession);
        if (tradesByLevel != null) {
            tradesByLevel.put(level, array);
        }
    }
    //?}

    private static String tradeKey(String owner, int level) {
        String cleanOwner = VillagerConfig.clean(owner);
        return cleanOwner + "|" + VillagerConfig.clampTradeLevel(cleanOwner, level);
    }

    private static String overrideKey(String owner, int level, int vanillaIndex) {
        return tradeKey(owner, level) + "|" + Math.max(0, vanillaIndex);
    }
}

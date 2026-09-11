package dev.xyat.contentstudio.villager.trade;

import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = VillagerModule.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillagerTradeRegistry {
    private static final Map<String, VillagerConfig.TradeGroup> ACTIVE_GROUPS = new HashMap<>();
    private static final Map<String, List<VillagerConfig.TradeOfferData>> ACTIVE_OFFERS = new HashMap<>();
    private static final Map<String, VillagerConfig.VanillaTradeOverride> ACTIVE_OVERRIDES = new HashMap<>();
    private static final Map<String, List<VillagerTrades.ItemListing>> BASELINE_LISTINGS = new HashMap<>();

    private static boolean sessionPrepared;
    private static MinecraftServer activeServer;

    private VillagerTradeRegistry() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (activeServer != event.getServer()) {
            resetSession();
            activeServer = event.getServer();
        }
        prepareSession();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onVillagerTrades(VillagerTradesEvent event) {
        prepareSession();

        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.getType());
        String owner = professionId.toString();
        for (int level = 1; level <= 5; level++) {
            List<VillagerTrades.ItemListing> trades = event.getTrades().get(level);
            if (trades == null) {
                continue;
            }
            captureBaseline(owner, level, trades);
            applyPublishedPool(owner, level, trades);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWandererTrades(WandererTradesEvent event) {
        prepareSession();

        String owner = VillagerConfig.WANDERING_TRADER_ID;
        captureBaseline(owner, 1, event.getGenericTrades());
        captureBaseline(owner, 2, event.getRareTrades());
        applyPublishedPool(owner, 1, event.getGenericTrades());
        applyPublishedPool(owner, 2, event.getRareTrades());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer == null || activeServer == event.getServer()) {
            restoreBaselinePools();
            resetSession();
            activeServer = null;
        }
    }

    public static boolean isSessionUnavailable() {
        return !sessionPrepared;
    }

    public static void reloadFromDisk() {
        VillagerConfig.load();
        if (sessionPrepared) {
            rebuildActiveState();
            publishAllLive();
        }
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
        return getActiveTradeGroup(owner, level) != null
                || hasActiveVanillaOverrides(owner, level)
                || hasActiveCustomOffers(owner, level);
    }

    public static VillagerTrades.ItemListing[] getBaselineListings(String owner, int level) {
        List<VillagerTrades.ItemListing> listings = BASELINE_LISTINGS.get(tradeKey(owner, level));
        return listings == null ? null : listings.toArray(VillagerTrades.ItemListing[]::new);
    }

    public static boolean applyAndSaveLive(
            List<String> groups,
            List<String> offers,
            List<String> overrides
    ) {
        if (!sessionPrepared || activeServer == null || !VillagerConfig.areValidTradeLists(groups, offers, overrides)) {
            return false;
        }

        List<String> oldGroups = new ArrayList<>(VillagerConfig.villagerTradeGroups);
        List<String> oldOffers = new ArrayList<>(VillagerConfig.villagerTradeOffers);
        List<String> oldOverrides = new ArrayList<>(VillagerConfig.villagerDefaultTradeOverrides);
        try {
            VillagerConfig.replaceTradeLists(groups, offers, overrides);
            VillagerConfig.save();
            rebuildActiveState();
            publishAllLive();
            return true;
        } catch (Exception e) {
            VillagerConfig.replaceTradeLists(oldGroups, oldOffers, oldOverrides);
            rebuildActiveState();
            VillagerModule.LOGGER.error("Failed to apply villager trade configuration live", e);
            return false;
        }
    }

    private static void resetSession() {
        sessionPrepared = false;
        ACTIVE_GROUPS.clear();
        ACTIVE_OFFERS.clear();
        ACTIVE_OVERRIDES.clear();
        BASELINE_LISTINGS.clear();
    }

    private static void prepareSession() {
        if (sessionPrepared) {
            return;
        }

        VillagerConfig.load();
        rebuildActiveState();
        sessionPrepared = true;
    }

    private static void rebuildActiveState() {
        ACTIVE_GROUPS.clear();
        ACTIVE_OFFERS.clear();
        ACTIVE_OVERRIDES.clear();

        for (String line : VillagerConfig.villagerTradeGroups) {
            VillagerConfig.TradeGroup group = VillagerConfig.TradeGroup.parse(line);
            if (group != null) {
                ACTIVE_GROUPS.put(tradeKey(group.profession, group.level), group);
            }
        }

        int index = 0;
        for (String line : VillagerConfig.villagerTradeOffers) {
            VillagerConfig.TradeOfferData data = VillagerConfig.TradeOfferData.parse(line, index);
            if (data != null) {
                ACTIVE_OFFERS.computeIfAbsent(tradeKey(data.profession(), data.level()), key -> new ArrayList<>()).add(data);
                index++;
            }
        }

        for (String line : VillagerConfig.villagerDefaultTradeOverrides) {
            VillagerConfig.VanillaTradeOverride override = VillagerConfig.VanillaTradeOverride.parse(line);
            if (override != null) {
                ACTIVE_OVERRIDES.put(overrideKey(override.profession(), override.level(), override.vanillaIndex()), override);
            }
        }
    }

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

        ResourceLocation id = ResourceLocation.tryParse(VillagerConfig.clean(owner));
        if (id == null) {
            return;
        }

        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(id);
        var tradesByLevel = VillagerTrades.TRADES.get(profession);
        if (tradesByLevel != null) {
            tradesByLevel.put(level, array);
        }
    }

    private static String tradeKey(String owner, int level) {
        String cleanOwner = VillagerConfig.clean(owner);
        return cleanOwner + "|" + VillagerConfig.clampTradeLevel(cleanOwner, level);
    }

    private static String overrideKey(String owner, int level, int vanillaIndex) {
        return tradeKey(owner, level) + "|" + Math.max(0, vanillaIndex);
    }
}

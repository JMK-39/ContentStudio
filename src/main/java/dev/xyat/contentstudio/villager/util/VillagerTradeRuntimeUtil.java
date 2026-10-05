package dev.xyat.contentstudio.villager.util;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
//? if <26.1
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

public final class VillagerTradeRuntimeUtil {
    private VillagerTradeRuntimeUtil() {
    }

    public static int getDefaultOfferCount(String owner, int level) {
        if (VillagerConfig.isWanderingTrader(owner)) {
            return VillagerConfig.clampTradeLevel(owner, level) == 1 ? 5 : 1;
        }
        return 2;
    }

    // The vanilla trades of one villager level, by index. Up to 1.21.1 they are the static item listings; 26.1 keeps
    // them in data as the profession's trade set, whose trades build offers from a loot context like vanilla does.
    //? if >=26.1 {
    /*public static int vanillaTradeCount(String owner, int level) {
        return onServerThread() ? vanillaTrades(owner, level).size() : remotePreviews(owner, level).size();
    }

    public static MerchantOffer createVanillaOffer(String owner, int level, int vanillaIndex, Entity entity, RandomSource random) {
        if (entity == null && !onServerThread()) {
            List<MerchantOffer> previews = remotePreviews(owner, level);
            MerchantOffer preview = vanillaIndex < 0 || vanillaIndex >= previews.size() ? null : previews.get(vanillaIndex);
            return preview == null ? null : preview.copy();
        }
        List<net.minecraft.core.Holder<net.minecraft.world.item.trading.VillagerTrade>> trades = vanillaTrades(owner, level);
        if (vanillaIndex < 0 || vanillaIndex >= trades.size()) {
            return null;
        }
        net.minecraft.server.level.ServerLevel serverLevel = entity != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level0
                ? level0 : previewLevel();
        if (serverLevel == null) {
            return null;
        }
        Entity source = entity != null ? entity : net.minecraft.world.entity.EntityType.VILLAGER.create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        if (source == null) {
            return null;
        }
        try {
            net.minecraft.world.level.storage.loot.LootContext context = new net.minecraft.world.level.storage.loot.LootContext.Builder(
                    new net.minecraft.world.level.storage.loot.LootParams.Builder(serverLevel)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, source.position())
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, source)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, net.minecraft.util.Unit.INSTANCE)
                            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.VILLAGER_TRADE))
                    .withOptionalRandomSource(random)
                    .create(java.util.Optional.empty());
            return trades.get(vanillaIndex).value().getOffer(context);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<net.minecraft.core.Holder<net.minecraft.world.item.trading.VillagerTrade>> vanillaTrades(String owner, int level) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.trading.TradeSet> key = tradeSetKey(owner, level);
        net.minecraft.server.level.ServerLevel serverLevel = previewLevel();
        if (key == null || serverLevel == null) {
            return List.of();
        }
        return serverLevel.registryAccess().lookup(net.minecraft.core.registries.Registries.TRADE_SET)
                .flatMap(lookup -> lookup.get(key))
                .map(set -> List.copyOf(set.value().getTrades().stream().toList()))
                .orElse(List.of());
    }

    // The wandering trader's common and uncommon sets are levels 1 and 2; its 26.1 buying set stays vanilla.
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.item.trading.TradeSet> tradeSetKey(String owner, int level) {
        String cleanOwner = VillagerConfig.clean(owner);
        int safeLevel = VillagerConfig.clampTradeLevel(cleanOwner, level);
        if (VillagerConfig.isWanderingTrader(cleanOwner)) {
            return safeLevel == 1 ? net.minecraft.world.item.trading.TradeSets.WANDERING_TRADER_COMMON
                    : net.minecraft.world.item.trading.TradeSets.WANDERING_TRADER_UNCOMMON;
        }
        ResourceLocation id = KineticResourceIds.tryParse(cleanOwner);
        VillagerProfession profession = id == null ? null : KineticRegistries.villagerProfessions().get(id);
        return profession == null ? null : profession.getTrades(safeLevel);
    }

    // Trade sets live on the server: the running (or integrated) server's overworld builds the offers.
    private static net.minecraft.server.level.ServerLevel previewLevel() {
        var server = dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer();
        return server == null ? null : server.overworld();
    }

    private static boolean onServerThread() {
        var server = dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer();
        return server != null && server.isSameThread();
    }

    // Clients have no trade sets, so the server sends the trade editor the preview offers of every owner and level
    // (see VillagerNetwork). A null preview keeps the vanilla index of a trade that could not build an offer.
    private static volatile java.util.Map<String, List<List<MerchantOffer>>> remotePreviews = java.util.Map.of();

    public static void setRemotePreviews(java.util.Map<String, List<List<MerchantOffer>>> previews) {
        remotePreviews = java.util.Map.copyOf(previews);
    }

    private static List<MerchantOffer> remotePreviews(String owner, int level) {
        String cleanOwner = VillagerConfig.clean(owner);
        List<List<MerchantOffer>> levels = remotePreviews.get(cleanOwner);
        int index = VillagerConfig.clampTradeLevel(cleanOwner, level) - 1;
        return levels == null || index >= levels.size() ? List.of() : levels.get(index);
    }

    /^* The preview offers of every villager profession and the wandering trader; list index 0 is level 1. ^/
    public static java.util.Map<String, List<List<MerchantOffer>>> collectPreviews() {
        List<String> owners = new ArrayList<>();
        KineticRegistries.villagerProfessions().ids().forEach(id -> owners.add(id.toString()));
        owners.add(VillagerConfig.WANDERING_TRADER_ID);
        java.util.Map<String, List<List<MerchantOffer>>> result = new java.util.LinkedHashMap<>();
        for (String owner : owners) {
            int maxLevel = VillagerConfig.isWanderingTrader(owner) ? 2 : 5;
            List<List<MerchantOffer>> levels = new ArrayList<>();
            for (int level = 1; level <= maxLevel; level++) {
                List<MerchantOffer> offers = new ArrayList<>();
                int count = vanillaTrades(owner, level).size();
                for (int i = 0; i < count; i++) {
                    offers.add(createPreviewOffer(owner, level, i));
                }
                levels.add(java.util.Collections.unmodifiableList(offers));
            }
            result.put(VillagerConfig.clean(owner), List.copyOf(levels));
        }
        return result;
    }
    *///?} else {
    public static int vanillaTradeCount(String owner, int level) {
        VillagerTrades.ItemListing[] listings = getVanillaListings(owner, level);
        return listings == null ? 0 : listings.length;
    }

    public static VillagerTrades.ItemListing[] getVanillaListings(String owner, int level) {
        String cleanOwner = VillagerConfig.clean(owner);
        int safeLevel = VillagerConfig.clampTradeLevel(cleanOwner, level);

        VillagerTrades.ItemListing[] baseline = VillagerTradeRegistry.getBaselineListings(cleanOwner, safeLevel);
        if (baseline != null) {
            return baseline;
        }

        if (VillagerConfig.isWanderingTrader(cleanOwner)) {
            return VillagerTrades.WANDERING_TRADER_TRADES.get(safeLevel);
        }

        ResourceLocation id = KineticResourceIds.tryParse(cleanOwner);
        if (id == null) {
            return null;
        }

        VillagerProfession profession = KineticRegistries.villagerProfessions().get(id);

        var tradesByLevel = VillagerTrades.TRADES.get(profession);
        if (tradesByLevel == null) {
            return null;
        }
        return tradesByLevel.get(safeLevel);
    }

    public static MerchantOffer createVanillaOffer(String owner, int level, int vanillaIndex, Entity entity, RandomSource random) {
        VillagerTrades.ItemListing[] listings = getVanillaListings(owner, level);
        if (listings == null || vanillaIndex < 0 || vanillaIndex >= listings.length) {
            return null;
        }
        try {
            return listings[vanillaIndex].getOffer(entity, random);
        } catch (Exception ignored) {
            return null;
        }
    }

    //?}

    public static MerchantOffer createPreviewOffer(String owner, int level, int vanillaIndex) {
        return createVanillaOffer(owner, level, vanillaIndex, null, RandomSource.create(1234567L + (long) level * 131L + vanillaIndex * 17L));
    }

    public static void addConfiguredOffers(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, int fallbackCount) {
        String cleanOwner = VillagerConfig.clean(owner);
        int safeLevel = VillagerConfig.clampTradeLevel(cleanOwner, level);
        VillagerConfig.TradeGroup group = VillagerTradeRegistry.getActiveTradeGroup(cleanOwner, safeLevel);
        boolean hasOverrides = VillagerTradeRegistry.hasActiveVanillaOverrides(cleanOwner, safeLevel);

        boolean hasCustomOffers = VillagerTradeRegistry.hasActiveCustomOffers(cleanOwner, safeLevel);

        if (group == null && !hasOverrides && !hasCustomOffers) {
            addVanillaOffers(target, entity, random, cleanOwner, safeLevel, fallbackCount, false);
            return;
        }

        if (group != null && group.disableLevel()) {
            return;
        }

        if (group != null && "add".equals(group.mode)) {
            addVanillaOffers(target, entity, random, cleanOwner, safeLevel, fallbackCount, true);
            addCustomOffers(target, random, cleanOwner, safeLevel, group.offerCount);
            return;
        }

        int count = group == null ? fallbackCount : group.offerCount;
        addCombinedOffers(target, entity, random, cleanOwner, safeLevel, count);
    }

    private static void addVanillaOffers(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, int count, boolean respectOverrides) {
        if (count <= 0) {
            return;
        }

        int vanillaCount = vanillaTradeCount(owner, level);
        if (vanillaCount == 0) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < vanillaCount; i++) {
            int weight = 1;
            if (respectOverrides || VillagerTradeRegistry.hasActiveVanillaOverrides(owner, level)) {
                if (VillagerTradeRegistry.isActiveVanillaTradeDisabled(owner, level, i)) {
                    continue;
                }
                weight = VillagerTradeRegistry.getActiveVanillaTradeWeight(owner, level, i);
            }
            if (weight > 0) {
                candidates.add(Candidate.vanilla(weight, i));
            }
        }

        addCandidates(target, entity, random, owner, level, candidates, count);
    }

    private static void addCustomOffers(MerchantOffers target, RandomSource random, String owner, int level, int count) {
        if (count <= 0) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        for (VillagerConfig.TradeOfferData data : VillagerTradeRegistry.getActiveTradeOffers(owner, level)) {
            if (data.weight() > 0) {
                candidates.add(Candidate.custom(data.weight(), data));
            }
        }

        addCandidates(target, null, random, owner, level, candidates, count);
    }

    private static void addCombinedOffers(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, int count) {
        if (count <= 0) {
            return;
        }

        List<Candidate> candidates = new ArrayList<>();
        int vanillaCount = vanillaTradeCount(owner, level);
        if (vanillaCount > 0) {
            for (int i = 0; i < vanillaCount; i++) {
                if (VillagerTradeRegistry.isActiveVanillaTradeDisabled(owner, level, i)) {
                    continue;
                }
                int weight = VillagerTradeRegistry.getActiveVanillaTradeWeight(owner, level, i);
                if (weight > 0) {
                    candidates.add(Candidate.vanilla(weight, i));
                }
            }
        }

        for (VillagerConfig.TradeOfferData data : VillagerTradeRegistry.getActiveTradeOffers(owner, level)) {
            if (data.weight() > 0) {
                candidates.add(Candidate.custom(data.weight(), data));
            }
        }

        addCandidates(target, entity, random, owner, level, candidates, count);
    }

    private static void addCandidates(MerchantOffers target, Entity entity, RandomSource random, String owner, int level, List<Candidate> candidates, int count) {
        int added = 0;
        while (!candidates.isEmpty() && added < count) {
            int selected = pickWeightedIndex(candidates, random);
            if (selected < 0 || selected >= candidates.size()) {
                break;
            }

            Candidate candidate = candidates.remove(selected);
            MerchantOffer offer;
            if (candidate.customOffer != null) {
                if (hasCustomOffer(target, candidate.customOffer.uniqueId())) {
                    continue;
                }
                offer = candidate.customOffer.createOffer();
            } else {
                offer = createVanillaOffer(owner, level, candidate.vanillaIndex, entity, random);
            }

            if (offer == null) {
                continue;
            }
            target.add(offer);
            added++;
        }
    }

    private static int pickWeightedIndex(List<Candidate> candidates, RandomSource random) {
        long total = 0L;
        for (Candidate candidate : candidates) {
            if (candidate.weight > 0) {
                total += candidate.weight;
            }
        }
        if (total <= 0L) {
            return -1;
        }

        long target = Math.floorMod(random.nextLong(), total);
        long current = 0L;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.weight <= 0) {
                continue;
            }
            current += candidate.weight;
            if (target < current) {
                return i;
            }
        }
        return candidates.size() - 1;
    }

    private static boolean hasCustomOffer(MerchantOffers offers, String id) {
        for (MerchantOffer offer : offers) {
            if (VillagerConfig.hasCustomTradeId(offer, id)) {
                return true;
            }
        }
        return false;
    }

    private record Candidate(int weight, int vanillaIndex, VillagerConfig.TradeOfferData customOffer) {
        private static Candidate vanilla(int weight, int vanillaIndex) {
            return new Candidate(weight, vanillaIndex, null);
        }

        private static Candidate custom(int weight, VillagerConfig.TradeOfferData customOffer) {
            return new Candidate(weight, -1, customOffer);
        }
    }
}

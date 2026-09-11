package dev.xyat.contentstudio.villager.trade;

import dev.xyat.contentstudio.villager.config.VillagerConfig;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.NotNull;

public final class TradeListing implements VillagerTrades.ItemListing {
    private final VillagerConfig.TradeOfferData data;

    public TradeListing(VillagerConfig.TradeOfferData data) {
        this.data = data;
    }

    @Override
    public MerchantOffer getOffer(@NotNull Entity trader, @NotNull RandomSource random) {
        return data.createOffer();
    }
}

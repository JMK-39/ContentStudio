package dev.xyat.contentstudio.villager.mixin;

import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerTradeSourceMixin {
    @Unique
    private int contentstudio_villager$offersReadDepth;

    @Unique
    private boolean contentstudio_villager$refillingLocalOffers;

    @Inject(method = "getOffers", at = @At("HEAD"))
    private void contentstudio_villager$beginOffersRead(CallbackInfoReturnable<MerchantOffers> cir) {
        contentstudio_villager$offersReadDepth++;
    }

    @Inject(method = "getOffers", at = @At("RETURN"))
    private void contentstudio_villager$filterExternalTrades(CallbackInfoReturnable<MerchantOffers> cir) {
        // updateTrades reads getOffers again, including the existing late-override snapshot. Only the outer read
        // filters and fills, after generation has finished, and filling never calls getOffers itself.
        contentstudio_villager$offersReadDepth--;
        if (contentstudio_villager$offersReadDepth != 0 || contentstudio_villager$refillingLocalOffers
                || !VillagerConfig.isLocalCustomTradesOnly() || VillagerTradeRegistry.isSessionUnavailable()) {
            return;
        }
        AbstractVillager trader = (AbstractVillager) (Object) this;
        if (trader.level().isClientSide() || (!(trader instanceof Villager) && !(trader instanceof WanderingTrader))) {
            return;
        }
        MerchantOffers offers = cir.getReturnValue();
        if (offers == null) {
            return;
        }

        contentstudio_villager$refillingLocalOffers = true;
        try {
            // Keep the existing marked offers themselves so uses, demand, price and restock metadata survive.
            offers.removeIf(offer -> !VillagerConfig.isKineticCustomTrade(offer));
            if (!offers.isEmpty()) {
                return;
            }
            if (trader instanceof Villager villager) {
                var villagerData = villager.getVillagerData();
                ResourceLocation professionId = KineticRegistries.villagerProfessions().id(villagerData.getProfession());
                if (professionId == null) {
                    return;
                }
                String owner = professionId.toString();
                int maxLevel = VillagerConfig.clampTradeLevel(owner, villagerData.getLevel());
                for (int level = 1; level <= maxLevel; level++) {
                    VillagerTradeRuntimeUtil.addConfiguredOffers(offers, villager, villager.getRandom(), owner, level,
                            VillagerTradeRuntimeUtil.getDefaultOfferCount(owner, level));
                }
            } else if (trader instanceof WanderingTrader wanderingTrader) {
                String owner = VillagerConfig.WANDERING_TRADER_ID;
                for (int level = 1; level <= 2; level++) {
                    VillagerTradeRuntimeUtil.addConfiguredOffers(offers, wanderingTrader, wanderingTrader.getRandom(), owner, level,
                            VillagerTradeRuntimeUtil.getDefaultOfferCount(owner, level));
                }
            }
        } finally {
            contentstudio_villager$refillingLocalOffers = false;
        }
    }
}

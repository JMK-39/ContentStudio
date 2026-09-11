package dev.xyat.contentstudio.villager.mixin;

import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTrader.class)
public abstract class WanderingTraderTweaksMixin extends AbstractVillager {
    public WanderingTraderTweaksMixin(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void contentstudio_villager$applyWanderingTraderTrades(CallbackInfo ci) {
        if (VillagerTradeRegistry.isSessionUnavailable()) {
            return;
        }

        String owner = VillagerConfig.WANDERING_TRADER_ID;
        boolean levelOneChanged = VillagerTradeRegistry.hasActiveLevelChanges(owner, 1);
        boolean levelTwoChanged = VillagerTradeRegistry.hasActiveLevelChanges(owner, 2);
        if (!levelOneChanged && !levelTwoChanged) {
            return;
        }

        MerchantOffers offers = this.getOffers();
        offers.clear();
        VillagerTradeRuntimeUtil.addConfiguredOffers(
                offers,
                this,
                this.getRandom(),
                owner,
                1,
                VillagerTradeRuntimeUtil.getDefaultOfferCount(owner, 1)
        );
        VillagerTradeRuntimeUtil.addConfiguredOffers(
                offers,
                this,
                this.getRandom(),
                owner,
                2,
                VillagerTradeRuntimeUtil.getDefaultOfferCount(owner, 2)
        );
        ci.cancel();
    }
}

package dev.xyat.contentstudio.villager.mixin;

import dev.xyat.contentstudio.villager.util.IMerchantOfferAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin implements IMerchantOfferAccess {
    @Unique
    private String contentstudio_villager$customTradeId = "";

    @Unique
    private boolean contentstudio_villager$restockDisabled = false;

//? if <1.21 {
    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void contentstudio_villager$readCustomData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("contentstudioTradeId", Tag.TAG_STRING)) {
            this.contentstudio_villager$customTradeId = tag.getString("contentstudioTradeId");
        }
        if (tag.contains("contentstudioNoRestock", Tag.TAG_BYTE)) {
            this.contentstudio_villager$restockDisabled = tag.getBoolean("contentstudioNoRestock");
        }
    }

    @Inject(method = "createTag", at = @At("RETURN"))
    private void contentstudio_villager$writeCustomData(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (this.contentstudio_villager$customTradeId != null && !this.contentstudio_villager$customTradeId.isEmpty()) {
            tag.putString("contentstudioTradeId", this.contentstudio_villager$customTradeId);
        }
        if (this.contentstudio_villager$restockDisabled) {
            tag.putBoolean("contentstudioNoRestock", true);
        }
    }

//?}

//? if >=1.21 {
/*    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final @org.spongepowered.asm.mixin.Mutable
    public static com.mojang.serialization.Codec<MerchantOffer> CODEC;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void contentstudio_villager$extendCodec(CallbackInfo ci) {
        CODEC = dev.xyat.contentstudio.villager.util.TradeOfferCodecs.wrap(CODEC);
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private void contentstudio_villager$copyMeta(CallbackInfoReturnable<MerchantOffer> cir) {
        IMerchantOfferAccess copy = (IMerchantOfferAccess) cir.getReturnValue();
        copy.contentstudio_villager$setCustomTradeId(this.contentstudio_villager$customTradeId);
        copy.contentstudio_villager$setRestockDisabled(this.contentstudio_villager$restockDisabled);
    }
*///?}

    @Inject(method = "resetUses", at = @At("HEAD"), cancellable = true)
    private void contentstudio_villager$cancelRestock(CallbackInfo ci) {
        if (this.contentstudio_villager$restockDisabled) {
            ci.cancel();
        }
    }

    @Override
    public void contentstudio_villager$setCustomTradeId(String id) {
        this.contentstudio_villager$customTradeId = id == null ? "" : id;
    }

    @Override
    public String contentstudio_villager$getCustomTradeId() {
        return this.contentstudio_villager$customTradeId;
    }

    @Override
    public void contentstudio_villager$setRestockDisabled(boolean disabled) {
        this.contentstudio_villager$restockDisabled = disabled;
    }

//? if >=1.21 {
/*    @Override
    public boolean contentstudio_villager$isRestockDisabled() { return this.contentstudio_villager$restockDisabled; }
*///?}
}

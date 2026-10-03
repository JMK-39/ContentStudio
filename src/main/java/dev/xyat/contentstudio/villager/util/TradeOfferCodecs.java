//? if >=1.21 {
/*package dev.xyat.contentstudio.villager.util;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.trading.MerchantOffer;

/^** Adds addon metadata to the native offer map without changing its item component codecs. *^/
public final class TradeOfferCodecs {
    private TradeOfferCodecs() {}
    public static Codec<MerchantOffer> wrap(Codec<MerchantOffer> delegate) {
        return new Codec<>() {
            @Override public <T> DataResult<Pair<MerchantOffer,T>> decode(DynamicOps<T> ops,T input) {
                return delegate.decode(ops,input).map(pair -> {
                    IMerchantOfferAccess access = (IMerchantOfferAccess) pair.getFirst();
                    ops.getMap(input).result().ifPresent(map -> {
                        T id = map.get("contentstudioTradeId");
                        T disabled = map.get("contentstudioNoRestock");
                        access.contentstudio_villager$setCustomTradeId(id == null ? "" : Codec.STRING.parse(ops,id).result().orElse(""));
                        access.contentstudio_villager$setRestockDisabled(disabled != null && Codec.BOOL.parse(ops,disabled).result().orElse(false));
                    });
                    return pair;
                });
            }
            @Override public <T> DataResult<T> encode(MerchantOffer offer,DynamicOps<T> ops,T prefix) {
                IMerchantOfferAccess access = (IMerchantOfferAccess) offer;
                DataResult<T> result = delegate.encode(offer,ops,prefix);
                String id = access.contentstudio_villager$getCustomTradeId();
                if (id != null && !id.isEmpty()) result = result.flatMap(map -> ops.mergeToMap(map,ops.createString("contentstudioTradeId"),ops.createString(id)));
                if (access.contentstudio_villager$isRestockDisabled()) result = result.flatMap(map -> ops.mergeToMap(map,ops.createString("contentstudioNoRestock"),ops.createBoolean(true)));
                return result;
            }
        };
    }
}
*///?}

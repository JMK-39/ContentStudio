//? if >=1.21 {
/*package dev.xyat.contentstudio.loot.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(MappedRegistry.class)
public interface LootRegistryAccessor<T> {
    @Accessor("byValue")
    Map<T, Holder.Reference<T>> contentstudio_loots$getByValue();
    @Accessor("toId")
    Reference2IntMap<T> contentstudio_loots$getToId();
}

*///?} else {

//?}

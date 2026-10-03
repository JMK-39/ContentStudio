//? if >=1.21 {
/*package dev.xyat.contentstudio.loot.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Holder.Reference.class)
public interface LootHolderAccessor<T> {
    @Invoker("bindValue")
    void contentstudio_loots$bindValue(T value);
}

*///?} else {

//?}

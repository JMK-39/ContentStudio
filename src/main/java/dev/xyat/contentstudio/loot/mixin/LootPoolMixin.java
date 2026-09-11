package dev.xyat.contentstudio.loot.mixin;

import dev.xyat.contentstudio.loot.server.LootTableOverrideStore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.function.Consumer;

@Mixin(LootPool.class)
public abstract class LootPoolMixin {
    @Inject(
            method = "addRandomItems(Ljava/util/function/Consumer;Lnet/minecraft/world/level/storage/loot/LootContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void contentstudio_loots$skipNestedGlobalAppend(
            Consumer<ItemStack> consumer,
            LootContext context,
            CallbackInfo ci
    ) {
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(context);
        if (LootTableOverrideStore.shouldSkipGlobalAppendPool((LootPool) (Object) this)) {
            ci.cancel();
        }
    }
}

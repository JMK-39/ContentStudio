package dev.xyat.contentstudio.loot.mixin;

import dev.xyat.contentstudio.loot.server.LootTableOverrideStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.function.Consumer;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Inject(
            method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At("HEAD")
    )
    private void contentstudio_loots$enterLootTableGeneration(
            LootContext context,
            Consumer<ItemStack> consumer,
            CallbackInfo ci
    ) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(ci);
        LootTableOverrideStore.enterLootTableGeneration();
    }

    @Inject(
            method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At("RETURN")
    )
    private void contentstudio_loots$exitLootTableGeneration(
            LootContext context,
            Consumer<ItemStack> consumer,
            CallbackInfo ci
    ) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(ci);
        LootTableOverrideStore.exitLootTableGeneration();
    }

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private void contentstudio_loots$filterGlobalChestRemoval(
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {
        Objects.requireNonNull(context);
        LootTable table = (LootTable) (Object) this;
        if (!LootTableOverrideStore.shouldFilterGlobalRemoval(table)) {
            return;
        }
        ObjectArrayList<ItemStack> items = cir.getReturnValue();
        if (items != null && !items.isEmpty()) {
            items.removeIf(LootTableOverrideStore::isGloballyRemoved);
        }
    }

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;J)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private void contentstudio_loots$filterGlobalChestRemovalSeeded(
            LootParams params,
            long seed,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {
        Objects.requireNonNull(params);
        LootTable table = (LootTable) (Object) this;
        if (!LootTableOverrideStore.shouldFilterGlobalRemoval(table)) {
            return;
        }
        ObjectArrayList<ItemStack> items = cir.getReturnValue();
        if (items != null && !items.isEmpty()) {
            items.removeIf(LootTableOverrideStore::isGloballyRemoved);
        }
    }

    @Redirect(
            method = "getAvailableSlots(Lnet/minecraft/world/Container;Lnet/minecraft/util/RandomSource;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/Util;shuffle(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/util/RandomSource;)V"
            )
    )
    private void contentstudio_loots$skipAvailableSlotShuffle(ObjectArrayList<Integer> slots, RandomSource random) {
        Objects.requireNonNull(slots);
        Objects.requireNonNull(random);
    }

    @Redirect(
            method = "shuffleAndSplitItems(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;ILnet/minecraft/util/RandomSource;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/Util;shuffle(Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/util/RandomSource;)V"
            )
    )
    private void contentstudio_loots$skipFinalLootShuffle(ObjectArrayList<ItemStack> items, RandomSource random) {
        Objects.requireNonNull(items);
        Objects.requireNonNull(random);
    }
}

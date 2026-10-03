//? if >=1.21 {
/*
package dev.xyat.contentstudio.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.Objects;
import java.util.stream.Stream;

// DataComponentPredicate cannot encode removal of a default component. Keep the native patch itself.
public record ComponentPatchIngredient(Holder<Item> item, DataComponentPatch components, boolean strict)
        implements ICustomIngredient {
    public static final MapCodec<ComponentPatchIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("item").forGetter(ComponentPatchIngredient::item),
            dev.xyat.contentstudio.item.ItemData.COMPONENT_PATCH_CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ComponentPatchIngredient::components),
            Codec.BOOL.optionalFieldOf("strict", false).forGetter(ComponentPatchIngredient::strict)
    ).apply(instance, ComponentPatchIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentPatchIngredient> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.ITEM), ComponentPatchIngredient::item,
            DataComponentPatch.STREAM_CODEC, ComponentPatchIngredient::components,
            ByteBufCodecs.BOOL, ComponentPatchIngredient::strict,
            ComponentPatchIngredient::new
    );

    public ComponentPatchIngredient {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(components, "components");
    }

    public static Ingredient of(boolean strict, ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("component ingredient is empty");
        return new ComponentPatchIngredient(stack.getItemHolder(), stack.getComponentsPatch(), strict).toVanilla();
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(item)) return false;
        if (strict) return ItemStack.isSameItemSameComponents(stack, displayStack());
        for (var entry : components.entrySet()) {
            Object actual = stack.get(entry.getKey());
            var required = entry.getValue();
            if (required.isEmpty() ? actual != null : !Objects.equals(actual, required.get())) return false;
        }
        return true;
    }

    private ItemStack displayStack() {
        return new ItemStack(item, 1, components);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(displayStack());
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ComponentRecipeIngredients.COMPONENT_PATCH.get();
    }
}
*///?}
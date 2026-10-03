//? if >=1.21 {
/*
package dev.xyat.contentstudio.recipe;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ComponentRecipeIngredients {
    private static final DeferredRegister<IngredientType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, "contentstudio");
    public static final DeferredHolder<IngredientType<?>, IngredientType<ComponentPatchIngredient>> COMPONENT_PATCH =
            TYPES.register("component_patch", () -> new IngredientType<>(ComponentPatchIngredient.CODEC, ComponentPatchIngredient.STREAM_CODEC));

    private ComponentRecipeIngredients() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
*///?}
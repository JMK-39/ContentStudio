package dev.xyat.contentstudio.recipe.compat.jei;

import com.google.gson.JsonObject;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeCompat;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
//? if >=1.21 {
/*import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
*///?}

/** Decodes a private draft, without changing the live recipe manager or JEI's recipe collection. */
final class JeiDraftRecipes {
    private JeiDraftRecipes() { }
    static Object decode(ResourceLocation id, JsonObject json) {
        var level = KineticClientRuntime.currentLevel();
        if (level == null) throw new IllegalArgumentException("Recipe registry is not ready");
//? if >=26.1 {
/*        var value = Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), json).getOrThrow();
        NativeRecipeCompat.initialize(value);
        return new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id), value);
*///?} else if >=1.21 {
/*        var value = Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), json).getOrThrow();
        NativeRecipeCompat.initialize(value);
        return new RecipeHolder<>(id, value);
*///?} else {
        var value = RecipeManager.fromJson(id, json, net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY);
        NativeRecipeCompat.initialize(value);
        return value;
//?}
    }
}

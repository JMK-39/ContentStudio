//? if >=26.1 {
/*
package dev.xyat.contentstudio.recipe.mixin;

import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 26.1 reads recipe JSON in prepare and hands it to ModifyRecipeJsonsEvent; RecipeMemoryManager filters it there.
@Mixin(RecipeManager.class)
public abstract class RecipeManagerOriginalRecipesMixin extends net.neoforged.neoforge.resource.ContextAwareReloadListener
        implements RecipeMemoryManager.RecipeMapAccess {
    @Shadow @Final private HolderLookup.Provider registries;
    @Shadow private RecipeMap recipes;

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
            at = @At("HEAD"))
    private void contentstudio$beginRecipeScan(ResourceManager resources, ProfilerFiller profiler,
                                               CallbackInfoReturnable<RecipeMap> callback) {
        RecipeMemoryManager.beginRecipeScan((RecipeManager) (Object) this, resources, registries, getContext());
    }

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
            at = @At("RETURN"))
    private void contentstudio$endRecipeScan(ResourceManager resources, ProfilerFiller profiler,
                                             CallbackInfoReturnable<RecipeMap> callback) {
        RecipeMemoryManager.endRecipeScan();
    }

    @Override
    public void contentstudio$replaceRecipes(RecipeMap recipes) {
        this.recipes = recipes;
    }
}

*///?} else if >=1.21 {
/*
package dev.xyat.contentstudio.recipe.mixin;

import com.google.gson.JsonElement;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerOriginalRecipesMixin extends net.neoforged.neoforge.resource.ContextAwareReloadListener {


    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void contentstudio$filterOriginalRecipes(Map<ResourceLocation, JsonElement> recipes,
                                                     ResourceManager resources, ProfilerFiller profiler,
                                                     CallbackInfo callback) {
        RecipeMemoryManager.beforeScriptRecipes((RecipeManager) (Object) this, recipes, resources, makeConditionalOps(), getRegistryLookup(), getContext());
    }
}

*///?} else {
package dev.xyat.contentstudio.recipe.mixin;

import com.google.gson.JsonElement;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerOriginalRecipesMixin {
    @Shadow @Final private ICondition.IContext context;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void contentstudio$filterOriginalRecipes(Map<ResourceLocation, JsonElement> recipes,
                                                     ResourceManager resources, ProfilerFiller profiler,
                                                     CallbackInfo callback) {
        RecipeMemoryManager.beforeScriptRecipes((RecipeManager) (Object) this, recipes, resources, context);
    }
}

//?}

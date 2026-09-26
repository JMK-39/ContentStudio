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

// KubeJS uses priority 1100 and cancels apply at HEAD. Lower mixin priority places
// this HEAD callback before its callback in the transformed method.
@Mixin(value = RecipeManager.class, priority = 1000)
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

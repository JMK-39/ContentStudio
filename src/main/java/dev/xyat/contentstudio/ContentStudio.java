package dev.xyat.contentstudio;

import dev.xyat.contentstudio.loot.LootModule;
import dev.xyat.contentstudio.recipe.RecipeModule;
import dev.xyat.contentstudio.tooltip.TooltipModule;
import dev.xyat.contentstudio.villager.VillagerModule;
import net.minecraftforge.fml.common.Mod;

@Mod(ContentStudio.MODID)
public final class ContentStudio {
    public static final String MODID = "contentstudio";

//? if >=1.21 {
/*    public ContentStudio(net.neoforged.bus.api.IEventBus modBus) {
        dev.xyat.contentstudio.recipe.ComponentRecipeIngredients.register(modBus);
        RecipeModule.install();
        LootModule.install();
        VillagerModule.install();
        TooltipModule.install();
    }
*///?} else {
    public ContentStudio() {
        RecipeModule.install();
        LootModule.install();
        VillagerModule.install();
        TooltipModule.install();
    }
//?}
}

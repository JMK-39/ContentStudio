package dev.xyat.contentstudio;

import dev.xyat.contentstudio.recipe.RecipeModule;
import dev.xyat.contentstudio.loot.LootModule;
import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.tooltip.TooltipModule;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ContentStudio.MODID)
public final class ContentStudio {
    public static final String MODID = "contentstudio";

    public ContentStudio(FMLJavaModLoadingContext context) {
        new RecipeModule(context);
        new LootModule(context);
        new VillagerModule(context);
        new TooltipModule(context);
    }
}

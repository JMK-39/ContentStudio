package dev.xyat.contentstudio;

import dev.xyat.contentstudio.loot.LootModule;
import dev.xyat.contentstudio.recipe.RecipeModule;
import dev.xyat.contentstudio.tooltip.TooltipModule;
import dev.xyat.contentstudio.villager.VillagerModule;
import net.minecraftforge.fml.common.Mod;

@Mod(ContentStudio.MODID)
public final class ContentStudio {
    public static final String MODID = "contentstudio";

    public ContentStudio() {
        new RecipeModule();
        new LootModule();
        new VillagerModule();
        new TooltipModule();
    }
}

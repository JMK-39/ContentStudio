package dev.xyat.contentstudio.recipe;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticMenuTypes;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.registry.KineticRegistryHandle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

public final class RecipeRegistry {
    public static final KineticRegistryHandle<MenuType<RecipeMenu>> HUB_MENU = KineticMenuTypes.register(
            KineticResourceIds.of(RecipeModule.MODID, "recipe_hub"),
            (windowId, inventory, data) -> new RecipeMenu(windowId, inventory)
    );

    public static final KineticRegistryHandle<MenuType<UniversalRecipeMenu>> EDITOR_MENU = KineticMenuTypes.register(
            KineticResourceIds.of(RecipeModule.MODID, "recipehud"),
            UniversalRecipeMenu::new
    );

    private RecipeRegistry() {
    }

    public static void register() {
    }

    public enum EditorType {
        CRAFTING("crafting", "minecraft:crafting_table", "minecraft:crafting_shaped"),
        FURNACE("furnace", "minecraft:furnace", "minecraft:smelting"),
        BLAST("blast", "minecraft:blast_furnace", "minecraft:blasting"),
        SMOKER("smoker", "minecraft:smoker", "minecraft:smoking"),
        SMITHING("smithing", "minecraft:smithing_table", "minecraft:smithing"),
        STONECUTTER("stonecutter", "minecraft:stonecutter", "minecraft:stonecutting");

        public final String id;
        public final String iconId;
        public final String recipeType;

        EditorType(String id, String iconId, String recipeType) {
            this.id = id;
            this.iconId = iconId;
            this.recipeType = recipeType;
        }

        public Item getIcon() {
            return KineticRegistries.items().get(KineticResourceIds.parse(iconId));
        }

        public Component getTitle() {
            return Component.translatable("gui.contentstudio.recipe.recipehud." + id);
        }
    }
}

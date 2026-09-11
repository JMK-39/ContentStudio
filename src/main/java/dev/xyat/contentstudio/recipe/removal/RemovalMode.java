package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.network.chat.Component;

public enum RemovalMode {
    MOD("mod", "gui.contentstudio.recipe.recipehud.mode.mod"),
    RECIPE_ID("id", "gui.contentstudio.recipe.recipehud.mode.id"),
    OUTPUT("output", "gui.contentstudio.recipe.recipehud.mode.output"),
    TAG("tag", "gui.contentstudio.recipe.recipehud.mode.tag"),
    TYPE("type", "gui.contentstudio.recipe.recipehud.mode.type");

    public final String key;
    public final String translationKey;

    RemovalMode(String key, String translationKey) {
        this.key = key;
        this.translationKey = translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }
}

package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.List;

public record RemovalEntry(RemovalMode mode, String value, String comment,
                           List<ResourceLocation> excludedRecipeIds) {
    public record Key(RemovalMode mode, String value) { }

    public RemovalEntry {
        comment = comment != null ? comment : "";
        excludedRecipeIds = List.copyOf(new LinkedHashSet<>(excludedRecipeIds == null ? List.of() : excludedRecipeIds));
    }

    public RemovalEntry(RemovalMode mode, String value, String comment) {
        this(mode, value, comment, List.of());
    }

    public Key key() {
        return new Key(mode, value);
    }

}

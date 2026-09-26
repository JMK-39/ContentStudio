package dev.xyat.contentstudio.recipe.removal;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record RemovalCandidate(ResourceLocation id, @Nullable ResourceLocation recipeType,
                               @Nullable ResourceLocation outputItemId, Set<ResourceLocation> outputTagIds) {
    public RemovalCandidate {
        outputTagIds = outputTagIds == null ? Set.of() : Set.copyOf(outputTagIds);
    }
}

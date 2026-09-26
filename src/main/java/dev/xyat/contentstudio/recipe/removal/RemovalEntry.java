package dev.xyat.contentstudio.recipe.removal;

import dev.xyat.kineticcore.api.network.NetworkBuffer;
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

    public void toNetwork(NetworkBuffer buffer) {
        buffer.writeEnum(mode);
        buffer.writeUtf(value);
        buffer.writeUtf(comment);
        buffer.writeVarInt(excludedRecipeIds.size());
        excludedRecipeIds.forEach(buffer::writeResourceLocation);
    }

    public static RemovalEntry fromNetwork(NetworkBuffer buffer) {
        RemovalMode mode = buffer.readEnum(RemovalMode.class);
        String value = buffer.readUtf();
        String comment = buffer.readUtf();
        int size = buffer.readVarInt();
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid exclusion count: " + size);
        java.util.ArrayList<ResourceLocation> exclusions = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) exclusions.add(buffer.readResourceLocation());
        return new RemovalEntry(mode, value, comment, exclusions);
    }
}

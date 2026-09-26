package dev.xyat.contentstudio.recipe.removal;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Predicate;

/** Cleans rules whose explicitly targeted output item is no longer registered. */
public final class RemovalRulePruner {
    private RemovalRulePruner() { }

    public static List<RemovalEntry> removeMissingOutputItems(List<RemovalEntry> rules,
                                                                Predicate<ResourceLocation> itemExists) {
        return rules.stream().filter(rule -> {
            if (rule.mode() != RemovalMode.OUTPUT) return true;
            ResourceLocation id = KineticResourceIds.tryParse(rule.value());
            return id != null && itemExists.test(id);
        }).toList();
    }
}

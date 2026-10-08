package dev.xyat.contentstudio.recipe.nativeedit;

import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;

/** Registry-backed workstation lookup; missing mod items are never fabricated. */
public final class NativeRecipeStations {
    private NativeRecipeStations() { }
    public static String icon(String serializer) {
        for(String candidate:NativeRecipeLayout.stationCandidates(serializer)) {
            var id=KineticResourceIds.tryParse(candidate);
            if(id!=null && KineticRegistries.items().contains(id))return candidate;
        }
        return "minecraft:knowledge_book";
    }
}

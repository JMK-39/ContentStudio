package dev.xyat.contentstudio.villager.client;

import dev.xyat.kineticcore.api.client.input.KineticKeyBindings;
//? if >=26.1
/*import dev.xyat.kineticcore.api.resource.KineticResourceIds;*/

/** Optional shortcut; users choose a key without displacing an existing binding. */
public final class VillagerClientKeyBindings {
    private static KineticKeyBindings.Binding binding;

    private VillagerClientKeyBindings() { }

    public static void register() {
        if (binding != null) return;
        binding = KineticKeyBindings.builder("key.contentstudio.villager.trade_editor")
                //? if >=26.1 {
                /*.category(KineticResourceIds.of("contentstudio", "villager"))
                *///?} else {
                .category("key.contentstudio.villager.category")
                //?}
                .context(KineticKeyBindings.Context.UNIVERSAL)
                .keyboard(KineticKeyBindings.Key.UNKNOWN)
                .onPressed(VillagerClientActions::toggleTradeEditor)
                .register();
    }
}

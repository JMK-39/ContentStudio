package dev.xyat.contentstudio.tooltip;

import com.google.gson.reflect.TypeToken;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Renders server-synchronized tooltip rules without a script engine. */
public final class TooltipRuntimeClient {
    private static volatile Map<String, List<TooltipManager.TooltipRule>> rules = Map.of();

    private TooltipRuntimeClient() {
    }

    public static void register() {
        KineticClientEvents.onLogout(() -> rules = Map.of());
        KineticClientEvents.onItemTooltip(context -> {
            if (context.itemStack().isEmpty()) return;
            ResourceLocation id = KineticRegistries.items().id(context.itemStack().getItem());
            if (id == null) return;
            List<TooltipManager.TooltipRule> itemRules = rules.get(id.toString());
            if (itemRules == null) return;

            boolean shift = KineticClientRuntime.shiftModifierDown();
            boolean alt = KineticClientRuntime.altModifierDown();
            List<Component> tooltip = context.tooltip();
            for (TooltipManager.TooltipRule rule : itemRules) {
                if (!matchesKeys(rule.keyCond, shift, alt)) continue;
                Component line = Component.translatable(rule.text);
                if (rule.mode == 0 && rule.line < tooltip.size()) {
                    tooltip.set(rule.line, line);
                } else {
                    tooltip.add(line);
                }
            }
        });
    }

    public static void acceptRules(String json) {
        try {
            Map<String, List<TooltipManager.TooltipRule>> next = TooltipManager.GSON.fromJson(
                    json, new TypeToken<Map<String, List<TooltipManager.TooltipRule>>>() {}.getType()
            );
            if (!TooltipManager.hasValidStructure(next)) {
                TooltipModule.LOGGER.warn("Rejected invalid synchronized tooltip rules");
                return;
            }
            rules = TooltipManager.copyData(next);
        } catch (RuntimeException exception) {
            TooltipModule.LOGGER.warn("Rejected malformed synchronized tooltip rules", exception);
        }
    }

    static boolean matchesKeys(int condition, boolean shift, boolean alt) {
        return switch (condition) {
            case 0 -> true;
            case 1 -> shift && !alt;
            case 2 -> !shift && alt;
            case 3 -> shift && alt;
            default -> false;
        };
    }
}

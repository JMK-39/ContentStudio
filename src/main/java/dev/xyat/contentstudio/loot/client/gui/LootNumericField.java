package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.screen.KineticScreen;
import dev.xyat.kineticcore.api.client.widget.input.KineticNumericFields.NumericEditBox;
import net.minecraft.network.chat.Component;

final class LootNumericField {
    enum Type {
        PROBABILITY,
        RATIO,
        POSITIVE_DECIMAL,
        NON_NEGATIVE_DECIMAL,
        POSITIVE_INTEGER,
        NON_NEGATIVE_INTEGER
    }

    private LootNumericField() {
    }

    static NumericEditBox add(
            KineticScreen screen,
            int x,
            int y,
            int width,
            String value,
            Type type,
            String tooltipKey
    ) {
        Component tooltip = Component.translatable(tooltipKey);
        NumericEditBox box = switch (type) {
            case PROBABILITY, RATIO -> screen.addDecimalField(
                    x, y, width, Component.empty(), false, 0D, 1D, null, tooltip
            );
            case POSITIVE_DECIMAL, NON_NEGATIVE_DECIMAL -> screen.addDecimalField(
                    x, y, width, Component.empty(), false, 0D, null, null, tooltip
            );
            case POSITIVE_INTEGER, NON_NEGATIVE_INTEGER -> screen.addIntegerField(
                    x, y, width, Component.empty(), false, 0, null, null, tooltip
            );
        };
        box.setMaxLength(16);
        box.setValue(value);
        return box;
    }

    static Integer integer(NumericEditBox box) {
        if (box != null) {
            Integer value = box.getIntValue();
            if (value != null) return value;
        }
        notifyInvalid("msg.contentstudio.loot.loots.number.integer");
        return null;
    }

    static Double decimal(NumericEditBox box) {
        if (box != null) {
            Double value = box.getDoubleValue();
            if (value != null) return value;
        }
        notifyInvalid("msg.contentstudio.loot.loots.number.decimal");
        return null;
    }

    static void notifyInvalid(String key) {
        KineticOverlays.toast(
                "loots_number_input",
                Component.translatable(key),
                KineticOverlays.Position.BOTTOM_CENTER,
                3000,
                0,
                -30
        );
    }
}

package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.NumericEditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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

    static EditBox create(
            Font font,
            int x,
            int y,
            int width,
            String value,
            Type type,
            String tooltipKey
    ) {
        NumericEditBox box = switch (type) {
            case PROBABILITY, RATIO -> NumericEditBox.decimal(
                    font, x, y, width, 20,
                    Component.empty(), false, 0D, 1D
            );
            case POSITIVE_DECIMAL, NON_NEGATIVE_DECIMAL -> NumericEditBox.decimal(
                    font, x, y, width, 20,
                    Component.empty(), false, 0D, null
            );
            case POSITIVE_INTEGER, NON_NEGATIVE_INTEGER -> NumericEditBox.integer(
                    font, x, y, width, 20,
                    Component.empty(), false, 0, null
            );
        };

        box.setMaxLength(16);
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return box;
    }

    static Integer integer(EditBox box) {
        if (box instanceof NumericEditBox numeric) {
            Integer value = numeric.getIntValue();
            if (value != null) return value;
        }

        notifyInvalid("msg.contentstudio.loot.loots.number.integer");
        return null;
    }

    static Double decimal(EditBox box) {
        if (box instanceof NumericEditBox numeric) {
            Double value = numeric.getDoubleValue();
            if (value != null) return value;
        }

        notifyInvalid("msg.contentstudio.loot.loots.number.decimal");
        return null;
    }

    static void notifyInvalid(String key) {
        GuiOverlay.toast(
                "loots_number_input",
                Component.translatable(key),
                GuiOverlay.Position.BOTTOM_CENTER,
                3000,
                0,
                -30
        );
    }
}

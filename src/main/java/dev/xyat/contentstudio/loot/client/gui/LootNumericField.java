package dev.xyat.contentstudio.loot.client.gui;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.ui.NumberType;
import dev.xyat.kineticcore.api.client.gui.widget.KineticNumberField;
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

    static KineticNumberField add(
            KineticUi ui,
            int x,
            int y,
            int width,
            String value,
            String defaultText,
            Type type,
            String tooltipKey
    ) {
        Component tooltip = KineticI18n.translatable(tooltipKey);
        KineticNumberField box = switch (type) {
            case PROBABILITY, RATIO -> ui.numberField(x, y, width, NumberType.DECIMAL)
                    .allowNegative(false).range(0D, 1D).tooltip(tooltip).build();
            case POSITIVE_DECIMAL, NON_NEGATIVE_DECIMAL -> ui.numberField(x, y, width, NumberType.DECIMAL)
                    .allowNegative(false).range(0D, null).tooltip(tooltip).build();
            case POSITIVE_INTEGER, NON_NEGATIVE_INTEGER -> ui.numberField(x, y, width, NumberType.INT)
                    .allowNegative(false).range(0, null).tooltip(tooltip).build();
        };
        box.limitTextLength(16);
        box.setTextValue(value);
        // 等于载入值时黑色，修改后绿色 / Black while equal to the loaded value, green once modified.
        box.setDefaultText(defaultText);
        return box;
    }

    static Integer integer(KineticNumberField box) {
        if (box != null) {
            Integer value = box.getIntValue();
            if (value != null) return value;
        }
        notifyInvalid("msg.contentstudio.loot.loots.number.integer");
        return null;
    }

    static Double decimal(KineticNumberField box) {
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
                KineticI18n.translatable(key),
                KineticOverlays.Position.BOTTOM_CENTER,
                3000,
                0,
                -30
        );
    }
}

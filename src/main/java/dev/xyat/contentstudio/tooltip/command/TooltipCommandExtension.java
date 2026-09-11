package dev.xyat.contentstudio.tooltip.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.contentstudio.tooltip.TooltipModule;
import dev.xyat.contentstudio.tooltip.TooltipManager;
import net.minecraft.commands.CommandSourceStack;

public final class TooltipCommandExtension implements KTCommandExtension {
    private TooltipCommandExtension() {}

    public static void install() {
        KTCommandApi.register(TooltipModule.MODID, new TooltipCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        TooltipManager.load();
    }
}

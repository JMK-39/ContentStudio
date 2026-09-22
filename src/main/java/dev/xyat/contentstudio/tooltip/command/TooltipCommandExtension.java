package dev.xyat.contentstudio.tooltip.command;

import dev.xyat.contentstudio.tooltip.TooltipManager;
import dev.xyat.contentstudio.tooltip.TooltipModule;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.kineticcore.api.command.KineticCommands;
import net.minecraft.commands.CommandSourceStack;

public final class TooltipCommandExtension implements CommandExtension {
    private TooltipCommandExtension() {}

    public static void install() {
        KineticCommands.registerExtension(TooltipModule.MODID, new TooltipCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        TooltipManager.load();
    }
}

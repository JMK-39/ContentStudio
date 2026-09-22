package dev.xyat.contentstudio.villager.command;

import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.kineticcore.api.command.CommandExtension;
import dev.xyat.kineticcore.api.command.KineticCommands;
import net.minecraft.commands.CommandSourceStack;

public final class VillagerCommandExtension implements CommandExtension {
    private VillagerCommandExtension() {
    }

    public static void install() {
        KineticCommands.registerExtension(VillagerModule.MODID, new VillagerCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        VillagerTradeRegistry.reloadFromDisk();
    }
}

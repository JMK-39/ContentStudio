package dev.xyat.contentstudio.villager.command;

import dev.xyat.kineticcore.command.KTCommandApi;
import dev.xyat.kineticcore.command.KTCommandExtension;
import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import net.minecraft.commands.CommandSourceStack;

public final class VillagerCommandExtension implements KTCommandExtension {
    private VillagerCommandExtension() {
    }

    public static void install() {
        KTCommandApi.register(VillagerModule.MODID, new VillagerCommandExtension());
    }

    @Override
    public void reload(CommandSourceStack source) {
        VillagerTradeRegistry.reloadFromDisk();
    }
}

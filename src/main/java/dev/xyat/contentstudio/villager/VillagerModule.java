package dev.xyat.contentstudio.villager;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.villager.command.VillagerCommandExtension;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.config.VillagerConfigGui;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.config.server.KTServerConfigSpec;
import dev.xyat.kineticcore.api.runtime.KineticEnvironment;
import org.slf4j.Logger;

public final class VillagerModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VillagerModule() {
        VillagerConfig.load();
        KTServerConfigApi.register(KTServerConfigSpec.builder(VillagerConfigGui.PAGE_ID)
                .doubleValue(
                        "tick_interval",
                        () -> VillagerConfig.villagerTickInterval / 20.0D,
                        value -> VillagerConfig.villagerTickInterval = Math.max(1, Math.min(600, (int) Math.round(value * 20.0D))),
                        0.05D,
                        30.0D
                )
                .booleanValue(
                        "trade_update_protection",
                        () -> VillagerConfig.enableVillagerTradeUpdateProtection,
                        value -> VillagerConfig.enableVillagerTradeUpdateProtection = value
                )
                .booleanValue(
                        "follow",
                        () -> VillagerConfig.enableVillagerFollow,
                        value -> VillagerConfig.enableVillagerFollow = value
                )
                .onSave(VillagerConfig::save)
                .build());
        KTServerConfigApi.registerActionPage(VillagerConfigGui.EDITOR_PAGE_ID);
        VillagerTradeRegistry.register();
        VillagerNetwork.init();
        VillagerCommandExtension.install();
        KineticEnvironment.runOnClient(() -> VillagerConfigGui::load);
    }
}

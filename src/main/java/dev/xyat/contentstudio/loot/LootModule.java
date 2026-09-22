package dev.xyat.contentstudio.loot;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.loot.config.LootConfigGui;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticEnvironment;
import org.slf4j.Logger;

public final class LootModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LootModule() {
        KTServerConfigApi.registerActionPage("contentstudio:loots");
        LootNetwork.register();
        KineticEnvironment.runOnClient(() -> LootConfigGui::load);
    }
}

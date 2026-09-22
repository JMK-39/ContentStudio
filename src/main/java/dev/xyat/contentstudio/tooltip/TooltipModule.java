package dev.xyat.contentstudio.tooltip;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.tooltip.command.TooltipCommandExtension;
import dev.xyat.contentstudio.tooltip.config.TooltipConfigGui;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticEnvironment;
import dev.xyat.kineticcore.api.server.event.KineticServerEvents;
import dev.xyat.kineticcore.api.event.KineticEventPriority;
import org.slf4j.Logger;

public final class TooltipModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TooltipModule() {
        TooltipManager.load();
        KTServerConfigApi.registerActionPage(TooltipConfigGui.PAGE_ID);
        TooltipNetwork.register();
        KineticServerEvents.onPlayerLogin(KineticEventPriority.NORMAL, TooltipNetwork::sendRulesTo);
        TooltipCommandExtension.install();
        KineticEnvironment.runOnClient(() -> () -> {
            TooltipConfigGui.load();
            TooltipRuntimeClient.register();
        });
    }
}

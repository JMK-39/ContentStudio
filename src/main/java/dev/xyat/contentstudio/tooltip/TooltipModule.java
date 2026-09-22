package dev.xyat.contentstudio.tooltip;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.tooltip.command.TooltipCommandExtension;
import dev.xyat.contentstudio.tooltip.config.TooltipConfigGui;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticEnvironment;
import org.slf4j.Logger;

public final class TooltipModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TooltipModule() {
        TooltipManager.load();
        KTServerConfigApi.registerActionPage(TooltipConfigGui.PAGE_ID);
        TooltipNetwork.register();
        TooltipCommandExtension.install();
        KineticEnvironment.runOnClient(() -> TooltipConfigGui::load);
    }
}

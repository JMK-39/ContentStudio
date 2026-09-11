package dev.xyat.contentstudio.tooltip;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.tooltip.TooltipManager;
import dev.xyat.contentstudio.tooltip.TooltipNetwork;
import dev.xyat.contentstudio.tooltip.command.TooltipCommandExtension;
import dev.xyat.contentstudio.tooltip.config.TooltipConfigGui;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public final class TooltipModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TooltipModule(FMLJavaModLoadingContext context) {
        TooltipManager.load();
        KTServerConfigApi.registerActionPage(TooltipConfigGui.PAGE_ID);
        TooltipNetwork.register();
        TooltipCommandExtension.install();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TooltipConfigGui::load);
    }
}

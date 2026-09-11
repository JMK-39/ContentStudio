package dev.xyat.contentstudio.loot;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.loot.config.LootConfigGui;
import dev.xyat.contentstudio.loot.network.LootNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public final class LootModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LootModule(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        KTServerConfigApi.registerActionPage("contentstudio:loots");
        LootNetwork.register(modEventBus);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> LootConfigGui::load);
    }
}

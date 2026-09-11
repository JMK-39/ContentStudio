package dev.xyat.contentstudio.recipe;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.recipe.RecipeConfigStore;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.config.RecipeConfigGui;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

public final class RecipeModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecipeModule(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        RecipeConfigStore.ensureDatapackFile();
        KTServerConfigApi.registerActionPage("contentstudio:recipehud");
        RecipeRegistry.register(modEventBus);
        RecipeNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RecipeConfigGui::load);
    }
}

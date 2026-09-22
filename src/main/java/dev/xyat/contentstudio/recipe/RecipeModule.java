package dev.xyat.contentstudio.recipe;

import com.mojang.logging.LogUtils;
import dev.xyat.contentstudio.recipe.client.RecipeEditorSetup;
import dev.xyat.contentstudio.recipe.config.RecipeConfigGui;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.config.server.KTServerConfigApi;
import dev.xyat.kineticcore.api.runtime.KineticEnvironment;
import org.slf4j.Logger;

public final class RecipeModule {
    public static final String MODID = "contentstudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecipeModule() {
        RecipeConfigStore.ensureDatapackFile();
        KTServerConfigApi.registerActionPage("contentstudio:recipehud");
        RecipeRegistry.register();
        RecipeMemoryManager.register();
        RecipeNetwork.register();
        KineticEnvironment.runOnClient(() -> () -> {
            RecipeEditorSetup.register();
            RecipeConfigGui.load();
        });
    }
}

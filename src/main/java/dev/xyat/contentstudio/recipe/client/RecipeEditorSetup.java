package dev.xyat.contentstudio.recipe.client;

import dev.xyat.contentstudio.recipe.RecipeModule;
import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.client.gui.RecipeHubScreen;
import dev.xyat.contentstudio.recipe.client.gui.RecipeScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = RecipeModule.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RecipeEditorSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 绑定 HUB 菜单 -> HUB 界面
            MenuScreens.register(RecipeRegistry.HUB_MENU.get(), RecipeHubScreen::new);
            // 绑定 编辑器 菜单 -> 编辑器 界面
            MenuScreens.register(RecipeRegistry.EDITOR_MENU.get(), RecipeScreen::new);
        });
    }
}

package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.RecipeRegistry;
import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import net.minecraft.world.item.ItemStack;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** The original six workstations keep their own existing native editors. */
public final class VanillaRecipeHubPage extends KineticPage {
    public VanillaRecipeHubPage(){super(tr("vanilla"));setPausesGame(false);}
    @Override protected void build(KineticUi ui) {
        int i=0;for(var type:RecipeRegistry.EditorType.values()) {
            ui.itemButton(40+i%3*188,98+i/3*48,NativeRecipeStationMenu.CARD_WIDTH,new ItemStack(type.getIcon())).text(type.getTitle()).tooltip(type.getTitle())
                    .onClick(()->RecipeNetwork.requestEdit("",type.name(),-1)).build();i++;
        }
        ui.button(40,60,128).text(tr("back")).onClick(this::close).build();
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panelAlt(g,22,54,596,226);g.scrollingText(title(),176,66,430,0xFFFFFFFF,false);
    }
}

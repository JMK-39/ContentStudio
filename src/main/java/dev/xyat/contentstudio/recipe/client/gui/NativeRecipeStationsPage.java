package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.JsonArray;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.input.*;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.network.chat.Component;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

public final class NativeRecipeStationsPage extends KineticPage {
    private final NativeRecipeStationMenu menu;
    public NativeRecipeStationsPage() {this("","",null);}
    NativeRecipeStationsPage(String mod,String group,JsonArray rows) {
        this(mod,group,rows,false);
    }
    NativeRecipeStationsPage(String mod,String group,JsonArray rows,boolean creating) {
        super(mod.isEmpty()?tr(creating?"new_recipe":"stations"):NativeRecipeStationMenu.modLabel(mod));
        setPausesGame(false);menu=new NativeRecipeStationMenu(this::rebuild,this::openChild,this::isAttached,mod,group,rows,creating);
    }
    public static NativeRecipeStationsPage creation() {return new NativeRecipeStationsPage("","",null,true);}
    @Override protected void build(KineticUi ui) {
        // The cards filter while typing; there is no separate search button.
        ui.textField(48,4,200).value(menu.query).maxLength(256).placeholder(tr("station_search")).onChange(menu::searchLive).build();
        menu.build(ui,4,28,632,328);
        ui.button(4,4,40).text(tr("back")).onClick(this::close).build();
        ui.button(564,4,72).text(tr("all_recipes")).onClick(()->openChild(new NativeRecipeBrowserPage())).build();
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,0,0,640,360);KineticTheme.separator(g,4,24,632);g.scrollingTextCentered(title(),320,8,2*(320-252),0xFFFFAA00,false);menu.render(g,mx,my);
    }
    @Override protected boolean onMouseScroll(ScrollInput i){return menu.wheel(i);}
    @Override protected boolean onMouseClick(MouseInput i){return menu.click(i);}
    @Override protected boolean onMouseDrag(MouseDragInput i){return menu.drag(i);}
    @Override protected boolean onMouseRelease(MouseInput i){return menu.release(i);}
}

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
        super(mod.isEmpty()?tr(creating?"new_recipe":"stations"):Component.literal(KineticPlatform.displayName(mod)));
        setPausesGame(false);menu=new NativeRecipeStationMenu(this::rebuild,this::openChild,this::isAttached,mod,group,rows,creating);
    }
    public static NativeRecipeStationsPage creation() {return new NativeRecipeStationsPage("","",null,true);}
    @Override protected void build(KineticUi ui) {
        ui.textField(22,42,470).value(menu.query).maxLength(256).placeholder(tr("station_search")).onChange(v->menu.query=v).build();
        ui.button(498,42,120).text(tr("search_button")).onClick(menu::search).build();
        menu.build(ui,22,76,596,210);
        ui.button(22,320,120).text(tr("back")).onClick(this::close).build();
        ui.button(458,320,160).text(tr("all_recipes")).onClick(()->openChild(new NativeRecipeBrowserPage())).build();
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,14,14,612,332);g.scrollingText(title(),22,24,596,0xFFFFAA00,false);menu.render(g,mx,my);
    }
    @Override protected boolean onMouseScroll(ScrollInput i){return menu.wheel(i);}
    @Override protected boolean onMouseClick(MouseInput i){return menu.click(i);}
    @Override protected boolean onMouseDrag(MouseDragInput i){return menu.drag(i);}
    @Override protected boolean onMouseRelease(MouseInput i){return menu.release(i);}
}

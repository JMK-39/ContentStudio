package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.network.chat.Component;
import java.util.*;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Any number of Create steps stays editable at full icon size, on whole rows. */
final class NativeRecipeSequencePage extends KineticPage {
    private final NativeRecipeFieldsPage.Session session;
    private final List<String> sequence;
    private int page;
    NativeRecipeSequencePage(NativeRecipeFieldsPage.Session session) {
        super(tr("sequence"));this.session=session;sequence=NativeRecipeLayout.append(session.viewPath,"sequence");setPausesGame(false);
    }
    private int count() {return session.document.at(sequence).getAsJsonArray().size();}
    private List<String> path(int i) {return NativeRecipeLayout.append(sequence,Integer.toString(i));}
    private Component name(String type) {
        var stacks=NativeRecipeStacks.read(new com.google.gson.JsonPrimitive(NativeRecipeStations.icon(type)));
        return stacks.isEmpty()?Component.literal(type):stacks.get(0).getHoverName();
    }
    @Override protected void build(KineticUi ui) {
        page=Math.min(page,Math.max(0,(count()-1)/8));
        ui.button(22,42,160).text(tr("add_step")).tooltip(tr("sequence_hint")).onClick(()->
            openContextMenu(22,60,NativeRecipeSequenceEdits.TYPES.stream().map(type->KineticOverlays.MenuItem.action(name(type),()->{
                try {NativeRecipeSequenceEdits.add(session.document,session.viewPath,type);page=(count()-1)/8;rebuild();}
                catch(RuntimeException error){KineticOverlays.toast(tr("error",Component.literal(error.getMessage())));}
            })).toList(),180)).build();
        for(int row=0;row<8;row++) {
            int i=page*8+row;if(i>=count())break;int y=78+row*28;
            String type=session.document.at(NativeRecipeLayout.append(path(i),"type")).getAsString();
            ui.button(56,y+3,150).text(name(type)).tooltip(tr("edit_step",i+1)).onClick(()->openChild(new NativeRecipeFieldsPage(session,path(i)))).build();
            var ingredientPath=NativeRecipeLayout.append(path(i),"ingredients");
            ui.button(212,y+3,132).text(tr("materials")).onClick(()->openChild(new NativeRecipeFieldsPage(session,ingredientPath))).build();
            ui.button(404,y+3,36).text(Component.literal("+")).tooltip(tr("duplicate_step")).onClick(()->{session.document.duplicate(path(i));page=(count()-1)/8;rebuild();}).build();
            ui.button(444,y+3,36).text(Component.literal("↑")).tooltip(tr("move_up")).enabled(i>0).onClick(()->{session.document.move(path(i),i-1);rebuild();}).build();
            ui.button(484,y+3,36).text(Component.literal("↓")).tooltip(tr("move_down")).enabled(i+1<count()).onClick(()->{session.document.move(path(i),i+1);rebuild();}).build();
            ui.button(524,y+3,94).text(tr("remove_slot")).onClick(()->{session.document.remove(path(i));rebuild();}).build();
        }
        ui.button(22,18,90).text(tr("back")).onClick(this::close).build();
        ui.button(404,320,50).text(Component.literal("<")).enabled(page>0).onClick(()->{page--;rebuild();}).build();
        ui.button(568,320,50).text(Component.literal(">")).enabled((page+1)*8<count()).onClick(()->{page++;rebuild();}).build();
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,14,14,612,332);g.scrollingText(title(),120,24,498,0xFFFFAA00,false);
        for(int row=0;row<8;row++) {
            int i=page*8+row;if(i>=count())break;int y=78+row*28;
            g.fill(20,y,622,y+26,row%2==0?0xFF292929:0xFF222222);
            var type=session.document.at(NativeRecipeLayout.append(path(i),"type")).getAsString();
            var stacks=NativeRecipeStacks.read(new com.google.gson.JsonPrimitive(NativeRecipeStations.icon(type)));
            KineticTheme.itemSlot(g,24,y+3,20,false);if(!stacks.isEmpty())g.item(stacks.get(0),26,y+5);
            g.scrollingTextCentered(Component.literal(Integer.toString(i+1)),376,y+8,48,0xFFFFFFFF,false);
        }
        g.scrollingTextCentered(Component.literal((page+1)+" / "+Math.max(1,(count()+7)/8)),511,326,102,0xFFFFFFFF,false);
    }
}

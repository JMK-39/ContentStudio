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
    // One step per row under the header: icon, "N. name" (opens the step), materials, copy / up / down, delete.
    private static final int PER_PAGE=12, ROW_PITCH=26, LIST_Y=28;
    private static int rowY(int row) {return LIST_Y+row*ROW_PITCH;}
    @Override protected void build(KineticUi ui) {
        page=Math.min(page,Math.max(0,(count()-1)/PER_PAGE));
        ui.button(4,4,40).text(tr("back")).onClick(this::close).build();
        ui.button(500,4,20).text(Component.literal("<")).enabled(page>0).onClick(()->{page--;rebuild();}).build();
        ui.button(556,4,20).text(Component.literal(">")).enabled((page+1)*PER_PAGE<count()).onClick(()->{page++;rebuild();}).build();
        ui.button(580,4,56).text(tr("add_step")).tooltip(tr("sequence_hint")).onClick(()->
            openContextMenu(580,26,NativeRecipeSequenceEdits.TYPES.stream().map(type->KineticOverlays.MenuItem.action(name(type),()->{
                mutate(()->{NativeRecipeSequenceEdits.add(session.document,session.viewPath,type);page=(count()-1)/PER_PAGE;});
            })).toList(),180)).build();
        for(int row=0;row<PER_PAGE;row++) {
            int i=page*PER_PAGE+row;if(i>=count())break;int y=rowY(row);
            String type=session.document.at(NativeRecipeLayout.append(path(i),"type")).getAsString();
            ui.button(30,y+2,378).text(Component.literal((i+1)+". ").append(name(type))).tooltip(tr("edit_step",i+1)).onClick(()->openChild(new NativeRecipeFieldsPage(session,path(i)))).build();
            var ingredientPath=NativeRecipeLayout.append(path(i),"ingredients");
            ui.button(412,y+2,72).text(tr("materials")).onClick(()->openChild(new NativeRecipeFieldsPage(session,ingredientPath))).build();
            ui.button(488,y+2,20).text(Component.literal("+")).tooltip(tr("duplicate_step")).onClick(()->mutate(()->{session.document.duplicate(path(i));page=(count()-1)/PER_PAGE;})).build();
            ui.button(510,y+2,20).text(Component.literal("↑")).tooltip(tr("move_up")).enabled(i>0).onClick(()->mutate(()->session.document.move(path(i),i-1))).build();
            ui.button(532,y+2,20).text(Component.literal("↓")).tooltip(tr("move_down")).enabled(i+1<count()).onClick(()->mutate(()->session.document.move(path(i),i+1))).build();
            ui.button(556,y+2,80).text(tr("remove_step")).onClick(()->mutate(()->session.document.remove(path(i)))).build();
        }
    }
    private void mutate(Runnable change) {
        // Array positions back the shared field errors. Do not move/remove their owners while text is invalid.
        if(session.busy)return;
        if(!session.invalid.isEmpty()){KineticOverlays.toast(tr("invalid"));return;}
        try {change.run();rebuild();}
        catch(RuntimeException error){KineticOverlays.toast(tr("error",Component.literal(error.getMessage())));}
    }
    @Override protected void renderTooltips(int mx,int my) {
        for(int row=0;row<PER_PAGE;row++) {
            int i=page*PER_PAGE+row;if(i>=count())break;int y=rowY(row);
            if(mx<4||mx>=26||my<y+1||my>=y+23)continue;
            var type=session.document.at(NativeRecipeLayout.append(path(i),"type")).getAsString();
            var stacks=NativeRecipeStacks.read(new com.google.gson.JsonPrimitive(NativeRecipeStations.icon(type)));
            if(!stacks.isEmpty())showItemTooltip(stacks.get(0));
            return;
        }
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,0,0,640,360);
        KineticTheme.separator(g,4,24,632);
        g.scrollingTextCentered(title(),320,8,2*(500-4-320),0xFFFFAA00,false);
        g.scrollingTextCentered(Component.literal((page+1)+"/"+Math.max(1,(count()+PER_PAGE-1)/PER_PAGE)),538,10,32,0xFFFFFFFF,false);
        for(int row=0;row<PER_PAGE;row++) {
            int i=page*PER_PAGE+row;if(i>=count())break;int y=rowY(row);
            g.fill(4,y,636,y+ROW_PITCH-2,row%2==0?0xFF292929:0xFF222222);
            var type=session.document.at(NativeRecipeLayout.append(path(i),"type")).getAsString();
            var stacks=NativeRecipeStacks.read(new com.google.gson.JsonPrimitive(NativeRecipeStations.icon(type)));
            KineticTheme.itemSlot(g,4,y+1,22,false);if(!stacks.isEmpty())KineticTheme.item(g,stacks.get(0),4,y+1,22,1.0F,false);
        }
    }
}

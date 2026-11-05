package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import java.util.*;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Add optional/defaulted fields or the first member of an empty array without raw JSON. */
final class NativeRecipeAddFieldPage extends KineticPage {
    private static final String[] TYPES={"text","number","boolean","object","array","null"};
    private final NativeRecipeDocument document;
    private final List<String> path;
    private String name="";
    private int type;
    NativeRecipeAddFieldPage(NativeRecipeDocument document,List<String> path) {
        super(tr("add_field"));this.document=document;this.path=List.copyOf(path);setPausesGame(false);
    }
    // A compact dialog centred on the screen: Back, the title and Add on one header row, then the name and the type.
    private static final int LEFT=160, WIDTH=320, BUTTON_W=56;
    private int top() {return document.at(path).isJsonArray()?106:96;}
    private int panelHeight() {return document.at(path).isJsonArray()?148:168;}
    @Override protected void build(KineticUi ui) {
        boolean array=document.at(path).isJsonArray();
        int top=top();
        ui.button(LEFT+6,top+6,40).text(tr("back")).onClick(this::close).build();
        ui.button(LEFT+WIDTH-6-BUTTON_W,top+6,BUTTON_W).text(tr("add_field")).onClick(()->{
            JsonElement value=switch(type) {
                case 1 -> new JsonPrimitive(0);case 2 -> new JsonPrimitive(false);case 3 -> new JsonObject();
                case 4 -> new JsonArray();case 5 -> JsonNull.INSTANCE;default -> new JsonPrimitive("");
            };
            try {document.add(path,name,value);navigateBack();}
            catch(RuntimeException error){KineticOverlays.toast(tr("error",net.minecraft.network.chat.Component.literal(error.getMessage())));}
        }).build();
        int y=top+30;
        if(!array) {ui.textField(LEFT+6,y,WIDTH-12).placeholder(tr("field_name")).value(name).maxLength(256).onChange(value->name=value).build();y+=20;}
        List<SelectionItem> items=new ArrayList<>();
        for(String kind:TYPES)items.add(new SelectionItem(tr("type."+kind),null,null,true,false));
        ui.selectionList(LEFT+6,y,WIDTH-12,112,items).textRows().selected(type).onSelect(index->type=index).build();
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        int top=top();
        KineticTheme.panel(g,LEFT,top,WIDTH,panelHeight());
        // The title is centred on the screen, between Back and Add.
        g.scrollingTextCentered(title(),320,top+10,WIDTH-2*(6+BUTTON_W+4),0xFFFFAA00,false);
        g.hLine(LEFT+4,LEFT+WIDTH-4,top+26,KineticTheme.current().accentHover());
    }
}

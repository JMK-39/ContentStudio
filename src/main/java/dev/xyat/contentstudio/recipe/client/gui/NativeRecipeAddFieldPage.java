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
    @Override protected void build(KineticUi ui) {
        boolean array=document.at(path).isJsonArray();
        if(!array) ui.textField(172,92,296).placeholder(tr("field_name")).value(name).maxLength(256).onChange(value->name=value).build();
        List<SelectionItem> items=new ArrayList<>();
        for(String kind:TYPES)items.add(new SelectionItem(tr("type."+kind),null,null,true,false));
        ui.selectionList(172,124,296,112,items).textRows().selected(type).onSelect(index->type=index).build();
        ui.button(172,70,144).text(tr("back")).onClick(this::close).build();
        ui.button(324,252,144).text(tr("add_field")).onClick(()->{
            JsonElement value=switch(type) {
                case 1 -> new JsonPrimitive(0);case 2 -> new JsonPrimitive(false);case 3 -> new JsonObject();
                case 4 -> new JsonArray();case 5 -> JsonNull.INSTANCE;default -> new JsonPrimitive("");
            };
            try {document.add(path,name,value);navigateBack();}
            catch(RuntimeException error){KineticOverlays.toast(tr("error",net.minecraft.network.chat.Component.literal(error.getMessage())));}
        }).build();
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,160,64,320,224);g.scrollingText(title(),324,76,144,0xFFFFAA00,false);
    }
}
